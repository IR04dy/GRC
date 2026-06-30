package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskControl;
import com.tss.grc.module.risk.repository.RiskControlRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Control-effectiveness → residual derivation — ERM remediation epic E-B.
 *
 * <p>The {@link RiskControl} bridge entity has always documented that an
 * exposure engine would "aggregate mitigation effectiveness per risk (weighted
 * by {@code contributionPct}) and recompute residual scores automatically" — but
 * that engine was never built, so linking a control or changing its
 * effectiveness had <em>zero</em> effect on a risk's residual. This service
 * closes that gap.
 *
 * <h3>Model (transparent + audit-defensible)</h3>
 * <pre>
 *   factor(effectiveness):  EFFECTIVE=1.0  NEEDS_IMPROVEMENT=0.5
 *                           INEFFECTIVE=0.1  NOT_TESTED/unknown=0.0
 *   weight(control)      :  contributionPct/100  if provided, else 1/N (equal share)
 *   aggregateMitigation  :  min(0.75, Σ factor·weight)         // controls reduce, never eliminate
 *   retained             :  1 − aggregateMitigation
 *   residualL            :  clamp(round(inherentL · √retained), 1, inherentL)
 *   residualI            :  clamp(round(inherentI · √retained), 1, inherentI)
 * </pre>
 * The √ split means {@code residualL · residualI ≈ inherentScore · retained} — the
 * mitigation scales the <em>score</em> by exactly the retained fraction, shared
 * evenly across likelihood and impact. {@code residual_score} itself is a
 * DB-computed column, so we only ever write {@code residual_likelihood/impact}.
 *
 * <h3>Manual override (explicit decision, plan §E-B)</h3>
 * Residual <strong>stays manually overridable</strong>. A risk manager's explicit
 * re-assessment ({@link RiskAssessmentService#record}) writes the residual
 * directly and is <em>not</em> auto-clobbered. {@code recomputeResidual} runs only
 * on a control link / effectiveness change (and on explicit demand via the
 * endpoint) — last writer wins, both are deliberate actions. This mirrors the
 * "derived, with override" pattern in ServiceNow IRM / RSA Archer.
 */
@Service
@RequiredArgsConstructor
public class RiskExposureService {

    private static final Logger log = LoggerFactory.getLogger(RiskExposureService.class);

    /** Controls reduce, never fully eliminate, residual exposure. */
    private static final double MAX_MITIGATION = 0.75;

    private final RiskRepository risks;
    private final RiskControlRepository riskControls;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    /** Outcome of a residual re-derivation — surfaced to the caller and the UI. */
    public record ExposureResult(
        Long    riskId,
        int     linkedControls,
        boolean uncontrolled,
        double  aggregateMitigation,   // 0.00 .. 0.75
        Integer inherentScore,
        Integer previousResidualScore,
        Integer newResidualScore,
        Integer residualLikelihood,
        Integer residualImpact,
        boolean applied
    ) {}

    /**
     * Re-derive a risk's residual likelihood/impact from its linked controls'
     * effectiveness. Tenant-scoped. No-ops (returns {@code applied=false}) when
     * the risk has no inherent baseline to mitigate, or is in a terminal state
     * (CLOSED/CANCELLED — frozen for the audit trail).
     */
    @Transactional
    public ExposureResult recomputeResidual(Long riskId) {
        Long o = org();
        Risk risk = risks.findById(riskId)
            .filter(r -> Objects.equals(r.getOrganisationId(), o))
            .orElseThrow(() -> new EntityNotFoundException("Risk " + riskId + " not found for tenant " + o));

        List<RiskControl> controls = riskControls.findByOrganisationIdAndRiskIdOrderByIdAsc(o, riskId);
        boolean uncontrolled = controls.isEmpty();
        Integer inhL = risk.getInherentLikelihood();
        Integer inhI = risk.getInherentImpact();
        Integer prev = risk.getResidualScore();
        // NB: keep both branches Integer — a mixed int/Integer ternary unboxes
        // the Integer branch and NPEs when inherent_score is null.
        Integer inherentScore = (inhL != null && inhI != null)
            ? Integer.valueOf(inhL * inhI)
            : risk.getInherentScore();

        String st = risk.getStatus();
        boolean terminal = "CLOSED".equalsIgnoreCase(st) || "CANCELLED".equalsIgnoreCase(st);
        if (inhL == null || inhI == null || terminal) {
            // Nothing to derive from / frozen — report posture without mutating.
            return new ExposureResult(riskId, controls.size(), uncontrolled, 0.0,
                inherentScore, prev, prev, risk.getResidualLikelihood(), risk.getResidualImpact(), false);
        }

        double mitigation = aggregateMitigation(controls);
        double retained   = 1.0 - mitigation;
        double dim        = Math.sqrt(retained);
        int newL = clamp((int) Math.round(inhL * dim), 1, inhL);
        int newI = clamp((int) Math.round(inhI * dim), 1, inhI);

        risk.setResidualLikelihood(newL);
        risk.setResidualImpact(newI);
        risks.save(risk);
        int newScore = newL * newI;

        audit.log("RECOMPUTE", "Risk", riskId,
            "Residual re-derived from " + controls.size() + " control(s): mitigation="
            + Math.round(mitigation * 100) + "% · residual " + prev + "→" + newScore
            + (uncontrolled ? " · UNCONTROLLED" : ""));
        log.info("[risk-exposure] risk={} controls={} mitigation={} residual {}→{}",
            riskId, controls.size(), round2(mitigation), prev, newScore);

        return new ExposureResult(riskId, controls.size(), uncontrolled, round2(mitigation),
            inherentScore, prev, newScore, newL, newI, true);
    }

    /**
     * Risks with zero linked controls (open, non-terminal) — "uncontrolled
     * exposure," ordered by residual score so the worst surface first. A
     * first-class register view that tier-1 platforms all expose.
     */
    @Transactional(readOnly = true)
    public List<Risk> listUncontrolledRisks() {
        return risks.findUncontrolled(org());
    }

    // ── internals ───────────────────────────────────────────────────

    private double aggregateMitigation(List<RiskControl> controls) {
        if (controls.isEmpty()) return 0.0;
        int n = controls.size();
        double sum = 0.0;
        for (RiskControl c : controls) {
            double factor = effectivenessFactor(c.getEffectiveness());
            double weight = (c.getContributionPct() != null)
                ? c.getContributionPct().doubleValue() / 100.0
                : 1.0 / n;
            sum += factor * weight;
        }
        return Math.min(MAX_MITIGATION, Math.max(0.0, sum));
    }

    private static double effectivenessFactor(String eff) {
        if (eff == null) return 0.0;
        return switch (eff.trim().toUpperCase()) {
            case "EFFECTIVE"         -> 1.0;
            case "NEEDS_IMPROVEMENT" -> 0.5;
            case "INEFFECTIVE"       -> 0.1;
            default                  -> 0.0;   // NOT_TESTED / unknown → no credit until proven
        };
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
