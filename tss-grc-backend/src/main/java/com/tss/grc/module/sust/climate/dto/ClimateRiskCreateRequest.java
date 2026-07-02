package com.tss.grc.module.sust.climate.dto;

import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * S-L — validated create request for a climate risk/opportunity. Status,
 * computed riskScore, and the ERM link (set via /link-erm, S-F) are server-owned
 * and NOT bindable. kind/category/likelihood/impact are validated in the service.
 */
public record ClimateRiskCreateRequest(
        @NotBlank @Size(max = 20) String kind,
        @NotBlank @Size(max = 30) String category,
        @NotNull Short reportingYear,
        @NotBlank @Size(max = 255) String titleEn,
        @Size(max = 255) String titleAr,
        String descriptionEn,
        String descriptionAr,
        @Size(max = 10) String timeHorizon,
        Short likelihood,
        Short impact,
        BigDecimal financialImpactSar,
        @Size(max = 255) String relatedService,
        @Size(max = 255) String relatedSupplier,
        @Size(max = 255) String relatedAsset,
        @Size(max = 40) String relatedTcfdRecommendation,
        String controlsSummary,
        String treatmentPlan,
        String metricsSummary,
        @Size(max = 255) String evidenceRef,
        @Size(max = 255) String ownerName,
        @Size(max = 255) String ownerEmail,
        LocalDate reviewDate
) {
    public ClimateRiskOpportunity toEntity() {
        ClimateRiskOpportunity e = new ClimateRiskOpportunity();
        e.setKind(kind);
        e.setCategory(category);
        e.setReportingYear(reportingYear);
        e.setTitleEn(titleEn);
        e.setTitleAr(titleAr);
        e.setDescriptionEn(descriptionEn);
        e.setDescriptionAr(descriptionAr);
        if (timeHorizon != null) e.setTimeHorizon(timeHorizon);
        if (likelihood != null) e.setLikelihood(likelihood);
        if (impact != null) e.setImpact(impact);
        e.setFinancialImpactSar(financialImpactSar);
        e.setRelatedService(relatedService);
        e.setRelatedSupplier(relatedSupplier);
        e.setRelatedAsset(relatedAsset);
        e.setRelatedTcfdRecommendation(relatedTcfdRecommendation);
        e.setControlsSummary(controlsSummary);
        e.setTreatmentPlan(treatmentPlan);
        e.setMetricsSummary(metricsSummary);
        e.setEvidenceRef(evidenceRef);
        e.setOwnerName(ownerName);
        e.setOwnerEmail(ownerEmail);
        e.setReviewDate(reviewDate);
        return e;
    }
}
