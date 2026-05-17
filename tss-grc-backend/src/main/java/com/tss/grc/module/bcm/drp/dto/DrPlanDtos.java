package com.tss.grc.module.bcm.drp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.module.bcm.drp.entity.DisasterRecoveryPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class DrPlanDtos {
    private DrPlanDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 255) String titleEn,
            String titleAr,
            String descriptionEn, String descriptionAr,
            String linkedBcpCode, String criticalSystemName, String owner,
            @NotNull Integer rtoHours,
            @NotNull Integer rpoHours,
            Integer mtpdHours,
            @NotBlank @Size(max = 20) String drSiteType,
            String drSiteLocation, String drSiteProvider,
            String recoveryStrategyEn, String recoveryStrategyAr,
            String dependenciesJson,
            String failoverStepsJson, String failbackStepsJson,
            String dataBackupStrategyEn, String dataBackupStrategyAr
    ) {}

    public record UpdateRequest(
            String titleEn, String titleAr,
            String descriptionEn, String descriptionAr,
            String linkedBcpCode, String criticalSystemName, String owner,
            Integer rtoHours, Integer rpoHours, Integer mtpdHours,
            String drSiteType, String drSiteLocation, String drSiteProvider,
            String recoveryStrategyEn, String recoveryStrategyAr,
            String dependenciesJson, String failoverStepsJson, String failbackStepsJson,
            String dataBackupStrategyEn, String dataBackupStrategyAr,
            LocalDate nextTestDue
    ) {}

    public record TestRequest(@NotBlank String outcome,
                              String notesEn,
                              LocalDate nextTestDue) {}
    public record ActivateRequest(@NotBlank @Size(max = 500) String reason) {}
    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {}
    public record ArchiveRequest(@NotBlank @Size(max = 500) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            Long id, String planCode, String externalId,
            String titleEn, String titleAr,
            String descriptionEn, String descriptionAr,
            String linkedBcpCode, String criticalSystemName, String owner,
            Integer rtoHours, Integer rpoHours, Integer mtpdHours,
            String drSiteType, String drSiteLocation, String drSiteProvider,
            String recoveryStrategyEn, String recoveryStrategyAr,
            String dependenciesJson, String failoverStepsJson, String failbackStepsJson,
            String dataBackupStrategyEn, String dataBackupStrategyAr,
            LocalDateTime lastTestedAt, String lastTestedOutcome, String lastTestNotesEn,
            LocalDate nextTestDue,
            String status,
            LocalDateTime approvedAt, String approverName,
            LocalDateTime activatedAt, String activationReason,
            LocalDateTime archivedAt, String archiveReason,
            String rejectionReason,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static Response from(DisasterRecoveryPlan p) {
            return new Response(
                    p.getId(), p.getPlanCode(), p.getExternalId(),
                    p.getTitleEn(), p.getTitleAr(),
                    p.getDescriptionEn(), p.getDescriptionAr(),
                    p.getLinkedBcpCode(), p.getCriticalSystemName(), p.getOwner(),
                    p.getRtoHours(), p.getRpoHours(), p.getMtpdHours(),
                    p.getDrSiteType(), p.getDrSiteLocation(), p.getDrSiteProvider(),
                    p.getRecoveryStrategyEn(), p.getRecoveryStrategyAr(),
                    p.getDependenciesJson(), p.getFailoverStepsJson(), p.getFailbackStepsJson(),
                    p.getDataBackupStrategyEn(), p.getDataBackupStrategyAr(),
                    p.getLastTestedAt(), p.getLastTestedOutcome(), p.getLastTestNotesEn(),
                    p.getNextTestDue(),
                    p.getStatus(),
                    p.getApprovedAt(), p.getApproverName(),
                    p.getActivatedAt(), p.getActivationReason(),
                    p.getArchivedAt(), p.getArchiveReason(),
                    p.getRejectionReason(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
