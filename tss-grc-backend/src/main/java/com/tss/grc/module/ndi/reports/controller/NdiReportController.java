package com.tss.grc.module.ndi.reports.controller;

import com.tss.grc.module.ndi.reports.entity.NdiReportDefinition;
import com.tss.grc.module.ndi.reports.service.NdiReportExporter;
import com.tss.grc.module.ndi.reports.service.NdiReportService;
import com.tss.grc.module.ndi.reports.service.NdiReportService.ReportResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/ndi/reports")
@RequiredArgsConstructor
@Tag(name = "NDI — Reports")
public class NdiReportController {

    // SEC-NDI-001: granular module authorization (was isAuthenticated() only).
    private static final String READ  = "hasPermission('NDI','READ')";
    private static final String WRITE = "hasPermission('NDI','WRITE')";
    private static final String APPROVE = "hasPermission('NDI','APPROVE') or hasRole('PLATFORM_ADMIN')";
    /**
     * F2-27 — running a report and streaming it as a file is an extraction, not a view.
     * A separate constant rather than a lift of {@link #READ}, because READ still guards the
     * JSON list/detail endpoints in this controller. Access-preserving: every role holding
     * NDI:READ also holds NDI:EXPORT (32 of 58, measured — zero gap).
     */
    private static final String EXPORT = "hasPermission('NDI','EXPORT')";

    private final NdiReportService service;
    private final NdiReportExporter exporter;

    @GetMapping
    @PreAuthorize(READ)
    public List<NdiReportDefinition> list(@RequestParam(required = false) String type) {
        return service.list(type);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public NdiReportDefinition get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<NdiReportDefinition> create(@RequestBody NdiReportDefinition body) {
        return ResponseEntity.ok(service.create(body));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE)
    public NdiReportDefinition update(@PathVariable Long id, @RequestBody NdiReportDefinition body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(APPROVE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    @PreAuthorize(READ)
    public ReportResult run(@PathVariable Long id, @RequestParam(required = false) Long cycleId) {
        return service.run(id, cycleId);
    }

    @PostMapping("/run-adhoc")
    @PreAuthorize(READ)
    public ReportResult runAdHoc(@RequestParam String type, @RequestParam(required = false) Long cycleId) {
        return service.runAdHoc(type, cycleId);
    }

    /** Run a saved report and stream the result as the requested binary format. */
    @GetMapping("/{id}/export")
    @PreAuthorize(EXPORT)
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(defaultValue = "XLSX") String format,
                                         @RequestParam(defaultValue = "en") String lang,
                                         @RequestParam(required = false) Long cycleId) throws IOException {
        ReportResult r = service.run(id, cycleId);
        return streamBytes(r, format, lang);
    }

    /** One-shot ad-hoc export — no saved definition required. */
    @GetMapping("/export-adhoc")
    @PreAuthorize(EXPORT)
    public ResponseEntity<byte[]> exportAdHoc(@RequestParam String type,
                                              @RequestParam(defaultValue = "XLSX") String format,
                                              @RequestParam(defaultValue = "en") String lang,
                                              @RequestParam(required = false) Long cycleId) throws IOException {
        ReportResult r = service.runAdHoc(type, cycleId);
        return streamBytes(r, format, lang);
    }

    private ResponseEntity<byte[]> streamBytes(ReportResult r, String format, String lang) throws IOException {
        byte[] body;
        MediaType ct;
        String ext;
        switch (format.toUpperCase()) {
            case "PDF"  -> { body = exporter.toPdf(r, lang);  ct = MediaType.APPLICATION_PDF; ext = "pdf"; }
            case "CSV"  -> { body = exporter.toCsv(r);  ct = MediaType.parseMediaType("text/csv;charset=utf-8"); ext = "csv"; }
            case "XLSX" -> { body = exporter.toXlsx(r); ct = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); ext = "xlsx"; }
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        }
        String filename = "ndi-" + r.reportType().toLowerCase().replace('_', '-')
            + (r.cycleId() == null ? "" : "-cycle" + r.cycleId()) + "." + ext;
        return ResponseEntity.ok()
            .contentType(ct)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(body);
    }
}
