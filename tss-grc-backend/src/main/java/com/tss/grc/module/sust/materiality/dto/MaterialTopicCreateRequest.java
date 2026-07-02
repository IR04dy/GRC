package com.tss.grc.module.sust.materiality.dto;

import com.tss.grc.module.sust.materiality.entity.MaterialTopic;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * S-L — validated create request for a material topic. The computed
 * materialityScore / priorityTier, the status machine, and approval columns
 * are server-owned and NOT bindable.
 */
public record MaterialTopicCreateRequest(
        @NotNull Short reportingYear,
        @NotBlank @Size(max = 20) String category,
        @Size(max = 40) String topicCode,
        @NotBlank @Size(max = 255) String topicEn,
        @Size(max = 255) String topicAr,
        String descriptionEn,
        String descriptionAr,
        BigDecimal stakeholderImportance,
        BigDecimal businessImpact,
        String stakeholderGroups,
        @Size(max = 255) String ownerName,
        @Size(max = 255) String ownerEmail,
        @Size(max = 255) String evidenceRef,
        String notes
) {
    public MaterialTopic toEntity() {
        MaterialTopic e = new MaterialTopic();
        e.setReportingYear(reportingYear);
        e.setCategory(category);
        e.setTopicCode(topicCode);
        e.setTopicEn(topicEn);
        e.setTopicAr(topicAr);
        e.setDescriptionEn(descriptionEn);
        e.setDescriptionAr(descriptionAr);
        if (stakeholderImportance != null) e.setStakeholderImportance(stakeholderImportance);
        if (businessImpact != null) e.setBusinessImpact(businessImpact);
        e.setStakeholderGroups(stakeholderGroups);
        e.setOwnerName(ownerName);
        e.setOwnerEmail(ownerEmail);
        e.setEvidenceRef(evidenceRef);
        e.setNotes(notes);
        return e;
    }
}
