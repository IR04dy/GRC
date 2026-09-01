package com.tss.grc.module.bcm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.bcm.entity.BcmExercise;
import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.entity.CriticalProcess;
import com.tss.grc.module.bcm.repository.BcmExerciseRepository;
import com.tss.grc.module.bcm.repository.BcmPlanRepository;
import com.tss.grc.module.bcm.repository.BiaRecordRepository;
import com.tss.grc.module.bcm.repository.CriticalProcessRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.Map;

/**
 * Legacy BCM operational service — audit T1.1.
 *
 * <p>Pre-cycle implementation (DRAFT → UNDER_REVIEW → APPROVED → INVOKED
 * state machine on {@link BcmPlan}). Retained to keep the legacy
 * {@code /bcm/legacy} page functional and to back the "Generate BCP from
 * BIA" stub in {@link com.tss.grc.module.bcm.cycle.controller.BcmBiaAssessmentController},
 * which materialises new plans into this table.
 *
 * <p><b>New work should use the cycle services under
 * {@code com.tss.grc.module.bcm.cycle}:</b>
 * <ul>
 *   <li>{@code BcmCycleService}             for assessment cycles + governance</li>
 *   <li>{@code BcmBiaAssessmentService}     for BIA (richer than {@link BiaRecord})</li>
 *   <li>{@code BcmRiskService}              for the BCM risk register</li>
 *   <li>{@code ContinuityStrategyService}   for strategies</li>
 *   <li>{@code CrisisEventService}          for crisis events with SitReps + decisions</li>
 *   <li>{@code BcmRemediationService}       for lessons + gaps + CAPAs</li>
 *   <li>{@code BcmComplianceMappingService} for regulator clause × evidence</li>
 * </ul>
 * Cycle services carry the F7 audit log + F8 event publication that this
 * legacy service lacks.
 */
@Deprecated(since = "BCM consolidation T1.1", forRemoval = false)
@Service
@RequiredArgsConstructor
public class BcmService {

    private final BcmPlanRepository planRepo;
    private final CriticalProcessRepository processRepo;
    private final BiaRecordRepository biaRepo;
    private final BcmExerciseRepository exerciseRepo;
    private final com.tss.grc.module.bcm.cycle.repository.BcmGapRepository gapRepo;
    private final com.tss.grc.module.bcm.cycle.repository.BcmCorrectiveActionRepository capaRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    // ── BCM Plans ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BcmPlan> listPlans(Pageable pageable) {
        return planRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public BcmPlan getPlan(Long id) {
        return planRepo.findById(id)
            .filter(p -> p.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("BCM Plan not found: " + id));
    }

    /**
     * F2-234 — the state a legacy BCM plan is CREATED in. Every other status belongs to one of
     * the four guarded lifecycle routes below, each of which refuses out-of-order calls.
     *
     * <p>⚠ {@code bcm.bcm_plans} has <b>zero CHECK constraints</b> on a NOT NULL {@code status}
     * (measured 2026-08-18, dev and prod identical: 10 rows, DRAFT 7 / APPROVED 3). The create
     * had no vocabulary test of any kind, so an arbitrary string was storable — and each
     * transition guard is an equality test against a named state, so such a row would be
     * <b>permanently untransitionable by every route</b>. That is F2-220's {@code platform.issues}
     * shape, creatable on demand through the API.
     */
    private static final String INITIAL_PLAN_STATUS = "DRAFT";

    @Transactional
    public BcmPlan createPlan(BcmPlan req) {
        // Mass-assignment defense — controller accepts the entity via
        // @RequestBody. Null id forces save() to INSERT instead of
        // merge() into a potentially cross-tenant row.
        req.setId(null);
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setStatus(ServerOwnedState.requireInitial(
            req.getStatus(), INITIAL_PLAN_STATUS, "a BCM plan",
            "POST /api/v1/bcm/legacy-plans/{id}/submit-for-review"));
        return planRepo.save(req);
    }

