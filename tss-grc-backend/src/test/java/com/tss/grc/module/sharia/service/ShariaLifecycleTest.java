package com.tss.grc.module.sharia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sharia.dto.ShariaDtos.PurifyNcEventRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionAuditFindingRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionNcEventRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionProductRequest;
import com.tss.grc.module.sharia.dto.ShariaDtos.TransitionResolutionRequest;
import com.tss.grc.module.sharia.entity.ShariaAuditFinding;
import com.tss.grc.module.sharia.entity.ShariaNonComplianceEvent;
import com.tss.grc.module.sharia.entity.ShariaProductApproval;
import com.tss.grc.module.sharia.entity.ShariaResolution;
import com.tss.grc.module.sharia.repository.ShariaAuditFindingRepository;
import com.tss.grc.module.sharia.repository.ShariaEventRepository;
import com.tss.grc.module.sharia.repository.ShariaNonComplianceEventRepository;
import com.tss.grc.module.sharia.repository.ShariaProductApprovalRepository;
import com.tss.grc.module.sharia.repository.ShariaResolutionRepository;
import com.tss.grc.module.sharia.repository.ShariaScholarRepository;
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
 * F2-03(c) — the Tier-1 floor for {@code sharia}, third in the row's order and the flagship of the
 * Saudi SKU: §6d calls F2-03 revenue critical-path precisely because {@code sharia} and
 * {@code data_officer} had no tests at all.
 *
 * <p>2,220 LoC, six entities, <b>four</b> state machines and <b>24 legal transitions</b>. Twenty rows
 * in dev across the whole module.
 *
 * <h3>⚠ A green machine test would have certified a machine that a sibling method bypasses</h3>
 * {@code transitionNcEvent} enforces {@code NC_ALLOWED}. {@code purifyNcEvent} does not consult it at
 * all: it sets {@code INCOME_PURIFIED} directly from either {@code INVESTIGATION} <b>or
 * {@code DETECTED}</b>, and the map permits {@code DETECTED → INVESTIGATION} only. So the module has
 * two writers of one status and they disagree about whether purification may skip investigation —
 * <b>F2-122</b>, and the reason {@link #purifyIsASecondWriterThatBypassesTheMap()} exists. Testing
 * only the map would have reported the non-compliance lifecycle as verified while a documented
 * shortcut around it stayed unmentioned.
 *
 * <p>The shortcut is <b>not</b> "fixed" here. Whether purifying Sharia non-compliance income may
 * precede investigation is a business decision under SAMA §6.4 / AAOIFI FAS 1, not an oversight to
 * be silently reversed by a test author — the author of {@code purifyNcEvent} wrote that
 * {@code DETECTED} branch deliberately. What this floor does is make the divergence impossible to
 * miss, and pin it so neither writer moves without the other being reconsidered.
 *
 * <h3>What this module does NOT have wrong, measured before assuming</h3>
 * Three hypotheses were tested and refuted, and recording them matters as much as the findings:
 * <ul>
 *   <li>The purification evidence <b>is</b> required — {@code PurifyNcEventRequest} carries
 *       {@code @NotBlank} on the destination and the charity receipt and {@code @NotNull} on the
 *       date, so a donation cannot be claimed with nothing behind it.</li>
 *   <li>{@code snciAmountSar} is {@code @NotNull} at detection, so the {@code Map.of} in
 *       {@code emit} — which throws on a null value — is not reachable through the API.</li>
 *   <li>Soft deletes are filtered: every entity carries {@code @Where}.</li>
 * </ul>
 *
 * <h3>What the floor pins rather than fixes</h3>
 * <b>F2-120</b> — every Sharia-specific role in the three guard constants is absent from
 * {@code iam.roles}: {@code SHARIA_OFFICER}, {@code SHARIA_AUDITOR}, {@code SHARIA_BOARD_CHAIR}. So
 * WRITE and APPROVE reduce to {@code PLATFORM_ADMIN} and {@code COMPLIANCE_OFFICER}, and the Sharia
 * Supervisory Board — whose independent authority is the entire regulatory point of the module —
 * cannot act in the platform. Creating privileged roles is a governance decision (F2-116's reasoning,
 * one module over), so the constants are pinned and the finding filed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShariaLifecycleTest {

    private static final Long ORG = 1L;

    @Mock private ShariaScholarRepository scholarRepo;
    @Mock private ShariaResolutionRepository resolutionRepo;
    @Mock private ShariaAuditFindingRepository findingRepo;
    @Mock private ShariaNonComplianceEventRepository ncRepo;
    @Mock private ShariaProductApprovalRepository productRepo;
    @Mock private ShariaEventRepository eventRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private ShariaService svc;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(resolutionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(findingRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ncRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(productRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── the four machines ────────────────────────────────────────────────────

    @Test
    @DisplayName("Resolution: every declared transition permitted, every undeclared pair refused")
    void resolutionMachineHonoursItsOwnMap() {
        machine("RESOLUTION_ALLOWED", "ShariaResolution",
            state -> {
                ShariaResolution r = new ShariaResolution();
                r.setId(1L);
                r.setOrganisationId(ORG);
                r.setReference("SSB-RES-1");
                // NOT NULL in the schema and @NotBlank at creation; emit() puts it in a
                // Map.of, which throws on a null value. The fixture must be as complete
                // as the API guarantees, or the test invents an unreachable world.
                r.setSubjectArea("MURABAHA");
                r.setStatus(state);
                when(resolutionRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
                    .thenReturn(Optional.of(r));
            },
            to -> svc.transitionResolution(1L, transitionResolution(to)));
    }

    @Test
    @DisplayName("AuditFinding: every declared transition permitted, every undeclared pair refused")
    void findingMachineHonoursItsOwnMap() {
        machine("FINDING_ALLOWED", "ShariaAuditFinding",
            state -> {
                ShariaAuditFinding f = new ShariaAuditFinding();
                f.setId(1L);
                f.setOrganisationId(ORG);
                f.setReference("SAF-1");
                f.setSeverity("HIGH");
                f.setStatus(state);
                when(findingRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
                    .thenReturn(Optional.of(f));
            },
            to -> svc.transitionFinding(1L, transitionFinding(to)));
    }

    @Test
    @DisplayName("NonComplianceEvent: every declared transition permitted, every undeclared pair refused")
    void ncMachineHonoursItsOwnMap() {
        machine("NC_ALLOWED", "ShariaNonComplianceEvent",
            state -> when(ncRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
                .thenReturn(Optional.of(nc(state))),
            to -> svc.transitionNcEvent(1L, transitionNc(to)));
    }

    @Test
    @DisplayName("ProductApproval: every declared transition permitted, every undeclared pair refused")
    void productMachineHonoursItsOwnMap() {
        machine("PRODUCT_ALLOWED", "ShariaProductApproval",
            state -> {
                ShariaProductApproval p = new ShariaProductApproval();
                p.setId(1L);
                p.setOrganisationId(ORG);
                p.setReference("SPA-1");
                p.setStructure("IJARA");   // same reason as subjectArea above
                p.setStatus(state);
                when(productRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
                    .thenReturn(Optional.of(p));
            },
            to -> svc.transitionProduct(1L, transitionProduct(to)));
    }

    private void machine(String field, String label,
                         Consumer<String> seed, Consumer<String> attempt) {
        StateMachineContract.assertHonoursItsMap(
            allowedMap(field), seed, attempt, "transition", label + " " + field);
    }

    // ── F2-122: the second writer ────────────────────────────────────────────

    @Test
    @DisplayName("F2-122: purifyNcEvent reaches INCOME_PURIFIED from DETECTED, which NC_ALLOWED forbids")
    void purifyIsASecondWriterThatBypassesTheMap() {
        Map<String, Set<String>> map = allowedMap("NC_ALLOWED");

        // The declared machine: DETECTED may only become INVESTIGATION.
        assertThat(map.get("DETECTED")).containsExactly("INVESTIGATION");
        assertThat(map.get("INVESTIGATION")).containsExactly("INCOME_PURIFIED");

        // The map's own writer honours that.
        when(ncRepo.findByIdAndOrganisationId(eq(1L), eq(ORG))).thenReturn(Optional.of(nc("DETECTED")));
        assertThatThrownBy(() -> svc.transitionNcEvent(1L, transitionNc("INCOME_PURIFIED")))
            .as("transitionNcEvent must refuse the shortcut the map forbids")
            .isInstanceOf(IllegalStateException.class);

        // purifyNcEvent does not. This is the finding: one status, two writers, different rules.
        ShariaNonComplianceEvent fresh = nc("DETECTED");
        when(ncRepo.findByIdAndOrganisationId(eq(1L), eq(ORG))).thenReturn(Optional.of(fresh));
        assertThatCode(() -> svc.purifyNcEvent(1L, purify(LocalDate.now())))
            .as("recorded, not endorsed — F2-122. If purification is later required to follow "
                + "investigation, NC_ALLOWED and purifyNcEvent must change together, and this "
                + "assertion is the reminder that they are two places.")
            .doesNotThrowAnyException();
        assertThat(fresh.getStatus()).isEqualTo("INCOME_PURIFIED");
    }

    // ── F2-121: the purification date is bounded ─────────────────────────────

    @Test
    @DisplayName("F2-121: a future purification date is refused")
    void purificationCannotBeDatedInTheFuture() {
        when(ncRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
            .thenReturn(Optional.of(nc("INVESTIGATION")));

        assertThatThrownBy(() -> svc.purifyNcEvent(1L, purify(LocalDate.now().plusDays(1))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("future");
    }

    @Test
    @DisplayName("F2-121: a purification dated before detection is refused")
    void purificationCannotPrecedeDetection() {
        ShariaNonComplianceEvent n = nc("INVESTIGATION");
        n.setDetectedAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        when(ncRepo.findByIdAndOrganisationId(eq(1L), eq(ORG))).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> svc.purifyNcEvent(1L, purify(LocalDate.of(2026, 5, 31))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("precedes detection");

        assertThat(n.getStatus()).as("a refused purification must not mutate the record")
            .isEqualTo("INVESTIGATION");
    }

    @Test
    @DisplayName("F2-121: a back-dated purification on or after detection is accepted")
    void aBackdatedPurificationIsLegitimate() {
        ShariaNonComplianceEvent n = nc("INVESTIGATION");
        n.setDetectedAt(LocalDateTime.of(2026, 6, 1, 9, 0));
        when(ncRepo.findByIdAndOrganisationId(eq(1L), eq(ORG))).thenReturn(Optional.of(n));

        // Purification happens outside the platform and is recorded from a charity receipt, so a
        // past date is the normal case. The bound must not have become "today only", and it must not
        // have become a server stamp — that would overwrite the real donation date with the
        // data-entry date and corrupt the AAOIFI FAS 1 disclosure it feeds.
        assertThatCode(() -> svc.purifyNcEvent(1L, purify(LocalDate.of(2026, 6, 15))))
            .doesNotThrowAnyException();
        assertThat(n.getPurifiedAt()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(n.getPurificationDestination()).isEqualTo("Riyadh Charitable Endowment");
        assertThat(n.getCharityReceiptRef()).isEqualTo("RCE-2026-0007");
    }

    @Test
    @DisplayName("Purification is refused outright once the event is closed")
    void purificationIsRefusedAfterClosure() {
        when(ncRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
            .thenReturn(Optional.of(nc("CLOSED")));
        assertThatThrownBy(() -> svc.purifyNcEvent(1L, purify(LocalDate.now())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot purify SNCI in status");
    }

    // ── the authorisation branches ───────────────────────────────────────────

    /**
     * F2-120, pinned. Measured against {@code iam.roles} (58 roles, dev, 2026-08-06): of the eight
     * distinct role names these three guards recite, <b>four do not exist</b> —
     * {@code SHARIA_OFFICER}, {@code SHARIA_AUDITOR}, {@code SHARIA_BOARD_CHAIR} and {@code CRO}.
     * WRITE therefore has 2 real holders of the 4 it names and APPROVE 2 of 5.
     */
    @Test
    @DisplayName("The three guard constants are exactly as reviewed, and their dead roles recorded")
    void theGuardConstantsArePinnedWithTheirDeadRoles() throws IOException {
        String src = controllerSource();

        assertThat(rolesIn(src, "READ_GUARD")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "COMPLIANCE_OFFICER", "RISK_MANAGER", "INTERNAL_AUDITOR", "CRO",
            "SHARIA_OFFICER", "SHARIA_AUDITOR");
        assertThat(rolesIn(src, "WRITE_GUARD")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "COMPLIANCE_OFFICER", "SHARIA_OFFICER", "SHARIA_AUDITOR");
        assertThat(rolesIn(src, "APPROVE_GUARD")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "COMPLIANCE_OFFICER", "SHARIA_OFFICER", "SHARIA_BOARD_CHAIR", "CRO");

        Set<String> recited = new TreeSet<>();
        recited.addAll(rolesIn(src, "READ_GUARD"));
        recited.addAll(rolesIn(src, "WRITE_GUARD"));
        recited.addAll(rolesIn(src, "APPROVE_GUARD"));
        assertThat(recited).as("the four fictional names are still recited — when they are created "
            + "or the guards convert to hasPermission under F2-05, close F2-120 and update this")
            .contains("SHARIA_OFFICER", "SHARIA_AUDITOR", "SHARIA_BOARD_CHAIR", "CRO");
    }

    @Test
    @DisplayName("Every write endpoint is guarded, and only by the reviewed constants")
    void everyWriteEndpointIsGuarded() throws IOException {
        String src = controllerSource();
        long writes = Pattern.compile("@(Post|Put|Patch|Delete)Mapping").matcher(src).results().count();
        Matcher m = Pattern.compile(
            "@(Post|Put|Patch|Delete)Mapping[^\\n]*\\n\\s*@PreAuthorize\\(([A-Z_]+)\\)").matcher(src);
        int guarded = 0;
        Set<String> used = new TreeSet<>();
        while (m.find()) {
            guarded++;
            used.add(m.group(2));
        }
        assertThat(guarded).as("all %d write endpoints must carry a guard constant", writes)
            .isEqualTo((int) writes);
        assertThat(used).as("only the reviewed constants may guard writes").isSubsetOf(
            Set.of("WRITE_GUARD", "APPROVE_GUARD"));
        assertThat(src).as("the entitlement gate must name the SKU that sells this module")
            .contains("@RequireModule(\"SAUDI_FS\")");
    }

    // ── F2-118/F2-119: what the module cannot do, recorded as mechanism ──────

    /**
     * F2-119. Three deadline claims exist in this module's own javadoc and <b>none</b> is enforced:
     * <ul>
     *   <li>{@code ShariaAuditFinding} — "HIGH-severity findings trigger AR-253 Board-Audit-Committee
     *       escalation <b>within 30 days</b> per SAMA §5.7"</li>
     *   <li>{@code ShariaNonComplianceEvent} — SNCI purified "<b>within 90 days</b> of detection per
     *       SAMA §6.4 + AAOIFI FAS 1"</li>
     *   <li>AR-260 — "SSB scholar term expiring → T-90/T-30 reminders"</li>
     * </ul>
     * AR-253 and AR-254 fire on <em>creation</em> events, which is a notification and not a deadline;
     * {@code remediation_due_at} is written and echoed back but nothing ever compares it to today; the
     * 90-day purification window has no column to be measured against at all; and AR-260 has no
     * producer, because a term-expiry sweep does not exist. This asserts the absence as mechanism, so
     * the finding cannot quietly become "done" without the test changing.
     */
    @Test
    @DisplayName("F2-119: the module has no clock — no scheduler, and nothing reads the due date")
    void theModuleHasNoDeadlineEnforcement() throws IOException {
        String svcSrc = serviceSource();

        // ⚠ Matches on "Schedul", not "@Scheduled". Mutation testing caught the difference: inserting
        // the fully-qualified @org.springframework.scheduling.annotation.Scheduled did NOT fail the
        // first version of this assertion, because the literal string "@Scheduled" never appeared.
        // A tripwire that only recognises one spelling of the thing it watches for is not a tripwire
        // — the same "matches too little" error that has now cost this session five measurements.
        // The word appears nowhere in the service today, so the broad form has no false positive.
        assertThat(svcSrc).as("F2-119 — if a sweep now exists, the 30/90-day claims may finally be "
            + "enforceable; verify them and close the finding")
            .doesNotContain("Schedul");

        // remediation_due_at is stored and echoed, never evaluated. `isBefore`/`isAfter` against
        // today would be the signature of an overdue check; the only date comparison in the module
        // is the F2-121 bound added above.
        assertThat(svcSrc).as("nothing compares remediation_due_at to the current date")
            .doesNotContain("getRemediationDueAt().isBefore")
            .doesNotContain("getRemediationDueAt().isAfter");

        // And the 90-day window has nowhere to live: no purification due date on the entity.
        assertThat(ShariaNonComplianceEvent.class.getDeclaredFields())
            .as("no purification-deadline column exists, so the SAMA §6.4 window cannot be "
                + "measured — a column is the prerequisite, exactly as F2-115's 60-day half")
            .noneMatch(f -> f.getName().toLowerCase().contains("due")
                         || f.getName().toLowerCase().contains("deadline"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ShariaNonComplianceEvent nc(String status) {
        ShariaNonComplianceEvent n = new ShariaNonComplianceEvent();
        n.setId(1L);
        n.setOrganisationId(ORG);
        n.setReference("SNCI-REVIEW-TEST-1");
        n.setStatus(status);
        n.setSeverity("HIGH");
        // Non-null: emit() builds its payload with Map.of, which throws on a null value.
        n.setSnciAmountSar(new BigDecimal("18500.00"));
        n.setDetectedAt(LocalDateTime.now().minusDays(10));
        return n;
    }

    private static PurifyNcEventRequest purify(LocalDate on) {
        PurifyNcEventRequest r = new PurifyNcEventRequest();
        r.setPurificationDestination("Riyadh Charitable Endowment");
        r.setCharityReceiptRef("RCE-2026-0007");
        r.setPurifiedAt(on);
        return r;
    }

    private static TransitionNcEventRequest transitionNc(String to) {
        TransitionNcEventRequest r = new TransitionNcEventRequest();
        r.setToStatus(to);
        return r;
    }

    private static TransitionResolutionRequest transitionResolution(String to) {
        TransitionResolutionRequest r = new TransitionResolutionRequest();
        r.setToStatus(to);
        return r;
    }

    private static TransitionAuditFindingRequest transitionFinding(String to) {
        TransitionAuditFindingRequest r = new TransitionAuditFindingRequest();
        r.setToStatus(to);
        return r;
    }

    private static TransitionProductRequest transitionProduct(String to) {
        TransitionProductRequest r = new TransitionProductRequest();
        r.setToStatus(to);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> allowedMap(String field) {
        Object v = ReflectionTestUtils.getField(ShariaService.class, field);
        assertThat(v).as("ShariaService.%s must exist — a rename would otherwise make this class "
            + "stop checking that machine", field).isInstanceOf(Map.class);
        return (Map<String, Set<String>>) v;
    }

    private static String controllerSource() throws IOException {
        return Files.readString(Paths.get("src", "main", "java", "com", "tss", "grc", "module",
            "sharia", "controller", "ShariaController.java").toAbsolutePath().normalize(),
            StandardCharsets.UTF_8);
    }

    private static String serviceSource() throws IOException {
        return Files.readString(Paths.get("src", "main", "java", "com", "tss", "grc", "module",
            "sharia", "service", "ShariaService.java").toAbsolutePath().normalize(),
            StandardCharsets.UTF_8);
    }

    private static Set<String> rolesIn(String src, String constant) {
        Matcher m = Pattern.compile(
            constant + "\\s*=\\s*\\n?\\s*\"hasAnyRole\\((.*?)\\)\"", Pattern.DOTALL).matcher(src);
        assertThat(m.find()).as("guard constant %s must be a hasAnyRole string — a pattern that "
            + "matches nothing passes forever", constant).isTrue();
        Set<String> out = new TreeSet<>();
        for (String part : m.group(1).replace("\"", "").replace("+", "").split(",")) {
            String r = part.trim().replace("'", "").replace("\n", "").trim();
            if (!r.isEmpty()) {
                out.add(r);
            }
        }
        return out;
    }
}
