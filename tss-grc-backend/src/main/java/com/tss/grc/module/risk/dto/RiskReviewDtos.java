package com.tss.grc.module.risk.dto;

import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskReview;

import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * ERM epic E-J — risk review / reassessment DTOs.
 */
public final class RiskReviewDtos {

    private RiskReviewDtos() {}

    /**
     * Request to reassess a risk. All fields optional:
     * <ul>
     *   <li>{@code residualLikelihood}/{@code residualImpact} — when both present,
     *       the risk is re-scored (decision defaults to RESCORED); otherwise the
     *       posture is simply re-confirmed (CONFIRMED).</li>
     *   <li>{@code cadenceDays} — overrides the risk's review cadence going forward.</li>
     *   <li>{@code decision} — CONFIRMED | RESCORED | ESCALATE | RETIRE_SUGGESTED.</li>
     * </ul>
     */
    public record ReassessDto(
            Integer residualLikelihood,
            Integer residualImpact,
            Integer cadenceDays,
            String decision,
            String noteEn,
            String noteAr) {}

    /** One review-log row for the per-risk history panel. */
    public record ReviewResponse(
            Long id,
            Long riskId,
            LocalDateTime reviewedAt,
            Long reviewedBy,
            String reviewedByName,
            String decision,
            Integer residualLikelihoodBefore,
            Integer residualImpactBefore,
            Integer residualLikelihoodAfter,
            Integer residualImpactAfter,
            Integer residualScoreSnapshot,
            String riskLevelSnapshot,
            LocalDate nextReviewDate,
            String noteEn,
            String noteAr) {

        public static ReviewResponse from(RiskReview r) {
            return new ReviewResponse(
                    r.getId(), r.getRiskId(), r.getReviewedAt(), r.getReviewedBy(), r.getReviewedByName(),
                    r.getDecision(),
                    r.getResidualLikelihoodBefore(), r.getResidualImpactBefore(),
                    r.getResidualLikelihoodAfter(), r.getResidualImpactAfter(),
                    r.getResidualScoreSnapshot(), r.getRiskLevelSnapshot(),
                    r.getNextReviewDate(), r.getNoteEn(), r.getNoteAr());
        }
    }

    /** A lightweight row for the "reviews overdue" register surface. */
    public record OverdueRow(
            Long riskId,
            String riskCode,
            String titleEn,
            String titleAr,
            String status,
            String riskLevel,
            Integer residualScore,
            LocalDate reviewDate,
            String reviewStatus,
            long daysOverdue) {

        public static OverdueRow from(Risk r, LocalDate today) {
            long days = r.getReviewDate() == null ? 0
                    : Math.max(0, ChronoUnit.DAYS.between(r.getReviewDate(), today));
            return new OverdueRow(
                    r.getId(), "RISK-" + String.format(Locale.ROOT, "%04d", r.getId()),
                    r.getTitleEn(), r.getTitleAr(), r.getStatus(), r.getRiskLevel(),
                    r.getResidualScore(), r.getReviewDate(), r.getReviewStatus(), days);
        }
    }

    /** Result of a stale-review sweep. */
    public record ReviewSweep(int flagged) {}
}
