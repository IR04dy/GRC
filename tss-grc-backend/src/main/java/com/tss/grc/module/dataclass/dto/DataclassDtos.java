package com.tss.grc.module.dataclass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DataclassDtos {
    private DataclassDtos() {}

    public record CreateAssetRequest(
            @NotBlank String reference,
            @NotBlank String nameEn, @NotBlank String nameAr,
            String descriptionEn, String descriptionAr,
            String domain, String hostSystemLabel,
            String pdplSpecialCategory, String pdplLawfulBasis,
            String ownerRole, Long ownerUserId,
            Integer retentionYears, Boolean encryptionAtRest,
            Boolean encryptionInTransit, Boolean maskingRequired,
            Long rowCountApprox, String linkedDpiaRef
    ) {}

    public record ClassifyAssetRequest(
            @NotBlank String classification,
            String pdplSpecialCategory, String pdplLawfulBasis,
            LocalDate nextReviewDue
    ) {}

    public record TransitionAssetRequest(
            @NotBlank String toStatus,
            String reasonEn, String reasonAr
    ) {}

    public record AssetResponse(
            Long id, String reference, String nameEn, String nameAr,
            String descriptionEn, String descriptionAr,
            String domain, String classification,
            String pdplSpecialCategory, String pdplLawfulBasis,
            String status, String ownerRole, Long ownerUserId,
            String hostSystemLabel, Integer retentionYears,
            Boolean encryptionAtRest, Boolean encryptionInTransit, Boolean maskingRequired,
            LocalDateTime classifiedAt, LocalDateTime approvedAt,
            LocalDate nextReviewDue, LocalDate decommissionedAt,
            Long rowCountApprox, String linkedDpiaRef
    ) {}

    public record CreateFlowRequest(
            @NotNull Long dataAssetId, @NotBlank String reference,
            String sourceSystemLabel, String destinationSystemLabel,
            String destinationCountry, String destinationAdequacy,
            String transferMode, String purposeEn, String purposeAr,
            String pdplArt7TransferRef
    ) {}

    public record TransitionFlowRequest(
            @NotBlank String toStatus,
            String reasonEn, String reasonAr,
            String pdplArt7TransferRef
    ) {}

    public record FlowResponse(
            Long id, Long dataAssetId, String reference,
            String sourceSystemLabel, String destinationSystemLabel,
            String destinationCountry, String destinationAdequacy,
            String transferMode, String status,
            String purposeEn, String purposeAr,
            String pdplArt7TransferRef,
            LocalDateTime approvedAt, LocalDate activatedAt,
            LocalDate suspendedAt, LocalDate terminatedAt,
            String rejectionReasonEn, String rejectionReasonAr
    ) {}

    public record CreateExceptionRequest(
            @NotBlank String reference, Long dataAssetId, Long dataFlowId,
            @NotBlank String exceptionType,
            String justificationEn, String justificationAr,
            String compensatingControlsEn, String compensatingControlsAr,
            LocalDate validFrom, LocalDate validUntil
    ) {}

    public record TransitionExceptionRequest(
            @NotBlank String toStatus,
            String rejectionReasonEn, String rejectionReasonAr
    ) {}

    public record ExceptionResponse(
            Long id, String reference, Long dataAssetId, Long dataFlowId,
            String exceptionType, String status,
            String justificationEn, String justificationAr,
            String compensatingControlsEn, String compensatingControlsAr,
            Long requestedByUserId, Long approverUserId,
            LocalDateTime approvedAt, LocalDate validFrom, LocalDate validUntil,
            LocalDate revokedAt,
            String rejectionReasonEn, String rejectionReasonAr
    ) {}

    public record EventResponse(
            Long id, Long dataAssetId, Long dataFlowId, Long dataExceptionId,
            LocalDateTime occurredAt, String eventType,
            String actorName, String statusAfter,
            String noteEn, String noteAr
    ) {}

    public record DashboardResponse(
            long assetsTotal,
            long assetsTopSecret, long assetsConfidential,
            long assetsInternal, long assetsPublic,
            long assetsSpecialCategory,
            long flowsActive, long flowsCrossBorder,
            long exceptionsActive, long exceptionsExpiringNext30d,
            /**
             * F2-128 — ACTIVE exceptions whose {@code valid_until} has already passed.
             *
             * <p>Separate from {@code exceptionsExpiringNext30d} on purpose: that window is
             * {@code [today, today+30]}, so it <b>excludes</b> anything already lapsed, and until this
             * field existed a lapsed control waiver appeared in no metric at all — it was counted in
             * {@code exceptionsActive} as though still valid. "About to lapse" and "already lapsed"
             * also demand different responses, so folding them together would hide the urgent case
             * inside the routine one.
             */
            long exceptionsLapsed,
            Map<String, Long> assetsByStatus,
            Map<String, Long> flowsByStatus,
            Map<String, Long> exceptionsByType,
            List<AssetResponse> recentAssets,
            List<FlowResponse> recentCrossBorderFlows
    ) {}
}
