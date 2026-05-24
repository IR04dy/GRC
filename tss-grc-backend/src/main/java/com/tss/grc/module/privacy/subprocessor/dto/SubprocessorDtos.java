package com.tss.grc.module.privacy.subprocessor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.privacy.subprocessor.entity.Subprocessor;
import com.tss.grc.module.privacy.subprocessor.entity.SubprocessorEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request / response DTOs for the PDPL sub-processor REST surface — Sprint 11.
 */
public final class SubprocessorDtos {

    private SubprocessorDtos() {}

    // ─── Subprocessor ─────────────────────────────────────────────

    public record SubprocessorRequest(
            String subprocessorCode,
            Long parentProcessorId,
            @NotBlank @Size(max = 255) String subProcessorLegalName,
            @Size(max = 255) String subProcessorLegalNameAr,
            Long vendorId,
            @NotBlank @Size(min = 2, max = 2) String countryOfProcessing,
            @Size(max = 255) String regionOfProcessing,
            String dataCategoriesJson,
            String dataSubjectCategoriesJson,
            @NotBlank @Size(max = 1000) String processingPurposeEn,
            @Size(max = 1000) String processingPurposeAr,
            @NotBlank @Size(max = 40) String lawfulBasis,
            @NotBlank @Size(max = 20) String authorisationType,
            @Size(max = 500) String authorisationBasisDocRef,
            @Size(max = 40) String crossBorderSafeguardType,
            @Size(max = 500) String crossBorderSafeguardDocRef,
            @Size(max = 255) String tiaReference,
            LocalDate tiaCompletedAt,
            @Size(max = 255) String adequacyDecisionRef,
            @Size(max = 20) String dataVolumeBand,
            Boolean hasDpaInPlace,
            LocalDate dpaSignedDate,
            LocalDate dpaExpiryDate
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubprocessorResponse(
            Long id,
            UUID externalId,
            String subprocessorCode,
            Long parentProcessorId,
            String subProcessorLegalName,
            String subProcessorLegalNameAr,
            Long vendorId,
            String countryOfProcessing,
            String regionOfProcessing,
            String dataCategoriesJson,
            String dataSubjectCategoriesJson,
            String processingPurposeEn,
            String processingPurposeAr,
            String lawfulBasis,
            String authorisationType,
            String authorisationBasisDocRef,
            String crossBorderSafeguardType,
            String crossBorderSafeguardDocRef,
            String tiaReference,
            LocalDate tiaCompletedAt,
            String adequacyDecisionRef,
            String dataVolumeBand,
            Boolean hasDpaInPlace,
            LocalDate dpaSignedDate,
            LocalDate dpaExpiryDate,
            String lifecycleStage,
            Long dpoApprovedByUserId,
            LocalDateTime dpoApprovedAt,
            LocalDateTime lastReviewedAt,
            LocalDateTime nextReviewDueAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static SubprocessorResponse from(Subprocessor s) {
            return new SubprocessorResponse(
                    s.getId(), s.getExternalId(), s.getSubprocessorCode(),
                    s.getParentProcessorId(),
                    s.getSubProcessorLegalName(), s.getSubProcessorLegalNameAr(),
                    s.getVendorId(),
                    s.getCountryOfProcessing(), s.getRegionOfProcessing(),
                    s.getDataCategoriesJson(), s.getDataSubjectCategoriesJson(),
                    s.getProcessingPurposeEn(), s.getProcessingPurposeAr(),
                    s.getLawfulBasis(),
                    s.getAuthorisationType(), s.getAuthorisationBasisDocRef(),
                    s.getCrossBorderSafeguardType(), s.getCrossBorderSafeguardDocRef(),
                    s.getTiaReference(), s.getTiaCompletedAt(),
                    s.getAdequacyDecisionRef(),
                    s.getDataVolumeBand(),
                    s.getHasDpaInPlace(), s.getDpaSignedDate(), s.getDpaExpiryDate(),
                    s.getLifecycleStage(),
                    s.getDpoApprovedByUserId(), s.getDpoApprovedAt(),
                    s.getLastReviewedAt(), s.getNextReviewDueAt(),
                    s.getCreatedAt(), s.getUpdatedAt()
            );
        }
    }

    /** Public-list response — strips internal fields per PDPL transparency. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PublicSubprocessorResponse(
            String subprocessorCode,
            String subProcessorLegalName,
            String subProcessorLegalNameAr,
            String countryOfProcessing,
            String regionOfProcessing,
            String processingPurposeEn,
            String processingPurposeAr,
            String crossBorderSafeguardType
    ) {
        public static PublicSubprocessorResponse from(Subprocessor s) {
            return new PublicSubprocessorResponse(
                    s.getSubprocessorCode(),
                    s.getSubProcessorLegalName(), s.getSubProcessorLegalNameAr(),
                    s.getCountryOfProcessing(), s.getRegionOfProcessing(),
                    s.getProcessingPurposeEn(), s.getProcessingPurposeAr(),
                    s.getCrossBorderSafeguardType()
            );
        }
    }

    public record LifecycleTransitionRequest(
            @NotBlank @Size(max = 20) String targetStage,
            @Size(max = 500) String note
    ) {}

    public record MaterialChangeRequest(
            @NotBlank @Size(max = 2000) String description,
            @Size(max = 2000) String descriptionAr
    ) {}

    // ─── Event ─────────────────────────────────────────────────────

    public record SubprocessorEventRequest(
            @NotBlank @Size(max = 40) String eventType,
            @Size(max = 2000) String descriptionEn,
            @Size(max = 2000) String descriptionAr,
            Boolean dataSubjectsNotified,
            LocalDate dataSubjectsNotificationDate
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubprocessorEventResponse(
            Long id,
            UUID externalId,
            Long subprocessorId,
            String eventType,
            LocalDateTime eventDate,
            Long recordedByUserId,
            String descriptionEn,
            String descriptionAr,
            Boolean dataSubjectsNotified,
            LocalDate dataSubjectsNotificationDate,
            LocalDateTime createdAt
    ) {
        public static SubprocessorEventResponse from(SubprocessorEvent e) {
            return new SubprocessorEventResponse(
                    e.getId(), e.getExternalId(), e.getSubprocessorId(),
                    e.getEventType(), e.getEventDate(), e.getRecordedByUserId(),
                    e.getDescriptionEn(), e.getDescriptionAr(),
                    e.getDataSubjectsNotified(), e.getDataSubjectsNotificationDate(),
                    e.getCreatedAt()
            );
        }
    }
}
