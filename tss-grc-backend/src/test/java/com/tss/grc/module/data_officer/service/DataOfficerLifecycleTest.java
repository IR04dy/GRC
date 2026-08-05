package com.tss.grc.module.data_officer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.data_officer.dto.DataOfficerDtos.CreateDsrRequest;
import com.tss.grc.module.data_officer.dto.DataOfficerDtos.TransitionBreachRequest;
import com.tss.grc.module.data_officer.dto.DataOfficerDtos.TransitionDpiaRequest;
import com.tss.grc.module.data_officer.dto.DataOfficerDtos.TransitionDsrRequest;
import com.tss.grc.module.data_officer.dto.DataOfficerDtos.TransitionNoticeRequest;
import com.tss.grc.module.data_officer.entity.Entities;
import com.tss.grc.module.data_officer.repository.BreachRepository;
import com.tss.grc.module.data_officer.repository.ConsentRepository;
import com.tss.grc.module.data_officer.repository.DpiaRepository;
import com.tss.grc.module.data_officer.repository.DsrRepository;
import com.tss.grc.module.data_officer.repository.EthicsRepository;
import com.tss.grc.module.data_officer.repository.PrivacyNoticeRepository;
import com.tss.grc.testsupport.StateMachineContract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * F2-03(d) — the Tier-1 floor for {@code data_officer}, the PDPL data-officer workspace. Fourth in
 * the row's order and the second module §6d names as revenue critical-path.
 *
 * <p>2,352 LoC, <b>13 entities in one file</b> (the case that has broken every regex census this
 * repository has attempted), <b>seven</b> state machines across three services sharing one
 * {@code DataOfficerSupport.validate}.
 *
 * <h3>This module is the best-built of the four floored so far, and that is worth stating</h3>
 * <ul>
 *   <li><b>37 {@code hasPermission} guards and zero {@code hasAnyRole}</b> — already the ratified
 *       idiom (F2-05's direction), so it carries none of F2-116's or F2-120's fictional-role class.
 *       Pinned below, because a regression to role names would be invisible.</li>
 *   <li><b>The statutory clocks exist and are read.</b> {@code sla_deadline} (PDPL Art.10, 30 days)
 *       and {@code sdaia_deadline} (72 hours) are computed, the remaining hours are surfaced per
 *       record, and {@code findOverdue} / {@code findOverdueSdaia} are counted on the dashboard —
 *       each correctly excluding terminal statuses, and the breach finder additionally requiring
 *       {@code sdaiaNotifiedAt IS NULL}. This is the direct counter-example to <b>F2-119</b>, which
 *       is the same platform one module over with three deadline claims and no clock at all.</li>
 * </ul>
 *
 * <h3>⚠ What the floor did find — F2-123, and it had to be fixed before the test</h3>
 * The 30-day PDPL Art.10 window was computed from {@code LocalDateTime.now()} — the moment the
 * request was <em>registered</em>, not the moment it was <em>received</em>. Measured in dev: <b>9 of
 * 17</b> requests arrive by a channel where receipt necessarily precedes entry ({@code EMAIL} 3,
 * {@code SDAIA_PORTAL} 2, {@code CALL} 2, {@code BRANCH} 1, {@code SERVICE_CENTER} 1), and all 17
 * carried {@code deadline = entry + 30d}. So a request that arrived last week and was logged today
 * received a fresh statutory window — up to 30 extra days, silently, in the organisation's favour.
 *
 * <p>The breach path next door already did it correctly: {@code sdaiaDeadline} is
 * {@code req.detectedAt().plusHours(72)}, from the caller's real-world moment. <b>That inconsistency
 * inside one module is what identified the defect</b> — same author, same regulation family, opposite
 * treatment of the same question.
 *
 * <p>Fixed before writing the floor, for F2-115's reason: a test asserting
 * {@code slaDeadline == now + 30d} would have pinned the defect as the specification.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataOfficerLifecycleTest {

    private static final Long ORG = 1L;

    @Mock private DsrRepository dsrRepo;
    @Mock private ConsentRepository consentRepo;
    @Mock private PrivacyNoticeRepository noticeRepo;
    @Mock private DpiaRepository dpiaRepo;
    @Mock private BreachRepository breachRepo;
    @Mock private EthicsRepository ethicsRepo;
    @Mock private DataOfficerSupport support;

    @InjectMocks private DataSubjectRightsService dsr;
    @InjectMocks private PrivacyAssuranceService privacy;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(dsrRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(noticeRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dpiaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(breachRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ethicsRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── the machines ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("DSR: every declared transition permitted, every undeclared pair refused")
    void dsrMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            map(DataSubjectRightsService.class, "DSR_ALLOWED"),
            state -> {
                Entities.DsrRequest d = new Entities.DsrRequest();
                d.setId(1L);
                d.setOrganisationId(ORG);
                d.setReference("DSR-1");
                d.setRequestType("ACCESS");
                d.setStatus(state);
                when(dsrRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(d));
            },
            to -> dsr.transitionDsr(1L, transitionDsr(to)),
            "transition", "DSR DSR_ALLOWED");
    }

    @Test
    @DisplayName("PrivacyNotice: every declared transition permitted, every undeclared pair refused")
    void noticeMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            map(DataSubjectRightsService.class, "NOTICE_ALLOWED"),
            state -> {
                Entities.PrivacyNotice n = new Entities.PrivacyNotice();
                n.setId(1L);
                n.setOrganisationId(ORG);
                n.setReference("PN-1");
                n.setChannel("WEB");
                n.setRequiresReConsent(Boolean.FALSE);   // all three NOT NULL; emit() uses Map.of
                n.setStatus(state);
                when(noticeRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(n));
            },
            to -> dsr.transitionNotice(1L, transitionNotice(to)),
            "transition", "Notice NOTICE_ALLOWED");
    }

    @Test
    @DisplayName("DPIA: every declared transition permitted, every undeclared pair refused")
    void dpiaMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            map(PrivacyAssuranceService.class, "DPIA_ALLOWED"),
            state -> {
                Entities.Dpia p = new Entities.Dpia();
                p.setId(1L);
                p.setOrganisationId(ORG);
                p.setReference("DPIA-1");
                p.setIsHighRisk(Boolean.TRUE);   // NOT NULL in the schema; emit() uses Map.of
                p.setStatus(state);
                when(dpiaRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(p));
            },
            to -> privacy.transitionDpia(1L, transitionDpia(to)),
            "transition", "DPIA DPIA_ALLOWED");
    }

    @Test
    @DisplayName("Breach: every declared transition permitted, every undeclared pair refused")
    void breachMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            map(PrivacyAssuranceService.class, "BREACH_ALLOWED"),
            state -> {
                Entities.Breach b = new Entities.Breach();
                b.setId(1L);
                b.setOrganisationId(ORG);
                b.setReference("BR-1");
                b.setStatus(state);
                b.setDetectedAt(LocalDateTime.now().minusHours(4));
                b.setSdaiaDeadline(LocalDateTime.now().plusHours(68));
                b.setIsSpecialCategory(Boolean.FALSE);
                when(breachRepo.findByOrganisationIdAndId(eq(ORG), eq(1L))).thenReturn(Optional.of(b));
            },
            to -> privacy.transitionBreach(1L, transitionBreach(to)),
            "transition", "Breach BREACH_ALLOWED");
    }

    // ── F2-123: the PDPL Art.10 clock runs from receipt ──────────────────────

    @Test
    @DisplayName("F2-123: the 30-day window is measured from RECEIPT when the caller supplies it")
    void theSlaDeadlineRunsFromReceiptNotRegistration() {
        LocalDateTime arrivedByPostLastWeek = LocalDateTime.now().minusDays(9);

        Entities.DsrRequest saved = createDsr(arrivedByPostLastWeek);

        assertThat(saved.getReceivedAt()).isEqualTo(arrivedByPostLastWeek);
        assertThat(saved.getSlaDeadline())
            .as("30 days from receipt, so ~21 days remain — not a fresh 30")
            .isEqualTo(arrivedByPostLastWeek.plusDays(30));
        long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), saved.getSlaDeadline());
        assertThat(daysLeft).as("a request 9 days old must not have 30 days left").isLessThan(22);
    }

    @Test
    @DisplayName("F2-123: absence still means 'arrived now', so no existing caller changes behaviour")
    void anAbsentReceivedAtStillMeansNow() {
        Entities.DsrRequest saved = createDsr(null);

        assertThat(saved.getReceivedAt()).isNotNull()
            .isCloseTo(LocalDateTime.now(), within10Seconds());

        // ⚠ Assert the RELATIONSHIP between the two stored values, never a day count against a
        // freshly-read now(). The first version asserted
        //     DAYS.between(now(), slaDeadline) == 29
        // which passed in isolation and FAILED in the full suite: DAYS.between truncates, so the
        // answer is 30 when no time has elapsed since the stamp and 29 once any has. A day-count
        // assertion against a re-read clock is a coin flip decided by scheduling — the third
        // time-dependent assertion this programme has had to correct, after nca_ecc's rework clock.
        assertThat(saved.getSlaDeadline())
            .as("the platform-channel case is unchanged: exactly 30 days from the stamped receipt")
            .isEqualTo(saved.getReceivedAt().plusDays(30));
    }

    @Test
    @DisplayName("F2-123: a future receipt is refused; a past one is the whole point and is not")
    void receiptCannotBeInTheFuture() {
        assertThatThrownBy(() -> createDsr(LocalDateTime.now().plusDays(1)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("future");

        // Deliberately unbounded in the past: a branch request registered a month late is exactly
        // the case being fixed, and it must be recordable. Its deadline will already have passed,
        // which is the truth and is what findOverdue is for.
        assertThatCode(() -> createDsr(LocalDateTime.now().minusDays(45)))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("The breach's 72-hour SDAIA clock runs from the caller's detection moment")
    void theSdaiaDeadlineRunsFromDetection() {
        // Pinned because this is the behaviour F2-123 was fixed to match, and because 72 hours is a
        // statutory figure: changing either the offset or its origin is a regulatory change and
        // should fail a build rather than pass review unnoticed.
        String src = source("service", "PrivacyAssuranceService.java");
        assertThat(src).contains("req.detectedAt().plusHours(72)");

        String dsrSrc = source("service", "DataSubjectRightsService.java");
        assertThat(dsrSrc).as("and the DSR window must stay anchored to receipt, not to now()")
            .contains("receivedAt.plusDays(30)")
            .doesNotContain("LocalDateTime.now().plusDays(30)");
    }

    // ── the clock is actually consulted, which F2-119's module could not claim ──

    @Test
    @DisplayName("Both overdue finders exclude terminal states, and the breach one requires un-notified")
    void theOverdueQueriesAreCorrectlyBounded() throws IOException {
        String dsrRepoSrc = source("repository", "DsrRepository.java");
        assertThat(dsrRepoSrc).contains("d.slaDeadline < :now")
            .as("a finder that ignored status would report fulfilled requests as overdue")
            .contains("d.status NOT IN ('FULFILLED','REJECTED','CLOSED','PARTIAL')");

        String breachRepoSrc = source("repository", "BreachRepository.java");
        assertThat(breachRepoSrc).contains("b.sdaiaDeadline < :now")
            .as("a breach already notified is not overdue however old the deadline is")
            .contains("b.sdaiaNotifiedAt IS NULL")
            .contains("b.status NOT IN ('CLOSED','FALSE_POSITIVE')");
    }

    // ── the authorisation style, pinned ──────────────────────────────────────

    @Test
    @DisplayName("Every guard is hasPermission — this module must not regress to role names")
    void everyGuardIsCapabilityBased() throws IOException {
        String ctl = source("controller", "DataOfficerController.java");

        long perms = ctl.lines().filter(l -> l.contains("hasPermission(")).count();

        // ⚠ MEASURED, after asserting the wrong thing first. Every guard here reads
        //     hasPermission('PRIVACY',<action>) or hasRole('PLATFORM_ADMIN')
        // so a naive "no hasRole anywhere" claim is FALSE -- all 37 lines contain one. The property
        // that actually matters is narrower and stronger: no guard authorises a BUSINESS role by
        // name. PLATFORM_ADMIN is the platform superuser, it exists in iam.roles, and hasPermission
        // already bypasses for it, so the disjunct is redundant rather than dangerous. What must
        // never appear is a CRO/CIO/SHARIA_OFFICER-style name, which is how `outsourcing` (F2-116)
        // and `sharia` (F2-120) came to authorise nobody.
        java.util.Set<String> namedRoles = new java.util.TreeSet<>();
        java.util.regex.Matcher rm =
            java.util.regex.Pattern.compile("has(?:Any)?Role\\(([^)]*)\\)").matcher(ctl);
        while (rm.find()) {
            for (String part : rm.group(1).split(",")) {
                namedRoles.add(part.trim().replace("'", "").replace("\"", ""));
            }
        }
        assertThat(namedRoles).as(
            "only PLATFORM_ADMIN may be named as a role; anything else is F2-116/F2-120's class")
            .containsExactly("PLATFORM_ADMIN");
        assertThat(perms).as("the module authorises by capability throughout").isEqualTo(37L);
        assertThat(ctl).as("and the capability is the PRIVACY module's, not a per-module invention")
            .contains("hasPermission('PRIVACY','READ')")
            .contains("hasPermission('PRIVACY','WRITE')");

        long mappings = ctl.lines()
            .filter(l -> l.contains("@GetMapping") || l.contains("@PostMapping")
                      || l.contains("@PutMapping") || l.contains("@DeleteMapping")).count();
        assertThat(perms).as("every one of the %d mappings carries a guard", mappings)
            .isEqualTo(mappings);
        assertThat(ctl).as("the entitlement gate must name the SKU that sells this module")
            .contains("@RequireModule(\"PDPL\")");
    }

    @Test
    @DisplayName("All 14 entities really are in one file — the case that broke every regex census")
    void theFourteenEntitiesInOneFileArePinned() throws IOException {
        String src = source("entity", "Entities.java");
        long entities = src.lines().filter(l -> l.trim().startsWith("@Entity")).count();

        // ⚠ MEASURED_BASELINE records "432 entity classes in 419 files, 13 of them in one file
        // (data_officer)". That file is this one, and it is why F2-01's registry is built by
        // reflection: a per-file scan counts this as one entity and undercounts the platform by 12.
        // ⚠ FOURTEEN, not the 13 MEASURED_BASELINE recorded -- corrected there by this slice.
        // The DB agrees: data_officer holds exactly 14 tables. This is the file that makes a per-file
        // entity scan undercount the platform by 13, which is why F2-01's registry is built by
        // reflection and why three separate greps have reported this repo's entity count differently.
        assertThat(entities).as("if this file is ever split, F2-01's reflection census stays correct "
            + "— but the MEASURED_BASELINE note goes stale, so update both").isEqualTo(14);
        assertThat(Entities.class.getDeclaredClasses().length)
            .as("and all 14 are nested classes of one outer type").isGreaterThanOrEqualTo(14);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Entities.DsrRequest createDsr(LocalDateTime receivedAt) {
        when(dsrRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        dsr.createDsr(new CreateDsrRequest("DSR-REVIEW-TEST-1", "ACCESS", "BRANCH",
            "Test Subject", "صاحب بيانات", null, null, null, "summary", "ملخص", null, receivedAt));

        org.mockito.ArgumentCaptor<Entities.DsrRequest> cap =
            org.mockito.ArgumentCaptor.forClass(Entities.DsrRequest.class);
        org.mockito.Mockito.verify(dsrRepo, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        return cap.getValue();
    }

    private static org.assertj.core.data.TemporalUnitOffset within10Seconds() {
        return new org.assertj.core.data.TemporalUnitWithinOffset(10, ChronoUnit.SECONDS);
    }

    private static TransitionDsrRequest transitionDsr(String to) {
        return new TransitionDsrRequest(to, null, null, null, null, null, null, null, null);
    }

    private static TransitionNoticeRequest transitionNotice(String to) {
        return new TransitionNoticeRequest(to, null);
    }

    private static TransitionDpiaRequest transitionDpia(String to) {
        return new TransitionDpiaRequest(to, null, null, null, null, null, null, null);
    }

    private static TransitionBreachRequest transitionBreach(String to) {
        return new TransitionBreachRequest(to, null, null, null, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> map(Class<?> owner, String field) {
        Object v = ReflectionTestUtils.getField(owner, field);
        assertThat(v).as("%s.%s must exist — a rename would otherwise make this class stop checking "
            + "that machine", owner.getSimpleName(), field).isInstanceOf(Map.class);
        return (Map<String, Set<String>>) v;
    }

    private static String source(String pkg, String file) {
        try {
            return Files.readString(Paths.get("src", "main", "java", "com", "tss", "grc", "module",
                "data_officer", pkg, file).toAbsolutePath().normalize(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + pkg + "/" + file, e);
        }
    }
}
