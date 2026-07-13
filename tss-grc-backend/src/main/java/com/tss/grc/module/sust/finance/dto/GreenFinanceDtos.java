package com.tss.grc.module.sust.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Green-Finance uplift G1 — request payloads (responses are the entities themselves,
 *  matching the climate-module convention). */
public final class GreenFinanceDtos {

    private GreenFinanceDtos() {}

    public record CreateInstrumentRequest(
        @NotBlank @Size(max = 40) String instrumentCode,
        @NotBlank @Pattern(regexp = "GREEN_SUKUK|GREEN_BOND|SUSTAINABILITY_SUKUK|GREEN_LOAN|SLL") String instrumentType,
        @NotBlank @Size(max = 300) String nameEn,
        @Size(max = 300) String nameAr,
        @Size(max = 20) String isin,
        @Size(max = 60) String listing,
        /** GF-5 (V298, REG-8) — offering-document reference. */
        @Size(max = 200) String prospectusRef,
        LocalDate issueDate,
        LocalDate maturityDate,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amountSarMn,
        @DecimalMin(value = "0") BigDecimal issuanceCostsSarMn,
        @Size(max = 300) String frameworkReference,
        @Pattern(regexp = "MUDARABA|WAKALA|IJARA|HYBRID|NA") String shariahStructure,
        @Size(max = 200) String shariahApprovalRef,
        @Size(max = 200) String externalReviewer,
        @Size(max = 60) String spoRating,
        @Size(max = 500) String icmaCategories,
        BigDecimal profitRatePct,
        Integer lookbackMonths
    ) {}

    public record TransitionInstrumentRequest(
        @NotBlank @Pattern(regexp = "DRAFT|SHARIAH_REVIEW|APPROVED|ISSUED|REPORTING|MATURED") String status,
        @Size(max = 200) String shariahApprovalRef
    ) {}

    public record CreateProjectRequest(
        @NotBlank @Size(max = 40) String projectCode,
        @NotBlank @Size(max = 300) String nameEn,
        @Size(max = 300) String nameAr,
        @NotBlank @Pattern(regexp = "RENEWABLE_ENERGY|ENERGY_EFFICIENCY|CLEAN_TRANSPORT|GREEN_BUILDINGS|WATER|WASTE|CCUS|OTHER_ELIGIBLE") String category,
        String descriptionEn,
        String descriptionAr,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal totalCostSarMn,
        BigDecimal expectedTco2eAvoidedYr,
        BigDecimal actualTco2eAvoidedYr,
        BigDecimal expectedMwhRenewableYr,
        BigDecimal actualMwhRenewableYr,
        BigDecimal expectedWaterM3SavedYr,
        BigDecimal actualWaterM3SavedYr,
        /** V293 (plan §4.2) — ACTUAL (default) | ESTIMATE: basis of the actual-impact figures. */
        @Pattern(regexp = "ACTUAL|ESTIMATE") String actualImpactBasis
    ) {}

    public record ProposeAllocationRequest(
        @NotNull Long instrumentId,
        @NotNull Long projectId,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal amountSarMn,
        @NotNull LocalDate allocationDate,
        @Pattern(regexp = "NEW_FINANCING|REFINANCING") String financingType
    ) {}

    public record RejectAllocationRequest(
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record CreateReportRequest(
        @NotNull Long instrumentId,
        @NotNull Integer periodYear,
        @Pattern(regexp = "ALLOCATION|IMPACT|COMBINED") String reportType,
        String narrativeEn,
        String narrativeAr
    ) {}

    /**
     * GF-1 — org-level attributed impact across ALL instruments with double-count
     * protection (plan §4.2 last formula): per project, Σ instrument shares is
     * capped at 1 and each instrument's contribution is scaled pro-rata.
     */
    public record PortfolioImpactResponse(
        BigDecimal tco2eAvoidedYr,
        BigDecimal mwhRenewableYr,
        BigDecimal waterM3SavedYr,
        /** True when any contributing project's actual-impact figures are ESTIMATE-based. */
        boolean estimateBasis,
        int projectsCounted,
        int projectsCapped,
        java.util.List<InstrumentContribution> byInstrument,
        java.util.List<ProjectAttribution> byProject
    ) {}

    /** GF-1 — one instrument's dedup-scaled slice of the portfolio impact. */
    public record InstrumentContribution(
        Long instrumentId, String instrumentCode,
        BigDecimal tco2eAvoidedYr, BigDecimal mwhRenewableYr, BigDecimal waterM3SavedYr
    ) {}

    /** GF-1 — per-project share accounting; dedupApplied = the cap kicked in. */
    public record ProjectAttribution(
        Long projectId, String projectCode,
        BigDecimal rawShareSum, BigDecimal countedShare, boolean dedupApplied
    ) {}

    /** Use-of-proceeds summary for one instrument (plan §4.2 figures). */
    public record InstrumentSummaryResponse(
        Long instrumentId,
        String instrumentCode,
        String status,
        BigDecimal netProceedsSarMn,
        BigDecimal allocatedSarMn,
        BigDecimal unallocatedSarMn,
        BigDecimal allocationRatioPct,
        BigDecimal refinancingSharePct,
        BigDecimal tco2eAvoidedYr,
        BigDecimal mwhRenewableYr,
        BigDecimal waterM3SavedYr,
        BigDecimal intensityTco2ePerSarMn,
        int approvedAllocations,
        int pendingAllocations,
        /** V293 — true when any contributing project's actual-impact figures are ESTIMATE-based. */
        boolean estimateBasis
    ) {}
}
