package com.tss.grc.module.rrp.service;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.rrp.dto.RrpDtos.DashboardResponse;
import com.tss.grc.module.rrp.dto.RrpDtos.SubmitCycleRequest;
import com.tss.grc.module.rrp.dto.RrpDtos.TransitionCycleRequest;
import com.tss.grc.module.rrp.entity.RrpCycle;
import com.tss.grc.module.rrp.repository.RrpCriticalFunctionRepository;
import com.tss.grc.module.rrp.repository.RrpCycleRepository;
import com.tss.grc.module.rrp.repository.RrpEventRepository;
import com.tss.grc.module.rrp.repository.RrpIndicatorRepository;
import com.tss.grc.module.rrp.repository.RrpRecoveryOptionRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * F2-03(g) — the Tier-1 floor for {@code rrp} (SAMA Recovery &amp; Resolution Planning). <b>The
 * seventh and last of the row.</b> 1,912 LoC, five entities, one cycle machine.
 *
 * <h3>⚠ F2-130 — the approval authority for a SAMA recovery plan is the platform superuser alone</h3>
 * {@code APPROVE_GUARD} names three roles. Measured against {@code iam.roles} and
 * {@code iam.user_roles} on 2026-08-06, <b>exactly one is satisfiable</b>:
 * <pre>
 *   PLATFORM_ADMIN   exists, 1 holder     <- the only party who can approve
 *   CRO              DOES NOT EXIST
 *   CFO              exists, 0 holders
 * </pre>
 * This is the fourth module in this row to recite a role that authorises nobody, and it adds a
 * distinction the previous three missed. F2-116, F2-120 and F2-127 were all measured by asking *does
 * this name exist in {@code iam.roles}* — and {@code CFO} <b>passes that check</b>. A role that exists
 * with no holders is equally unusable and fails differently. So F2-52's platform-wide figure (~50% of
 * recited names do not exist) is a <b>lower bound</b>: it counts the fictional names and misses the
 * unheld ones. {@code WRITE_GUARD} is 2 of 4 for the same reason ({@code BCM_MANAGER} exists, 0
 * holders), and {@code READ_GUARD} 4 of 8.
 *
 * <h3>⚠ F2-132 — the submission due date was compared to nothing</h3>
 * {@code submission_due_date} was written at creation, updatable, echoed in every cycle response, and
 * never compared. Measured: <b>all three tenants</b> hold an {@code IN_PREPARATION} FY2026 cycle
 * <b>128 days past</b> its 2026-03-31 due date, and the dashboard reported it only as "active". Now
 * counted as {@code overdueCycleCount}, with a UI tile — reported, never enforced.
 *
 * <h3>What this module gets RIGHT, and it is the counter-example to F2-122</h3>
 * {@code ALLOWED} does not contain {@code APPROVED → SUBMITTED}, and {@code submitCycle} performs
 * exactly that transition. In {@code sharia} the equivalent split was a silent bypass (F2-122). Here
 * it is <b>deliberate and self-documenting</b>: {@code transitionCycle} refuses {@code SUBMITTED}
 * explicitly with <em>"Use the dedicated /submit endpoint to lodge with SAMA"</em>, and the dedicated
 * endpoint exists because lodging captures a regulator acknowledgement reference that the generic
 * transition DTO has no field for. Two writers, one status, <b>no contradiction</b> — the refusal
 * names the other door. Covered below so the arrangement cannot decay into F2-122's version.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RrpLifecycleTest {

    private static final Long ORG = 1L;

    @Mock private RrpCycleRepository cycleRepo;
    @Mock private RrpRecoveryOptionRepository optionRepo;
    @Mock private RrpCriticalFunctionRepository cfRepo;
    @Mock private RrpIndicatorRepository indRepo;
    @Mock private RrpEventRepository eventRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    @InjectMocks private RrpService svc;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        when(cycleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── the machine ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cycle: every declared transition permitted, every undeclared pair refused")
    void cycleMachineHonoursItsOwnMap() {
        StateMachineContract.assertHonoursItsMap(
            allowed(),
            state -> when(cycleRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
                .thenReturn(Optional.of(cycle(state, LocalDate.now().plusDays(60)))),
            to -> svc.transitionCycle(1L, transition(to)),
            // Two correct refusals exist here: the map's, and the pre-map routing guard
            // that sends SUBMITTED to its own endpoint. Both are legitimate.
            java.util.Set.of("transition", "/submit endpoint"), "RrpCycle ALLOWED");
    }

    @Test
    @DisplayName("The generic transition refuses SUBMITTED and names the endpoint that performs it")
    void theGenericTransitionRoutesSubmissionToItsOwnEndpoint() {
        when(cycleRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
            .thenReturn(Optional.of(cycle("APPROVED", LocalDate.now().plusDays(60))));

        // ⚠ This is the arrangement that would be F2-122 if the refusal were silent. It is not: the
        // message names the other door, which is what makes two writers of one status defensible.
        assertThatThrownBy(() -> svc.transitionCycle(1L, transition("SUBMITTED")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("/submit")
            .hasMessageContaining("SAMA");

        assertThat(allowed().get("APPROVED")).as(
            "and the map deliberately omits APPROVED -> SUBMITTED, consistent with that routing. If "
            + "it ever gains the edge, the two writers must be reconciled or this becomes F2-122.")
            .doesNotContain("SUBMITTED");
    }

    @Test
    @DisplayName("Only an APPROVED cycle may be lodged, and lodging records the regulator's ack")
    void submissionRequiresApprovalAndCapturesTheAcknowledgement() {
        RrpCycle c = cycle("BOARD_REVIEW", LocalDate.now().plusDays(60));
        when(cycleRepo.findByIdAndOrganisationId(eq(1L), eq(ORG))).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> svc.submitCycle(1L, submit("SAMA-ACK-1")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be APPROVED");
        assertThat(c.getStatus()).isEqualTo("BOARD_REVIEW");

        RrpCycle approved = cycle("APPROVED", LocalDate.now().plusDays(60));
        when(cycleRepo.findByIdAndOrganisationId(eq(1L), eq(ORG))).thenReturn(Optional.of(approved));
        assertThatCode(() -> svc.submitCycle(1L, submit("SAMA-ACK-2026-014")))
            .doesNotThrowAnyException();
        assertThat(approved.getStatus()).isEqualTo("SUBMITTED");
        assertThat(approved.getSubmittedAt()).isNotNull();
        assertThat(approved.getRegulatorAckReference()).as(
            "the ack reference is the whole reason this endpoint exists rather than a generic "
            + "transition — the generic DTO has no field for it")
            .isEqualTo("SAMA-ACK-2026-014");
    }

    @Test
    @DisplayName("Acknowledgement requires a lodged cycle")
    void acknowledgementRequiresSubmission() {
        when(cycleRepo.findByIdAndOrganisationId(eq(1L), eq(ORG)))
            .thenReturn(Optional.of(cycle("APPROVED", LocalDate.now().plusDays(60))));
        assertThatThrownBy(() -> svc.acknowledgeCycle(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be SUBMITTED");
    }

    // ── F2-132 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F2-132: a cycle awaiting submission past its due date is counted; a lodged one is not")
    void overdueCyclesAreCounted() {
        RrpCycle latePrep = cycle("IN_PREPARATION", LocalDate.now().minusDays(128));
        RrpCycle lateDraft = cycle("DRAFT", LocalDate.now().minusDays(2));
        RrpCycle onTime = cycle("BOARD_REVIEW", LocalDate.now().plusDays(30));
        RrpCycle lodgedLate = cycle("SUBMITTED", LocalDate.now().minusDays(300));
        RrpCycle closedLate = cycle("CLOSED", LocalDate.now().minusDays(493));
        RrpCycle noDueDate = cycle("IN_PREPARATION", null);

        Page<RrpCycle> page = new PageImpl<>(
            List.of(latePrep, lateDraft, onTime, lodgedLate, closedLate, noDueDate));
        when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(eq(ORG), any())).thenReturn(page);
        when(cycleRepo.countByOrganisationIdAndStatus(eq(ORG), any())).thenReturn(0L);
        when(indRepo.findByCycleIdAndOrganisationIdOrderByIndicatorCodeAsc(any(), any()))
            .thenReturn(List.of());
        when(optionRepo.findByCycleIdAndOrganisationIdOrderByOptionCodeAsc(any(), any()))
            .thenReturn(List.of());

        DashboardResponse d = svc.dashboard();

        assertThat(d.getOverdueCycleCount()).as(
            "two are awaiting submission past their due date. ⚠ The SUBMITTED and CLOSED ones are "
            + "NOT overdue however old their dates — the filing has happened, so a passed date is "
            + "history rather than an outstanding obligation. Counting them would report a permanent "
            + "unfixable figure, which is the trap A17-D3 fell into. And a null due date is not "
            + "overdue: no deadline was set.")
            .isEqualTo(2L);
    }

    /**
     * F2-132's other half: the population must be the pre-submission statuses, and that set is the
     * service's own {@code ACTIVE_STATUSES}. Pinned so the two cannot drift apart.
     */
    @Test
    @DisplayName("F2-132: the overdue population is exactly the pre-submission statuses")
    void theOverduePopulationIsThePreSubmissionStatuses() {
        assertThat(activeStatuses())
            .containsExactlyInAnyOrder("DRAFT", "IN_PREPARATION", "BOARD_REVIEW", "APPROVED");
        assertThat(activeStatuses()).as("a lodged or acknowledged cycle has discharged the obligation")
            .doesNotContain("SUBMITTED", "ACKNOWLEDGED", "CLOSED");

        String svcSrc = source("service", "RrpService.java");
        assertThat(svcSrc).as("the overdue count must use that set, not an ad-hoc list")
            .contains("ACTIVE_STATUSES.contains(c.getStatus())");
    }

    // ── F2-130 ───────────────────────────────────────────────────────────────

    /**
     * F2-130, pinned. See the class javadoc: {@code APPROVE_GUARD} is satisfiable only by
     * {@code PLATFORM_ADMIN}, because {@code CRO} does not exist and {@code CFO} exists with no
     * holders. Filed rather than fixed — creating roles or granting them is a governance decision, and
     * converting to {@code hasPermission} is F2-05's ratified direction.
     */
    @Test
    @DisplayName("F2-130: the guard constants are as reviewed, and their unusable roles recorded")
    void theGuardConstantsArePinnedWithTheirUnusableRoles() {
        String ctl = source("controller", "RrpController.java");

        assertThat(rolesIn(ctl, "READ_GUARD")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "RISK_MANAGER", "COMPLIANCE_OFFICER", "INTERNAL_AUDITOR",
            "BCM_MANAGER", "BOARD_SECRETARY", "CFO", "CRO");
        assertThat(rolesIn(ctl, "WRITE_GUARD")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "RISK_MANAGER", "BCM_MANAGER", "CRO");
        assertThat(rolesIn(ctl, "APPROVE_GUARD")).containsExactlyInAnyOrder(
            "PLATFORM_ADMIN", "CRO", "CFO");

        // ⚠ CRO is fictional; CFO and BCM_MANAGER exist with zero holders. A test cannot join to
        // iam.roles without a database, so what is pinned here is the recited SET — and the finding
        // records which members are unusable and why the two failure modes differ.
        assertThat(rolesIn(ctl, "APPROVE_GUARD")).as(
            "F2-130 — of these three, only PLATFORM_ADMIN can approve a SAMA recovery plan today. "
            + "When CRO is created or CFO is granted, close the finding and update this.")
            .hasSize(3).contains("CRO", "CFO");
    }

    @Test
    @DisplayName("Every one of the 17 mappings carries a guard")
    void everyEndpointIsGuarded() {
        String ctl = source("controller", "RrpController.java");
        long mappings = ctl.lines()
            .filter(l -> l.contains("@GetMapping") || l.contains("@PostMapping")
                      || l.contains("@PutMapping") || l.contains("@DeleteMapping")).count();
        long guards = ctl.lines().filter(l -> l.contains("@PreAuthorize")).count();
        assertThat(mappings).isEqualTo(17L);
        assertThat(guards).isEqualTo(mappings);
        assertThat(ctl).as("the entitlement gate must name the SKU that sells this module")
            .contains("@RequireModule(\"SAUDI_FS\")");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RrpCycle cycle(String status, LocalDate due) {
        RrpCycle c = new RrpCycle();
        c.setId(1L);
        c.setOrganisationId(ORG);
        c.setReference("RRP-REVIEW-TEST-001");
        c.setFiscalYear(2026);
        c.setStatus(status);
        c.setSubmissionDueDate(due);
        return c;
    }

    private static TransitionCycleRequest transition(String to) {
        TransitionCycleRequest r = new TransitionCycleRequest();
        r.setToStatus(to);
        return r;
    }

    private static SubmitCycleRequest submit(String ack) {
        SubmitCycleRequest r = new SubmitCycleRequest();
        r.setRegulatorAckReference(ack);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> allowed() {
        Object v = ReflectionTestUtils.getField(RrpService.class, "ALLOWED");
        assertThat(v).as("RrpService.ALLOWED must exist — a rename would make this class stop "
            + "checking the machine").isInstanceOf(Map.class);
        return (Map<String, Set<String>>) v;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> activeStatuses() {
        Object v = ReflectionTestUtils.getField(RrpService.class, "ACTIVE_STATUSES");
        assertThat(v).as("RrpService.ACTIVE_STATUSES must exist").isInstanceOf(Set.class);
        return (Set<String>) v;
    }

    private static String source(String pkg, String file) {
        try {
            return Files.readString(Paths.get("src", "main", "java", "com", "tss", "grc", "module",
                "rrp", pkg, file).toAbsolutePath().normalize(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + pkg + "/" + file, e);
        }
    }

    private static Set<String> rolesIn(String src, String constant) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "String\\s+" + constant + "\\s*=\\s*(.*?);", java.util.regex.Pattern.DOTALL).matcher(src);
        assertThat(m.find()).as("guard constant %s must be declared — a pattern that matches nothing "
            + "passes forever", constant).isTrue();
        Set<String> out = new TreeSet<>();
        java.util.regex.Matcher r = java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(m.group(1));
        while (r.find()) {
            out.add(r.group(1));
        }
        return out;
    }
}
