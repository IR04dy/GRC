package com.tss.grc.module.audit_lc.ia_plan.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanTransitionRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.PlanUpdateRequest;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaAnnualPlan;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaEngagement;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAnnualPlanRepository;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAuditUniverseEntryRepository;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaEngagementRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — {@link IaAnnualPlanService} unit tests.
 *
 * <p>Covers:
 * <ul>
 *   <li>Create + read + update happy path</li>
 *   <li>Lifecycle gates: DRAFT→BOARD_REVIEW needs methodology summary</li>
 *   <li>BOARD_REVIEW→APPROVED requires approverUserId and stamps it</li>
 *   <li>EXECUTING→COMPLETED blocked while engagements are open</li>
 *   <li>Illegal transition raises {@link IllegalTransitionException}</li>
 *   <li>F8 event emission on every state change</li>
 *   <li>Tenant isolation — every read filters by org</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IaAnnualPlanServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long PLAN_ID = 100L;

    @Mock private IaAnnualPlanRepository planRepo;
    @Mock private IaAuditUniverseEntryRepository universeRepo;
    @Mock private IaEngagementRepository engagementRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private IaAnnualPlanService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new IaAnnualPlanService(planRepo, universeRepo, engagementRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static IaAnnualPlan plan(String status) {
        IaAnnualPlan p = new IaAnnualPlan();
        p.setId(PLAN_ID);
        p.setOrganisationId(ORG_ID);
        p.setPlanCode("IAP-2026");
        p.setCycleYear(2026);
        p.setTitleEn("2026 IA Plan");
        p.setTitleAr("خطة المراجعة الداخلية 2026");
        p.setStatus(status);
        p.setAuditUniverseCount(20);
        p.setTotalEngagementsPlanned(5);
        return p;
    }

    // ─── CRUD ────────────────────────────────────────────────────────

    @Test void create_persistsPlanAndEmitsCreatedEvent() {
        PlanCreateRequest req = new PlanCreateRequest(
            "IAP-2026", 2026, "2026 IA Plan",
            "خطة المراجعة الداخلية 2026", 42L,
            "methodology summary", "ملخص المنهجية"
        );
        when(planRepo.existsByOrganisationIdAndPlanCodeAndIsDeletedFalse(ORG_ID, "IAP-2026")).thenReturn(false);
        when(planRepo.save(any(IaAnnualPlan.class))).thenAnswer(inv -> {
            IaAnnualPlan p = inv.getArgument(0);
            p.setId(PLAN_ID);
            return p;
        });

        PlanResponse out = service.create(req);
        assertThat(out.planCode()).isEqualTo("IAP-2026");
        assertThat(out.status()).isEqualTo("DRAFT");
        assertThat(out.cycleYear()).isEqualTo(2026);

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("IA_PLAN_CREATED");
        assertThat(ev.getValue().organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.getValue().entityType()).isEqualTo("IaAnnualPlan");
    }

    @Test void create_defaultsPlanCodeFromCycleYearWhenAbsent() {
        PlanCreateRequest req = new PlanCreateRequest(
            null, 2027, "2027 plan", "خطة 2027", null, "m", "م"
        );
        when(planRepo.existsByOrganisationIdAndPlanCodeAndIsDeletedFalse(ORG_ID, "IAP-2027")).thenReturn(false);
        when(planRepo.save(any(IaAnnualPlan.class))).thenAnswer(inv -> {
            IaAnnualPlan p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });

        PlanResponse out = service.create(req);
        assertThat(out.planCode()).isEqualTo("IAP-2027");
    }

    @Test void create_rejectsDuplicatePlanCode() {
        PlanCreateRequest req = new PlanCreateRequest(
            "IAP-2026", 2026, "x", "x", null, "m", "م"
        );
        when(planRepo.existsByOrganisationIdAndPlanCodeAndIsDeletedFalse(ORG_ID, "IAP-2026")).thenReturn(true);
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void update_modifiesTitleInDraftStatus() {
        IaAnnualPlan p = plan("DRAFT");
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));
        when(planRepo.save(any(IaAnnualPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        PlanUpdateRequest req = new PlanUpdateRequest("Updated EN", null, null, null, null);
        PlanResponse out = service.update(PLAN_ID, req);
        assertThat(out.titleEn()).isEqualTo("Updated EN");
    }

    @Test void update_rejectedInApprovedStatus() {
        IaAnnualPlan p = plan("APPROVED");
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));
        PlanUpdateRequest req = new PlanUpdateRequest("x", null, null, null, null);
        assertThatThrownBy(() -> service.update(PLAN_ID, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("APPROVED");
    }

    // ─── Lifecycle gates ─────────────────────────────────────────────

    @Test void transition_draftToBoardReview_requiresMethodology() {
        IaAnnualPlan p = plan("DRAFT");
        p.setMethodologySummaryEn(null);
        p.setMethodologySummaryAr(null);
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));

        PlanTransitionRequest req = new PlanTransitionRequest("BOARD_REVIEW", null, null, null);
        assertThatThrownBy(() -> service.transition(PLAN_ID, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("methodologySummary");
    }

    @Test void transition_draftToBoardReview_succeedsWhenMethodologyPresent() {
        IaAnnualPlan p = plan("DRAFT");
        p.setMethodologySummaryEn("m");
        p.setMethodologySummaryAr("م");
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));
        when(planRepo.save(any(IaAnnualPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        PlanTransitionRequest req = new PlanTransitionRequest("BOARD_REVIEW", null, null, null);
        PlanResponse out = service.transition(PLAN_ID, req);
        assertThat(out.status()).isEqualTo("BOARD_REVIEW");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("IA_PLAN_SUBMITTED");
    }

    @Test void transition_boardReviewToApproved_requiresApprover() {
        IaAnnualPlan p = plan("BOARD_REVIEW");
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));
        PlanTransitionRequest req = new PlanTransitionRequest("APPROVED", null, null, null);
        assertThatThrownBy(() -> service.transition(PLAN_ID, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("approverUserId");
    }

    @Test void transition_boardReviewToApproved_stampsApprover() {
        IaAnnualPlan p = plan("BOARD_REVIEW");
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));
        when(planRepo.save(any(IaAnnualPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        PlanTransitionRequest req = new PlanTransitionRequest("APPROVED", 77L, "ok", "حسناً");
        PlanResponse out = service.transition(PLAN_ID, req);
        assertThat(out.status()).isEqualTo("APPROVED");
        assertThat(out.approvedByUserId()).isEqualTo(77L);
        assertThat(out.approvedAt()).isNotNull();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("IA_PLAN_APPROVED");
    }

    @Test void transition_illegalJump_throws() {
        IaAnnualPlan p = plan("DRAFT");
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));
        // DRAFT → APPROVED is not allowed; you must go through BOARD_REVIEW.
        PlanTransitionRequest req = new PlanTransitionRequest("APPROVED", 1L, null, null);
        assertThatThrownBy(() -> service.transition(PLAN_ID, req))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void transition_executingToCompleted_blockedWhileEngagementsOpen() {
        IaAnnualPlan p = plan("EXECUTING");
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));

        IaEngagement openEng = new IaEngagement();
        openEng.setOrganisationId(ORG_ID);
        openEng.setPlanId(PLAN_ID);
        openEng.setPhase("FIELDWORK");
        when(engagementRepo.findByOrganisationIdAndPlanIdAndIsDeletedFalseOrderByEngagementCodeAsc(ORG_ID, PLAN_ID))
            .thenReturn(List.of(openEng));

        PlanTransitionRequest req = new PlanTransitionRequest("COMPLETED", null, null, null);
        assertThatThrownBy(() -> service.transition(PLAN_ID, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("still open");
    }

    @Test void transition_executingToCompleted_succeedsWhenAllClosed() {
        IaAnnualPlan p = plan("EXECUTING");
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));

        IaEngagement closed = new IaEngagement();
        closed.setOrganisationId(ORG_ID);
        closed.setPlanId(PLAN_ID);
        closed.setPhase("CLOSED");
        IaEngagement cancelled = new IaEngagement();
        cancelled.setOrganisationId(ORG_ID);
        cancelled.setPlanId(PLAN_ID);
        cancelled.setPhase("CANCELLED");
        when(engagementRepo.findByOrganisationIdAndPlanIdAndIsDeletedFalseOrderByEngagementCodeAsc(ORG_ID, PLAN_ID))
            .thenReturn(List.of(closed, cancelled));
        when(planRepo.save(any(IaAnnualPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        PlanTransitionRequest req = new PlanTransitionRequest("COMPLETED", null, null, null);
        PlanResponse out = service.transition(PLAN_ID, req);
        assertThat(out.status()).isEqualTo("COMPLETED");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues().stream().map(DomainEvent::triggerEvent))
            .contains("IA_PLAN_COMPLETED");
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void list_filtersByOrgId() {
        when(planRepo.findByOrganisationIdAndIsDeletedFalseOrderByCycleYearDesc(ORG_ID))
            .thenReturn(List.of(plan("DRAFT")));
        List<PlanResponse> out = service.list();
        assertThat(out).hasSize(1);
        verify(planRepo).findByOrganisationIdAndIsDeletedFalseOrderByCycleYearDesc(ORG_ID);
    }

    // ─── Dashboard ───────────────────────────────────────────────────

    @Test void dashboard_aggregatesPhaseCounts() {
        IaAnnualPlan p = plan("EXECUTING");
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(p));
        // Lenient — Mockito strict mode would flag stubs whose args don't
        // match every call; we only care about the two we assert below.
        org.mockito.Mockito.lenient()
            .when(engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(
                org.mockito.ArgumentMatchers.eq(ORG_ID), org.mockito.ArgumentMatchers.eq(PLAN_ID), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(0L);
        when(engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(ORG_ID, PLAN_ID, "PLANNED")).thenReturn(2L);
        when(engagementRepo.countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(ORG_ID, PLAN_ID, "FIELDWORK")).thenReturn(1L);
        when(engagementRepo.findByOrganisationIdAndPlanIdAndIsDeletedFalseOrderByEngagementCodeAsc(ORG_ID, PLAN_ID))
            .thenReturn(List.of());

        var d = service.dashboard(PLAN_ID);
        assertThat(d.engagementsPlanned()).isEqualTo(2);
        assertThat(d.engagementsInFieldwork()).isEqualTo(1);
        assertThat(d.planCode()).isEqualTo("IAP-2026");
        assertThat(d.onTimePercentage()).isNull(); // nothing measurable yet
    }
}
