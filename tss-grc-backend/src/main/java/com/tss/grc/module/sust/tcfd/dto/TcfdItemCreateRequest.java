package com.tss.grc.module.sust.tcfd.dto;

import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * S-L — validated create request for a TCFD alignment item. Approval columns
 * ({@code approvalStatus}, {@code approvedBy/at}) + {@code linkedCapaId} are
 * server-owned and NOT bindable. {@code relatedClimateRiskId} is validated by
 * the service (S-F). Pillar/status are validated in the service.
 */
public record TcfdItemCreateRequest(
        @NotNull Short reportingYear,
        @NotBlank @Size(max = 20) String pillar,
        @NotBlank @Size(max = 5) String recommendationCode,
        @NotBlank @Size(max = 255) String titleEn,
        @Size(max = 255) String titleAr,
        String recommendationEn,
        String recommendationAr,
        @Size(max = 30) String disclosureStatus,
        @Size(max = 255) String evidenceRef,
        @Size(max = 255) String evidenceOwner,
        String gapDescriptionEn,
        String gapDescriptionAr,
        Long relatedClimateRiskId,
        Long relatedMetricId,
        @Size(max = 255) String ownerName,
        @Size(max = 255) String ownerEmail,
        @Size(max = 255) String reviewerName,
        String notes
) {
    public TcfdItem toEntity() {
        TcfdItem e = new TcfdItem();
        e.setReportingYear(reportingYear);
        e.setPillar(pillar);
        e.setRecommendationCode(recommendationCode);
        e.setTitleEn(titleEn);
        e.setTitleAr(titleAr);
        e.setRecommendationEn(recommendationEn);
        e.setRecommendationAr(recommendationAr);
        if (disclosureStatus != null) e.setDisclosureStatus(disclosureStatus);
        e.setEvidenceRef(evidenceRef);
        e.setEvidenceOwner(evidenceOwner);
        e.setGapDescriptionEn(gapDescriptionEn);
        e.setGapDescriptionAr(gapDescriptionAr);
        e.setRelatedClimateRiskId(relatedClimateRiskId);
        e.setRelatedMetricId(relatedMetricId);
        e.setOwnerName(ownerName);
        e.setOwnerEmail(ownerEmail);
        e.setReviewerName(reviewerName);
        e.setNotes(notes);
        return e;
    }
}
