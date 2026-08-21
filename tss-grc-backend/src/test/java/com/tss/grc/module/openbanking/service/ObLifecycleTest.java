package com.tss.grc.module.openbanking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.openbanking.dto.ObDtos.TransitionIncidentRequest;
import com.tss.grc.module.openbanking.dto.ObDtos.TransitionTppRequest;
import com.tss.grc.module.openbanking.entity.ObConsent;
import com.tss.grc.module.openbanking.entity.ObIncident;
import com.tss.grc.module.openbanking.entity.ObTpp;
import com.tss.grc.module.openbanking.repository.ObApiCallRepository;
import com.tss.grc.module.openbanking.repository.ObConsentRepository;
import com.tss.grc.module.openbanking.repository.ObEventRepository;
import com.tss.grc.module.openbanking.repository.ObIncidentRepository;
import com.tss.grc.module.openbanking.repository.ObTppRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.support.GuardExpressions;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * F2-03(e) — the Tier-1 floor for {@code openbanking} (SAMA Open Banking Framework v2). Fifth in the
 * row's order: 1,901 LoC, five entities, <b>two</b> state machines.
 *
 * <h3>⚠ The consent lifecycle is documented and has no machine — F2-125</h3>
 * {@code ObService}'s own class javadoc states
 * {@code Consent: AUTHORISED → ACTIVE → EXPIRED | REVOKED}. There is no {@code CONSENT_ALLOWED} map,
 * no validator call on that path, and <b>nothing anywhere sets {@code status = "EXPIRED"}</b> — the
 * only occurrence of the literal is a read-side terminal check that refuses re-revoking an already
 * finished consent. So an {@code ACTIVE} consent whose {@code expires_at} passes stays {@code ACTIVE}
 * indefinitely, and the register asserts a TPP's access is authorised after it has lapsed.
 *
 * <p>Latent rather than manifest today: measured, <b>0 of 13</b> ACTIVE consents are currently past
 * their expiry. Recorded as mechanism below rather than by inventing a machine — which of a sweep and
 * a derived read is correct is a design decision (S7e argued for deriving, since a stored word can
 * disagree with the rows), and inventing the answer in a test is the F2-14 anti-pattern.
 *
 * <h3>⚠ The 24-hour SAMA obligation is tracked as a binary, not a deadline — F2-126</h3>
 * {@code ObIncident}'s javadoc: an incident affecting the OB service <em>"must be reported to SAMA's
 * OBF unit within 24 hours under OBF v2 §11.4"</em>. Everything needed is on the row —
 * {@code detected_at}, {@code sama_reportable}, {@code sama_notified_at} — and the module does more
 * than {@code sharia} did: the flag is settable at creation, and
 * {@code countByOrganisationIdAndSamaReportableAndStatusNotIn} feeds a {@code samaReportableOpen}
 * dashboard metric. <b>What is missing is only the time dimension.</b> {@code notifySama} records the
 * act and never its timeliness, so an incident notified in two hours and one notified in five days are
 * byte-identical in the record.
 *
 * <p>Measured: incident #1 is flagged reportable, was detected 2026-05-26 and has <b>never</b> been
 * notified — roughly 72 days against a 24-hour obligation. The dashboard can say "1 reportable and
 * open". It cannot say "72 days late", because no code subtracts the two timestamps it already holds.
 *
 * <h3>What this module does right</h3>
 * Revoking or suspending a TPP <b>auto-revokes its active consents</b>, and a burst of more than
 * {@link #bulkRevocationThreshold()} revocations inside an hour writes a distinct audit action —
 * a real control, not a report. Both are covered below.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObLifecycleTest {

    private static final Long ORG = 1L;

    @Mock private ObTppRepository tppRepo;
    @Mock private ObConsentRepository consentRepo;
    @Mock private ObApiCallRepository apiCallRepo;
    @Mock private ObIncidentRepository incidentRepo;
    @Mock private ObEventRepository eventRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private ObService svc;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(tppRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(consentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(incidentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(consentRepo.findByTppIdAndOrganisationIdOrderByGrantedAtDesc(any(), any()))
            .thenReturn(List.of());
        // An incident belongs to a TPP and its timeline write resolves that TPP, so the lookup has to
        // be available on the incident path too — not only on the TPP path. The first version stubbed
        // it per-test and the incident machine failed with "TPP 1 not found in tenant" on every
        // DECLARED transition, which reads like a machine defect and was a fixture gap.
        when(tppRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
            .thenReturn(Optional.of(tpp("ACTIVE")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── the two real machines ────────────────────────────────────────────────

    @Test
    @DisplayName("TPP: every declared transition permitted, every undeclared pair refused")
    void tppMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            map("TPP_ALLOWED"),
            state -> when(tppRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
                .thenReturn(Optional.of(tpp(state))),
            to -> svc.transitionTpp(1L, transitionTpp(to)),
            "transition", "ObTpp TPP_ALLOWED");
    }

    @Test
    @DisplayName("Incident: every declared transition permitted, every undeclared pair refused")
    void incidentMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            map("INCIDENT_ALLOWED"),
            state -> when(incidentRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
                .thenReturn(Optional.of(incident(state))),
            to -> svc.transitionIncident(1L, transitionIncident(to)),
            "transition", "ObIncident INCIDENT_ALLOWED");
    }

    // ── the control that is a control ────────────────────────────────────────

    @Test
    @DisplayName("Revoking a TPP auto-revokes its ACTIVE consents; already-finished ones are left alone")
    void revokingATppRevokesItsActiveConsents() {
        ObConsent active = consent("ACTIVE");
        ObConsent alreadyRevoked = consent("REVOKED");
        alreadyRevoked.setRevokedAt(LocalDateTime.now().minusDays(3));
        ObConsent expired = consent("EXPIRED");
        when(consentRepo.findByTppIdAndOrganisationIdOrderByGrantedAtDesc(eq(1L), eq(ORG)))
            .thenReturn(new ArrayList<>(List.of(active, alreadyRevoked, expired)));
        when(tppRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
            .thenReturn(Optional.of(tpp("ACTIVE")));

        svc.transitionTpp(1L, transitionTpp("REVOKED"));

        assertThat(active.getStatus()).as("an ACTIVE consent must not survive its TPP's revocation")
            .isEqualTo("REVOKED");
        assertThat(active.getRevokedAt()).isNotNull();
        assertThat(alreadyRevoked.getRevokedAt()).as("a finished consent must not be re-stamped")
            .isEqualTo(LocalDateTime.now().minusDays(3).withNano(
                alreadyRevoked.getRevokedAt().getNano()));
        assertThat(expired.getStatus()).as("EXPIRED is terminal and must be left alone")
            .isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("Suspending a TPP also revokes consents — suspension is not a softer state for access")
    void suspendingATppAlsoRevokesConsents() {
        ObConsent active = consent("ACTIVE");
        when(consentRepo.findByTppIdAndOrganisationIdOrderByGrantedAtDesc(eq(1L), eq(ORG)))
            .thenReturn(new ArrayList<>(List.of(active)));
        when(tppRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
            .thenReturn(Optional.of(tpp("ACTIVE")));

        svc.transitionTpp(1L, transitionTpp("SUSPENDED"));
        assertThat(active.getStatus()).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("The bulk-revocation threshold is 100 per hour and is a real audit action")
    void theBulkRevocationThresholdIsPinned() {
        // Pinned because it is a fraud/abuse signal: a TPP whose consents are being mass-revoked is
        // either compromised or exiting, and both are reportable. A silent change to the number or
        // the window changes what the bank notices.
        assertThat(bulkRevocationThreshold()).isEqualTo(100L);
        assertThat(source("service", "ObService.java"))
            .contains("minusHours(1)")
            .contains("OB_CONSENT_BULK_REVOCATION");
    }

    @Test
    @DisplayName("A consent with no caller-supplied expiry gets SAMA OBF's 180-day default")
    void theOneEightyDayDefaultIsApplied() {
        // The javadoc and package-info both state 180 days per SAMA OBF v2; a change to that number
        // is a regulatory change and should fail a build rather than pass review.
        assertThat(source("service", "ObService.java")).contains("LocalDate.now().plusDays(180)");
    }

    // ── F2-125 / F2-126, recorded as mechanism ───────────────────────────────

    /**
     * F2-125. Asserts the <b>absence</b>, not the behaviour — there is no consent machine to test, and
     * writing one here would invent a design decision. When a machine or a derived-expiry read lands,
     * this test fails and that is the signal to close the finding.
     */
    @Test
    @DisplayName("F2-125: the documented consent lifecycle has no machine, and EXPIRED has no producer")
    void theConsentLifecycleIsDocumentedButUnenforced() {
        String svcSrc = source("service", "ObService.java");

        assertThat(svcSrc).as("the four-state consent lifecycle is documented in the class javadoc")
            .contains("Consent: AUTHORISED → ACTIVE → EXPIRED | REVOKED");

        Set<String> machines = new TreeSet<>();
        Matcher m = Pattern.compile("Map<String, Set<String>>\\s+([A-Z_]+)").matcher(svcSrc);
        while (m.find()) {
            machines.add(m.group(1));
        }
        assertThat(machines).as(
            "F2-125 — only TPP and INCIDENT have maps. If a CONSENT_ALLOWED appears, the lifecycle "
            + "may finally be enforced: verify it and close the finding.")
            .containsExactly("INCIDENT_ALLOWED", "TPP_ALLOWED");

        // Nothing WRITES the EXPIRED state. The one occurrence is a read-side terminal check, so the
        // state is reachable only by a direct database write — which is how dev's single EXPIRED row
        // came to exist.
        long writes = svcSrc.lines()
            .filter(l -> l.contains("setStatus(\"EXPIRED\")") || l.contains("\"EXPIRED\");")).count();
        assertThat(writes).as("F2-126/F2-125 — no code path expires a consent").isZero();
    }

    /**
     * F2-126. The obligation is 24 hours; the module tracks it as a binary. Everything needed to
     * compute lateness is already on the row, which is what makes this a small fix and a real gap.
     */
    @Test
    @DisplayName("F2-126: notifySama records the act, never its timeliness against the 24-hour window")
    void theSamaTwentyFourHourWindowIsNotComputed() {
        String svcSrc = source("service", "ObService.java");
        String entSrc = source("entity", "ObIncident.java");

        assertThat(entSrc).as("the obligation is stated on the entity")
            .contains("24 hours under OBF v2 §11.4");

        assertThat(svcSrc).as(
            "F2-126 — if a 24-hour computation now exists, the obligation may be measurable: verify "
            + "it and close the finding. Both timestamps it needs are already stored.")
            .doesNotContain("plusHours(24)")
            .doesNotContain("Duration.between");

        // And the entity has no deadline column, so nothing persists one either.
        assertThat(ObIncident.class.getDeclaredFields())
            .as("no SAMA deadline field exists; lateness would have to be derived on read")
            .noneMatch(f -> f.getName().toLowerCase().contains("deadline"));
    }

    // ── the authorisation branches ───────────────────────────────────────────

    /**
     * F2-127 — <b>CLOSED 2026-08-07</b>, and this test now asserts the closure rather than the defect.
     *
     * <p>What it recorded: measured against {@code iam.roles} (58 roles, dev, 2026-08-06),
     * {@code OPEN_BANKING_OFFICER} — the module's own named owner role — and {@code CRO} did not exist,
     * so WRITE had 2 real holders of the 3 it named and APPROVE 2 of 3, leaving {@code PLATFORM_ADMIN}
     * and {@code COMPLIANCE_OFFICER} as the only parties who could onboard, suspend or revoke a TPP.
     *
     * <p>Closed in three steps, and the order is the whole lesson:
     * <ol>
     *   <li><b>V362</b> seated {@code OPEN_BANKING_OFFICER} and catalogued {@code OPENBANKING}
     *       READ/WRITE/APPROVE. Immediately effective for {@code hasAnyRole}, which compares names.</li>
     *   <li><b>V368</b> revoked {@code OPENBANKING:APPROVE} from {@code OPEN_BANKING_OFFICER}, because
     *       V362's classifier had derived that grant from a transition-shaped method on the WRITE tier
     *       and no APPROVE guard here has ever named the role. Inert when written.</li>
     *   <li><b>F2-145(e) slice 2</b> converted all 18 guards to {@code hasPermission}, which is what made
     *       steps 1 and 2 decide requests instead of describing an intention — and deleted {@code CRO}'s
     *       three recitations outright, since a converted guard recites no names at all.</li>
     * </ol>
     *
     * <p>⚠ <b>This test used to pin the two ghost names as PRESENT.</b> It survived its own fix only
     * because its assertion message named the exit condition ("when these are created, or the guards
     * convert to hasPermission under F2-05, close the finding and update this") — so the failure read as
     * "the fix landed, update me" instead of "the conversion broke something". A test that pins a defect
     * must state how it ends, or it outlives the fix and starts reading as a requirement (F2-115's shape).
     */
    @Test
    @DisplayName("F2-127 closed: the guard constants are permissions, and recite no role name at all")
    void theGuardConstantsAreConvertedToPermissions() {
        String ctl = source("controller", "ObController.java");

        assertThat(permissionIn(ctl, "READ_GUARD")).isEqualTo("OPENBANKING:READ");
        assertThat(permissionIn(ctl, "WRITE_GUARD")).isEqualTo("OPENBANKING:WRITE");
        assertThat(permissionIn(ctl, "APPROVE_GUARD")).isEqualTo("OPENBANKING:APPROVE");

        // The ghost CRO is gone by DELETION, not by seating: a converted guard recites no names. Assert
        // it over the declarations themselves rather than the file, because the controller's comment
        // block still explains in prose what it was converted from — and a substring assertion over raw
        // source would either fail on that documentation or pass because of it.
        Matcher decls = Pattern.compile(
            "private static final String\\s+\\w+\\s*=\\s*((?:\\s*\\+?\\s*\"[^\"]*\")+)\\s*;").matcher(ctl);
        int found = 0;
        while (decls.find()) {
            found++;
            assertThat(decls.group(1))
                .as("guard constant #%d still authorises by role name; F2-127 closed by converting "
                    + "every one of them", found)
                .doesNotContain("hasAnyRole")
                .doesNotContain("hasRole(");
        }
        assertThat(found).as("a declaration regex that matches nothing passes forever").isEqualTo(3);
    }

    @Test
    @DisplayName("Every one of the 18 mappings carries a guard, and only the reviewed constants")
    void everyEndpointIsGuarded() {
        String ctl = source("controller", "ObController.java");
        long mappings = ctl.lines()
            .filter(l -> l.contains("@GetMapping") || l.contains("@PostMapping")
                      || l.contains("@PutMapping") || l.contains("@DeleteMapping")).count();
        long guards = ctl.lines().filter(l -> l.contains("@PreAuthorize")).count();

        assertThat(mappings).isEqualTo(18L);
        assertThat(guards).as("all %d mappings guarded", mappings).isEqualTo(mappings);
        assertThat(ctl).as("the entitlement gate must name the SKU that sells this module")
            .contains("@RequireModule(\"SAUDI_FS\")");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ObTpp tpp(String status) {
        ObTpp t = new ObTpp();
        t.setId(1L);
        t.setOrganisationId(ORG);
        t.setReference("TPP-REVIEW-TEST-1");
        t.setRoles("AISP");            // emit() uses Map.of, which throws on a null value
        t.setStatus(status);
        return t;
    }

    private ObIncident incident(String status) {
        ObIncident i = new ObIncident();
        i.setId(1L);
        i.setOrganisationId(ORG);
        i.setReference("OBI-REVIEW-TEST-1");
        i.setSeverity("HIGH");
        i.setTppId(1L);
        i.setDetectedAt(LocalDateTime.now().minusHours(3));
        i.setStatus(status);
        return i;
    }

    private ObConsent consent(String status) {
        ObConsent c = new ObConsent();
        c.setId(1L);
        c.setOrganisationId(ORG);
        c.setTppId(1L);
        c.setStatus(status);
        c.setExpiresAt(LocalDate.now().plusDays(180));
        return c;
    }

    private static TransitionTppRequest transitionTpp(String to) {
        TransitionTppRequest r = new TransitionTppRequest();
        r.setToStatus(to);
        return r;
    }

    private static TransitionIncidentRequest transitionIncident(String to) {
        TransitionIncidentRequest r = new TransitionIncidentRequest();
        r.setToStatus(to);
        return r;
    }

    private static long bulkRevocationThreshold() {
        Object v = ReflectionTestUtils.getField(ObService.class, "BULK_REVOCATION_THRESHOLD");
        assertThat(v).as("BULK_REVOCATION_THRESHOLD must exist").isInstanceOf(Long.class);
        return (Long) v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> map(String field) {
        Object v = ReflectionTestUtils.getField(ObService.class, field);
        assertThat(v).as("ObService.%s must exist — a rename would make this class stop checking "
            + "that machine", field).isInstanceOf(Map.class);
        return (Map<String, Set<String>>) v;
    }

    private static String source(String pkg, String file) {
        try {
            return Files.readString(Paths.get("src", "main", "java", "com", "tss", "grc", "module",
                "openbanking", pkg, file).toAbsolutePath().normalize(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + pkg + "/" + file, e);
        }
    }

    /**
     * The {@code MODULE:ACTION} a guard constant resolves to. Replaces {@code rolesIn}, which parsed the
     * {@code hasAnyRole} name list this module no longer has. ⚠ The anti-vacuity check is why the
     * conversion produced a readable failure instead of an {@code IllegalStateException} from
     * {@code m.group(1)} — a regex that matches nothing satisfies every downstream assertion forever, so
     * the anti-vacuity check belongs in the <em>helper</em>, where every caller inherits it.
     *
     * <p>⚠⚠ <b>F2-263: that rule was right and this method was the counter-example to it for four
     * slices.</b> It compiled its own pattern requiring the initialiser to <em>be</em> a bare
     * {@code "hasPermission('X','Y')"} literal — the closing {@code \)"} demanding the string end right
     * after the pair — so giving either OB guard a second leg would have failed the build saying <i>the
     * constant must be a hasPermission string</i>: a gate refusing correct code, blaming the guard. 12 of
     * the platform's 94 permission-bearing guard constants are already that composed shape. Delegating to
     * {@link GuardExpressions#permissionPairOfConstant} keeps the anti-vacuity property and drops the
     * requirement that produced it — see that method's javadoc for why the two are separable.
     */
    private static String permissionIn(String src, String constant) {
        return GuardExpressions.permissionPairOfConstant(src, constant);
    }
}
