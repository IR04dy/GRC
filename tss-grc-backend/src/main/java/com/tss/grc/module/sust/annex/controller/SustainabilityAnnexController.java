package com.tss.grc.module.sust.annex.controller;

import com.tss.grc.module.sust.annex.entity.ReportingYear;
import com.tss.grc.module.sust.annex.entity.SustainabilityReport;
import com.tss.grc.module.sust.annex.service.SustainabilityReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Sustainability Annex + Reports REST endpoints.
 *
 * <p>Two namespaces under {@code /api/sustainability}:
 * <ul>
 *   <li>{@code /reporting-years/*} — the parent year lifecycle</li>
 *   <li>{@code /reports/*}         — generated annex / board / matrix /
 *                                     TCFD / GHG / SGI report instances</li>
 * </ul>
 *
 * <p>Export endpoints stream the persisted content snapshot with a
 * Content-Disposition: attachment header so the browser triggers a save.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sustainability — Annex & Reports")
public class SustainabilityAnnexController {

    private final SustainabilityReportService service;

    // ── Reporting years ──────────────────────────────────────────────
    @GetMapping("/api/sustainability/reporting-years")
    @PreAuthorize("hasPermission('SUST','READ')")
    public List<ReportingYear> listYears() { return service.listYears(); }

    @GetMapping("/api/sustainability/reporting-years/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public ReportingYear getYear(@PathVariable Long id) { return service.getYear(id); }

    @PostMapping("/api/sustainability/reporting-years")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<ReportingYear> createYear(@RequestBody ReportingYear req) {
        return ResponseEntity.status(201).body(service.createYear(req));
    }

    @PostMapping("/api/sustainability/reporting-years/{id}/advance")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ReportingYear advanceYear(@PathVariable Long id, @RequestParam String to) {
        return service.transitionYear(id, to);
    }

    // ── Reports ──────────────────────────────────────────────────────
    @GetMapping("/api/sustainability/reports")
    @PreAuthorize("hasPermission('SUST','READ')")
    public Page<SustainabilityReport> listReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listReports(page, Math.min(size, 200));
    }

    @GetMapping("/api/sustainability/reporting-years/{ryId}/reports")
    @PreAuthorize("hasPermission('SUST','READ')")
    public List<SustainabilityReport> reportsForYear(@PathVariable Long ryId) {
        return service.listReportsForYear(ryId);
    }

    @GetMapping("/api/sustainability/reports/{id}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public SustainabilityReport getReport(@PathVariable Long id) { return service.getReport(id); }

    /**
     * Generate (or re-generate) an Annual Annex draft for the given
     * reporting year. Returns the freshly-persisted DRAFT row including
     * the generated content snapshots.
     */
    @PostMapping("/api/sustainability/reporting-years/{ryId}/generate-annex")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<SustainabilityReport> generateAnnex(@PathVariable Long ryId) {
        return ResponseEntity.status(201).body(service.generateAnnex(ryId));
    }

    /**
     * Generic report generator (slice 12). Body: {@code { "type": "..." }}
     * where type is one of ANNUAL_ANNEX / BOARD_ESG / CMA_MATRIX /
     * TCFD_REPORT / GHG_REPORT / SGI_PROGRESS / CLIMATE_RISK /
     * CAPA_STATUS / EVIDENCE_PKG / DASH_EXPORT.
     */
    @PostMapping("/api/sustainability/reporting-years/{ryId}/generate")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public ResponseEntity<SustainabilityReport> generateReport(
            @PathVariable Long ryId,
            @RequestBody java.util.Map<String, String> body) {
        String type = body == null ? null : body.get("type");
        if (type == null || type.isBlank()) type = "ANNUAL_ANNEX";
        return ResponseEntity.status(201).body(service.generateReport(ryId, type));
    }

    @PostMapping("/api/sustainability/reports/{id}/submit")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public SustainabilityReport submit(@PathVariable Long id) { return service.transitionReport(id, "SUBMITTED"); }

    @PostMapping("/api/sustainability/reports/{id}/approve")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public SustainabilityReport approve(@PathVariable Long id) { return service.transitionReport(id, "APPROVED"); }

    @PostMapping("/api/sustainability/reports/{id}/publish")
    @PreAuthorize("hasPermission('SUST','WRITE')")
    public SustainabilityReport publish(@PathVariable Long id) { return service.transitionReport(id, "PUBLISHED"); }

    /**
     * Export endpoint — streams the chosen content snapshot with an
     * appropriate Content-Type + Content-Disposition. Supported formats:
     * {@code md} | {@code html} | {@code json}.
     */
    @GetMapping("/api/sustainability/reports/{id}/export/{format}")
    @PreAuthorize("hasPermission('SUST','READ')")
    public ResponseEntity<String> export(@PathVariable Long id, @PathVariable String format) {
        SustainabilityReport r = service.getReport(id);
        String body; MediaType mt; String ext;
        switch (format.toLowerCase()) {
            case "md", "markdown" -> { body = r.getContentMarkdown(); mt = MediaType.TEXT_PLAIN;   ext = "md";   }
            case "html"           -> { body = r.getContentHtml();     mt = MediaType.TEXT_HTML;    ext = "html"; }
            case "json"           -> { body = r.getContentJson();     mt = MediaType.APPLICATION_JSON; ext = "json"; }
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        }
        if (body == null || body.isEmpty()) body = "(empty)";
        String filename = (r.getReportCode() == null ? ("report-" + r.getId()) : r.getReportCode()) + "." + ext;
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(mt)
            .body(body);
    }
}
