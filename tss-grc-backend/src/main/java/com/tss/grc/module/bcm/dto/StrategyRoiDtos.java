package com.tss.grc.module.bcm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the Slice 9-D continuity-strategy ROI endpoints.
 *
 * <p>Grouped in a single file (final + private ctor) the same way
 * {@code BiaQuantitativeDtos} groups its create/update/response shapes
 * — keeps the controller surface compact and discoverable.
 */
public final class StrategyRoiDtos {
    private StrategyRoiDtos() {}

    // ─── Create / Update request ────────────────────────────────────
    public record StrategyRoiRequest(
        @Size(max = 64) String analysisCode,
        @NotNull Long biaQuantitativeId,
        Long strategyId,
        @NotBlank @Size(max = 255) String strategyNameEn,
        @NotBlank @Size(max = 255) String strategyNameAr,
        @NotBlank @Size(max = 40) String strategyType,
        @NotNull @Positive Integer planningHorizonYears,
        @NotNull @PositiveOrZero BigDecimal capexSar,
        @NotNull @PositiveOrZero BigDecimal opexAnnualSar,
        @NotNull @Positive Integer expectedRtoHours,
        @PositiveOrZero BigDecimal expectedIncidentsPerYear,
        @PositiveOrZero BigDecimal discountRatePercent
    ) {}

    // ─── Recommendation transition ──────────────────────────────────
    public record RecommendationRequest(
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED|DEFERRED|PENDING_REVIEW") String recommendation,
        @Size(max = 255) String decidedByName,
        Long decidedByUserId,
        @Size(max = 1000) String recommendationRationaleEn,
        @Size(max = 1000) String recommendationRationaleAr
    ) {}

    // ─── Response ────────────────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StrategyRoiResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String analysisCode,
        Long biaQuantitativeId,
        Long strategyId,
        String strategyNameEn,
        String strategyNameAr,
        String strategyType,
        Integer planningHorizonYears,
        BigDecimal capexSar,
        BigDecimal opexAnnualSar,
        Integer expectedRtoHours,
        BigDecimal expectedIncidentsPerYear,
        BigDecimal avoidableLossPerIncidentSar,
        BigDecimal totalAvoidableLossSar,
        BigDecimal totalCostSar,
        BigDecimal roiPercent,
        BigDecimal paybackPeriodMonths,
        BigDecimal npvSar,
        BigDecimal discountRatePercent,
        String recommendation,
        String recommendationRationaleEn,
        String recommendationRationaleAr,
        Long decidedByUserId,
        String decidedByName,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Side-by-side comparison ────────────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StrategyRoiComparison(
        Long biaQuantitativeId,
        List<StrategyRoiResponse> analyses
    ) {}
}
