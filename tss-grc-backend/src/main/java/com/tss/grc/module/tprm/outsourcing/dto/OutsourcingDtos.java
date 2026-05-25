package com.tss.grc.module.tprm.outsourcing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — Outsourcing register DTOs. Bundled in one file for the
 * same reason other modules do — keeps the controller surface compact
 * and lets the dev see every request/response shape at a glance.
 */
public final class OutsourcingDtos {
    private OutsourcingDtos() {}

    // ─── Arrangement create / update ────────────────────────────────

    public record ArrangementCreateRequest(
        @Size(max = 64) String arrangementCode,
        @NotBlank @Size(max = 255) String titleEn,
        @NotBlank @Size(max = 255) String titleAr,
        @Size(max = 8000) String descriptionEn,
        @Size(max = 8000) String descriptionAr,
        @NotBlank @Size(max = 40) String serviceType,
        Long primaryVendorId,
        @Size(max = 500) String coVendorIds,
        Boolean isMaterial,
        @Size(max = 1000) String materialityBasisEn,
        @Size(max = 1000) String materialityBasisAr,
        Boolean samaNonObjectionRequired,
        Boolean samaNonObjectionObtained,
        @Size(max = 255) String samaNonObjectionRef,
        LocalDate samaNonObjectionDate,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        @DecimalMin("0.0") BigDecimal contractValueSarAnnual,
        @Size(max = 255) String contractRef,
        @Size(max = 500) String contractDocumentUrl,
        @Size(max = 40) String criticalFunction,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryOfProcessing,
        @Size(max = 255) String regionOfProcessing,
        Boolean dataResidencyKdc,
        Boolean personalDataProcessed,
        Boolean pdplDpaSigned,
        LocalDate pdplDpaDate,
        @Size(max = 40) String crossBorderSafeguardType,
        @NotNull Long ownerUserId,
        @NotNull Long sponsorUserId,
        LocalDate nextReviewDueDate
    ) {}

    public record ArrangementUpdateRequest(
        @Size(max = 255) String titleEn,
        @Size(max = 255) String titleAr,
        @Size(max = 8000) String descriptionEn,
        @Size(max = 8000) String descriptionAr,
        @Size(max = 40) String serviceType,
        Long primaryVendorId,
        @Size(max = 500) String coVendorIds,
        Boolean isMaterial,
        @Size(max = 1000) String materialityBasisEn,
        @Size(max = 1000) String materialityBasisAr,
        Boolean samaNonObjectionRequired,
        Boolean samaNonObjectionObtained,
        @Size(max = 255) String samaNonObjectionRef,
        LocalDate samaNonObjectionDate,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        BigDecimal contractValueSarAnnual,
        @Size(max = 255) String contractRef,
        @Size(max = 500) String contractDocumentUrl,
        @Size(max = 40) String criticalFunction,
        @Size(max = 2) String countryOfProcessing,
        @Size(max = 255) String regionOfProcessing,
        Boolean dataResidencyKdc,
        Boolean personalDataProcessed,
        Boolean pdplDpaSigned,
        LocalDate pdplDpaDate,
        @Size(max = 40) String crossBorderSafeguardType,
        Long ownerUserId,
        Long sponsorUserId,
        LocalDate nextReviewDueDate
    ) {}

