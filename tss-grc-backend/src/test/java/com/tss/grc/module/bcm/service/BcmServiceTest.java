package com.tss.grc.module.bcm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.entity.BcmExercise;
import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.repository.BcmExerciseRepository;
import com.tss.grc.module.bcm.repository.BcmPlanRepository;
import com.tss.grc.module.bcm.repository.BiaRecordRepository;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BcmService — Sprint 4 §4.1 test backlog.
 *
 * Locks the ISO 22301 BCM plan lifecycle:
 *
 *   DRAFT ──► UNDER_REVIEW ──► APPROVED ──► INVOKED ──► APPROVED  (after restoration)
 *      └─────────────────────► APPROVED  (skip review for trivial plans)
 *
 * Plus exercise programme behaviour (ISO 22301 §8.5):
 *   - completeExercise stamps status=COMPLETED + executedDate=today + persists score/findings
 *   - delete is soft (sets isDeleted, never deleteById)
 *
 * Cross-tenant lookups must surface as 404, never leak data.
 */
@ExtendWith(MockitoExtension.class)
class BcmServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long ID        = 7L;

    @Mock private BcmPlanRepository planRepo;
    @Mock private CriticalProcessRepository processRepo;
    @Mock private BiaRecordRepository biaRepo;
    @Mock private BcmExerciseRepository exerciseRepo;
    @InjectMocks private BcmService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static BcmPlan plan(String status, Long orgId) {
        BcmPlan p = new BcmPlan();
        p.setId(ID);
        p.setOrganisationId(orgId);
        p.setPlanNameEn("Crisis Recovery Plan");
        p.setStatus(status);
        return p;
    }

    private static BcmPlan plan(String status) { return plan(status, ORG_ID); }

    private static BcmExercise exercise(String status) {
        BcmExercise e = new BcmExercise();
        e.setId(ID);
        e.setOrganisationId(ORG_ID);
        e.setStatus(status);
        return e;
    }

    private static final Long PROC_ID = 42L;

    private static CriticalProcess process(Long orgId) {
        CriticalProcess p = new CriticalProcess();
        p.setId(PROC_ID);
        p.setOrganisationId(orgId);
        p.setProcessNameEn("Core Banking");
        return p;
    }

    private static BiaRecord bia(Long processId) {
        BiaRecord b = new BiaRecord();
        b.setId(ID);
        b.setProcessId(processId);
        b.setStatus("DRAFT");
        b.setRtoHours(4);
        return b;
    }

    // ─── Plan lifecycle: happy path ──────────────────────────────────

    @Test
    void submitForReview_DRAFT_to_UNDER_REVIEW() {
        BcmPlan p = plan("DRAFT");
        when(planRepo.findById(ID)).thenReturn(Optional.of(p));
        when(planRepo.save(any(BcmPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.submitForReview(ID).getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void approvePlan_from_DRAFT() {
        // Allowed: DRAFT can skip review for trivial plans
        BcmPlan p = plan("DRAFT");
        when(planRepo.findById(ID)).thenReturn(Optional.of(p));
        when(planRepo.save(any(BcmPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.approvePlan(ID).getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approvePlan_from_UNDER_REVIEW() {
        BcmPlan p = plan("UNDER_REVIEW");
        when(planRepo.findById(ID)).thenReturn(Optional.of(p));
        when(planRepo.save(any(BcmPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.approvePlan(ID).getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void invokePlan_APPROVED_to_INVOKED() {
        BcmPlan p = plan("APPROVED");
        when(planRepo.findById(ID)).thenReturn(Optional.of(p));
        when(planRepo.save(any(BcmPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.invokePlan(ID).getStatus()).isEqualTo("INVOKED");
    }

    @Test
    void restorePlan_INVOKED_back_to_APPROVED() {
        // Crisis-over: revert to baseline approved plan
        BcmPlan p = plan("INVOKED");
        when(planRepo.findById(ID)).thenReturn(Optional.of(p));
        when(planRepo.save(any(BcmPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.restorePlan(ID).getStatus()).isEqualTo("APPROVED");
    }

    // ─── Plan lifecycle: illegal transitions ─────────────────────────

    @Test
    void submitForReview_from_APPROVED_throws() {
        when(planRepo.findById(ID)).thenReturn(Optional.of(plan("APPROVED")));

        assertThatThrownBy(() -> service.submitForReview(ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DRAFT");

        verify(planRepo, never()).save(any());
    }

    @Test
    void approvePlan_from_INVOKED_throws() {
        when(planRepo.findById(ID)).thenReturn(Optional.of(plan("INVOKED")));

        assertThatThrownBy(() -> service.approvePlan(ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DRAFT or UNDER_REVIEW");
    }

    @Test
    void invokePlan_from_DRAFT_throws() {
        // Cannot invoke an unapproved plan in production
        when(planRepo.findById(ID)).thenReturn(Optional.of(plan("DRAFT")));

        assertThatThrownBy(() -> service.invokePlan(ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("APPROVED");
    }

    @Test
    void restorePlan_from_APPROVED_throws() {
        // Already restored — restoring from APPROVED is a no-op caller error
        when(planRepo.findById(ID)).thenReturn(Optional.of(plan("APPROVED")));

        assertThatThrownBy(() -> service.restorePlan(ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("INVOKED");
    }

    // ─── Tenant scope ────────────────────────────────────────────────

    @Test
    void getPlan_other_tenant_throws_EntityNotFound() {
        when(planRepo.findById(ID)).thenReturn(Optional.of(plan("DRAFT", OTHER_ORG)));

        assertThatThrownBy(() -> service.getPlan(ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("BCM Plan not found");
    }

    @Test
    void invokePlan_other_tenant_throws_EntityNotFound() {
        when(planRepo.findById(ID)).thenReturn(Optional.of(plan("APPROVED", OTHER_ORG)));

        assertThatThrownBy(() -> service.invokePlan(ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(planRepo, never()).save(any());
    }

    // ─── Soft delete ─────────────────────────────────────────────────

    @Test
    void deletePlan_soft_deletes_via_save() {
        BcmPlan p = plan("DRAFT");
        when(planRepo.findById(ID)).thenReturn(Optional.of(p));
        when(planRepo.save(any(BcmPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deletePlan(ID);

        assertThat(p.getIsDeleted()).isTrue();
        verify(planRepo).save(p);
        verify(planRepo, never()).deleteById(any());
    }

    // ─── Exercise programme ──────────────────────────────────────────

    @Test
    void completeExercise_stamps_status_executedDate_score_findings() {
        BcmExercise e = exercise("IN_PROGRESS");
        when(exerciseRepo.findById(ID)).thenReturn(Optional.of(e));
        when(exerciseRepo.save(any(BcmExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        BcmExercise after = service.completeExercise(ID, 87, "RTO target met; comms gap identified");
        assertThat(after.getStatus()).isEqualTo("COMPLETED");
        assertThat(after.getExecutedDate()).isEqualTo(LocalDate.now());
        assertThat(after.getScore()).isEqualTo(87);
        assertThat(after.getFindings()).contains("comms gap");
    }

    @Test
    void completeExercise_null_score_findings_keeps_existing_values() {
        // Defensive: caller may complete without bumping score (e.g. just status change)
        BcmExercise e = exercise("IN_PROGRESS");
        e.setScore(50);
        e.setFindings("prior findings");
        when(exerciseRepo.findById(ID)).thenReturn(Optional.of(e));
        when(exerciseRepo.save(any(BcmExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        BcmExercise after = service.completeExercise(ID, null, null);
        assertThat(after.getStatus()).isEqualTo("COMPLETED");
        assertThat(after.getScore()).isEqualTo(50);              // unchanged
        assertThat(after.getFindings()).isEqualTo("prior findings");  // unchanged
    }

    @Test
    void deleteExercise_soft_deletes_and_bumps_updatedAt() {
        BcmExercise e = exercise("SCHEDULED");
        java.time.LocalDateTime priorUpdate = java.time.LocalDateTime.now().minusDays(5);
        e.setUpdatedAt(priorUpdate);
        when(exerciseRepo.findById(ID)).thenReturn(Optional.of(e));
        when(exerciseRepo.save(any(BcmExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteExercise(ID);

        assertThat(e.getIsDeleted()).isTrue();
        assertThat(e.getUpdatedAt()).isAfter(priorUpdate);
        verify(exerciseRepo, never()).deleteById(any());
    }

    // ─── BIA tenant scope (SEC-BCM-001 — IDOR / CWE-639) ─────────────
    //
    // BiaRecord has no organisationId column; tenant ownership is enforced
    // via the parent CriticalProcess. These lock the cross-tenant read /
    // overwrite / hard-delete IDOR shut.

    @Test
    void listBia_with_processId_of_other_tenant_throws_EntityNotFound() {
        // Attacker in ORG_ID supplies a processId owned by OTHER_ORG.
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(OTHER_ORG)));

        assertThatThrownBy(() -> service.listBia(PROC_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Critical process not found");

        verify(biaRepo, never()).findByProcessId(any(), any());
    }

    @Test
    void listBia_with_own_processId_returns_records() {
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(ORG_ID)));
        when(biaRepo.findByProcessId(eq(PROC_ID), any()))
            .thenReturn(new PageImpl<>(java.util.List.of(bia(PROC_ID))));

        assertThat(service.listBia(PROC_ID)).hasSize(1);
    }

    @Test
    void createBia_with_processId_of_other_tenant_throws_EntityNotFound() {
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(OTHER_ORG)));

        assertThatThrownBy(() -> service.createBia(bia(PROC_ID)))
            .isInstanceOf(EntityNotFoundException.class);

        verify(biaRepo, never()).save(any());
    }

    @Test
    void updateBia_of_other_tenant_throws_EntityNotFound_and_no_save() {
        // BIA row exists, but its parent process belongs to OTHER_ORG.
        when(biaRepo.findById(ID)).thenReturn(Optional.of(bia(PROC_ID)));
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(OTHER_ORG)));

        assertThatThrownBy(() -> service.updateBia(ID, bia(PROC_ID)))
            .isInstanceOf(EntityNotFoundException.class);

        verify(biaRepo, never()).save(any());
    }

    @Test
    void deleteBia_of_other_tenant_throws_EntityNotFound_never_hard_deletes() {
        when(biaRepo.findById(ID)).thenReturn(Optional.of(bia(PROC_ID)));
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(OTHER_ORG)));

        assertThatThrownBy(() -> service.deleteBia(ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(biaRepo, never()).deleteById(any());
        verify(biaRepo, never()).save(any());
    }

    @Test
    void deleteBia_of_own_tenant_soft_deletes_via_save() {
        BiaRecord b = bia(PROC_ID);
        when(biaRepo.findById(ID)).thenReturn(Optional.of(b));
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(ORG_ID)));
        when(biaRepo.save(any(BiaRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteBia(ID);

        assertThat(b.getStatus()).isEqualTo("DELETED");
        verify(biaRepo).save(b);
        verify(biaRepo, never()).deleteById(any());
    }
}
