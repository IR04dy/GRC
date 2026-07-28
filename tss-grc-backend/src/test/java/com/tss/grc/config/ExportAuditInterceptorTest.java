package com.tss.grc.config;

import com.tss.grc.module.platform.export.ExportAuditContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

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

    // ─── F2-41: how much left the building ───────────────────────────────────
    //
    // The audit row recorded that an export happened but not its volume, so it could not answer
    // either question a DLP or PDPL enquiry asks: how much was extracted, and how sensitive was it.

    @AfterEach
    void clearExportContext() {
        ExportAuditContext.clear();
    }

    @Test
    void exportContext_carriesTheGeneratorsRowCount() {
        assertThat(ExportAuditContext.rowCount())
            .as("nothing recorded yet, so the export is uninstrumented — not zero rows")
            .isNull();

        ExportAuditContext.record(1234, 3, "TabularExportService.xlsx");
        ExportAuditContext.Counted c = ExportAuditContext.rowCount();
        assertThat(c).isNotNull();
        assertThat(c.rows()).isEqualTo(1234);
        assertThat(c.sheets()).isEqualTo(3);
        assertThat(c.generator())
            .as("the audit row names WHERE the number came from, so an unexpected count is traceable "
                + "to a generator rather than being an anonymous figure")
            .isEqualTo("TabularExportService.xlsx");
    }

    /**
     * The failure mode that matters more than the feature. Servlet threads are pooled, so a value
     * left behind by one export would be attributed to whatever request ran next on that thread —
     * an audit trail that is confidently wrong about volume, which is worse than one that is silent.
     * The interceptor clears in a {@code finally}; this pins the contract that makes that correct.
     */
    @Test
    void exportContext_isClearedSoOneExportsCountCannotLeakIntoTheNextRequest() {
        ExportAuditContext.record(9_999, 1, "RegisterExport.pdf");
        assertThat(ExportAuditContext.rowCount()).isNotNull();

        ExportAuditContext.clear();

        assertThat(ExportAuditContext.rowCount())
            .as("a leaked count would make the NEXT request's export row claim 9,999 rows")
            .isNull();
        ExportAuditContext.clear();   // idempotent — the interceptor calls it unconditionally
        assertThat(ExportAuditContext.rowCount()).isNull();
    }

    /** A thread-local must not be shared: two concurrent exports must not see each other's count. */
    @Test
    void exportContext_isPerThread() throws Exception {
        ExportAuditContext.record(10, 1, "this-thread");
        AtomicReference<ExportAuditContext.Counted> seen = new AtomicReference<>();
        Thread other = new Thread(() -> {
            seen.set(ExportAuditContext.rowCount());
            ExportAuditContext.record(20, 2, "other-thread");
        });
        other.start();
        other.join();

        assertThat(seen.get()).as("the other thread must not see this thread's count").isNull();
        assertThat(ExportAuditContext.rowCount().rows())
            .as("and the other thread's write must not overwrite ours")
            .isEqualTo(10);
    }

    @Test
    void contentLength_absentOrGarbageIsMinusOneNotZero() throws Exception {
        // Reflection rather than a servlet mock: the point is only that an unparseable or missing
        // Content-Length reports "unknown" (-1, serialised as null) instead of a plausible 0 bytes.
        var m = ExportAuditInterceptor.class.getDeclaredMethod("contentLength",
            jakarta.servlet.http.HttpServletResponse.class);
        m.setAccessible(true);
        assertThat((long) m.invoke(null, stubResponse(null))).isEqualTo(-1L);
        assertThat((long) m.invoke(null, stubResponse("  "))).isEqualTo(-1L);
        assertThat((long) m.invoke(null, stubResponse("not-a-number"))).isEqualTo(-1L);
        assertThat((long) m.invoke(null, stubResponse(" 4096 "))).isEqualTo(4096L);
    }

    /**
     * {@code getHeader} is overridden rather than {@code setHeader} called, because
     * {@code MockHttpServletResponse} parses {@code Content-Length} itself and throws on a
     * non-numeric value — so the mock cannot express the case under test. A real container can:
     * a proxy or a filter is free to write any string into that header, which is exactly why
     * {@code contentLength()} parses defensively instead of trusting it.
     */
    private static jakarta.servlet.http.HttpServletResponse stubResponse(String contentLength) {
        return new org.springframework.mock.web.MockHttpServletResponse() {
            @Override
            public String getHeader(String name) {
                return org.springframework.http.HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)
                    ? contentLength : super.getHeader(name);
            }
        };
    }
}
