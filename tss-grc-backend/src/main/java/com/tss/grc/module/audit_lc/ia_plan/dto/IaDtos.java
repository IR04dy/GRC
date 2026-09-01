package com.tss.grc.module.audit_lc.ia_plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 13 — Internal Audit DTOs (plan + universe + engagement + milestone).
 *
 * <p>Grouped in a single file to keep the controller compact.
 */
public final class IaDtos {
    private IaDtos() {}

    public static final String PLAN_STATUS_REGEX =
        "DRAFT|BOARD_REVIEW|APPROVED|EXECUTING|COMPLETED|ARCHIVED";
    public static final String ENG_PHASE_REGEX =
        "PLANNED|PLANNING|FIELDWORK|REPORTING|REPORT_ISSUED|FOLLOW_UP|CLOSED|CANCELLED";
    public static final String ENG_TYPE_REGEX =
        "OPERATIONAL|FINANCIAL|COMPLIANCE|IT_GENERAL|IT_APPLICATION|FRAUD|SPECIAL_REQUEST|FOLLOW_UP|MANAGEMENT_REVIEW";
    public static final String MILESTONE_TYPE_REGEX =
        "KICKOFF|FIELDWORK_START|DRAFT_REPORT|MANAGEMENT_RESPONSE|FINAL_REPORT|FOLLOW_UP_DUE";
    public static final String OPINION_REGEX =
        "SATISFACTORY|PARTIAL|UNSATISFACTORY|NA";
    public static final String QUARTER_REGEX =
        "Q1|Q2|Q3|Q4";

    // ── Annual plan ──────────────────────────────────────────────────

    public record PlanCreateRequest(
        @Size(max = 64) String planCode,
        @NotNull Integer cycleYear,
        @NotBlank @Size(max = 500) String titleEn,
        @NotBlank @Size(max = 500) String titleAr,
        Long preparedByUserId,
        @Size(max = 8000) String methodologySummaryEn,
        @Size(max = 8000) String methodologySummaryAr
    ) {}

    public record PlanUpdateRequest(
        @Size(max = 500) String titleEn,
        @Size(max = 500) String titleAr,
        Long preparedByUserId,
        @Size(max = 8000) String methodologySummaryEn,
        @Size(max = 8000) String methodologySummaryAr
    ) {}

