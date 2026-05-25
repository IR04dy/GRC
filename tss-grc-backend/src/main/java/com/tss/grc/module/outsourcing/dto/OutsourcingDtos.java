package com.tss.grc.module.outsourcing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** All DTOs for Sprint 30 Outsourcing &amp; Cloud Governance module. */
public final class OutsourcingDtos {

    private OutsourcingDtos() {}

    // -------------------------------------------------------- Arrangement

    public record CreateArrangementRequest(
            @NotBlank String reference,
            @NotBlank String providerLegalNameEn,
            @NotBlank String providerLegalNameAr,
            String providerCountry,
            String providerJurisdiction,
            @NotBlank String serviceTitleEn,
            @NotBlank String serviceTitleAr,
            String serviceDescriptionEn,
            String serviceDescriptionAr,
            @NotBlank String serviceCategory,
            @NotBlank String materiality,
            Boolean isCoreActivity,
            LocalDate contractStart,
            LocalDate contractEnd,
            Integer renewalNoticeDays,
            BigDecimal annualSpendSar,
            Integer reviewCadenceMonths,
            Long relationshipOwnerUserId,
            Long riskOwnerUserId,
            Long linkedVendorId
    ) {}

    public record TransitionRequest(
            @NotBlank String toStatus,
            String reasonEn,
            String reasonAr,
            String samaNonObjectionRef,
            String riskRating
    ) {}

    public record ArrangementResponse(
            Long id, Long organisationId, String reference,
            String providerLegalNameEn, String providerLegalNameAr,
            String providerCountry, String providerJurisdiction,
            String serviceTitleEn, String serviceTitleAr,
            String serviceDescriptionEn, String serviceDescriptionAr,
            String serviceCategory, String materiality, Boolean isCoreActivity,
            String status, String riskRating,
            LocalDateTime riskAssessedAt, LocalDateTime boardApprovedAt,
            LocalDateTime samaNotifiedAt, String samaNonObjectionRef,
            LocalDate liveAt, LocalDate exitedAt, LocalDateTime rejectedAt,
            String rejectionReasonEn, String rejectionReasonAr,
            LocalDate contractStart, LocalDate contractEnd,
            Integer renewalNoticeDays, BigDecimal annualSpendSar,
            LocalDate nextReviewDue, Integer reviewCadenceMonths,
            Long relationshipOwnerUserId, Long riskOwnerUserId, Long linkedVendorId
    ) {}

    // ---------------------------------------------------------- Cloud Svc

    public record CreateCloudServiceRequest(
            @NotNull Long arrangementId,
            @NotBlank String reference,
            @NotBlank String serviceNameEn,
            @NotBlank String serviceNameAr,
            @NotBlank String deploymentModel,
            String serviceModel,
            @NotBlank String cspLegalNameEn,
            @NotBlank String cspLegalNameAr,
            String cspBrand,
            String dataClassification,
            String dataResidency,
            String cspRegionLabel,
            String approverRole,
            String supportsPdplDpiaRef
    ) {}

    public record ClassifyCloudServiceRequest(
            @NotBlank String dataClassification,
            @NotBlank String dataResidency,
            String cspRegionLabel
    ) {}

    public record CloudServiceTransitionRequest(
            @NotBlank String toStatus,
            String blockReasonEn,
            String blockReasonAr,
            String approverRole
    ) {}

    public record CloudServiceResponse(
            Long id, Long organisationId, Long arrangementId, String reference,
            String serviceNameEn, String serviceNameAr,
            String deploymentModel, String serviceModel,
            String cspLegalNameEn, String cspLegalNameAr, String cspBrand,
            String dataClassification, String dataResidency, String cspRegionLabel,
            String status,
            LocalDateTime classifiedAt, LocalDateTime approvedAt,
            LocalDate inUseAt, LocalDate deprecatedAt, LocalDate decommissionedAt,
            String blockReasonEn, String blockReasonAr,
            String approverRole, String supportsPdplDpiaRef
    ) {}

    // ----------------------------------------------------- Ongoing review

    public record CreateReviewRequest(
            @NotNull Long arrangementId,
            @NotBlank String reference,
            String reviewPeriodLabel,
            LocalDate dueDate,
            Long reviewerUserId
    ) {}

    public record TransitionReviewRequest(
            @NotBlank String toStatus,
            String outcomeRag,
            String findingsSummaryEn,
            String findingsSummaryAr,
            Long linkedCapaId
    ) {}

    public record ReviewResponse(
            Long id, Long organisationId, Long arrangementId, String reference,
            String reviewPeriodLabel, String status, LocalDate dueDate,
            LocalDateTime startedAt, LocalDateTime completedAt,
            String outcomeRag, String findingsSummaryEn, String findingsSummaryAr,
            Long linkedCapaId, Long reviewerUserId
    ) {}

    // ---------------------------------------------------------- Exit plan

    public record CreateExitPlanRequest(
            @NotNull Long arrangementId,
            @NotBlank String reference,
            String summaryEn,
            String summaryAr,
            Integer targetMigrationDays,
            String alternativeProviderEn,
            String alternativeProviderAr
    ) {}

    public record ExitPlanTransitionRequest(
            @NotBlank String toStatus,
            String rejectionReasonEn,
            String rejectionReasonAr
    ) {}

    public record ExitPlanResponse(
            Long id, Long organisationId, Long arrangementId, String reference,
            String status, String summaryEn, String summaryAr,
            Integer targetMigrationDays,
            String alternativeProviderEn, String alternativeProviderAr,
            LocalDateTime approvedAt, LocalDate rehearsedAt, LocalDate executedAt,
            String rejectionReasonEn, String rejectionReasonAr
    ) {}

    // ----------------------------------------------------------- Timeline

    public record EventResponse(
            Long id, Long arrangementId, Long cloudServiceId,
            Long reviewId, Long exitPlanId,
            LocalDateTime occurredAt, String eventType,
            String actorName, String statusAfter,
            String noteEn, String noteAr
    ) {}

    // ---------------------------------------------------------- Dashboard

    public record DashboardResponse(
            long arrangementsTotal,
            long materialActive,
            long arrangementsLive,
            long arrangementsUnderReview,
            long cloudServicesTotal,
            long cloudServicesT3T4,
            long cloudServicesBlocked,
            long reviewsOverdue,
            long exitPlansReady,
            Map<String, Long> arrangementsByStatus,
            Map<String, Long> cloudServicesByClassification,
            Map<String, Long> cloudServicesByResidency,
            List<ArrangementResponse> recentArrangements,
            List<ArrangementResponse> overdueReviews
    ) {}
}
