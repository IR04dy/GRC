package com.tss.grc.module.risk.dto;

import com.tss.grc.module.risk.entity.Risk;

import java.util.Locale;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ERM epic E-K — reporting + audit evidence package DTOs.
 */
public final class RiskReportDtos {

    private RiskReportDtos() {}

    /** One row of the top-risk / board / committee report. */
    public record TopRiskRow(
            Long riskId, String riskCode, String titleEn, String titleAr,
            String status, String riskLevel, Integer residualScore, Integer inherentScore,
            Long ownerId, Long categoryId) {

        public static TopRiskRow from(Risk r) {
            return new TopRiskRow(
                    r.getId(), "RISK-" + String.format(Locale.ROOT, "%04d", r.getId()),
                    r.getTitleEn(), r.getTitleAr(), r.getStatus(), r.getRiskLevel(),
                    r.getResidualScore(), r.getInherentScore(), r.getOwnerId(), r.getCategoryId());
        }
    }

    /** One appetite-breach row with its statement context. */
    public record AppetiteBreachRow(
            Long breachId, Long appetiteId, String appetiteTitleEn, Long categoryId,
            String severity, BigDecimal observedValue, BigDecimal toleranceUpper,
            BigDecimal currentExposure, String status,
            LocalDateTime breachedAt, LocalDateTime acknowledgedAt) {}

    /** Live register posture stats. */
    public record RegisterStats(
            long totalRisks,
            Map<String, Long> byLevel,
            Map<String, Long> byStatus,
            long reviewOverdue,
            long activeAcceptances,
            long openAppetiteBreaches) {}

    /**
     * A single, auditor-pullable risk evidence package: the register posture,
     * the top residual risks, the open appetite breaches, and the governance
     * counts (acceptances, overdue reviews) — one JSON manifest for an audit.
     */
    public record AuditPackage(
            Long organisationId,
            LocalDateTime generatedAt,
            RegisterStats stats,
            List<TopRiskRow> topRisks,
            List<AppetiteBreachRow> openAppetiteBreaches) {}
}