    public record PlanTransitionRequest(
        @NotBlank @Pattern(regexp = PLAN_STATUS_REGEX) String newStatus,
        @Size(max = 1000) String noteEn,
        @Size(max = 1000) String noteAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String planCode,
        Integer cycleYear,
        String titleEn,
        String titleAr,
        Long preparedByUserId,
        String status,
        String methodologySummaryEn,
        String methodologySummaryAr,
        Integer auditUniverseCount,
        Integer totalEngagementsPlanned,
        Long approvedByUserId,
        LocalDateTime approvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanDashboardResponse(
        Long planId,
        String planCode,
        Integer cycleYear,
        String status,
        Integer auditUniverseCount,
        Integer totalEngagementsPlanned,
        long engagementsPlanned,
        long engagementsInPlanning,
        long engagementsInFieldwork,
        long engagementsInReporting,
        long engagementsReportIssued,
        long engagementsInFollowUp,
        long engagementsClosed,
        long engagementsCancelled,
        Double onTimePercentage
    ) {}

    // ── Audit universe ───────────────────────────────────────────────

    public record UniverseCreateRequest(
        Long planId,
        @NotBlank @Size(max = 64) String entryCode,
        @NotBlank @Size(max = 255) String titleEn,
        @NotBlank @Size(max = 255) String titleAr,
        @Size(max = 255) String processNameEn,
        @Size(max = 255) String processNameAr,
        @Size(max = 255) String businessUnitEn,
        @Size(max = 255) String businessUnitAr,
        BigDecimal inherentRiskScore,
        BigDecimal controlEnvironmentScore,
        // F2-392 measured that create() ALWAYS derives the residual from
        // inherent × (1 - controlEnvironmentScore) and ignores whatever the caller sends, the same
        // as update() and recomputeResidual(). F2-413 removes the component rather than leaving it
        // declared: a request field the server discards tells the caller their value matters
        // (F2-184), and a field the bound type does not declare can never arrive (F2-233), which is
        // the stronger closure. UniverseResponse still carries the DERIVED value.
        LocalDate lastAuditedDate,
        @Size(max = 20) String lastAuditRating,
        Integer plannedAuditYear,
        @Pattern(regexp = QUARTER_REGEX) String plannedQuarter,
        Long linkedRiskId,
        @Size(max = 500) String linkedControlIds
    ) {}

    public record UniverseUpdateRequest(
        @Size(max = 255) String titleEn,
        @Size(max = 255) String titleAr,
        @Size(max = 255) String processNameEn,
        @Size(max = 255) String processNameAr,
        @Size(max = 255) String businessUnitEn,
        @Size(max = 255) String businessUnitAr,
        BigDecimal inherentRiskScore,
        BigDecimal controlEnvironmentScore,
        LocalDate lastAuditedDate,
        @Size(max = 20) String lastAuditRating,
        Integer plannedAuditYear,
        @Pattern(regexp = QUARTER_REGEX) String plannedQuarter,
        Long linkedRiskId,
        @Size(max = 500) String linkedControlIds
    ) {}

    public record UniverseBulkImportRequest(
        Long planId,
        // F2-413: @Valid is load-bearing. @NotNull alone stops the cascade at this record, so on
        // POST /universe/bulk-import NONE of UniverseCreateRequest's constraints ran — @NotBlank
        // entryCode/titleEn/titleAr (house rule 7), six @Size caps and the @Pattern on
        // plannedQuarter — although POST /universe binds the very same type with @Valid. Every cap
        // mirrors its @Column(length), so enabling the cascade converts a truncation 8152 into a
        // 400 naming the field and cannot break a request that works today (F2-184).
        @NotNull @Valid List<UniverseCreateRequest> entries
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UniverseResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long planId,
        String entryCode,
        String titleEn,
        String titleAr,
        String processNameEn,
        String processNameAr,
        String businessUnitEn,
        String businessUnitAr,
        BigDecimal inherentRiskScore,
        BigDecimal controlEnvironmentScore,
        BigDecimal residualRiskScore,
        LocalDate lastAuditedDate,
        String lastAuditRating,
        Integer plannedAuditYear,
        String plannedQuarter,
        Long linkedRiskId,
        String linkedControlIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UniverseBulkImportResponse(
        int totalSubmitted,
        int created,
        int skippedExisting
    ) {}

    // ── Engagement ───────────────────────────────────────────────────

    public record EngagementCreateRequest(
        @NotNull @Positive Long planId,
        Long universeEntryId,
        @Size(max = 64) String engagementCode,
        @NotBlank @Size(max = 500) String titleEn,
        @NotBlank @Size(max = 500) String titleAr,
        @NotBlank @Pattern(regexp = ENG_TYPE_REGEX) String auditType,
        @Size(max = 8000) String scopeSummaryEn,
        @Size(max = 8000) String scopeSummaryAr,
        Long leadAuditorUserId,
        @Size(max = 500) String teamMemberIds,
        Long sponsorUserId,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate
    ) {}

    public record EngagementCreateFromUniverseRequest(
        @NotNull @Positive Long planId,
        @NotNull @Positive Long universeEntryId,
        @Size(max = 64) String engagementCode,
        @Pattern(regexp = ENG_TYPE_REGEX) String auditType,
        Long leadAuditorUserId,
        Long sponsorUserId,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate
    ) {}

    public record EngagementUpdateRequest(
        @Size(max = 500) String titleEn,
        @Size(max = 500) String titleAr,
        @Pattern(regexp = ENG_TYPE_REGEX) String auditType,
        @Size(max = 8000) String scopeSummaryEn,
        @Size(max = 8000) String scopeSummaryAr,
        Long leadAuditorUserId,
        @Size(max = 500) String teamMemberIds,
        Long sponsorUserId,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        @Size(max = 500) String planningMemoUrl,
        @Pattern(regexp = OPINION_REGEX) String opinion,
        @PositiveOrZero Integer findingCount,
        @PositiveOrZero Integer criticalFindingCount
    ) {}

    public record EngagementPhaseTransitionRequest(
        @NotBlank @Pattern(regexp = ENG_PHASE_REGEX) String newPhase,
        Long actorUserId,
        @Size(max = 1000) String noteEn,
        @Size(max = 1000) String noteAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EngagementResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long planId,
        Long universeEntryId,
        String engagementCode,
        String titleEn,
        String titleAr,
        String auditType,
        String scopeSummaryEn,
        String scopeSummaryAr,
        Long leadAuditorUserId,
        String teamMemberIds,
        Long sponsorUserId,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        String phase,
        String planningMemoUrl,
        LocalDateTime planningMemoSignedOffAt,
        String opinion,
        Integer findingCount,
        Integer criticalFindingCount,
        List<MilestoneResponse> milestones,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ── Milestone ────────────────────────────────────────────────────

    public record MilestoneCreateRequest(
        @NotBlank @Pattern(regexp = MILESTONE_TYPE_REGEX) String milestoneType,
        LocalDate plannedDate,
        @Size(max = 500) String notesEn,
        @Size(max = 500) String notesAr
    ) {}

    public record MilestoneRecordRequest(
        @NotBlank @Pattern(regexp = MILESTONE_TYPE_REGEX) String milestoneType,
        @NotNull LocalDate actualDate,
        @Positive Long completedByUserId,
        @Size(max = 500) String notesEn,
        @Size(max = 500) String notesAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MilestoneResponse(
        Long id,
        Long engagementId,
        String milestoneType,
        LocalDate plannedDate,
        LocalDate actualDate,
        Long completedByUserId,
        String notesEn,
        String notesAr,
        LocalDateTime createdAt
    ) {}
}
