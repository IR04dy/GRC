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

        // F2-421: repointed at the UNCAPPED overload, because dashboard() no longer windows its
        // own population. Worth recording WHY this test could not have caught that defect: it
        // stubbed the PAGED finder with `any()` for the Pageable, and a mocked repository has no
        // page size — it returns whatever it is handed. So the leg asserted F2-132's PREDICATE
        // (which statuses count, that a null due date is not overdue) perfectly and was
        // structurally blind to the POPULATION, exactly as F2-132's comment reasoned about the
        // predicate and never about the cap two lines above it.
        when(cycleRepo.findByOrganisationIdOrderByFiscalYearDesc(ORG)).thenReturn(
            List.of(latePrep, lateDraft, onTime, lodgedLate, closedLate, noDueDate));
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
     * F2-130 — <b>CLOSED 2026-08-21 by F2-260.</b> This pin used to assert the three role lists and
     * record that only {@code PLATFORM_ADMIN} could approve a SAMA recovery plan. Its tripwire fired
     * the moment the guards were converted, which is what a pin is for.
     *
     * <h3>What the pin got right, and the one thing it got wrong</h3>
     * F2-130 is the row that <b>coined</b> the distinction the whole F2-253 → F2-259 lineage rests
     * on — its own Lesson cell reads <i>"Ask of an authorisation list not do these names exist but
     * can anybody satisfy it today"</i> — and its javadoc named the remedy correctly:
     * <i>"converting to {@code hasPermission} is F2-05's ratified direction"</i>.
     *
     * <p>⚠⚠ But its <b>exit condition</b> was written as <i>"When CRO is created or CFO is granted,
     * close the finding and update this"</i> — the <b>seating</b> remedy, and only that one. V373
     * later chose exactly that remedy for these roles and F2-258 measured what it achieved:
     * seating a role assigns it to nobody, so 79 guard sites became <i>satisfiable</i> rather than
     * satisfied. 🔑 <b>A pin that names three remedies in its prose and phrases its tripwire in terms
     * of one of them points the next reader at that one.</b> {@code CRO} was in fact created (V373)
     * and the finding stayed open, because creation was never the thing that mattered.
     *
     * <p>⚙ And one of its measurements had aged: <i>"CRO does not exist"</i> was true on 2026-08-06
     * and false in dev from V373 onward, while remaining true in prod. That is F2-221's rule — a
     * filing's measurements are the least-verified thing in it, so re-derive before executing.
     *
     * <p>What is pinned now is the converted state. The role-list assertions are kept as the
     * historical record of what those three tiers used to admit, so the diff between the two is
     * legible: {@link RrpGuardConversionTest} holds the live contract, V383 holds the catalogue.
     */
    @Test
    @DisplayName("F2-130 CLOSED: the three tiers are permissions, and the old role lists are recorded")
    void theGuardConstantsArePinnedWithTheirUnusableRoles() {
        String ctl = source("controller", "RrpController.java");

        assertThat(rolesIn(ctl, "READ_GUARD"))
            .as("F2-260: no role name survives on any tier — all three are hasPermission('RRP',…)")
            .isEmpty();
        assertThat(rolesIn(ctl, "WRITE_GUARD")).isEmpty();
        assertThat(rolesIn(ctl, "APPROVE_GUARD")).isEmpty();

        assertThat(ctl).as(
            "F2-130's Severity cell was \"no business role can approve a Recovery & Resolution "
            + "Plan; approval authority is the platform superuser alone\". Probed live 13/13 both "
            + "ways: pre-fix `compliance` and `rmanager` were refused the SAMA filing at 403 and "
            + "`admin` lodged at 200; post-fix `compliance` (GRC_ADMINISTRATOR, granted "
            + "RRP:APPROVE by V383) lodges at 200 while `rmanager` — which drives the cycle to "
            + "APPROVED — is still refused, because the preparer may not lodge.")
            .contains("hasPermission('RRP','READ')")
            .contains("hasPermission('RRP','WRITE')")
            .contains("hasPermission('RRP','APPROVE')");

        // The historical record. F2-130 measured these on 2026-08-06: of APPROVE_GUARD's three
        // names only PLATFORM_ADMIN was satisfiable, CRO did not exist at all, and CFO existed
        // with zero holders — the observation that made "existence" a weaker question than
        // "holding" and gave four earlier findings (F2-116/120/127/130) a common shape.
        assertThat(ctl).as(
            "the retired role lists must stay recorded in the constants' javadoc, so the reason "
            + "this module was converted is legible where the guards are")
            .contains("hasAnyRole('PLATFORM_ADMIN','CRO','CFO')");
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

    /**
     * The ROLE NAMES a guard constant recites — from {@code hasAnyRole(…)} / {@code hasRole(…)}
     * argument lists only.
     *
     * <p>⚠⚠ <b>F2-260 — this used to scan {@code '([A-Z_]+)'} across the whole initialiser, so
     * {@code hasPermission('RRP','READ')} parsed as two role names, {@code RRP} and {@code READ}.</b>
     * It is the <b>third</b> instance of that one extractor bug: F2-254 repaired it in
     * {@code BiaApprovalChainGuardTest}, F2-259 in {@code AuditCommitteeGuardTest.namesIn}, and here.
     * 🔑 <b>A quoted-token scan over a guard expression flags every role-to-permission conversion
     * F2-05 exists to make</b> — a gate that fails on correct code, which is the one failure a gate
     * must never have (F2-214). Each instance bit on the slice that converted its own module, so the
     * shape recurs once per module <b>because there is no shared primitive</b> — measured at 5
     * hand-rolled copies across the test tree, of which `sharia` and `openbanking` were quietly
     * updated when F2-145(e) converted them and `cpty` is still latent. Extracting one helper is
     * filed (F2-190's rule: do not hand-roll a fifth).
     *
     * <p>⚠⚠ <b>F2-262 executed that filing, and re-deriving the population first (F2-221) refuted
     * three of the five members above.</b> {@code sharia}, {@code openbanking} and {@code cpty} hold
     * {@code hasPermission('MODULE','ACTION')} <b>pair</b> extractors — a different question — so
     * they were never members of this population, while the one copy that still had the defect
     * ({@code CrisisRoomGuardTest}) was not in the list at all. 🔑 <b>A filing that names its members
     * by MODULE inherits the module list of the slice that filed it, and a module can host a
     * different question's extractor.</b> Re-derived: <b>8</b> members, <b>3</b> mechanisms,
     * <b>6</b> spellings, <b>5</b> slices already bitten.
     *
     * <p>Two properties this copy had and the helper now gives every caller: the constant must
     * exist (anti-vacuity), and the extraction must be tolerant of what it contains. This copy's own
     * narrowing was the inner {@code '([A-Z_]+)'}, which cannot see a role name carrying a digit —
     * latent, since all 38 recited names are upper snake case, and closed anyway because what made
     * it harmless was six regexes agreeing and nobody maintaining that (F2-177).
     */
    private static Set<String> rolesIn(String src, String constant) {
        return GuardExpressions.rolesRecitedByConstant(src, constant);
    }
}
