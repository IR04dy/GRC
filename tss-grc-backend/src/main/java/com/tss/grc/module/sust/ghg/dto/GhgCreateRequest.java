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
    /**
     * The create-path default for coverage, F2-240 — a record whose coverage the submitter did not
     * state is taken as fully covering its source, which is what the entity's initialiser meant.
     */
    static final BigDecimal DEFAULT_COVERAGE_PERCENT = new BigDecimal("100.00");

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
        // F2-240 — this create path OWNS the coverage default, because the entity no longer
        // initialises it. That initialiser was `new BigDecimal("100.00")`, and it made
        // `GhgEmissionRecordService#update`'s `!= null` guard un-fireable — so a PUT omitting the
        // key raised an ISAE 3410 assurance input to FULL coverage (8 of 18 live dev rows and 7 of
        // 17 in prod sit between 48% and 89%). A reset that MANUFACTURES an assurance is the
        // sharpest member of this family: it does not lose a fact, it asserts one.
        e.setCoveragePercent(coveragePercent != null ? coveragePercent : DEFAULT_COVERAGE_PERCENT);
        e.setVerificationProvider(verificationProvider);
        e.setMethodology(methodology);
        e.setNotes(notes);
        e.setEvidenceRef(evidenceRef);
        e.setOwnerName(ownerName);
        e.setOwnerEmail(ownerEmail);
        return e;
    }
}
