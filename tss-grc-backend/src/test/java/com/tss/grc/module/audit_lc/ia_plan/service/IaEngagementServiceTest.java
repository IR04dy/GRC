package com.tss.grc.module.audit_lc.ia_plan.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementCreateFromUniverseRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementPhaseTransitionRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.EngagementUpdateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneRecordRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.MilestoneResponse;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaAnnualPlan;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaAuditUniverseEntry;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaEngagement;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaEngagementMilestone;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAnnualPlanRepository;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAuditUniverseEntryRepository;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaEngagementMilestoneRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — {@link IaEngagementService} unit tests.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code create} happy path and duplicate-code rejection</li>
 *   <li>{@code createFromUniverse} copies scope from the universe entry</li>
 *   <li>Phase transition gates:
 *     <ul>
 *       <li>{@code REPORTING} blocked when {@code actualStartDate} is null</li>
 *       <li>{@code REPORT_ISSUED} stamps {@code actualEndDate}</li>
 *       <li>Forward-only — REPORTING→FIELDWORK rejected</li>
 *       <li>CANCELLED reachable from any non-CLOSED state</li>
 *     </ul>
 *   </li>
 *   <li>Phase emits the right F8 trigger per transition</li>
 *   <li>{@code recordMilestone} idempotent on (engagement, milestoneType)</li>
 *   <li>{@code recordMilestone} fires {@code IA_MILESTONE_REACHED} only on first record</li>
 *   <li>{@code update} blocked in CLOSED/CANCELLED</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IaEngagementServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long PLAN_ID = 100L;
    private static final Long ENG_ID = 200L;
    private static final Long UNIVERSE_ID = 500L;

    @Mock private IaEngagementRepository engagementRepo;
    @Mock private IaAuditUniverseEntryRepository universeRepo;
    @Mock private IaAnnualPlanRepository planRepo;
    @Mock private IaEngagementMilestoneRepository milestoneRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private IaEngagementService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new IaEngagementService(
            engagementRepo, universeRepo, planRepo, milestoneRepo, audit, publisher);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ────────────────────────────────────────────────────

    private static IaEngagement eng(String phase) {
        IaEngagement e = new IaEngagement();
        e.setId(ENG_ID);
        e.setOrganisationId(ORG_ID);
        e.setPlanId(PLAN_ID);
        e.setEngagementCode("ENG-2026-001");
        e.setTitleEn("AML KYC Audit");
        e.setTitleAr("مراجعة مكافحة غسل الأموال واعرف عميلك");
        e.setAuditType("COMPLIANCE");
        e.setPhase(phase);
        e.setPlannedStartDate(LocalDate.of(2026, 1, 15));
        e.setPlannedEndDate(LocalDate.of(2026, 3, 31));
        return e;
    }

    private static IaAnnualPlan plan(String status) {
        IaAnnualPlan p = new IaAnnualPlan();
        p.setId(PLAN_ID);
        p.setOrganisationId(ORG_ID);
        p.setPlanCode("IAP-2026");
        p.setCycleYear(2026);
        p.setStatus(status);
        return p;
    }

    private static IaAuditUniverseEntry universe() {
        IaAuditUniverseEntry u = new IaAuditUniverseEntry();
        u.setId(UNIVERSE_ID);
        u.setOrganisationId(ORG_ID);
        u.setPlanId(PLAN_ID);
        u.setEntryCode("AU-AML-KYC-001");
        u.setTitleEn("AML / KYC Onboarding");
        u.setTitleAr("اعرف عميلك ومكافحة غسل الأموال");
        u.setProcessNameEn("Customer onboarding");
        u.setProcessNameAr("استقبال العميل");
        u.setBusinessUnitEn("Compliance");
        u.setBusinessUnitAr("الالتزام");
        u.setInherentRiskScore(new BigDecimal("22.00"));
        u.setControlEnvironmentScore(new BigDecimal("0.30"));
        u.setResidualRiskScore(new BigDecimal("15.40"));
        return u;
    }

    // ─── create ──────────────────────────────────────────────────────

    @Test void create_persistsAndEmitsCreatedEvent() {
        EngagementCreateRequest req = new EngagementCreateRequest(
            PLAN_ID, null, "ENG-2026-001", "AML Audit", "مراجعة AML",
            "COMPLIANCE", "scope en", "نطاق", 42L, null, 99L,
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 31)
        );
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan("EXECUTING")));
        when(engagementRepo.existsByOrganisationIdAndEngagementCodeAndIsDeletedFalse(ORG_ID, "ENG-2026-001")).thenReturn(false);
        when(engagementRepo.save(any(IaEngagement.class))).thenAnswer(inv -> {
            IaEngagement e = inv.getArgument(0);
            e.setId(ENG_ID);
            return e;
        });
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(ORG_ID, ENG_ID))
            .thenReturn(List.of());

        EngagementResponse out = service.create(req);
        assertThat(out.engagementCode()).isEqualTo("ENG-2026-001");
        assertThat(out.phase()).isEqualTo("PLANNED");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("IA_ENGAGEMENT_CREATED");
    }

    @Test void create_rejectsDuplicateEngagementCode() {
        EngagementCreateRequest req = new EngagementCreateRequest(
            PLAN_ID, null, "ENG-2026-001", "x", "x",
            "COMPLIANCE", null, null, null, null, null, null, null);
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan("EXECUTING")));
        when(engagementRepo.existsByOrganisationIdAndEngagementCodeAndIsDeletedFalse(ORG_ID, "ENG-2026-001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void create_blockedWhenPlanArchived() {
        EngagementCreateRequest req = new EngagementCreateRequest(
            PLAN_ID, null, "ENG-2026-X", "x", "x",
            "COMPLIANCE", null, null, null, null, null, null, null);
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID)).thenReturn(Optional.of(plan("ARCHIVED")));
        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ARCHIVED");
    }

    // ─── createFromUniverse ──────────────────────────────────────────

    @Test void createFromUniverse_copiesScopeFromUniverseEntry() {
        EngagementCreateFromUniverseRequest req = new EngagementCreateFromUniverseRequest(
            PLAN_ID, UNIVERSE_ID, "ENG-2026-FROM-U", "COMPLIANCE",
            42L, 99L, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 31)
        );
        when(universeRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, UNIVERSE_ID))
            .thenReturn(Optional.of(universe()));
        when(planRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, PLAN_ID))
            .thenReturn(Optional.of(plan("EXECUTING")));
        when(engagementRepo.existsByOrganisationIdAndEngagementCodeAndIsDeletedFalse(ORG_ID, "ENG-2026-FROM-U")).thenReturn(false);
        when(engagementRepo.save(any(IaEngagement.class))).thenAnswer(inv -> {
            IaEngagement e = inv.getArgument(0);
            e.setId(ENG_ID);
            return e;
        });
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(ORG_ID, ENG_ID))
            .thenReturn(List.of());

        EngagementResponse out = service.createFromUniverse(req);
        assertThat(out.titleEn()).isEqualTo("AML / KYC Onboarding");
        assertThat(out.titleAr()).contains("اعرف عميلك");
        assertThat(out.universeEntryId()).isEqualTo(UNIVERSE_ID);
        assertThat(out.scopeSummaryEn()).contains("Customer onboarding");
        assertThat(out.scopeSummaryAr()).contains("استقبال العميل");
    }

    @Test void createFromUniverse_universeNotFound_throws() {
        EngagementCreateFromUniverseRequest req = new EngagementCreateFromUniverseRequest(
            PLAN_ID, 999L, "ENG-X", "COMPLIANCE", null, null, null, null);
        when(universeRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, 999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createFromUniverse(req))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ─── transitionPhase ─────────────────────────────────────────────

    @Test void transitionPhase_plannedToPlanning_succeeds() {
        IaEngagement e = eng("PLANNED");
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));
        when(engagementRepo.save(any(IaEngagement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(ORG_ID, ENG_ID))
            .thenReturn(List.of());

        EngagementResponse out = service.transitionPhase(ENG_ID,
            new EngagementPhaseTransitionRequest("PLANNING", 42L, null, null));
        assertThat(out.phase()).isEqualTo("PLANNING");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("IA_ENGAGEMENT_PLANNING_STARTED");
    }

    @Test void transitionPhase_fieldworkStampsActualStartDate() {
        IaEngagement e = eng("PLANNING");
        assertThat(e.getActualStartDate()).isNull();
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));
        when(engagementRepo.save(any(IaEngagement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(ORG_ID, ENG_ID))
            .thenReturn(List.of());

        EngagementResponse out = service.transitionPhase(ENG_ID,
            new EngagementPhaseTransitionRequest("FIELDWORK", 42L, null, null));
        assertThat(out.phase()).isEqualTo("FIELDWORK");
        assertThat(out.actualStartDate()).isNotNull();
    }

    @Test void transitionPhase_reporting_blockedWithoutActualStart() {
        IaEngagement e = eng("FIELDWORK");
        e.setActualStartDate(null); // user skipped FIELDWORK side-effect
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.transitionPhase(ENG_ID,
            new EngagementPhaseTransitionRequest("REPORTING", 42L, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("actual fieldwork start");
    }

    @Test void transitionPhase_reportIssuedStampsActualEndDate() {
        IaEngagement e = eng("REPORTING");
        e.setActualStartDate(LocalDate.now().minusDays(30));
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));
        when(engagementRepo.save(any(IaEngagement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(ORG_ID, ENG_ID))
            .thenReturn(List.of());

        EngagementResponse out = service.transitionPhase(ENG_ID,
            new EngagementPhaseTransitionRequest("REPORT_ISSUED", 42L, null, null));
        assertThat(out.phase()).isEqualTo("REPORT_ISSUED");
        assertThat(out.actualEndDate()).isNotNull();

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("IA_ENGAGEMENT_REPORT_ISSUED");
    }

    @Test void transitionPhase_backwardJumpRejected() {
        IaEngagement e = eng("REPORTING");
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));

        // REPORTING → FIELDWORK is not in allowed forward transitions
        assertThatThrownBy(() -> service.transitionPhase(ENG_ID,
            new EngagementPhaseTransitionRequest("FIELDWORK", 42L, null, null)))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void transitionPhase_closedBlockedBeforeReportIssued() {
        IaEngagement e = eng("PLANNED");
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));

        // PLANNED → CLOSED is not in allowed list (must walk through phases)
        assertThatThrownBy(() -> service.transitionPhase(ENG_ID,
            new EngagementPhaseTransitionRequest("CLOSED", 42L, null, null)))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void transitionPhase_cancelledFromPlanned_succeeds() {
        IaEngagement e = eng("PLANNED");
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));
        when(engagementRepo.save(any(IaEngagement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(ORG_ID, ENG_ID))
            .thenReturn(List.of());

        EngagementResponse out = service.transitionPhase(ENG_ID,
            new EngagementPhaseTransitionRequest("CANCELLED", 42L, null, null));
        assertThat(out.phase()).isEqualTo("CANCELLED");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("IA_ENGAGEMENT_CANCELLED");
    }

    // ─── update ──────────────────────────────────────────────────────

    @Test void update_blockedInClosed() {
        IaEngagement e = eng("CLOSED");
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));
        EngagementUpdateRequest req = new EngagementUpdateRequest(
            "new", null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(ENG_ID, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CLOSED");
    }

    @Test void update_stampsPlanningMemoSignOffWhenUrlFirstSet() {
        IaEngagement e = eng("PLANNING");
        assertThat(e.getPlanningMemoSignedOffAt()).isNull();
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));
        when(engagementRepo.save(any(IaEngagement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(ORG_ID, ENG_ID))
            .thenReturn(List.of());

        EngagementUpdateRequest req = new EngagementUpdateRequest(
            null, null, null, null, null, null, null, null, null, null,
            "https://evidence/eng-2026-001-memo.pdf", null, null, null);
        EngagementResponse out = service.update(ENG_ID, req);
        assertThat(out.planningMemoUrl()).contains("eng-2026-001-memo");
        assertThat(out.planningMemoSignedOffAt()).isNotNull();
    }

    // ─── milestones ──────────────────────────────────────────────────

    @Test void recordMilestone_firstRecordEmitsReachedEvent() {
        IaEngagement e = eng("FIELDWORK");
        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndMilestoneTypeAndIsDeletedFalse(ORG_ID, ENG_ID, "KICKOFF"))
            .thenReturn(Optional.empty());
        when(milestoneRepo.save(any(IaEngagementMilestone.class))).thenAnswer(inv -> {
            IaEngagementMilestone m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });

        MilestoneResponse out = service.recordMilestone(ENG_ID,
            new MilestoneRecordRequest("KICKOFF", LocalDate.of(2026, 1, 15), 42L, "kickoff", "انطلاق"));
        assertThat(out.milestoneType()).isEqualTo("KICKOFF");
        assertThat(out.actualDate()).isEqualTo(LocalDate.of(2026, 1, 15));

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(ev.capture());
        assertThat(ev.getAllValues().stream().map(DomainEvent::triggerEvent))
            .contains("IA_MILESTONE_REACHED");
    }

    @Test void recordMilestone_idempotent_updatesExistingRow() {
        IaEngagement e = eng("FIELDWORK");
        IaEngagementMilestone existing = new IaEngagementMilestone();
        existing.setId(1L);
        existing.setOrganisationId(ORG_ID);
        existing.setEngagementId(ENG_ID);
        existing.setMilestoneType("KICKOFF");
        existing.setActualDate(LocalDate.of(2026, 1, 15)); // already recorded

        when(engagementRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENG_ID)).thenReturn(Optional.of(e));
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndMilestoneTypeAndIsDeletedFalse(ORG_ID, ENG_ID, "KICKOFF"))
            .thenReturn(Optional.of(existing));
        when(milestoneRepo.save(any(IaEngagementMilestone.class))).thenAnswer(inv -> inv.getArgument(0));

        MilestoneResponse out = service.recordMilestone(ENG_ID,
            new MilestoneRecordRequest("KICKOFF", LocalDate.of(2026, 1, 16), 42L, "corrected", "تصحيح"));
        assertThat(out.actualDate()).isEqualTo(LocalDate.of(2026, 1, 16));

        // No IA_MILESTONE_REACHED event this time — actualDate was already set
        // before this call, so service correctly treats it as an update, not first record.
        verify(publisher, org.mockito.Mockito.never()).publishEvent(any(DomainEvent.class));
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void listAll_filtersByOrgId() {
        when(engagementRepo.findByOrganisationIdAndIsDeletedFalseOrderByEngagementCodeAsc(ORG_ID))
            .thenReturn(List.of(eng("PLANNED")));
        when(milestoneRepo.findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(ORG_ID, ENG_ID))
            .thenReturn(List.of());
        List<EngagementResponse> out = service.listAll();
        assertThat(out).hasSize(1);
        verify(engagementRepo).findByOrganisationIdAndIsDeletedFalseOrderByEngagementCodeAsc(ORG_ID);
    }
}
