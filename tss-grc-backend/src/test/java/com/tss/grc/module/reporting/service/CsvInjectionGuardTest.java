package com.tss.grc.module.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.ndi.reports.service.NdiReportExporter;
import com.tss.grc.module.ndi.reports.service.NdiReportService.ReportResult;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.sust.annex.entity.SustainabilityReport;
import com.tss.grc.module.sust.annex.service.AnnexExportService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Export P0-4 — CSV/spreadsheet formula-injection guard on the two exporters that previously
 * only quote-escaped (NDI report CSV, sustainability annex CSV). A cell whose value begins with
 * {@code = + - @} (or tab/CR) must be neutralised with a leading apostrophe so a regulator who
 * opens the file in Excel/Sheets sees literal text, never an executed formula (OWASP).
 * Mirrors the guard already shipped in {@code BcmEvidencePackageService.csv}.
 */
class CsvInjectionGuardTest {

    @Test
    void ndiCsv_neutralisesFormulaLeadingCells() {
        NdiReportExporter exporter = new NdiReportExporter(mock(OrganisationRepository.class));
        ReportResult result = new ReportResult("executive-summary", 1L, null, Map.of(),
            List.of(
                Map.of("indicator", "=SUM(A1)"),       // formula
                Map.of("indicator", "+1234567890"),     // phone-as-formula
                Map.of("indicator", "@cmd"),            // DDE
                Map.of("indicator", "Normal value")     // benign — must NOT be prefixed
            ));
        String csv = new String(exporter.toCsv(result), StandardCharsets.UTF_8);

        assertThat(csv).contains("'=SUM(A1)");
        assertThat(csv).contains("'+1234567890");
        assertThat(csv).contains("'@cmd");
        assertThat(csv).contains("Normal value");
        assertThat(csv).doesNotContain("'Normal value");
        // the raw formula must never appear un-prefixed at a cell boundary
        assertThat(csv).doesNotContain(",=SUM(A1)").doesNotContain("\n=SUM(A1)");
    }

    @Test
    void annexCsv_neutralisesFormulaLeadingCells() {
        AnnexExportService annex = new AnnexExportService(new ObjectMapper(), mock(OrganisationRepository.class));
        SustainabilityReport r = new SustainabilityReport();
        r.setReportCode("=cmd|'/c calc'!A1");   // classic CSV-injection payload in the report code
        String csv = annex.csv(r);

        assertThat(csv).contains("'=cmd");
        assertThat(csv).doesNotContain(",=cmd");   // never an un-prefixed formula cell
    }
}
