package com.tss.grc.module.sust.capa.dto;

import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * S-L — validated create request for a corrective action. Status + verification
 * columns (started/completed/verified/closed) are server-owned and NOT bindable.
 * sourceType / priority are validated in the service.
 */
public record CorrectiveActionCreateRequest(
        @NotBlank @Size(max = 30) String sourceType,
        @Size(max = 64) String sourceRef,
        @Size(max = 20) String actionType,
        @NotBlank @Size(max = 255) String titleEn,
        @Size(max = 255) String titleAr,
        String descriptionEn,
        String descriptionAr,
        @Size(max = 20) String priority,
        @Size(max = 255) String ownerName,
        @Size(max = 255) String ownerEmail,
        @Size(max = 255) String assignedTo,
        LocalDate targetDate,
        String rootCauseEn,
        String rootCauseAr,
        String remediationPlanEn,
        String remediationPlanAr,
        @Size(max = 255) String evidenceRef,
        BigDecimal costEstimateSar,
        Integer effortHours,
        Long linkedRiskId,
        String notes
) {
    public CorrectiveAction toEntity() {
        CorrectiveAction e = new CorrectiveAction();
        e.setSourceType(sourceType);
        e.setSourceRef(sourceRef);
        if (actionType != null && !actionType.isBlank()) e.setActionType(actionType);
        e.setTitleEn(titleEn);
        e.setTitleAr(titleAr);
        e.setDescriptionEn(descriptionEn);
        e.setDescriptionAr(descriptionAr);
        if (priority != null) e.setPriority(priority);
        e.setOwnerName(ownerName);
        e.setOwnerEmail(ownerEmail);
        e.setAssignedTo(assignedTo);
        e.setTargetDate(targetDate);
        e.setRootCauseEn(rootCauseEn);
        e.setRootCauseAr(rootCauseAr);
        e.setRemediationPlanEn(remediationPlanEn);
        e.setRemediationPlanAr(remediationPlanAr);
        e.setEvidenceRef(evidenceRef);
        e.setCostEstimateSar(costEstimateSar);
        e.setEffortHours(effortHours);
        e.setLinkedRiskId(linkedRiskId);
        e.setNotes(notes);
        return e;
    }
}