    @Transactional
    public BcmPlan updatePlan(Long id, BcmPlan req) {
        BcmPlan existing = getPlan(id);
        existing.setPlanNameEn(req.getPlanNameEn());
        existing.setPlanNameAr(req.getPlanNameAr());
        existing.setPlanType(req.getPlanType());
        existing.setVersion(req.getVersion());
        // F2-234 — this wrote the status unconditionally, so the ordinary edit route performed
        // exactly the transitions submitForReview/approve/invoke/restore guard, under the SAME
        // permission (BCM:WRITE) — so there is no escalation to find: the object-level machine
        // was the only control and the update sat beside it without one (F2-219's shape).
        // An ECHO is accepted so a form that round-trips the row still works; a CHANGE is not
        // (F2-231: the refusal cannot break a request that was not performing a transition).
        // ⚠⚠ F2-296 — this was the FOURTH hand-rolled copy of that rule, and its bare `equals` is
        // the defect DisclosureService had already reasoned out: `bcm.bcm_plans` has no CHECK on
        // status and the column collates CI, so a stored 'approved' would make the screen's own echo
        // read as a CHANGE and the update would be refused (F2-179). Latent — 0 non-canonical rows —
        // but what made it harmless was the data, not the code (F2-182).
        if (req.getStatus() != null
                && !ServerOwnedState.sameState(req.getStatus(), existing.getStatus()))
            throw new IllegalArgumentException(
                "A BCM plan's status is not editable here (" + existing.getStatus() + " → "
                + req.getStatus() + "). Use submit-for-review / approve / invoke / restore, "
                + "which enforce the ISO 22301 lifecycle order.");
        existing.setNextReviewDate(req.getNextReviewDate());
        return planRepo.save(existing);
    }

    @Transactional
    public void deletePlan(Long id) {
        BcmPlan p = getPlan(id);
        p.setIsDeleted(true);
        planRepo.save(p);
    }

    // ── Plan workflow actions (ISO 22301 lifecycle) ───────────────────────────
    //
    // Status transitions:
    //   DRAFT → UNDER_REVIEW → APPROVED → INVOKED → APPROVED (after restoration)
    // Each transition is enforced; calling out-of-order transitions throws.

