package com.tss.grc.module.platform.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Export P5 — client-supplied tabular data to be rendered as a branded workbook.
 *
 * <p>Some exports (e.g. the regulatory lifecycle packets) hold their data on the
 * client. This lets the frontend POST assembled rows and receive a real, branded
 * {@code .xlsx} (XlsxStyler house treatment + tenant BrandTheme + ExportNaming
 * filename + the export audit trail) rather than a browser-print PDF only.
 *
 * <p>Values are written as literal cell strings (never formulas) so a leading
 * {@code =}/{@code +}/{@code -}/{@code @} cannot inject a formula.
 */
public record TabularExportRequest(
    /** Module code for the filename, e.g. "ESG". */
    @NotBlank @Size(max = 40) String module,
    /** Export type for the filename, e.g. "SustainabilityAnnex". */
    @NotBlank @Size(max = 60) String exportType,
    /** "ar" or "en" — controls RTL sheet direction + the LANG filename segment. */
    @Size(max = 5) String lang,
    /** One or more sheets. */
    @NotEmpty @Size(max = 20) List<Sheet> sheets
) {
    /** A single worksheet: a name, a header row, and data rows. */
    public record Sheet(
        @NotBlank @Size(max = 120) String name,
        List<String> headers,
        @Size(max = 50_000) List<List<String>> rows
    ) {}
}
