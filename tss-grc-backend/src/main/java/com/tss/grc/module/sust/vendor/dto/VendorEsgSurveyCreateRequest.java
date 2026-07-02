package com.tss.grc.module.sust.vendor.dto;

import com.tss.grc.module.sust.vendor.entity.VendorEsgSurvey;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * S-O / S-L — validated create request for a vendor ESG survey. Carries ONLY
 * the caller-settable business fields: the vendor register link, the reporting
 * year, the annual spend, and free notes. Everything else — status, external
 * id, vendor-name snapshot, sent/responded/reviewed stamps, the reported ESG
 * data, the computed {@code esgScore}, the evidence FK, the auto-CAPA link — is
 * server-owned and NOT bindable, so a survey cannot be born RESPONDED/ACCEPTED
 * or carry a forged score. Vendor existence + same-tenant is validated in the
 * service (→ 400 on a dangling id).
 */
public record VendorEsgSurveyCreateRequest(
        @NotNull Long tprmVendorId,
        Long scmSupplierId,
        @NotNull Short reportingYear,
        @PositiveOrZero BigDecimal spendSarK,
        @Size(max = 255) String reviewerName,
        @Size(max = 255) String reviewerEmail,
        String notes
) {
    public VendorEsgSurvey toEntity() {
        VendorEsgSurvey e = new VendorEsgSurvey();
        e.setTprmVendorId(tprmVendorId);
        e.setScmSupplierId(scmSupplierId);
        e.setReportingYear(reportingYear);
        e.setSpendSarK(spendSarK);
        e.setReviewerName(reviewerName);
        e.setReviewerEmail(reviewerEmail);
        e.setNotes(notes);
        return e;
    }
}
