package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmAssessmentCycle;
import com.tss.grc.module.bcm.cycle.entity.BcmGovernanceRecord;
import com.tss.grc.module.bcm.cycle.repository.BcmAssessmentCycleRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmGovernanceRecordRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * BcmCycleService — Sprint BCM-1 lifecycle locks.
 *
 * <p>Cycle:    DRAFT → IN_PROGRESS → UNDER_REVIEW → APPROVED → ARCHIVED.
 * <p>Gov:      DRAFT → UNDER_REVIEW → APPROVED → ACTIVE; CHANGES_REQUIRED loops.
 */
@ExtendWith(MockitoExtension.class)
class BcmCycleServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private BcmAssessmentCycleRepository cycles;
    @Mock private BcmGovernanceRecordRepository governance;
    @Mock private CommitteeRepository committees;
    @Mock private AuditLogService audit;
    @Mock private org.springframework.context.ApplicationEventPublisher publisher;
    @InjectMocks private BcmCycleService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static BcmAssessmentCycle cycle(String status) {
        BcmAssessmentCycle c = new BcmAssessmentCycle();
        c.setId(1L); c.setOrganisationId(ORG_ID);
        c.setCycleNameEn("BCM Readiness Cycle 2025");
        c.setReportingYear((short) 2025);
        c.setStatus(status);
        return c;
    }

    private static BcmGovernanceRecord gov(String status) {
        BcmGovernanceRecord g = new BcmGovernanceRecord();
        g.setId(1L); g.setOrganisationId(ORG_ID);
        g.setPolicyTitleEn("BCM Governance Policy v1.0");
        g.setPolicyVersion("1.0");
        g.setApprovalStatus(status);
        return g;
    }

    // ── Cycle creation ─────────────────────────────────────────────

    @Test void cycle_createRequiresName() {
        BcmAssessmentCycle body = new BcmAssessmentCycle();
        body.setReportingYear((short) 2025);
        assertThatThrownBy(() -> service.createCycle(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cycleNameEn");
    }

    @Test void governance_createRejectsUnknownCommittee() {
        BcmGovernanceRecord body = gov("DRAFT");
        body.setCommitteeId(999L);
        when(committees.findByIdAndOrganisationId(999L, ORG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createGovernance(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("committee");
    }

    @Test void cycle_createDuplicateYearRejected() {
        BcmAssessmentCycle body = cycle("DRAFT");
        when(cycles.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(Optional.of(body));
        assertThatThrownBy(() -> service.createCycle(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
    }

    // ── Cycle lifecycle ────────────────────────────────────────────

    @Test void cycle_transitionDraftToInProgressOk() {
        BcmAssessmentCycle c = cycle("DRAFT");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(cycles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmAssessmentCycle out = service.transitionCycle(1L, "IN_PROGRESS", "alice");
        assertThat(out.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test void cycle_approvalStampsApprover() {
        BcmAssessmentCycle c = cycle("UNDER_REVIEW");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(cycles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmAssessmentCycle out = service.transitionCycle(1L, "APPROVED", "alice");
        assertThat(out.getStatus()).isEqualTo("APPROVED");
        assertThat(out.getApprovedBy()).isEqualTo("alice");
        assertThat(out.getApprovedAt()).isNotNull();
    }

    @Test void cycle_underReviewCanReturnToInProgress() {
        BcmAssessmentCycle c = cycle("UNDER_REVIEW");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(cycles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmAssessmentCycle out = service.transitionCycle(1L, "IN_PROGRESS", null);
        assertThat(out.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test void cycle_illegalTransitionRejected() {
        BcmAssessmentCycle c = cycle("DRAFT");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.transitionCycle(1L, "APPROVED", null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void cycle_updateApprovedRefused() {
        BcmAssessmentCycle c = cycle("APPROVED");
        when(cycles.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        BcmAssessmentCycle patch = new BcmAssessmentCycle();
        patch.setCycleNameEn("Edited after approval");
        assertThatThrownBy(() -> service.updateCycle(1L, patch))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPROVED");
    }

    // ── Governance lifecycle ──────────────────────────────────────

    @Test void gov_createRequiresTitle() {
        BcmGovernanceRecord body = new BcmGovernanceRecord();
        assertThatThrownBy(() -> service.createGovernance(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("policyTitleEn");
    }

    @Test void gov_changesRequiredLoopsBackToDraft() {
        BcmGovernanceRecord g = gov("CHANGES_REQUIRED");
        when(governance.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(g));
        when(governance.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmGovernanceRecord out = service.transitionGovernance(1L, "DRAFT", null);
        assertThat(out.getApprovalStatus()).isEqualTo("DRAFT");
    }

    @Test void gov_approveStampsApprover() {
        BcmGovernanceRecord g = gov("UNDER_REVIEW");
        when(governance.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(g));
        when(governance.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmGovernanceRecord out = service.transitionGovernance(1L, "APPROVED", "bob");
        assertThat(out.getApprovedBy()).isEqualTo("bob");
        assertThat(out.getApprovedAt()).isNotNull();
    }

    @Test void gov_activeCannotJumpBackToDraft() {
        BcmGovernanceRecord g = gov("ACTIVE");
        when(governance.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(g));
        assertThatThrownBy(() -> service.transitionGovernance(1L, "DRAFT", null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void gov_invalidReviewFrequencyRejected() {
        BcmGovernanceRecord body = gov("DRAFT");
        body.setReviewFrequency("FORTNIGHTLY");
        assertThatThrownBy(() -> service.createGovernance(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("review frequency");
    }
}
