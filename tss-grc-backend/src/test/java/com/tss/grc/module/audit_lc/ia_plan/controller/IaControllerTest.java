package com.tss.grc.module.audit_lc.ia_plan.controller;

import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementCreateFromUniverseRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementPhaseTransitionRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneRecordRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanDashboardResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanTransitionRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseResponse;
import com.tss.grc.module.audit_lc.ia_plan.service.IaAnnualPlanService;
import com.tss.grc.module.audit_lc.ia_plan.service.IaAuditUniverseService;
import com.tss.grc.module.audit_lc.ia_plan.service.IaEngagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — Smoke tests for {@link IaController}.
 *
 * <p>Verifies method dispatch and response passthrough. PreAuthorize
 * checks run through Spring Security proxy and are covered by
 * integration tests elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class IaControllerTest {

    @Mock private IaAnnualPlanService planService;
    @Mock private IaAuditUniverseService universeService;
    @Mock private IaEngagementService engagementService;
    @InjectMocks private IaController controller;

    private static PlanResponse stubPlan() {
        return new PlanResponse(
            100L, UUID.randomUUID(), 7L,
            "IAP-2026", 2026,
            "2026 Plan", "خطة 2026",
            42L, "DRAFT",
            "m", "م",
            20, 5, null, null,
            LocalDateTime.now(), null
        );
    }

    private static EngagementResponse stubEngagement(String phase) {
        return new EngagementResponse(
            200L, UUID.randomUUID(), 7L, 100L, 500L,
            "ENG-2026-001",
            "AML Audit", "مراجعة AML",
            "COMPLIANCE", "scope", "نطاق",
            42L, null, 99L,
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 31),
            null, null,
            phase, null, null, null, 0, 0,
            List.of(), LocalDateTime.now(), null
        );
    }

    @Test void createPlan_dispatchesToService() {
        PlanCreateRequest req = new PlanCreateRequest(
            "IAP-2026", 2026, "x", "x", 42L, "m", "م");
        when(planService.create(any(PlanCreateRequest.class))).thenReturn(stubPlan());

        PlanResponse out = controller.createPlan(req);
        assertThat(out.planCode()).isEqualTo("IAP-2026");
        verify(planService).create(req);
    }

    @Test void transitionPlan_dispatchesToService() {
        PlanTransitionRequest req = new PlanTransitionRequest("BOARD_REVIEW", null, null);
        when(planService.transition(eq(100L), any(PlanTransitionRequest.class))).thenReturn(stubPlan());

        PlanResponse out = controller.transitionPlan(100L, req);
        assertThat(out).isNotNull();
        verify(planService).transition(100L, req);
    }

    @Test void planDashboard_returnsKpiSnapshot() {
        PlanDashboardResponse snap = new PlanDashboardResponse(
            100L, "IAP-2026", 2026, "EXECUTING", 20, 5,
            2L, 1L, 1L, 0L, 1L, 0L, 0L, 0L, 75.0
        );
        when(planService.dashboard(100L)).thenReturn(snap);
        PlanDashboardResponse out = controller.planDashboard(100L);
        assertThat(out.engagementsPlanned()).isEqualTo(2L);
        assertThat(out.onTimePercentage()).isEqualTo(75.0);
    }

    @Test void createEngagement_dispatchesToService() {
        EngagementCreateRequest req = new EngagementCreateRequest(
            100L, null, "ENG-2026-001", "AML Audit", "مراجعة AML",
            "COMPLIANCE", null, null, null, null, null, null, null);
        when(engagementService.create(any(EngagementCreateRequest.class))).thenReturn(stubEngagement("PLANNED"));

        EngagementResponse out = controller.createEngagement(req);
        assertThat(out.phase()).isEqualTo("PLANNED");
        verify(engagementService).create(req);
    }

    @Test void createFromUniverse_dispatchesToService() {
        EngagementCreateFromUniverseRequest req = new EngagementCreateFromUniverseRequest(
            100L, 500L, "ENG-2026-002", "COMPLIANCE", 42L, null, null, null);
        when(engagementService.createFromUniverse(any(EngagementCreateFromUniverseRequest.class)))
            .thenReturn(stubEngagement("PLANNED"));

        EngagementResponse out = controller.createFromUniverse(req);
        assertThat(out).isNotNull();
        verify(engagementService).createFromUniverse(req);
    }

    @Test void transitionEngagement_dispatchesToService() {
        EngagementPhaseTransitionRequest req = new EngagementPhaseTransitionRequest("PLANNING", 42L, null, null);
        when(engagementService.transitionPhase(eq(200L), any(EngagementPhaseTransitionRequest.class)))
            .thenReturn(stubEngagement("PLANNING"));

        EngagementResponse out = controller.transitionEngagement(200L, req);
        assertThat(out.phase()).isEqualTo("PLANNING");
        verify(engagementService).transitionPhase(200L, req);
    }

    @Test void recordMilestone_dispatchesToService() {
        MilestoneRecordRequest req = new MilestoneRecordRequest(
            "KICKOFF", LocalDate.of(2026, 1, 15), 42L, "ok", "حسناً");
        MilestoneResponse stub = new MilestoneResponse(
            1L, 200L, "KICKOFF",
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 15), 42L,
            "ok", "حسناً", LocalDateTime.now());
        when(engagementService.recordMilestone(eq(200L), any(MilestoneRecordRequest.class))).thenReturn(stub);

        MilestoneResponse out = controller.recordMilestone(200L, req);
        assertThat(out.milestoneType()).isEqualTo("KICKOFF");
        verify(engagementService).recordMilestone(200L, req);
    }

    @Test void listUniverse_dispatchesToService() {
        UniverseResponse u = new UniverseResponse(
            500L, UUID.randomUUID(), 7L, 100L,
            "AU-AML-KYC-001", "AML/KYC", "مكافحة غسل الأموال",
            null, null, null, null,
            null, null, null, null, null, 2026, "Q1", null, null,
            LocalDateTime.now(), null);
        when(universeService.listAll()).thenReturn(List.of(u));
        List<UniverseResponse> out = controller.listAllUniverse();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).entryCode()).isEqualTo("AU-AML-KYC-001");
    }
}
