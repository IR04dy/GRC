package com.tss.grc.module.sust.metrics.dto;

import com.tss.grc.module.sust.metrics.entity.EsgMetric;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * S-L — validated create request for an ESG metric. Status is server-owned and
 * NOT bindable; the evidence FK is set via the S-C bridge, not on create.
 */
public record EsgMetricCreateRequest(
        @NotNull Short reportingYear,
        @NotBlank @Size(max = 20) String category,
        @Size(max = 40) String subcategory,
        @Size(max = 40) String metricCode,
        @NotBlank @Size(max = 255) String metricNameEn,
        @Size(max = 255) String metricNameAr,
        String descriptionEn,
        String descriptionAr,
        @NotBlank @Size(max = 40) String unit,
        BigDecimal baselineValue,
        Short baselineYear,
        BigDecimal currentValue,
        LocalDate currentValueAsOf,
        BigDecimal targetValue,
        Short targetYear,
        @Size(max = 255) String ownerName,
        @Size(max = 255) String ownerEmail,
        @Size(max = 120) String responsibleDepartment,
        Long linkedSgiCommitmentId,
        @Size(max = 255) String evidenceRef,
        String notes
) {
    public EsgMetric toEntity() {
        EsgMetric e = new EsgMetric();
        e.setReportingYear(reportingYear);
        e.setCategory(category);
        e.setSubcategory(subcategory);
        e.setMetricCode(metricCode);
        e.setMetricNameEn(metricNameEn);
        e.setMetricNameAr(metricNameAr);
        e.setDescriptionEn(descriptionEn);
        e.setDescriptionAr(descriptionAr);
        e.setUnit(unit);
        e.setBaselineValue(baselineValue);
        e.setBaselineYear(baselineYear);
        e.setCurrentValue(currentValue);
        e.setCurrentValueAsOf(currentValueAsOf);
        e.setTargetValue(targetValue);
        e.setTargetYear(targetYear);
        e.setOwnerName(ownerName);
        e.setOwnerEmail(ownerEmail);
        e.setResponsibleDepartment(responsibleDepartment);
        e.setLinkedSgiCommitmentId(linkedSgiCommitmentId);
        e.setEvidenceRef(evidenceRef);
        e.setNotes(notes);
        return e;
    }
}
