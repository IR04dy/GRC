package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmBiaAssessment;
import com.tss.grc.module.bcm.cycle.repository.BcmBiaAssessmentRepository;
import com.tss.grc.module.bcm.drp.entity.DisasterRecoveryPlan;
import com.tss.grc.module.bcm.drp.repository.DisasterRecoveryPlanRepository;
import com.tss.grc.module.bcm.entity.BcmPlan;
import com.tss.grc.module.bcm.repository.BcmPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Continuity Readiness Score — audit Tier-2.
 *
 * <p>The headline composite metric the audit identified as "every CXO asks for
 * but the platform doesn't compute" (audit §4 row 10). One score per
 * {@link BcmBiaAssessment} (which represents the assessed scope: a critical
 * service or process) on a 0..100 scale, broken down across five binary
 * dimensions:
 *
 * <pre>
 *   BIA approved             25 pts   approvalStatus IN (APPROVED, ACTIVE)
 *   BCP generated + approved 25 pts   generatedBcpId set AND that plan APPROVED
 *   DRP generated + approved 20 pts   generatedDrpId set AND that plan APPROVED
 *   DR last test PASSED      20 pts   linked DRP's lastTestedOutcome == PASSED
 *   Manual workaround docs   10 pts   manualWorkaroundAvailable AND description ≥ 20 chars
 * </pre>
 *
 * <p>Aggregations:
 * <ul>
 *   <li>{@link #scoreForBia} — single BIA breakdown.</li>
 *   <li>{@link #scoreForCycle} — average + per-BIA list for one cycle.</li>
 *   <li>{@link #leaderboard} — every approved/active BIA in the tenant
 *       ranked by composite score, descending.</li>
 *   <li>{@link #tenantOverall} — single 0..100 across the whole tenant.</li>
 * </ul>
 *
 * <p>Weights are hard-coded here because the audit calls this out as the
 * "headline metric every CXO asks for" — having configurable weights would
 * undermine benchmark-ability across tenants. A future sprint can move the
 * weights into {@code BcmSettings} once the metric is established.
 */
@Service
@RequiredArgsConstructor
public class BcmReadinessScoreService {

    public static final int WEIGHT_BIA_APPROVED        = 25;
    public static final int WEIGHT_BCP_APPROVED        = 25;
    public static final int WEIGHT_DRP_APPROVED        = 20;
    public static final int WEIGHT_DR_TEST_PASSED      = 20;
    public static final int WEIGHT_MANUAL_WORKAROUND   = 10;
    public static final int MAX_SCORE                  =
        WEIGHT_BIA_APPROVED + WEIGHT_BCP_APPROVED + WEIGHT_DRP_APPROVED
        + WEIGHT_DR_TEST_PASSED + WEIGHT_MANUAL_WORKAROUND;       // = 100

    private final BcmBiaAssessmentRepository bias;
    private final BcmPlanRepository plans;
    private final DisasterRecoveryPlanRepository drps;

    /** Breakdown returned by {@link #scoreForBia}. Suitable for direct JSON serialisation. */
    public record ScoreBreakdown(
        Long biaId,
        String biaCode,
        Long serviceId,
        Long processId,
        String criticality,
        int totalScore,                            // 0..100
        boolean biaApproved,
        boolean bcpApproved,
        boolean drpApproved,
        boolean drTestPassed,
        boolean manualWorkaroundDocumented,
        String lowestDimension                     // human-readable hint
    ) {}

    public record CycleScore(
        Long cycleId,
        double averageScore,
        int biaCount,
        List<ScoreBreakdown> rows
    ) {}

    public record TenantScore(
        double averageScore,
        int biaCount,
        Map<String, Long> biasByCriticality        // distribution snapshot
    ) {}

    // ─── Single-BIA scoring ──────────────────────────────────────

    @Transactional(readOnly = true)
    public ScoreBreakdown scoreForBia(Long biaId) {
        BcmBiaAssessment b = bias.findByIdAndOrganisationId(biaId, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("BIA not found: " + biaId));
        return compute(b);
    }

    private ScoreBreakdown compute(BcmBiaAssessment b) {
        boolean biaApproved        = "APPROVED".equals(b.getApprovalStatus()) || "ACTIVE".equals(b.getApprovalStatus());
        boolean bcpApproved        = b.getGeneratedBcpId() != null
                                  && bcpIsApproved(b.getGeneratedBcpId());
        boolean drpApproved        = b.getGeneratedDrpId() != null
                                  && drpIsApproved(b.getGeneratedDrpId());
        boolean drTestPassed       = b.getGeneratedDrpId() != null
                                  && drpTestPassed(b.getGeneratedDrpId());
        boolean workaroundDocs     = Boolean.TRUE.equals(b.getManualWorkaroundAvailable())
                                  && b.getManualWorkaroundDescription() != null
                                  && b.getManualWorkaroundDescription().trim().length() >= 20;

        int score = 0;
        if (biaApproved)     score += WEIGHT_BIA_APPROVED;
        if (bcpApproved)     score += WEIGHT_BCP_APPROVED;
        if (drpApproved)     score += WEIGHT_DRP_APPROVED;
        if (drTestPassed)    score += WEIGHT_DR_TEST_PASSED;
        if (workaroundDocs)  score += WEIGHT_MANUAL_WORKAROUND;

        return new ScoreBreakdown(
            b.getId(), b.getBiaCode(), b.getServiceId(), b.getProcessId(),
            b.getOverallCriticality() == null ? "UNKNOWN" : b.getOverallCriticality(),
            score,
            biaApproved, bcpApproved, drpApproved, drTestPassed, workaroundDocs,
            lowestDimension(biaApproved, bcpApproved, drpApproved, drTestPassed, workaroundDocs)
        );
    }

    /**
     * Surface which dimension is dragging the score the most. Sorted by
     * weight × failing — biggest-impact failure first. Returns "complete"
     * if all five dimensions pass.
     */
    private static String lowestDimension(boolean bia, boolean bcp, boolean drp, boolean drt, boolean wa) {
        if (!bia)  return "BIA not approved";
        if (!bcp)  return "BCP not approved";
        if (!drp)  return "DRP not approved";
        if (!drt)  return "DR test not passing";
        if (!wa)   return "Manual workaround undocumented";
        return "complete";
    }

    // ─── Cycle aggregation ───────────────────────────────────────

    @Transactional(readOnly = true)
    public CycleScore scoreForCycle(Long cycleId) {
        Long org = TenantContext.getCurrentTenant();
        List<BcmBiaAssessment> rows = bias.findByOrganisationIdAndCycleIdOrderByIdDesc(org, cycleId);
        if (rows.isEmpty()) {
            return new CycleScore(cycleId, 0.0, 0, List.of());
        }
        List<ScoreBreakdown> scores = new ArrayList<>(rows.size());
        double sum = 0;
        for (BcmBiaAssessment b : rows) {
            ScoreBreakdown sb = compute(b);
            scores.add(sb);
            sum += sb.totalScore();
        }
        double avg = round1(sum / rows.size());
        return new CycleScore(cycleId, avg, rows.size(), scores);
    }

    // ─── Tenant leaderboard ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScoreBreakdown> leaderboard() {
        Long org = TenantContext.getCurrentTenant();
        // Only include approved or active BIAs — DRAFT scores would be
        // misleading because the dimensions aren't yet meaningful.
        List<BcmBiaAssessment> rows = new ArrayList<>();
        rows.addAll(bias.findByOrganisationIdAndApprovalStatusOrderByIdDesc(org, "APPROVED"));
        rows.addAll(bias.findByOrganisationIdAndApprovalStatusOrderByIdDesc(org, "ACTIVE"));

        List<ScoreBreakdown> out = new ArrayList<>(rows.size());
        for (BcmBiaAssessment b : rows) out.add(compute(b));
        out.sort(Comparator.comparingInt(ScoreBreakdown::totalScore).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public TenantScore tenantOverall() {
        List<ScoreBreakdown> board = leaderboard();
        if (board.isEmpty()) {
            return new TenantScore(0.0, 0, Map.of());
        }
        double sum = 0;
        Map<String, Long> byCrit = new LinkedHashMap<>();
        for (ScoreBreakdown sb : board) {
            sum += sb.totalScore();
            byCrit.merge(sb.criticality(), 1L, Long::sum);
        }
        return new TenantScore(round1(sum / board.size()), board.size(), byCrit);
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private boolean bcpIsApproved(Long bcpId) {
        // Legacy BcmPlan status enum: DRAFT / UNDER_REVIEW / APPROVED / INVOKED.
        // Treat both APPROVED and INVOKED as "approved enough" — an invoked plan
        // is by definition activated and so passed approval.
        return plans.findById(bcpId)
            .filter(p -> "APPROVED".equals(p.getStatus()) || "INVOKED".equals(p.getStatus()))
            .isPresent();
    }

    private boolean drpIsApproved(Long drpId) {
        // DisasterRecoveryPlan status enum: DRAFT / APPROVED / ACTIVATED / ARCHIVED / REJECTED.
        return drps.findById(drpId)
            .filter(d -> "APPROVED".equals(d.getStatus()) || "ACTIVATED".equals(d.getStatus()))
            .isPresent();
    }

    private boolean drpTestPassed(Long drpId) {
        Optional<DisasterRecoveryPlan> d = drps.findById(drpId);
        return d.isPresent() && "PASSED".equals(d.get().getLastTestedOutcome());
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
