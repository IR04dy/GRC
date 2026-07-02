package com.tss.grc.module.sust.annex.dto;

import com.tss.grc.module.sust.annex.entity.ReportingYear;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * S-L — validated create request for a reporting year. Status + approval /
 * publish stamps + computed readiness/gap counts are server-owned and NOT
 * bindable (a year can never be born APPROVED/PUBLISHED).
 */
public record ReportingYearCreateRequest(
        @NotNull Short year,
        LocalDate periodStart,
        LocalDate periodEnd,
        @NotBlank @Size(max = 255) String reportingEntityEn,
        @Size(max = 255) String reportingEntityAr,
        @Size(max = 255) String preparedBy,
        String notes
) {
    public ReportingYear toEntity() {
        ReportingYear e = new ReportingYear();
        e.setYear(year);
        e.setPeriodStart(periodStart);
        e.setPeriodEnd(periodEnd);
        e.setReportingEntityEn(reportingEntityEn);
        e.setReportingEntityAr(reportingEntityAr);
        e.setPreparedBy(preparedBy);
        e.setNotes(notes);
        return e;
    }
}
