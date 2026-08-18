package com.tss.grc.module.ndi;

import org.springframework.security.core.context.SecurityContextHolder;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.audit.service.NdiAuditPublisher;
import com.tss.grc.module.ndi.cycle.entity.NdiAssessmentCycle;
import com.tss.grc.module.ndi.cycle.repository.NdiAssessmentCycleRepository;
import com.tss.grc.module.ndi.cycle.service.NdiAssessmentCycleService;
import com.tss.grc.module.ndi.gap.repository.NdiCorrectiveActionRepository;
import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlan;
import com.tss.grc.module.ndi.plan.repository.NdiImprovementPlanItemRepository;
import com.tss.grc.module.ndi.plan.repository.NdiImprovementPlanRepository;
import com.tss.grc.module.ndi.plan.service.NdiImprovementPlanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * F2-218 — the improvement plan and the assessment cycle: a create lands in the initial state and
 * carries no approval stamp.
 *
 * <h2>Why this class exists at all</h2>
 *
 * <p>The mutation harness found two survivors that {@link
 * com.tss.grc.security.ServerStampedFieldContractTest} cannot see, and it is worth saying why
 * rather than widening that gate to chase them:
 *
 * <ul>
 *   <li>{@code NdiImprovementPlanService#approve(Long, String approverName)} takes the approver as
 *       a <b>parameter</b>. The gate — correctly, after its third detector fix — treats a scalar
 *       parameter as caller data, so {@code approverName} is not in its "server stamps this" set
 *       and deleting the create's {@code setApproverName(null)} does not register as a
 *       contradiction. The clearing is still right: on a create nobody has approved, and the
 *       difference between the two routes is that {@code approve()} checks the state and writes an
 *       audit transition.</li>
 *   <li>The cycle's initial state is {@code FORWARD_FLOW.get(0)}. Replacing that derivation with a
 *       literal is exactly the drift the derivation prevents, and nothing structural notices.</li>
 * </ul>
 *
 * <p>🔑 <b>A surviving mutant that the gate legitimately cannot see is not a gate hole — it is an
 * unasserted behaviour</b>, and the answer is a test, not a looser instrument. Widening the gate to
 * catch them would have meant treating a caller-supplied parameter as a server stamp again, which
 * is the false positive its third detector fix removed.
 */
@ExtendWith(MockitoExtension.class)
class NdiCreateInitialStateTest {

    private static final Long ORG_ID = 1L;

    @Mock private NdiImprovementPlanRepository plans;
    @Mock private NdiImprovementPlanItemRepository planItems;
    @Mock private NdiCorrectiveActionRepository capas;
    @Mock private NdiAssessmentCycleRepository cycles;
    @Mock private NdiAuditPublisher audit;

    private NdiImprovementPlanService planService;
    private NdiAssessmentCycleService cycleService;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        planService = new NdiImprovementPlanService(plans, planItems, capas, audit);
        cycleService = new NdiAssessmentCycleService(cycles, audit);
        lenient().when(plans.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(cycles.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach void tearDown() { TenantContext.clear(); SecurityContextHolder.clearContext(); }
    /** F2-236 — a principal to record, since the attribution now comes from the security context. */
    private static void f236AuthAs(String username, Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new com.tss.grc.security.GrcUserDetails(
                    userId, ORG_ID, username, "x", true, true, java.util.List.of()),
                "x", java.util.List.of()));
    }


    private static NdiImprovementPlan plan() {
        NdiImprovementPlan p = new NdiImprovementPlan();
        p.setCycleId(1L);
        p.setNameEn("2026 data governance uplift");
        p.setPeriodStart(LocalDate.of(2026, 1, 1));
        p.setPeriodEnd(LocalDate.of(2026, 12, 31));
        return p;
    }

    private static NdiAssessmentCycle cycle() {
        NdiAssessmentCycle c = new NdiAssessmentCycle();
        c.setCycleCode("CY-2031");
        c.setCycleNameEn("2031 assessment");
        c.setReportingYear((short) 2031);
        return c;
    }

    // ── improvement plan ─────────────────────────────────────────────────────

    @Test
    void aPlanCannotBeCreatedPastTheStateItsApprovalGrantsIt() {
        NdiImprovementPlan p = plan();
        p.setStatus("COMPLETED");

        assertThatThrownBy(() -> planService.create(p))
            .as("probed live as ranalyst: POST /api/ndi/plans with status=COMPLETED answered 200, "
                + "skipping DRAFT -> APPROVED and the whole machine under it")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status")
            .hasMessageContaining("approve");
    }

    @Test
    void aPlanCreateNeverKeepsAnApproverTheCallerSupplied() {
        NdiImprovementPlan p = plan();
        p.setApproverName("Nobody Who Approved This");
        p.setApprovedAt(LocalDateTime.of(2001, 1, 1, 0, 0));

        NdiImprovementPlan saved = planService.create(p);

        assertThat(saved.getApproverName())
            .as("approve() records the approver behind a DRAFT precondition and an audit "
                + "transition; a create has been approved by nobody")
            .isNull();
        assertThat(saved.getApprovedAt()).isNull();
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void aPlanApprovalStillRecordsWhoApprovedIt() {
        NdiImprovementPlan draft = plan();
        draft.setStatus("DRAFT");
        when(plans.findByIdAndOrganisationId(4L, ORG_ID)).thenReturn(Optional.of(draft));

        f236AuthAs("so_officer", 8L);
        NdiImprovementPlan approved = planService.approve(4L);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        // F2-236 — the assertion is unchanged in substance and its source has moved: the approver
        // is now resolved from the security context rather than passed in. The intent this test
        // was written with still holds — the route that IS allowed to approve must still record
        // who did — and it is now recording somebody the caller could not have chosen.
        assertThat(approved.getApproverName())
            .as("the route that IS allowed to approve must still record it, or this slice closed "
                + "the bypass by removing the feature")
            .isEqualTo("so_officer");
        assertThat(approved.getApprovedAt()).isNotNull();
    }

    // ── assessment cycle ─────────────────────────────────────────────────────

    @Test
    void aCycleCannotBeCreatedPartWayThroughItsForwardFlow() {
        NdiAssessmentCycle c = cycle();
        c.setStatus("SUBMITTED");

        assertThatThrownBy(() -> cycleService.create(c))
            .as("`if (status == null) status = DRAFT` was a DEFAULT, not a guard, so any declared "
                + "status the caller sent survived")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status")
            .hasMessageContaining("transition");
    }

    /**
     * The initial state is {@code FORWARD_FLOW.get(0)}, so this asserts the derivation as well as
     * the value: replacing it with a literal that is not the head of the flow fails here.
     */
    @Test
    void aCycleIsCreatedAtTheHeadOfItsOwnForwardFlow() {
        when(cycles.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2031)).thenReturn(List.of());

        assertThat(cycleService.create(cycle()).getStatus())
            .as("DRAFT is the first entry in FORWARD_FLOW; a typed literal here could drift from "
                + "the machine the transition route walks")
            .isEqualTo("DRAFT");
    }

    @Test
    void aCycleCreateCarriesNoTransitionTimestamps() {
        when(cycles.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2031)).thenReturn(List.of());

        NdiAssessmentCycle c = cycle();
        c.setApprovedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        c.setSubmittedAt(LocalDateTime.of(2001, 1, 1, 0, 0));
        c.setClosedAt(LocalDateTime.of(2001, 1, 1, 0, 0));

        NdiAssessmentCycle saved = cycleService.create(c);

        assertThat(saved.getApprovedAt()).isNull();
        assertThat(saved.getSubmittedAt()).isNull();
        assertThat(saved.getClosedAt())
            .as("transition() stamps one of these per target state; a cycle being created has "
                + "reached none of them")
            .isNull();
    }
}
