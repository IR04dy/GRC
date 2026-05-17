package com.tss.grc.module.scm.resilience.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.scm.resilience.entity.AlternateSupplier;
import com.tss.grc.module.scm.resilience.entity.ExitPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Request / response DTOs for the supplier-resilience REST surface. */
public final class SupplierResilienceDtos {

    private SupplierResilienceDtos() {}

    // ═══ AlternateSupplier ════════════════════════════════════════════
    public record AltCreateRequest(
            Long primaryVendorId,
            @NotBlank @Size(max = 255) String primaryVendorName,
            @NotBlank @Size(max = 255) String alternateVendorName,
            @Size(max = 3) String alternateVendorCountry,
            @NotBlank @Size(max = 20) String alternateVendorRole,
            @NotBlank String serviceScopeEn,
            String serviceScopeAr,
            Integer estimatedOnboardingDays,
            BigDecimal estimatedCostSar,
            String lastAssessedOutcome,
            String contractualStatus,
            String notesEn,
            String notesAr
    ) {}

    public record AltUpdateRequest(
            String alternateVendorName,
            String alternateVendorCountry,
            String alternateVendorRole,
            String serviceScopeEn,
            String serviceScopeAr,
            Integer estimatedOnboardingDays,
            BigDecimal estimatedCostSar,
            String lastAssessedOutcome,
            String contractualStatus,
            String notesEn,
            String notesAr
    ) {}

    public record AltArchiveRequest(@NotBlank @Size(max = 500) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AltResponse(
            Long id,
            String externalId,
            Long primaryVendorId,
            String primaryVendorName,
            String alternateVendorName,
            String alternateVendorCountry,
            String alternateVendorRole,
            String serviceScopeEn,
            String serviceScopeAr,
            Integer estimatedOnboardingDays,
            BigDecimal estimatedCostSar,
            LocalDateTime lastAssessedAt,
            String lastAssessedOutcome,
            String contractualStatus,
            String notesEn,
            String notesAr,
            String status,
            LocalDateTime approvedAt,
            String approverName,
            LocalDateTime archivedAt,
            String archiveReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static AltResponse from(AlternateSupplier a) {
            return new AltResponse(
                    a.getId(), a.getExternalId(),
                    a.getPrimaryVendorId(), a.getPrimaryVendorName(),
                    a.getAlternateVendorName(), a.getAlternateVendorCountry(), a.getAlternateVendorRole(),
                    a.getServiceScopeEn(), a.getServiceScopeAr(),
                    a.getEstimatedOnboardingDays(), a.getEstimatedCostSar(),
                    a.getLastAssessedAt(), a.getLastAssessedOutcome(),
                    a.getContractualStatus(),
                    a.getNotesEn(), a.getNotesAr(),
                    a.getStatus(),
                    a.getApprovedAt(), a.getApproverName(),
                    a.getArchivedAt(), a.getArchiveReason(),
                    a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    // ═══ ExitPlan ═════════════════════════════════════════════════════
    public record PlanCreateRequest(
            Long vendorId,
            @NotBlank @Size(max = 255) String vendorName,
            @Size(max = 64) String contractCode,
            @NotBlank @Size(max = 255) String titleEn,
            @Size(max = 255) String titleAr,
            String triggerConditions,
            String severityThreshold,
            Integer targetDisengagementDays,
            @NotBlank String stepsJson,
            String dataMigrationPlanEn,
            String dataMigrationPlanAr,
            String knowledgeTransferPlanEn,
            String knowledgeTransferPlanAr
    ) {}

    public record PlanUpdateRequest(
            String titleEn,
            String titleAr,
            String triggerConditions,
            String severityThreshold,
            Integer targetDisengagementDays,
            String stepsJson,
            String dataMigrationPlanEn,
            String dataMigrationPlanAr,
            String knowledgeTransferPlanEn,
            String knowledgeTransferPlanAr
    ) {}

    public record PlanRejectRequest(@NotBlank @Size(max = 500) String reason) {}
    public record PlanArchiveRequest(@NotBlank @Size(max = 500) String reason) {}
    public record PlanActivateRequest(@NotBlank @Size(max = 500) String reason) {}
    public record PlanTestRequest(
            @NotBlank String outcome,        // PASSED | GAP_IDENTIFIED | FAILED
            LocalDate nextTestDue
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanResponse(
            Long id,
            String externalId,
            String planCode,
            Long vendorId,
            String vendorName,
            String contractCode,
            String titleEn,
            String titleAr,
            String triggerConditions,
            String severityThreshold,
            Integer targetDisengagementDays,
            String stepsJson,
            String dataMigrationPlanEn,
            String dataMigrationPlanAr,
            String knowledgeTransferPlanEn,
            String knowledgeTransferPlanAr,
            LocalDateTime lastTestedAt,
            String lastTestedOutcome,
            LocalDate nextTestDue,
            String status,
            LocalDateTime approvedAt,
            String approverName,
            LocalDateTime activatedAt,
            String activationReason,
            LocalDateTime archivedAt,
            String archiveReason,
            String rejectionReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static PlanResponse from(ExitPlan p) {
            return new PlanResponse(
                    p.getId(), p.getExternalId(), p.getPlanCode(),
                    p.getVendorId(), p.getVendorName(), p.getContractCode(),
                    p.getTitleEn(), p.getTitleAr(),
                    p.getTriggerConditions(), p.getSeverityThreshold(),
                    p.getTargetDisengagementDays(),
                    p.getStepsJson(),
                    p.getDataMigrationPlanEn(), p.getDataMigrationPlanAr(),
                    p.getKnowledgeTransferPlanEn(), p.getKnowledgeTransferPlanAr(),
                    p.getLastTestedAt(), p.getLastTestedOutcome(), p.getNextTestDue(),
                    p.getStatus(),
                    p.getApprovedAt(), p.getApproverName(),
                    p.getActivatedAt(), p.getActivationReason(),
                    p.getArchivedAt(), p.getArchiveReason(),
                    p.getRejectionReason(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
