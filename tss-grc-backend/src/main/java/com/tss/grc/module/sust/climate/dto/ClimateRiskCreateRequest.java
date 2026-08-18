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
    /**
     * The create-path defaults for the three axes, F2-240.
     *
     * <p>Named rather than inlined so the test that pins them, and any future second create path,
     * read the same values instead of a second copy of the numbers.
     */
    static final String DEFAULT_TIME_HORIZON = "MEDIUM";
    static final short DEFAULT_LIKELIHOOD = 3;
    static final short DEFAULT_IMPACT = 3;

    public ClimateRiskOpportunity toEntity() {
        ClimateRiskOpportunity e = new ClimateRiskOpportunity();
        e.setKind(kind);
        e.setCategory(category);
        e.setReportingYear(reportingYear);
        e.setTitleEn(titleEn);
        e.setTitleAr(titleAr);
        e.setDescriptionEn(descriptionEn);
        e.setDescriptionAr(descriptionAr);
        // F2-240 — this create path OWNS these three defaults, because the entity no longer
        // initialises them. It used to: the initialiser served the create and simultaneously made
        // `ClimateRiskOpportunityService#update`'s `!= null` guard un-fireable, so a PUT that
        // omitted an axis reset a TCFD / IFRS S2 assessment's rating (12, 10 and 7 of 17 live rows
        // in dev and prod carry a value these defaults would overwrite). All three columns are
        // NOT NULL, so an omitted default here is a 500 on create, not a null.
        e.setTimeHorizon(timeHorizon != null ? timeHorizon : DEFAULT_TIME_HORIZON);
        e.setLikelihood(likelihood != null ? likelihood : DEFAULT_LIKELIHOOD);
        e.setImpact(impact != null ? impact : DEFAULT_IMPACT);
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
