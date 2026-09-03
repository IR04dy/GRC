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
import java.util.List;
import java.util.Map;
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
    // F2-457: getStats() also reads these two. Without them @InjectMocks leaves the fields
    // null and every stats assertion below dies on an NPE rather than on its own claim.
    @Mock private com.tss.grc.module.bcm.cycle.repository.BcmGapRepository gapRepo;
    @Mock private com.tss.grc.module.bcm.cycle.repository.BcmCorrectiveActionRepository capaRepo;
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

    // ─── F2-298: the status guard on a PARTIAL put ────────────────────
    //
    // updatePlan had NO test of any kind before this, which is not the same as a path somebody
    // decided was safe (F2-248). The route is a full-replace PUT that binds the entity, and
    // F2-234/F2-296 made it tolerate an ECHO of the stored status and refuse a CHANGE — correct,
    // and while BcmPlan#status carried `= "DRAFT"` an OMITTED status deserialised to "DRAFT" and
    // was therefore read as a change, so every plan that was not already DRAFT refused a partial
    // body with a 400 naming a transition the caller had never asked for.

    @Test
    void updatePlan_partialBodyWithNoStatusIsAcceptedOnAnApprovedPlan() {
        BcmPlan stored = plan("APPROVED");
        when(planRepo.findById(ID)).thenReturn(Optional.of(stored));
        when(planRepo.save(any(BcmPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        BcmPlan patch = new BcmPlan();
        patch.setPlanNameEn("Renamed while approved");

        BcmPlan saved = service.updatePlan(ID, patch);

        assertThat(saved.getPlanNameEn()).isEqualTo("Renamed while approved");
        assertThat(saved.getStatus())
            .as("an omitted status must leave the stored state alone, not be read as a request to "
                + "move the plan back to the initial state")
            .isEqualTo("APPROVED");
    }

    @Test
    void updatePlan_theEntityDoesNotDefaultStatusSoAnOmittedOneIsGenuinelyAbsent() {
        // The structural half, pinned here as well as in NeverFiringNullGuardContractTest: if the
        // initialiser returns, the test above starts failing for a reason that reads like a service
        // bug rather than an entity one.
        assertThat(new BcmPlan().getStatus())
            .as("BcmPlan#status must NOT be initialised — createPlan owns the default through "
                + "ServerOwnedState.requireInitial, and an initialiser makes an omitted status "
                + "indistinguishable from a caller naming the initial state")
            .isNull();
    }

    @Test
    void updatePlan_anEchoOfTheStoredStatusIsToleratedEvenInADifferentCase() {
        // bcm.bcm_plans has no CHECK on status and the column collates case-insensitively, so
        // 'approved' IS the stored value as far as the database is concerned (F2-296).
        BcmPlan stored = plan("APPROVED");
        when(planRepo.findById(ID)).thenReturn(Optional.of(stored));
        when(planRepo.save(any(BcmPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        BcmPlan patch = new BcmPlan();
        patch.setPlanNameEn("Echoed");
        patch.setStatus("approved");

        assertThat(service.updatePlan(ID, patch).getStatus())
            .as("an echo is tolerated, and the STORED canonical spelling is what survives")
            .isEqualTo("APPROVED");
    }

    @Test
    void updatePlan_aStatedDifferentStatusIsStillRefusedAndNamesTheLifecycleRoutes() {
        BcmPlan stored = plan("APPROVED");
        when(planRepo.findById(ID)).thenReturn(Optional.of(stored));

        BcmPlan patch = new BcmPlan();
        patch.setStatus("DRAFT");

        assertThatThrownBy(() -> service.updatePlan(ID, patch))
            .isInstanceOf(IllegalArgumentException.class)
            // The information the refusal must carry, never its phrasing (F2-296: a message has
            // several fragments and asserting any one makes the whole wording load-bearing).
            .hasMessageContaining("APPROVED")
            .hasMessageContaining("DRAFT")
            .hasMessageContaining("submit-for-review");
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

    // ─── F2-300: an exercise may not be COMPLETED without an execution date ──
    //
    // completeExercise owns the pair (status = COMPLETED, executedDate = today). The
    // entity-bound create and the entity-bound full-replace PUT wrote both fields
    // independently and unconditionally, so either could produce a COMPLETED exercise
    // with no execution date — and the date is the evidence an ISO 22301 / NCEMA
    // reviewer reads to accept that the exercise took place. Screen-reachable: the BCM
    // page's status select offers COMPLETED with executedDate as a separate optional field.

    @Test
    void createExercise_completedWithNoExecutionDateIsRefusedAndNamesTheCompleteRoute() {
        BcmExercise req = new BcmExercise();
        req.setExerciseNameEn("Tabletop");
        req.setStatus("COMPLETED");

        assertThatThrownBy(() -> service.createExercise(req))
            .isInstanceOf(IllegalArgumentException.class)
            // The information the refusal must carry, never its phrasing (F2-296).
            .hasMessageContaining("executedDate")
            .hasMessageContaining("COMPLETED")
            .hasMessageContaining("/complete");
    }

    @Test
    void createExercise_completedWithAnExecutionDateIsAccepted() {
        when(exerciseRepo.save(any(BcmExercise.class))).thenAnswer(inv -> inv.getArgument(0));
        BcmExercise req = new BcmExercise();
        req.setExerciseNameEn("Tabletop");
        req.setStatus("COMPLETED");
        req.setExecutedDate(LocalDate.of(2026, 5, 1));

        assertThat(service.createExercise(req).getExecutedDate())
            .as("supplying the date is a legitimate way to record an exercise run offline")
            .isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void createExercise_aNonCompletedStatusNeedsNoExecutionDate() {
        // The leg that stops this becoming a gate that fails on correct usage (F2-214):
        // SCHEDULED/PLANNED are ordinary data entry and this register has no state machine.
        when(exerciseRepo.save(any(BcmExercise.class))).thenAnswer(inv -> inv.getArgument(0));
        BcmExercise req = new BcmExercise();
        req.setExerciseNameEn("Planned walkthrough");
        req.setStatus("SCHEDULED");

        assertThat(service.createExercise(req).getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void updateExercise_settingCompletedWithNoExecutionDateIsRefused() {
        when(exerciseRepo.findById(ID)).thenReturn(Optional.of(exercise("SCHEDULED")));
        BcmExercise patch = new BcmExercise();
        patch.setExerciseNameEn("Tabletop");
        patch.setStatus("COMPLETED");

        assertThatThrownBy(() -> service.updateExercise(ID, patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("executedDate")
            .hasMessageContaining("/complete");
    }

    @Test
    void updateExercise_aPartialBodyPreservesBothHalvesOfACompletedExercise() {
        // ⚠ My first draft of this leg asserted a 400 here, because executedDate was written
        // unconditionally and a partial body nulled it. That would have been a gate failing on
        // CORRECT usage — renaming a completed exercise is legitimate — which is exactly the trap
        // F2-297 and F2-298 were about. Both fields are null-guarded now, so "omitted" means
        // "leave alone" and the pair moves together.
        BcmExercise stored = exercise("COMPLETED");
        stored.setExecutedDate(LocalDate.of(2026, 3, 9));
        when(exerciseRepo.findById(ID)).thenReturn(Optional.of(stored));
        when(exerciseRepo.save(any(BcmExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        BcmExercise patch = new BcmExercise();
        patch.setExerciseNameEn("Renamed");
        // status and executedDate deliberately omitted

        BcmExercise saved = service.updateExercise(ID, patch);
        assertThat(saved.getExerciseNameEn()).isEqualTo("Renamed");
        assertThat(saved.getStatus())
            .as("an omitted status must not reset a COMPLETED exercise — the entity's "
                + "'SCHEDULED' initialiser used to make that silent (F2-238's reset shape)")
            .isEqualTo("COMPLETED");
        assertThat(saved.getExecutedDate())
            .as("and an omitted execution date must not clear the evidence the exercise ran")
            .isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void updateExercise_theEntityDoesNotDefaultStatusSoAnOmittedOneIsGenuinelyAbsent() {
        assertThat(new BcmExercise().getStatus())
            .as("BcmExercise#status must NOT be initialised — createExercise owns the default, and "
                + "an initialiser makes an omitted status indistinguishable from a caller naming "
                + "SCHEDULED, which is what silently reset completed exercises")
            .isNull();
    }

    @Test
    void updateExercise_keepingBothHalvesIsAccepted() {
        BcmExercise stored = exercise("COMPLETED");
        stored.setExecutedDate(LocalDate.of(2026, 3, 9));
        when(exerciseRepo.findById(ID)).thenReturn(Optional.of(stored));
        when(exerciseRepo.save(any(BcmExercise.class))).thenAnswer(inv -> inv.getArgument(0));

        BcmExercise patch = new BcmExercise();
        patch.setExerciseNameEn("Renamed");
        patch.setStatus("COMPLETED");
        patch.setExecutedDate(LocalDate.of(2026, 3, 9));

        BcmExercise saved = service.updateExercise(ID, patch);
        assertThat(saved.getExerciseNameEn()).isEqualTo("Renamed");
        assertThat(saved.getExecutedDate()).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void updateExercise_theGuardIsCaseInsensitiveOnTheStatusToken() {
        // bcm.bcm_exercises has no CHECK on status and the column collates case-insensitively,
        // so 'completed' IS the completed state as far as the database is concerned (F2-179).
        when(exerciseRepo.findById(ID)).thenReturn(Optional.of(exercise("SCHEDULED")));
        BcmExercise patch = new BcmExercise();
        patch.setStatus("completed");

        assertThatThrownBy(() -> service.updateExercise(ID, patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("executedDate");
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
    // ⚠⚠ This comment read "BiaRecord has no organisationId column; tenant ownership is
    // enforced via the parent CriticalProcess" until F2-279. The column has existed since
    // V1__core_schemas.sql — BIGINT NOT NULL DEFAULT ((1)) — and the entity simply did not
    // map it, so the database default decided every row's tenant and no query could scope on
    // it. The claim was written HERE and in BcmService, and two independent statements of a
    // false premise are what made it look settled (F2-177). Tenant ownership is now enforced
    // on the row's own column AND on the parent-process reference: different questions
    // (F2-242). These lock the cross-tenant read / overwrite / hard-delete IDOR shut.

    @Test
    void listBia_with_processId_of_other_tenant_throws_EntityNotFound() {
        // Attacker in ORG_ID supplies a processId owned by OTHER_ORG.
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(OTHER_ORG)));

        assertThatThrownBy(() -> service.listBia(PROC_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Critical process not found");

        // ⚠ F2-279: this verified the RETIRED finder, which listBia no longer calls at all —
        // so it would have passed trivially forever. Point a never() at the finder the code
        // actually reaches, or it asserts nothing.
        verify(biaRepo, never()).findByOrganisationIdAndProcessId(any(), any(), any());
        verify(biaRepo, never()).findByOrganisationId(any());
    }

    @Test
    void listBia_with_own_processId_returns_records() {
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(ORG_ID)));
        when(biaRepo.findByOrganisationIdAndProcessId(eq(ORG_ID), eq(PROC_ID), any()))
            .thenReturn(new PageImpl<>(java.util.List.of(bia(PROC_ID))));

        assertThat(service.listBia(PROC_ID)).hasSize(1);
    }

    @Test
    void listBia_without_processId_reads_by_the_tenant_column_not_the_process_link() {
        // F2-279: the old form enumerated the org's process ids and returned any row whose
        // PARENT was ours, so a row whose own organisation_id named another tenant was
        // served (probed live). ix_bia_records_org exists for this query.
        when(biaRepo.findByOrganisationId(ORG_ID))
            .thenReturn(java.util.List.of(bia(PROC_ID)));

        assertThat(service.listBia(null)).hasSize(1);

        verify(biaRepo).findByOrganisationId(ORG_ID);
        verify(biaRepo, never()).findByProcessIdIn(any());
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
        // The row is not this tenant's, so the org-scoped load misses.
        when(biaRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBia(ID, bia(PROC_ID)))
            .isInstanceOf(EntityNotFoundException.class);

        verify(biaRepo, never()).save(any());
    }

    @Test
    void updateBia_of_a_row_whose_parent_process_is_another_tenants_throws() {
        // Both checks are kept on purpose: the row can be ours while the parent-process
        // REFERENCE it holds (or the new one supplied) is not (F2-242).
        when(biaRepo.findByIdAndOrganisationId(ID, ORG_ID))
            .thenReturn(Optional.of(bia(PROC_ID)));
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(OTHER_ORG)));

        assertThatThrownBy(() -> service.updateBia(ID, bia(PROC_ID)))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Critical process not found");

        verify(biaRepo, never()).save(any());
    }

    @Test
    void deleteBia_of_other_tenant_throws_EntityNotFound_never_hard_deletes() {
        when(biaRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteBia(ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(biaRepo, never()).deleteById(any());
        verify(biaRepo, never()).save(any());
    }

    /**
     * ⚠⚠ F2-279: this asserted {@code b.getStatus()).isEqualTo("DELETED")} — i.e. it asserted
     * the defect as correct, and its name already said "soft_deletes". {@code is_deleted} was
     * unmapped and the entity carried no {@code @Where}, so writing a status token deleted
     * nothing: probed live, {@code DELETE} answered 204 and the row stayed in the list. Nth
     * instance of a test locking in a defect, and it is hardest to spot when it describes the
     * code accurately (F2-159). Repurposed rather than deleted — the half that protects the
     * record (never a hard delete, exactly one save) is kept.
     */
    @Test
    void deleteBia_of_own_tenant_sets_the_soft_delete_flag_not_a_status_token() {
        BiaRecord b = bia(PROC_ID);
        when(biaRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(b));
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(ORG_ID)));
        when(biaRepo.save(any(BiaRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteBia(ID);

        assertThat(b.getIsDeleted()).isTrue();
        assertThat(b.getStatus())
            .as("the lifecycle status is not a delete marker; @Where(is_deleted = 0) is")
            .isEqualTo("DRAFT");
        verify(biaRepo).save(b);
        verify(biaRepo, never()).deleteById(any());
    }

    @Test
    void createBia_stamps_the_tenant_and_never_takes_it_from_the_body() {
        // F2-279: bcm.bia_records.organisation_id is NOT NULL DEFAULT ((1)) and the entity
        // did not map it, so Hibernate never named the column and the DATABASE DEFAULT
        // decided the tenant. ServerOwnedFieldsModule deliberately does not cover
        // organisationId, so the stamp has to be here and must be unconditional.
        BiaRecord req = bia(PROC_ID);
        req.setOrganisationId(OTHER_ORG);        // hostile: a body claiming another tenant
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(ORG_ID)));
        when(biaRepo.save(any(BiaRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        BiaRecord saved = service.createBia(req);

        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getId()).as("null id forces an INSERT rather than a merge").isNull();
    }

    @Test
    void updateBia_writes_the_linked_risk_so_it_is_not_frozen_after_creation() {
        // F2-279: BcmBiaRequest has always declared linkedRiskId and the whitelist omitted
        // it, so a BIA's linked risk was settable once at creation and every later edit was
        // accepted with HTTP 200 and silently discarded.
        BiaRecord existing = bia(PROC_ID);
        existing.setLinkedRiskId(5L);
        when(biaRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(existing));
        when(processRepo.findById(PROC_ID)).thenReturn(Optional.of(process(ORG_ID)));
        when(biaRepo.save(any(BiaRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        BiaRecord req = bia(PROC_ID);
        req.setLinkedRiskId(6L);
        assertThat(service.updateBia(ID, req).getLinkedRiskId()).isEqualTo(6L);

        // and it must be clearable, not just changeable
        BiaRecord clear = bia(PROC_ID);
        clear.setLinkedRiskId(null);
        assertThat(service.updateBia(ID, clear).getLinkedRiskId()).isNull();
    }

    // ── F2-457 — getStats() provenance ───────────────────────────────────────
    //
    // Two ISO 22301 KPI keys used to be published as `successRate`, the EXERCISE COMPLETION
    // rate: one number rendered three times on the BCM dashboard under three labels, twice
    // as a recovery-objective compliance figure. Nothing in the platform computes RTO or RPO
    // compliance -- no exercise records an achieved recovery time -- so the honest answer is
    // to publish neither and let UX-008's em-dash carry the absence.
    //
    // These legs are BEHAVIOURAL on purpose. A source scan cannot tell a re-added key from a
    // comment naming one, and the fix deliberately quotes both retired names (F2-450), so the
    // only assertion that discriminates is what the map actually contains.

    /** Build a stats map from a fixture with `processes` processes and BIAs on `covered` of them. */
    private Map<String, Long> statsWith(int processes, List<Long> biaProcessIds, int exercises,
                                        int completedExercises) {
        List<CriticalProcess> procs = new java.util.ArrayList<>();
        for (int i = 0; i < processes; i++) {
            CriticalProcess p = new CriticalProcess();
            p.setId((long) (i + 1));
            p.setOrganisationId(ORG_ID);
            procs.add(p);
        }
        List<BiaRecord> bias = new java.util.ArrayList<>();
        for (Long pid : biaProcessIds) {
            BiaRecord b = new BiaRecord();
            b.setOrganisationId(ORG_ID);
            b.setProcessId(pid);
            bias.add(b);
        }
        List<BcmExercise> exs = new java.util.ArrayList<>();
        for (int i = 0; i < exercises; i++) {
            exs.add(exercise(i < completedExercises ? "COMPLETED" : "SCHEDULED"));
        }

        when(planRepo.findByOrganisationId(eq(ORG_ID), any())).thenReturn(new PageImpl<>(List.of()));
        when(processRepo.findByOrganisationId(eq(ORG_ID), any())).thenReturn(new PageImpl<>(procs));
        when(biaRepo.findByOrganisationId(ORG_ID)).thenReturn(bias);
        when(exerciseRepo.findByOrganisationId(eq(ORG_ID), any())).thenReturn(new PageImpl<>(exs));
        when(gapRepo.findByOrganisationIdOrderByIdDesc(ORG_ID)).thenReturn(List.of());
        when(capaRepo.findByOrganisationIdAndDueDateBeforeAndStatusNotIn(
                eq(ORG_ID), any(), any())).thenReturn(List.of());
        return service.getStats();
    }

    @Test
    void getStats_publishesNoRtoOrRpoComplianceFigureBecauseNothingComputesOne() {
        Map<String, Long> m = statsWith(4, List.of(1L, 2L, 3L, 4L), 2, 1);

        assertThat(m).as("""
            getStats() must publish NEITHER rtoCompliancePercent NOR rpoCompliancePercent.

            Both used to carry `successRate` -- completed exercises over total exercises -- which \
            is the exercise completion rate and neither an RTO nor an RPO compliance figure. The \
            dashboard rendered the same number three times under three labels, twice as recovery- \
            objective compliance, in both languages.

            Nothing in this platform computes either: no exercise records an achieved recovery \
            time, so there is nothing to compare against the RTO/RPO targets the BIAs and plans \
            carry. Publishing nothing lets BcmDashboardPage render the em-dash with a reason \
            (UX-008), which is the ratified treatment for a metric that cannot be computed \
            (F2-195/F2-197: deleting an invented figure must not assert health).

            Computing them for real is a FEATURE -- it needs an achieved-recovery-time column \
            that does not exist -- not a value borrowed from the nearest available percentage.""")
            .doesNotContainKeys("rtoCompliancePercent", "rpoCompliancePercent");
    }

    @Test
    void getStats_labelsTheExerciseSuccessRateOnceAndOnlyAsItself() {
        Map<String, Long> m = statsWith(4, List.of(1L, 2L, 3L, 4L), 4, 3);

        // 3 of 4 completed = 75%. The point is not the arithmetic but that exactly ONE key
        // carries it: a measurement published under a second label is a mislabelled metric.
        assertThat(m.get("exerciseSuccessRate")).isEqualTo(75L);
        long keysCarrying75 = m.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() == 75L)
                .count();
        assertThat(keysCarrying75).as("""
            The exercise completion rate must reach exactly ONE map key. An earlier draft \
            excluded rtoCompliancePercent/rpoCompliancePercent from this count, which only ever \
            suppressed a CORRECT failure: re-adding either alias must fail here as well as in \
            the absence leg above, and defence in depth is the point of having both. Two keys \
            carrying \
            one measurement is how F2-457 happened: the number was correct and two of its three \
            labels were not.

            If this fires because a NEW key legitimately equals 75 by coincidence, change the \
            fixture's ratio rather than relaxing the count.""")
            .isEqualTo(1L);
    }

    @Test
    void getStats_biaCompletionCountsProcessesCoveredNotBiaRows() {
        // 4 processes; 5 BIA rows, but process 1 carries two of them. Rows/processes = 125%,
        // which the retired Math.min(100, ...) would have capped to a flattering 100%.
        Map<String, Long> m = statsWith(4, List.of(1L, 1L, 2L, 3L, 4L), 0, 0);

        assertThat(m.get("totalBia")).as(
            "totalBia is a count of BIA RECORDS and keeps that meaning").isEqualTo(5L);
        assertThat(m.get("biaCompletionPercent")).as("""
            biaCompletionPercent is coverage over PROCESSES, so its numerator must count \
            processes. Here 4 of 4 are covered by 5 records, so it is 100 -- and the old \
            expression (rows / processes, capped) reached the same 100 for the wrong reason.

            The discriminating case is the next assertion: drop one process's BIA and the \
            honest figure falls while the row count does not.""")
            .isEqualTo(100L);
    }

    @Test
    void getStats_biaCompletionFallsWhenAProcessHasNoBiaEvenIfRowCountStillExceedsIt() {
        // 4 processes; 4 BIA rows, but two of them are on process 1 -- so processes 3 and 4
        // are uncovered. Rows/processes = 100% (flattering and wrong); coverage = 50%.
        Map<String, Long> m = statsWith(4, List.of(1L, 1L, 2L, 2L), 0, 0);

        assertThat(m.get("totalBia")).isEqualTo(4L);
        assertThat(m.get("biaCompletionPercent")).as("""
            THE discriminating leg. Four BIA rows over four processes is 100%% by the retired \
            arithmetic, while only two of the four processes actually have a BIA. A readiness \
            KPI that reports full BIA coverage for a programme with half its critical processes \
            unanalysed is wrong in the flattering direction, which is the direction nobody \
            reports.""")
            .isEqualTo(50L);
    }

    @Test
    void getStats_aBiaWithNoProcessLinkCoversNoProcess() {
        // 2 processes; 3 BIA rows, one of them unlinked. Only process 1 is covered.
        Map<String, Long> m = statsWith(2, java.util.Arrays.asList(1L, null, null), 0, 0);

        assertThat(m.get("totalBia")).isEqualTo(3L);
        assertThat(m.get("biaCompletionPercent")).as("""
            A BIA with a null process_id cannot cover a process, so it must be excluded from the \
            numerator rather than counted. Both live databases have zero such rows today, so this \
            pins a decision rather than observing a defect -- and the alternative (counting them) \
            would inflate coverage with records that analyse nothing.""")
            .isEqualTo(50L);
    }

    /**
     * F2-457, leg 6 -- cross-codebase: the dashboard must not name a key the server does not send.
     *
     * <p>Without this the whole frontend half is unasserted. Reverting it re-declares
     * {@code rtoCompliancePercent} on {@code BcmStatsShape}, {@code tsc} is satisfied, and the
     * panel interpolates {@code undefined%} with nothing to notice -- the mirror of F2-211, where a
     * client type declaring a field the wire never carries produced an em-dash on every row.
     *
     * <p>Comments are stripped first, in ONE left-to-right alternation. This fix quotes both
     * retired key names on purpose (F2-450 -- a faithful quotation is what makes the stripping
     * demonstrably load-bearing rather than decorative), so a raw scan would read the epitaph as
     * the corpse (F2-193) and fail on a correct tree. Two sequential passes corrupt the source
     * (F2-198), and the {@code (?<!:)} is what stops a {@code https://} inside a string literal
     * opening a comment.
     */
    @Test
    void theBcmDashboardNamesNoKeyTheStatsMapNoLongerSends() throws java.io.IOException {
        java.nio.file.Path page = java.nio.file.Paths.get(
            "../tss-grc-ui-v2/src/pages/dashboards/BcmDashboardPage.tsx")
            .toAbsolutePath().normalize();
        assertThat(java.nio.file.Files.exists(page)).as(
            "Expected %s to exist. A wrong path would make this assertion vacuous rather than "
            + "failing, which is the one outcome a cross-codebase leg must not have.", page)
            .isTrue();

        String stripped = java.util.regex.Pattern
            .compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/")
            .matcher(java.nio.file.Files.readString(page))
            .replaceAll("");

        // Anti-vacuity: the stripper must leave the page's real code behind. If it ever blanks
        // the file, the absence assertions below pass over nothing (F2-235).
        assertThat(stripped).as(
            "The comment stripper appears to have blanked real code -- the page's own KPI array "
            + "should survive it.").contains("const kpis").contains("exerciseSuccessRate");

        for (String retired : java.util.List.of("rtoCompliancePercent", "rpoCompliancePercent")) {
            assertThat(stripped).as("""
                BcmDashboardPage still references %s outside a comment, and BcmService no longer \
                publishes it. The server used to send the exercise completion rate under that \
                name; a client that keeps reading it renders `undefined%%` in the recovery- \
                objectives panel, or an em-dash that looks like missing data rather than an \
                uncomputable metric.

                If RTO/RPO compliance is ever really computed, add it to the stats map FIRST and \
                this leg will let the page read it again.""".formatted(retired))
                .doesNotContain(retired);
        }

        // And the tile must still be THERE, as an explicit absence. UX-008's convention exists so
        // a metric the platform cannot compute stays visible; silently deleting the tile drops
        // the question rather than answering it (F2-197 -- an absent figure must not read as health).
        // WARNING: asserted as a PAIRING, not as the token. The first draft was
        // .contains("notMeasured"), which the const DECLARATION satisfies -- so swapping the
        // tile's hint back to `notComputed` SURVIVED the whole build. F2-194's
        // substring-satisfied-by-something-else, found by the control harness in a leg written
        // after quoting that very rule.
        assertThat(stripped).as("""
            The RTO tile must survive as an em-dash whose hint is `notMeasured`, not `notComputed`.

            UX-008 has three states and the difference between the last two is the whole point: a \
            value, a metric NOT YET measured, and one the platform CANNOT measure. RTO compliance \
            is the third -- no exercise records an achieved recovery time -- so telling an operator \
            it is "not yet computed" promises a figure that is never coming.

            Deleting the tile is equally wrong: the convention exists so an uncomputable metric \
            stays visible as absent rather than quietly disappearing (F2-197).""")
            .containsPattern("(?s)'RTO compliance'.{0,240}?emptyHint:\\s*notMeasured");

        // F2-209: the reason must reach a screen reader as well as a pointer. A `title` alone is
        // a tooltip a keyboard user never receives, which is why the Kpi convention sets both.
        assertThat(stripped).as(
            "The recovery-objectives disclosure must carry its reason on BOTH title and "
            + "aria-label -- a title-only hint is invisible to a screen reader (F2-209).")
            .containsPattern("(?s)title=\\{notMeasured\\}.{0,80}?aria-label=\\{notMeasured\\}");
    }
}
