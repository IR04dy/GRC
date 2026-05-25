package com.tss.grc.module.rrp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Sprint 17 PR #1 — Recovery &amp; Resolution Plan DTOs. */
public final class RrpDtos {
    private RrpDtos() {}

    // ─── Cycle ─────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateCycleRequest {
        @NotNull @Min(2020) private Integer fiscalYear;
        @NotBlank @Size(max = 256) private String titleEn;
        @NotBlank @Size(max = 256) private String titleAr;
        private LocalDate submissionDueDate;
        private Long ownerUserId;
        private Long sponsorUserId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateCycleRequest {
        @Size(max = 256) private String titleEn;
        @Size(max = 256) private String titleAr;
        private LocalDate submissionDueDate;
        private Long ownerUserId;
        private Long sponsorUserId;
        @Size(max = 4000) private String executiveSummaryEn;
        @Size(max = 4000) private String executiveSummaryAr;
        @Size(max = 512) private String filingArtifactReference;
        private LocalDate boardApprovalDate;
        @Size(max = 128) private String boardResolutionReference;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransitionCycleRequest {
        @NotBlank @Size(max = 24) private String toStatus;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SubmitCycleRequest {
        @NotBlank @Size(max = 256) private String regulatorAckReference;
        @Size(max = 512) private String filingArtifactReference;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CycleResponse {
        private Long id;
        private String reference;
        private Integer fiscalYear;
        private String titleEn;
        private String titleAr;
        private String status;
        private LocalDate submissionDueDate;
        private LocalDateTime submittedAt;
        private String regulatorAckReference;
        private LocalDateTime acknowledgedAt;
        private LocalDateTime closedAt;
        private Long ownerUserId;
        private Long sponsorUserId;
        private LocalDate boardApprovalDate;
        private String boardResolutionReference;
        private String executiveSummaryEn;
        private String executiveSummaryAr;
        private String filingArtifactReference;
        private Integer criticalFunctionCount;
        private Integer recoveryOptionCount;
        private Integer indicatorCount;
        private Integer indicatorsAmber;
        private Integer indicatorsRed;
        private Long totalRecoveryCapacitySarM;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ─── Recovery Option ────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateOptionRequest {
        @NotBlank @Size(max = 32) private String optionCode;
        @NotBlank @Size(max = 24) private String category;
        @NotBlank @Size(max = 256) private String titleEn;
        @NotBlank @Size(max = 256) private String titleAr;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        @NotNull @Min(0) private Long capacitySarM;
        @NotNull @Min(0) private Integer timeToExecuteDays;
        @NotBlank @Size(max = 12) private String feasibility;
        @Size(max = 4000) private String impactEn;
        @Size(max = 4000) private String impactAr;
        @Size(max = 4000) private String preconditionsEn;
        @Size(max = 4000) private String preconditionsAr;
        @Size(max = 4000) private String limitationsEn;
        @Size(max = 4000) private String limitationsAr;
        @Size(max = 64) private String accountableRole;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateOptionRequest {
        @Size(max = 24) private String category;
        @Size(max = 256) private String titleEn;
        @Size(max = 256) private String titleAr;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        @Min(0) private Long capacitySarM;
        @Min(0) private Integer timeToExecuteDays;
        @Size(max = 12) private String feasibility;
        @Size(max = 4000) private String impactEn;
        @Size(max = 4000) private String impactAr;
        @Size(max = 4000) private String preconditionsEn;
        @Size(max = 4000) private String preconditionsAr;
        @Size(max = 4000) private String limitationsEn;
        @Size(max = 4000) private String limitationsAr;
        @Size(max = 64) private String accountableRole;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OptionResponse {
        private Long id;
        private Long cycleId;
        private String optionCode;
        private String category;
        private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        private Long capacitySarM;
        private Integer timeToExecuteDays;
        private String feasibility;
        private String impactEn;
        private String impactAr;
        private String preconditionsEn;
        private String preconditionsAr;
        private String limitationsEn;
        private String limitationsAr;
        private String accountableRole;
    }

    // ─── Critical Function ──────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateCriticalFunctionRequest {
        @NotBlank @Size(max = 32) private String functionCode;
        @NotBlank @Size(max = 256) private String titleEn;
        @NotBlank @Size(max = 256) private String titleAr;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        @NotBlank @Size(max = 16) private String materiality;
        @Min(0) private Integer maxTolerableDowntimeHours;
        @Min(0) private Long customerCountEstimate;
        @Min(0) private Long dailyVolumeEstimate;
        @Size(max = 4000) private String criticalProviderDependenciesEn;
        @Size(max = 4000) private String criticalProviderDependenciesAr;
        @Size(max = 4000) private String continuityStrategyEn;
        @Size(max = 4000) private String continuityStrategyAr;
        @Size(max = 64) private String ownerRole;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CriticalFunctionResponse {
        private Long id;
        private Long cycleId;
        private String functionCode;
        private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        private String materiality;
        private Integer maxTolerableDowntimeHours;
        private Long customerCountEstimate;
        private Long dailyVolumeEstimate;
        private String criticalProviderDependenciesEn;
        private String criticalProviderDependenciesAr;
        private String continuityStrategyEn;
        private String continuityStrategyAr;
        private String ownerRole;
    }

    // ─── Indicator ──────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateIndicatorRequest {
        @NotBlank @Size(max = 32) private String indicatorCode;
        @NotBlank @Size(max = 24) private String category;
        @NotBlank @Size(max = 256) private String titleEn;
        @NotBlank @Size(max = 256) private String titleAr;
        @Size(max = 4000) private String descriptionEn;
        @Size(max = 4000) private String descriptionAr;
        @Size(max = 16) private String unit;
        @NotBlank @Size(max = 20) private String direction;
        private BigDecimal greenThreshold;
        private BigDecimal amberThreshold;
        private BigDecimal currentValue;
        private LocalDate measuredAt;
        @Size(max = 256) private String dataSource;
        @Size(max = 64) private String ownerRole;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RecordIndicatorMeasurementRequest {
        @NotNull private BigDecimal value;
        @NotNull private LocalDate measuredAt;
        @Size(max = 4000) private String noteEn;
        @Size(max = 4000) private String noteAr;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class IndicatorResponse {
        private Long id;
        private Long cycleId;
        private String indicatorCode;
        private String category;
        private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        private String unit;
        private String direction;
        private BigDecimal greenThreshold;
        private BigDecimal amberThreshold;
        private BigDecimal currentValue;
        private String currentBand;
        private LocalDate measuredAt;
        private String dataSource;
        private String ownerRole;
    }

    // ─── Event ──────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class EventResponse {
        private Long id;
        private Long cycleId;
        private LocalDateTime occurredAt;
        private String eventType;
        private Long actorUserId;
        private String actorName;
        private String statusAfter;
        private Long linkedOptionId;
        private Long linkedIndicatorId;
        private Long linkedCriticalFunctionId;
        private String bandBefore;
        private String bandAfter;
        private String noteEn;
        private String noteAr;
    }

    // ─── Dashboard ──────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DashboardResponse {
        /** Currently in DRAFT|IN_PREPARATION|BOARD_REVIEW|APPROVED. */
        private long activeCycleCount;
        private long submittedCycleCount;
        private long closedCycleCount;
        /** Across all cycles for tenant, indicators currently RED. */
        private long indicatorsRed;
        /** Across all cycles for tenant, indicators currently AMBER. */
        private long indicatorsAmber;
        /** Sum of recovery capacity (SAR M) on the most-recent cycle. */
        private long currentRecoveryCapacitySarM;
        private List<CycleResponse> recentCycles;
        /** Status breakdown across cycles. */
        private Map<String, Long> byStatus;
    }
}
