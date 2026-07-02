package com.tss.grc.module.sust.climate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.sust.climate.entity.FinancedEmissionsRecord;
import com.tss.grc.module.sust.climate.entity.FinancedEmissionsTarget;
import com.tss.grc.module.sust.climate.entity.TcfdClimateScenario;
import com.tss.grc.module.sust.climate.entity.TcfdSectorImpact;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request / response DTOs for the TCFD + Scope-3 financed emissions
 * REST surface — Sprint 11 (V186).
 */
public final class ClimateDtos {

    private ClimateDtos() {}

    // ─── TCFD scenario ────────────────────────────────────────────

    // S-E: portfolioAtRiskSarBillions + expectedLossSarMillions are NO LONGER
    // request inputs. They are DERIVED server-side from the scenario's sector
    // impacts (Σ exposure × PD-uplift × scenario factor) and returned read-only
    // in the response. probabilityWeight stays an input (the board allocation).
    public record TcfdScenarioRequest(
            String scenarioCode,
            @NotBlank @Size(max = 255) String titleEn,
            @Size(max = 255) String titleAr,
            String descriptionEn,
            String descriptionAr,
            @NotBlank @Size(max = 20) String scenarioType,
            @NotBlank @Size(max = 40) String referenceFramework,
            @NotNull @Positive Integer timeHorizonYears,
            @NotNull @PositiveOrZero BigDecimal globalWarmingCelsius,
            String assumptionsJson,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal probabilityWeight
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TcfdScenarioResponse(
            Long id,
            UUID externalId,
            String scenarioCode,
            String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            String scenarioType,
            String referenceFramework,
            Integer timeHorizonYears,
            BigDecimal globalWarmingCelsius,
            String assumptionsJson,
            BigDecimal portfolioAtRiskSarBillions,
            BigDecimal expectedLossSarMillions,
            BigDecimal probabilityWeight,
            String lifecycleStage,
            Long approvedByUserId,
            LocalDateTime approvedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static TcfdScenarioResponse from(TcfdClimateScenario s) {
            return new TcfdScenarioResponse(
                    s.getId(), s.getExternalId(), s.getScenarioCode(),
                    s.getTitleEn(), s.getTitleAr(),
                    s.getDescriptionEn(), s.getDescriptionAr(),
                    s.getScenarioType(), s.getReferenceFramework(),
                    s.getTimeHorizonYears(), s.getGlobalWarmingCelsius(),
                    s.getAssumptionsJson(),
                    s.getPortfolioAtRiskSarBillions(), s.getExpectedLossSarMillions(),
                    s.getProbabilityWeight(),
                    s.getLifecycleStage(), s.getApprovedByUserId(), s.getApprovedAt(),
                    s.getCreatedAt(), s.getUpdatedAt()
            );
        }
    }

    public record TcfdLifecycleTransitionRequest(
            @NotBlank @Size(max = 20) String toStage
    ) {}

    // ─── Sector impact ────────────────────────────────────────────

    public record TcfdSectorImpactRequest(
            @NotBlank @Size(max = 20) String sectorCode,
            @NotBlank @Size(max = 255) String sectorNameEn,
            @Size(max = 255) String sectorNameAr,
            @NotNull @PositiveOrZero BigDecimal exposureSarMillions,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal transitionRiskScore,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal physicalRiskScore,
            @PositiveOrZero Integer expectedPdUpliftBps,
            @Size(max = 1000) String mitigantActionsEn,
            @Size(max = 1000) String mitigantActionsAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TcfdSectorImpactResponse(
            Long id,
            UUID externalId,
            Long scenarioId,
            String sectorCode,
            String sectorNameEn,
            String sectorNameAr,
            BigDecimal exposureSarMillions,
            BigDecimal transitionRiskScore,
            BigDecimal physicalRiskScore,
            Integer expectedPdUpliftBps,
            String mitigantActionsEn,
            String mitigantActionsAr,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static TcfdSectorImpactResponse from(TcfdSectorImpact s) {
            return new TcfdSectorImpactResponse(
                    s.getId(), s.getExternalId(), s.getScenarioId(),
                    s.getSectorCode(), s.getSectorNameEn(), s.getSectorNameAr(),
                    s.getExposureSarMillions(),
                    s.getTransitionRiskScore(), s.getPhysicalRiskScore(),
                    s.getExpectedPdUpliftBps(),
                    s.getMitigantActionsEn(), s.getMitigantActionsAr(),
                    s.getCreatedAt(), s.getUpdatedAt()
            );
        }
    }

    // ─── Financed emissions record ────────────────────────────────

    public record FinancedEmissionsRequest(
            @NotNull @Positive Integer reportingYear,
            @NotBlank @Size(max = 10) String reportingPeriod,
            @NotBlank @Size(max = 40) String assetClass,
            @NotBlank @Size(max = 255) String assetClassLabelEn,
            @Size(max = 255) String assetClassLabelAr,
            @NotNull @PositiveOrZero BigDecimal outstandingAmountSarMillions,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal attributionFactor,
            @NotNull Integer dataQualityScore,
            @PositiveOrZero BigDecimal emissionsScope1Tco2e,
            @PositiveOrZero BigDecimal emissionsScope2Tco2e,
            @PositiveOrZero BigDecimal emissionsScope3Tco2e,
            @Size(max = 1000) String methodologyEn,
            @Size(max = 1000) String methodologyAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FinancedEmissionsResponse(
            Long id,
            UUID externalId,
            Integer reportingYear,
            String reportingPeriod,
            String assetClass,
            String assetClassLabelEn,
            String assetClassLabelAr,
            BigDecimal outstandingAmountSarMillions,
            BigDecimal attributionFactor,
            Integer dataQualityScore,
            BigDecimal emissionsScope1Tco2e,
            BigDecimal emissionsScope2Tco2e,
            BigDecimal emissionsScope3Tco2e,
            BigDecimal totalFinancedEmissionsTco2e,
            String methodologyEn,
            String methodologyAr,
            String lifecycleStage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static FinancedEmissionsResponse from(FinancedEmissionsRecord r) {
            return new FinancedEmissionsResponse(
                    r.getId(), r.getExternalId(),
                    r.getReportingYear(), r.getReportingPeriod(),
                    r.getAssetClass(), r.getAssetClassLabelEn(), r.getAssetClassLabelAr(),
                    r.getOutstandingAmountSarMillions(),
                    r.getAttributionFactor(),
                    r.getDataQualityScore(),
                    r.getEmissionsScope1Tco2e(),
                    r.getEmissionsScope2Tco2e(),
                    r.getEmissionsScope3Tco2e(),
                    r.getTotalFinancedEmissionsTco2e(),
                    r.getMethodologyEn(), r.getMethodologyAr(),
                    r.getLifecycleStage(),
                    r.getCreatedAt(), r.getUpdatedAt()
            );
        }
    }

    public record FeLifecycleTransitionRequest(
            @NotBlank @Size(max = 20) String toStage
    ) {}

    // ─── Target ───────────────────────────────────────────────────

    public record FinancedEmissionsTargetRequest(
            String targetCode,
            @NotBlank @Size(max = 255) String titleEn,
            @Size(max = 255) String titleAr,
            @NotNull @Positive Integer targetYear,
            @NotNull @Positive Integer baselineYear,
            @NotNull @PositiveOrZero BigDecimal baselineEmissionsTco2e,
            @NotNull @PositiveOrZero BigDecimal targetEmissionsTco2e,
            Boolean sbtiAligned,
            @NotBlank @Size(max = 40) String alignmentFramework
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FinancedEmissionsTargetResponse(
            Long id,
            UUID externalId,
            String targetCode,
            String titleEn,
            String titleAr,
            Integer targetYear,
            Integer baselineYear,
            BigDecimal baselineEmissionsTco2e,
            BigDecimal targetEmissionsTco2e,
            BigDecimal reductionPercent,
            Boolean sbtiAligned,
            String alignmentFramework,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static FinancedEmissionsTargetResponse from(FinancedEmissionsTarget t) {
            return new FinancedEmissionsTargetResponse(
                    t.getId(), t.getExternalId(), t.getTargetCode(),
                    t.getTitleEn(), t.getTitleAr(),
                    t.getTargetYear(), t.getBaselineYear(),
                    t.getBaselineEmissionsTco2e(), t.getTargetEmissionsTco2e(),
                    t.getReductionPercent(),
                    t.getSbtiAligned(), t.getAlignmentFramework(),
                    t.getCreatedAt(), t.getUpdatedAt()
            );
        }
    }

    // ─── Aggregates / annual report ───────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProbabilityWeightedExpectedLoss(
            BigDecimal weightedExpectedLossSarMillions,
            BigDecimal totalProbabilityWeight,
            Integer approvedScenarioCount,
            String warning
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FinancedEmissionsTotal(
            Integer reportingYear,
            String reportingPeriod,
            BigDecimal totalFinancedEmissionsTco2e,
            BigDecimal totalScope1Tco2e,
            BigDecimal totalScope2Tco2e,
            BigDecimal totalScope3Tco2e,
            BigDecimal totalOutstandingSarMillions,
            BigDecimal weightedAverageDataQuality,
            Integer assetClassCount
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TargetProgress(
            UUID targetExternalId,
            String targetCode,
            String titleEn,
            String titleAr,
            Integer baselineYear,
            BigDecimal baselineEmissionsTco2e,
            Integer targetYear,
            BigDecimal targetEmissionsTco2e,
            BigDecimal reductionPercent,
            BigDecimal currentEmissionsTco2e,
            Integer currentReportingYear,
            BigDecimal achievedReductionPercent,
            BigDecimal achievementRatio,
            Boolean sbtiAligned,
            String alignmentFramework
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnnualClimateReport(
            Integer reportingYear,
            List<TcfdScenarioResponse> scenarios,
            List<TcfdSectorImpactResponse> sectorImpacts,
            ProbabilityWeightedExpectedLoss weightedExpectedLoss,
            FinancedEmissionsTotal financedEmissionsTotal,
            List<FinancedEmissionsResponse> emissionsRecords,
            List<TargetProgress> targetProgress,
            String generatedAtIso
    ) {}
}
