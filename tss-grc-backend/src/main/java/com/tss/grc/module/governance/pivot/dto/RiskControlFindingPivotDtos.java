package com.tss.grc.module.governance.pivot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Request / response DTOs for the Risk-Control-Finding Pivot view.
 *
 * <p>The pivot has three lenses — Risk, Control, Finding — all of
 * which return the same shape so the frontend can reuse a single
 * layout. The {@link FocalEntity} discriminator on
 * {@link PivotResponse} tells the caller which entity sits at the
 * centre.
 *
 * <p>Records are deliberately shallow — each "Pivot" record is the
 * minimum the auditor needs to recognise the row + click through to
 * the source module's detail page.
 */
public final class RiskControlFindingPivotDtos {

    private RiskControlFindingPivotDtos() {}

    /** Discriminator on {@link PivotResponse#focalEntity()}. */
    public enum FocalEntity { RISK, CONTROL, FINDING }

    // ─── Focal-entity summaries ────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RiskSummary(
            Long id,
            String externalId,
            String code,
            String titleEn,
            String titleAr,
            String riskLevel,
            Integer residualScore,
            Long ownerId,
            String status
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ControlSummary(
            Long id,
            String externalId,
            String code,
            String titleEn,
            String titleAr,
            String controlType,
            String automationLevel,
            String status,
            String domain,
            Long ownerUserId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FindingSummary(
            Long id,
            String externalId,
            String refNumber,
            String titleEn,
            String titleAr,
            String severity,
            String status,
            String engagementCode,
            LocalDateTime raisedAt
    ) {}

    // ─── Related-entity rows (used in side cards) ──────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ControlPivot(
            Long id,
            String code,
            String titleEn,
            String titleAr,
            /** EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED */
            String effectiveness,
            /** Most recent CCM run status — PASS | FAIL | ERROR | SKIPPED | TIMEOUT | null */
            String lastTestStatus,
            LocalDateTime lastTestAt,
            String domain,
            Long ownerUserId
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RiskPivot(
            Long id,
            String externalId,
            String titleEn,
            String titleAr,
            String riskLevel,
            Integer residualScore,
            String status
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FindingPivot(
            Long id,
            String externalId,
            String refNumber,
            String titleEn,
            String titleAr,
            String severity,
            String status,
            LocalDateTime raisedAt,
            String engagementCode
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CapaPivot(
            Long id,
            String externalId,
            String titleEn,
            String titleAr,
            LocalDate targetDate,
            String status,
            String assignee,
            /** Negative when not yet due; positive when overdue; null when no targetDate. */
            Long daysOverdue,
            /** Source module the CAPA belongs to: AUDIT_LC | CAPA */
            String sourceModule
    ) {}

    // ─── Rollups ───────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CcmCoverageStats(
            long totalTests,
            long passingLast24h,
            long failingLast24h,
            long errorLast24h,
            LocalDateTime lastRunAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PivotSummary(
            int controlsCount,
            int effectiveControlsCount,
            double effectiveControlsPct,
            int openFindingsCount,
            int overdueCapaCount,
            int linkedRisksCount,
            /** 0..100 — proportion of "useful" fields populated across all rows. */
            double dataCompletenessPct
    ) {}

    // ─── Top-level response ────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PivotResponse(
            FocalEntity focalEntity,
            LocalDateTime computedAt,
            RiskSummary risk,
            ControlSummary control,
            FindingSummary finding,
            List<RiskPivot> linkedRisks,
            List<ControlPivot> linkedControls,
            List<FindingPivot> linkedFindings,
            List<CapaPivot> linkedCapa,
            CcmCoverageStats ccmCoverage,
            PivotSummary summary
    ) {}
}