    public record ArrangementTransitionRequest(
        @NotBlank @Size(max = 20) String toStage,
        @Size(max = 1000) String reason
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ArrangementResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        String arrangementCode,
        String titleEn,
        String titleAr,
        String descriptionEn,
        String descriptionAr,
        String serviceType,
        Long primaryVendorId,
        String coVendorIds,
        Boolean isMaterial,
        String materialityBasisEn,
        String materialityBasisAr,
        Boolean samaNonObjectionRequired,
        Boolean samaNonObjectionObtained,
        String samaNonObjectionRef,
        LocalDate samaNonObjectionDate,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        BigDecimal contractValueSarAnnual,
        String contractRef,
        String contractDocumentUrl,
        String criticalFunction,
        String countryOfProcessing,
        String regionOfProcessing,
        Boolean dataResidencyKdc,
        Boolean personalDataProcessed,
        Boolean pdplDpaSigned,
        LocalDate pdplDpaDate,
        String crossBorderSafeguardType,
        String lifecycleStage,
        Long ownerUserId,
        Long sponsorUserId,
        LocalDate nextReviewDueDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Due Diligence ──────────────────────────────────────────────

    public record DueDiligenceCreateRequest(
        @NotNull Long arrangementId,
        @NotNull LocalDate assessmentDate,
        @NotNull Long assessorUserId,
        @NotNull @Min(0) @Max(100) Integer financialHealthScore,
        @NotNull @Min(0) @Max(100) Integer operationalCapabilityScore,
        @NotNull @Min(0) @Max(100) Integer cybersecurityScore,
        @NotNull @Min(0) @Max(100) Integer bcmScore,
        @NotNull @Min(0) @Max(100) Integer pdplComplianceScore,
        @NotNull @Min(0) @Max(100) Integer reputationScore,
        @NotBlank @Pattern(regexp = "APPROVE|CONDITIONAL|REJECT") String recommendation,
        @NotBlank @Size(max = 2000) String rationaleEn,
        @NotBlank @Size(max = 2000) String rationaleAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DueDiligenceResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long arrangementId,
        LocalDate assessmentDate,
        Long assessorUserId,
        Integer financialHealthScore,
        Integer operationalCapabilityScore,
        Integer cybersecurityScore,
        Integer bcmScore,
        Integer pdplComplianceScore,
        Integer reputationScore,
        BigDecimal compositeScore,
        String recommendation,
        String rationaleEn,
        String rationaleAr,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Monitoring Event ───────────────────────────────────────────

    public record MonitoringEventCreateRequest(
        @NotNull Long arrangementId,
        @NotBlank @Size(max = 40) String eventType,
        @NotNull LocalDate eventDate,
        @Size(max = 20) String severity,
        @NotBlank @Size(max = 2000) String descriptionEn,
        @NotBlank @Size(max = 2000) String descriptionAr,
        @Size(max = 1000) String actionTakenEn,
        @Size(max = 1000) String actionTakenAr,
        Boolean resolved
    ) {}

    public record MonitoringEventResolveRequest(
        @Size(max = 1000) String actionTakenEn,
        @Size(max = 1000) String actionTakenAr
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MonitoringEventResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long arrangementId,
        String eventType,
        LocalDate eventDate,
        String severity,
        String descriptionEn,
        String descriptionAr,
        String actionTakenEn,
        String actionTakenAr,
        Boolean resolved,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Exit Plan ──────────────────────────────────────────────────

    public record ExitPlanCreateRequest(
        @NotNull Long arrangementId,
        @NotBlank @Size(max = 2000) String triggerConditionsEn,
        @NotBlank @Size(max = 2000) String triggerConditionsAr,
        String exitStepsJson,
        @Size(max = 2000) String dataRepatriationPlanEn,
        @Size(max = 2000) String dataRepatriationPlanAr,
        @NotBlank @Pattern(regexp = "NEVER|ANNUAL|BIANNUAL|NEVER_TESTED") String testFrequency,
        LocalDate lastTestedDate
    ) {}

    public record ExitPlanUpdateRequest(
        @Size(max = 2000) String triggerConditionsEn,
        @Size(max = 2000) String triggerConditionsAr,
        String exitStepsJson,
        @Size(max = 2000) String dataRepatriationPlanEn,
        @Size(max = 2000) String dataRepatriationPlanAr,
        @Pattern(regexp = "NEVER|ANNUAL|BIANNUAL|NEVER_TESTED") String testFrequency,
        LocalDate lastTestedDate
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExitPlanResponse(
        Long id,
        UUID externalId,
        Long organisationId,
        Long arrangementId,
        String triggerConditionsEn,
        String triggerConditionsAr,
        String exitStepsJson,
        String dataRepatriationPlanEn,
        String dataRepatriationPlanAr,
        String testFrequency,
        LocalDate lastTestedDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}

    // ─── Dashboard ──────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DashboardResponse(
        long totalArrangements,
        long activeMaterial,
        long nonMaterial,
        long awaitingSama,
        long overdueReviews,
        long overdueExitTests,
        long openMajorOrCriticalEvents
    ) {}

    // ─── Materiality Auto-Detect ────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MaterialityAssessment(
        Long arrangementId,
        boolean suggestedMaterial,
        boolean currentMaterial,
        String[] reasons
    ) {}
}
