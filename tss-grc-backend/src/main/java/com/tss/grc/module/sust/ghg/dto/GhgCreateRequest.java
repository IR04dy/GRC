package com.tss.grc.module.sust.ghg.dto;

import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * S-L — validated create request for a GHG emission record. Carries only the
 * caller-settable business fields: workflow / verification / value-source /
 * audit columns are NOT bindable here (closes mass-assignment — a record can
 * never be born VERIFIED/APPROVED or with a forged value source). The service's
 * hand-rolled {@code validate()} remains the second line of defence for ranges.
 */
public record GhgCreateRequest(
        @NotNull Short reportingYear,
        @NotNull Short scope,
        @NotBlank @Size(max = 60) String category,
        /** V295 — GHG Protocol Scope-3 category (1–15); only valid when scope = 3. */
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(15) Short scope3Category,
        @NotBlank @Size(max = 255) String sourceNameEn,
        @Size(max = 255) String sourceNameAr,
        @Size(max = 120) String businessUnit,
        @Size(max = 120) String facility,
        BigDecimal activityData,
        @Size(max = 40) String activityUnit,
        BigDecimal emissionFactor,
        @Size(max = 40) String emissionFactorUnit,
        Long emissionFactorId,
        BigDecimal emissionValueTco2e,
        BigDecimal coveragePercent,
        @Size(max = 120) String verificationProvider,
        @Size(max = 120) String methodology,
        String notes,
        @Size(max = 255) String evidenceRef,
        @Size(max = 255) String ownerName,
        @Size(max = 255) String ownerEmail
) {
    public GhgEmissionRecord toEntity() {
        GhgEmissionRecord e = new GhgEmissionRecord();
        e.setReportingYear(reportingYear);
        e.setScope(scope);
        e.setCategory(category);
        e.setScope3Category(scope3Category);
        e.setSourceNameEn(sourceNameEn);
        e.setSourceNameAr(sourceNameAr);
        e.setBusinessUnit(businessUnit);
        e.setFacility(facility);
        e.setActivityData(activityData);
        e.setActivityUnit(activityUnit);
        e.setEmissionFactor(emissionFactor);
        e.setEmissionFactorUnit(emissionFactorUnit);
        e.setEmissionFactorId(emissionFactorId);
        if (emissionValueTco2e != null) e.setEmissionValueTco2e(emissionValueTco2e);
        if (coveragePercent != null) e.setCoveragePercent(coveragePercent);
        e.setVerificationProvider(verificationProvider);
        e.setMethodology(methodology);
        e.setNotes(notes);
        e.setEvidenceRef(evidenceRef);
        e.setOwnerName(ownerName);
        e.setOwnerEmail(ownerEmail);
        return e;
    }
}