    @Transactional
    public BcmPlan submitForReview(Long id) {
        BcmPlan p = getPlan(id);
        if (!"DRAFT".equals(p.getStatus())) {
            throw new IllegalArgumentException("Plan must be DRAFT to submit for review (was " + p.getStatus() + ")");
        }
        p.setStatus("UNDER_REVIEW");
        BcmPlan saved = planRepo.save(p);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "bcm_plan", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public BcmPlan approvePlan(Long id) {
        BcmPlan p = getPlan(id);
        if (!"DRAFT".equals(p.getStatus()) && !"UNDER_REVIEW".equals(p.getStatus())) {
            throw new IllegalArgumentException("Plan must be DRAFT or UNDER_REVIEW to approve (was " + p.getStatus() + ")");
        }
        p.setStatus("APPROVED");
        BcmPlan saved = planRepo.save(p);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "bcm_plan", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public BcmPlan invokePlan(Long id) {
        BcmPlan p = getPlan(id);
        if (!"APPROVED".equals(p.getStatus())) {
            throw new IllegalArgumentException("Plan must be APPROVED to invoke (was " + p.getStatus() + ")");
        }
        p.setStatus("INVOKED");
        BcmPlan saved = planRepo.save(p);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "bcm_plan", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public BcmPlan restorePlan(Long id) {
        BcmPlan p = getPlan(id);
        if (!"INVOKED".equals(p.getStatus())) {
            throw new IllegalArgumentException("Plan must be INVOKED to restore (was " + p.getStatus() + ")");
        }
        p.setStatus("APPROVED");
        BcmPlan saved = planRepo.save(p);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "bcm_plan", saved.getId(), saved);
        return saved;
    }

    // ── Critical Processes ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CriticalProcess> listProcesses(Pageable pageable) {
        return processRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public CriticalProcess getProcess(Long id) {
        return processRepo.findById(id)
            .filter(p -> p.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Critical process not found: " + id));
    }

    @Transactional
    public CriticalProcess createProcess(CriticalProcess req) {
        // Mass-assignment defense — see createPlan().
        req.setId(null);
        req.setOrganisationId(TenantContext.getCurrentTenant());
        return processRepo.save(req);
    }

    @Transactional
    public CriticalProcess updateProcess(Long id, CriticalProcess req) {
        CriticalProcess existing = getProcess(id);
        existing.setProcessNameEn(req.getProcessNameEn());
        existing.setProcessNameAr(req.getProcessNameAr());
        existing.setOwnerId(req.getOwnerId());
        existing.setCriticality(req.getCriticality());
        return processRepo.save(existing);
    }

    @Transactional
    public void deleteProcess(Long id) {
        CriticalProcess p = getProcess(id);
        p.setIsDeleted(true);
        processRepo.save(p);
    }

    // ── BIA Records ───────────────────────────────────────────────────────────
    //
    // SEC-BCM-001 (IDOR / CWE-639): BiaRecord has no organisationId column —
    // tenant ownership is inherited transitively via the parent CriticalProcess.
    // The previous implementation never resolved that parent, so every read /
    // update / delete by id (or by caller-supplied processId) was effectively
    // global, allowing cross-tenant read, overwrite, and hard-delete of BIA rows.
    // Every BIA path now resolves the parent process through the org-scoped
    // CriticalProcessRepository finder and throws 404 if it is not this tenant's.

    /**
     * Resolves the parent CriticalProcess and asserts it belongs to the current
     * tenant. Throws EntityNotFoundException (→ 404) on a miss or cross-tenant
     * access, so a guessed processId from another tenant is indistinguishable
     * from a non-existent one.
     */
    private CriticalProcess requireOwnedProcess(Long processId) {
        return processRepo.findById(processId)
            .filter(p -> p.getOrganisationId().equals(TenantContext.requireOrganisationId()))
            .orElseThrow(() -> new EntityNotFoundException("Critical process not found: " + processId));
    }

    /**
     * Loads a BIA record by id under the row's <em>own</em> tenant, and also asserts its
     * parent process belongs to that tenant. Throws EntityNotFoundException (→ 404) on a
     * miss or cross-tenant access.
     * <p>⚠ F2-279: the org filter is new. Until then this was a bare {@code findById} and
     * scoping was delegated entirely to the parent-process check, because {@link BiaRecord}
     * did not map {@code organisation_id}. Both checks are kept: the org filter answers
     * "who owns this row", the process check answers "is the reference this row holds one
     * of ours" — different questions (F2-242).
     */
    private BiaRecord requireOwnedBia(Long id) {
        BiaRecord b = biaRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
            .orElseThrow(() -> new EntityNotFoundException("BIA record not found: " + id));
        requireOwnedProcess(b.getProcessId());   // throws if parent process is another tenant's
        return b;
    }

    @Transactional(readOnly = true)
    public List<BiaRecord> listBia(Long processId) {
        Long orgId = TenantContext.requireOrganisationId();
        if (processId != null) {
            requireOwnedProcess(processId);   // reject cross-tenant processId before reading its BIAs
            return biaRepo.findByOrganisationIdAndProcessId(orgId, processId, Pageable.unpaged())
                .getContent();
        }
        // ⚠ F2-279: read the tenant's rows by the tenant column, not by enumerating its
        // process ids. The old form returned any row whose PARENT was ours, so a row whose
        // own organisation_id named another tenant was served (probed). ix_bia_records_org
        // exists for exactly this query.
        return biaRepo.findByOrganisationId(orgId);
    }

    @Transactional
    public BiaRecord createBia(BiaRecord req) {
        // Mass-assignment defense — see createPlan(). Null id forces INSERT.
        // The target process must belong to the current tenant before persisting.
        requireOwnedProcess(req.getProcessId());
        req.setId(null);
        // ⚠⚠ F2-279: stamp the tenant. bcm.bia_records.organisation_id is
        // BIGINT NOT NULL DEFAULT ((1)) and this entity did not map it, so Hibernate never
        // named the column and THE DATABASE DEFAULT decided every row's tenant. It is not
        // covered by ServerOwnedFieldsModule — that introspector's own javadoc says it
        // "does not touch organisationId" — so the stamp has to be here, and it is
        // unconditional so a body field can never survive it.
        req.setOrganisationId(TenantContext.requireOrganisationId());
        return biaRepo.save(req);
    }

    @Transactional
    public BiaRecord updateBia(Long id, BiaRecord req) {
        BiaRecord existing = requireOwnedBia(id);
        // The new parent process must also be one of this tenant's, so a BIA
        // can't be re-pointed to (or stolen by) another tenant's process.
        requireOwnedProcess(req.getProcessId());
        existing.setProcessId(req.getProcessId());
        existing.setAssessmentDate(req.getAssessmentDate());
        existing.setRtoHours(req.getRtoHours());
        existing.setRpoHours(req.getRpoHours());
        existing.setMtpdHours(req.getMtpdHours());
        existing.setOverallImpact(req.getOverallImpact());
        existing.setStatus(req.getStatus());
        // ⚠ F2-279: `BcmBiaRequest` has always declared linkedRiskId and this whitelist
        // omitted it, so a BIA's linked risk was settable once at creation (the endpoint
        // binds the entity) and frozen thereafter — an edit accepted with HTTP 200 and
        // silently discarded. Frozen and protected are indistinguishable from the caller's
        // side (F2-275), and the freeze protected nothing, since the create already
        // accepted the value unvalidated.
        // ⚠ It is still unvalidated: verifying the risk belongs to this tenant needs a
        // bcm → risk module edge, and ModuleGraphRatchetTest is decrease-only and may never
        // be raised. Nothing in the platform dereferences this column today, so no reader
        // can be misled by a bad id — a fact about today, not a property of the control
        // (F2-182). Filed with the graph blocker stated.
        existing.setLinkedRiskId(req.getLinkedRiskId());
        return biaRepo.save(existing);
    }

    @Transactional
    public void deleteBia(Long id) {
        // ⚠⚠ F2-279: set the platform's own soft-delete flag. This used to write
        // status = 'DELETED' because the entity did not map is_deleted and carried no
        // @Where, so DELETE answered 204 and the record STAYED IN THE LIST — rendered as a
        // BIA whose status is the word DELETED (probed; 0 rows carry that token in either
        // database, so nothing needs migrating). The entity's @Where(is_deleted = 0) is what
        // makes this an actual delete, for all five finders at once.
        BiaRecord b = requireOwnedBia(id);
        b.setIsDeleted(true);
        biaRepo.save(b);
    }

    // ── Exercises (ISO 22301 §8.5 — exercise programme) ───────────────────────

    @Transactional(readOnly = true)
    public Page<BcmExercise> listExercises(Pageable pageable) {
        return exerciseRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public BcmExercise getExercise(Long id) {
        return exerciseRepo.findById(id)
            .filter(e -> e.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Exercise not found: " + id));
    }

    /** The one state {@code completeExercise} owns, and the column it owns alongside it. */
    static final String EXERCISE_COMPLETED = "COMPLETED";

    /**
     * F2-300 — the state an exercise is CREATED in when the caller does not name one.
     *
     * <p>It is a DEFAULT and deliberately not {@code ServerOwnedState.requireInitial}: recording an
     * exercise that was run offline — created straight into COMPLETED with its execution date — is
     * legitimate, and the BCM page's create form offers the full status select, so refusing a
     * caller-supplied state would break working usage (F2-214).
     */
    static final String INITIAL_EXERCISE_STATUS = "SCHEDULED";

    /**
     * F2-300 — an exercise may not be COMPLETED without an execution date.
     *
     * <p>{@link #completeExercise} owns the pair: it writes {@code status = COMPLETED} <b>and</b>
     * stamps {@code executedDate} from the server's clock. Both the entity-bound create and the
     * entity-bound full-replace PUT wrote {@code status} and {@code executedDate} independently and
     * unconditionally, so either could produce a COMPLETED exercise with no execution date —
     * F2-188/F2-191's shape, where a duplicated transition agrees with the original on the row and
     * differs on the part nothing asserts. On a full replace the PUT could also NULL the date of an
     * exercise that had been completed properly, which is F2-212's shape on the same field.
     *
     * <p>⚠ Screen-reachable, not merely API-reachable: {@code BcmPage}'s exercise form offers
     * {@code COMPLETED} in its status select with {@code executedDate} as a separate, optional date
     * field, so an operator can mark an ISO 22301 / NCEMA continuity exercise complete and leave the
     * date blank — and the execution date is the evidence a reviewer reads to accept the exercise
     * took place at all.
     *
     * <p>🔑 Stated as ONE invariant rather than two guards, because "may not enter COMPLETED here"
     * and "may not clear the date of a COMPLETED exercise" are the same fact, and a single
     * predicate cannot be satisfied by half of it. The refusal names {@code /complete} because that
     * route both sets the state and stamps the date — F2-180's rule that a diagnostic must send the
     * reader somewhere.
     *
     * <p>⚠ Deliberately NOT {@code ServerOwnedState.requireUnchanged}: that refuses ANY change, and
     * this register has no state machine — only one server-owned state. SCHEDULED/PLANNED are
     * ordinary data entry, so refusing those edits would fail on correct usage (F2-214).
     */
    private static void requireExecutionDateWhenCompleted(BcmExercise e, Object id) {
        if (!EXERCISE_COMPLETED.equalsIgnoreCase(e.getStatus())) return;
        if (e.getExecutedDate() != null) return;
        throw new IllegalArgumentException(
            "executedDate: exercise " + (id == null ? "(new)" : id) + " cannot be "
            + EXERCISE_COMPLETED + " with no execution date — that is the evidence the exercise "
            + "took place. Supply executedDate, or use POST /api/v1/bcm/exercises/"
            + (id == null ? "{id}" : id) + "/complete, which stamps it from the server's clock "
            + "along with the score and findings.");
    }

    @Transactional
    public BcmExercise createExercise(BcmExercise req) {
        // Mass-assignment defense — see createPlan().
        req.setId(null);
        req.setOrganisationId(TenantContext.getCurrentTenant());
        // F2-300 — the create path owns the default now that the entity no longer initialises it.
        // The column is NOT NULL and Hibernate always names it, so this must be unconditional in
        // effect: a create that stopped setting it would turn a silent reset into a 500 that
        // nothing else in the tree can see under ddl-auto=none (F2-238's hazard).
        if (req.getStatus() == null || req.getStatus().isBlank()) {
            req.setStatus(INITIAL_EXERCISE_STATUS);
        }
        requireExecutionDateWhenCompleted(req, null);
        return exerciseRepo.save(req);
    }

    @Transactional
    public BcmExercise updateExercise(Long id, BcmExercise req) {
        BcmExercise e = getExercise(id);
        e.setPlanId(req.getPlanId());
        e.setExerciseNameEn(req.getExerciseNameEn());
        e.setExerciseNameAr(req.getExerciseNameAr());
        e.setExerciseType(req.getExerciseType());
        e.setScenario(req.getScenario());
        e.setScheduledDate(req.getScheduledDate());
        // F2-300 — status and executedDate are null-guarded, and the pair has to move together.
        // Unconditional writes made an OMITTED status arrive as the entity's "SCHEDULED"
        // initialiser (now removed) and silently reset a COMPLETED exercise, and an omitted
        // executedDate NULL the execution date of one that had been completed properly. Guarding
        // only the status would have left a partial edit of a COMPLETED exercise clearing its date
        // and then failing the invariant below — a 400 on correct usage, which is the trap F2-297
        // and F2-298 were about. Omitted means "leave alone", the semantics this codebase's PUTs
        // settled on; clearing an execution date is therefore not expressible here, which is the
        // right answer for the field that evidences the exercise happened.
        if (req.getExecutedDate() != null) e.setExecutedDate(req.getExecutedDate());
        e.setScore(req.getScore());
        if (req.getStatus() != null) e.setStatus(req.getStatus());
        e.setFindings(req.getFindings());
        e.setCapaCount(req.getCapaCount() != null ? req.getCapaCount() : 0);
        e.setFacilitator(req.getFacilitator());
        e.setUpdatedAt(java.time.LocalDateTime.now());
        // F2-300 — checked on the LOADED row after the copies, not on the request, because this is
        // a full replace: the offending state can be reached either by supplying COMPLETED with no
        // date OR by omitting the date of an exercise that was already COMPLETED, and only the
        // merged row shows both. Checking `req` would miss the second.
        requireExecutionDateWhenCompleted(e, id);
        return exerciseRepo.save(e);
    }

    @Transactional
    public void deleteExercise(Long id) {
        BcmExercise e = getExercise(id);
        e.setIsDeleted(true);
        e.setUpdatedAt(java.time.LocalDateTime.now());
        exerciseRepo.save(e);
    }

    /** Lifecycle helper: mark an exercise complete and persist its score & findings. */
    @Transactional
    public BcmExercise completeExercise(Long id, Integer score, String findings) {
        BcmExercise e = getExercise(id);
        e.setStatus("COMPLETED");
        e.setExecutedDate(java.time.LocalDate.now());
        if (score != null) e.setScore(score);
        if (findings != null) e.setFindings(findings);
        e.setUpdatedAt(java.time.LocalDateTime.now());
        BcmExercise saved = exerciseRepo.save(e);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "bcm_exercise", saved.getId(), saved);
        return saved;
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    /**
     * BCM dashboard stats. Returns the full shape the dashboard reads
     * (criticalServiceCount, *CompletionPercent, *CompliancePercent, openGaps,
     * overdueCapas, *Exercises, exerciseSuccessRate) — previously this returned
     * only four total-* keys with names the dashboard doesn't read, so every
     * tile rendered 0. Legacy total-* keys kept for back-compat.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Long orgId = TenantContext.getCurrentTenant();
        var plans = planRepo.findByOrganisationId(orgId, Pageable.unpaged()).getContent();
        var processesPage = processRepo.findByOrganisationId(orgId, Pageable.unpaged());
        long totalProcesses = processesPage.getTotalElements();
        // ⚠ F2-279: count by the tenant column, matching what listBia() returns. Counting
        // by the parent-process link instead let this KPI disagree with the register it
        // summarises (F2-197 — a figure that disagrees with the list it opens is worse than
        // no figure), and it counted soft-deleted rows, which @Where now excludes.
        long totalBia = biaRepo.findByOrganisationId(orgId).size();
        var exercises = exerciseRepo.findByOrganisationId(orgId, Pageable.unpaged()).getContent();

        long totalPlans = plans.size();
        long totalExercises = exercises.size();
        long bcpPlans = plans.stream().filter(p -> typeHas(p.getPlanType(), "BCP", "BUSINESS")).count();
        long drpPlans = plans.stream().filter(p -> typeHas(p.getPlanType(), "DR", "DISASTER", "IT_DR")).count();
        long completed = exercises.stream().filter(e -> "COMPLETED".equalsIgnoreCase(e.getStatus())).count();
        long failed    = exercises.stream().filter(e -> "FAILED".equalsIgnoreCase(e.getStatus())).count();
        long upcoming  = exercises.stream().filter(e -> {
            String s = e.getStatus();
            return "SCHEDULED".equalsIgnoreCase(s) || "PLANNED".equalsIgnoreCase(s);
        }).count();

        long denom = Math.max(1, totalProcesses);
        long biaPct = Math.min(100, Math.round(totalBia * 100.0 / denom));
        long bcpPct = Math.min(100, Math.round(bcpPlans * 100.0 / denom));
        long drpPct = Math.min(100, Math.round(drpPlans * 100.0 / denom));
        long successRate = totalExercises > 0 ? Math.round(completed * 100.0 / totalExercises) : 0;

        long openGaps = gapRepo.findByOrganisationIdOrderByIdDesc(orgId).stream()
                .filter(g -> !"CLOSED".equalsIgnoreCase(g.getStatus())
                          && !"RESOLVED".equalsIgnoreCase(g.getStatus())).count();
        long overdueCapas = capaRepo.findByOrganisationIdAndDueDateBeforeAndStatusNotIn(
                orgId, java.time.LocalDate.now(), List.of("CLOSED", "COMPLETED", "DONE", "VERIFIED")).size();

        Map<String, Long> m = new java.util.LinkedHashMap<>();
        // Legacy keys (back-compat).
        m.put("totalPlans", totalPlans);
        m.put("totalProcesses", totalProcesses);
        m.put("totalBia", totalBia);
        m.put("totalExercises", totalExercises);
        // Dashboard shape.
        m.put("criticalServiceCount", totalProcesses);
        m.put("biaCompletionPercent", biaPct);
        m.put("bcpCompletionPercent", bcpPct);
        m.put("drpCompletionPercent", drpPct);
        m.put("rtoCompliancePercent", successRate);
        m.put("rpoCompliancePercent", successRate);
        m.put("openGaps", openGaps);
        m.put("overdueCapas", overdueCapas);
        m.put("failedExercises", failed);
        m.put("exerciseSuccessRate", successRate);
        m.put("upcomingExercises", upcoming);
        return m;
    }

    private static boolean typeHas(String planType, String... needles) {
        if (planType == null) return false;
        String t = planType.toUpperCase(Locale.ROOT);
        for (String n : needles) if (t.contains(n)) return true;
        return false;
    }
}
