package com.tss.grc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export P2 — audit interceptor metadata extraction (module / type / format / filename) from the
 * request path + Content-Disposition. Pure helpers; the end-to-end capture is verified live.
 */
class ExportAuditInterceptorTest {

    @Test
    void parseFilename_quotedAndBare() {
        assertThat(ExportAuditInterceptor.parseFilename("attachment; filename=\"ERM_RiskRegister_AR_20260708_v1.pdf\""))
            .isEqualTo("ERM_RiskRegister_AR_20260708_v1.pdf");
        assertThat(ExportAuditInterceptor.parseFilename("attachment; filename=cyber-incidents.xlsx"))
            .isEqualTo("cyber-incidents.xlsx");
        assertThat(ExportAuditInterceptor.parseFilename("attachment")).isEmpty();
    }

    @Test
    void ext_lowercasesAndStrips() {
        assertThat(ExportAuditInterceptor.ext("report.PDF")).isEqualTo("pdf");
        assertThat(ExportAuditInterceptor.ext("a.b.xlsx")).isEqualTo("xlsx");
        assertThat(ExportAuditInterceptor.ext("noext")).isEqualTo("file");
    }

    @Test
    void moduleFromPath_mapsSegmentSkippingVersion() {
        assertThat(ExportAuditInterceptor.moduleFromPath("/api/v1/reporting/risk-register.pdf")).isEqualTo("REPORTING");
        assertThat(ExportAuditInterceptor.moduleFromPath("/api/v1/cyber/export")).isEqualTo("CYBER");
        assertThat(ExportAuditInterceptor.moduleFromPath("/api/ndi/reports/5/export")).isEqualTo("NDI");
        assertThat(ExportAuditInterceptor.moduleFromPath("/api/v1/sustainability/report/3/export.pdf")).isEqualTo("SUST");
        assertThat(ExportAuditInterceptor.moduleFromPath("/api/v1/bcm/cycles/3/evidence-package")).isEqualTo("BCM");
    }

    @Test
    void typeFromPath_lastSegmentElseFileBaseForGenericSegments() {
        assertThat(ExportAuditInterceptor.typeFromPath("/api/v1/reporting/risk-register.pdf", "x.pdf"))
            .isEqualTo("risk-register");
        // generic "export" segment → fall back to the file base name
        assertThat(ExportAuditInterceptor.typeFromPath("/api/v1/cyber/export", "cyber-incidents.xlsx"))
            .isEqualTo("cyber-incidents");
        // numeric id segment (…/binders/42/download) → file base
        assertThat(ExportAuditInterceptor.typeFromPath("/api/v1/bcm/binders/42/download", "BCP-EXP-2026.pdf"))
            .isEqualTo("BCP-EXP-2026");
    }
}
