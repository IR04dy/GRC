package com.tss.grc.module.sust.climate.controller;

import com.tss.grc.module.sust.climate.dto.ClimateDtos.AnnualClimateReport;
import com.tss.grc.module.sust.climate.entity.ClimateReport;
import com.tss.grc.module.sust.climate.service.ClimateReportExportService;
import com.tss.grc.module.sust.climate.service.ClimateReportService;
import com.tss.grc.module.sust.climate.service.TcfdReportService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Annual IFRS S2 / TCFD disclosure pack — Sprint 11, completed under S-G.
 *
 * <pre>
 *   GET  /annual?year           live-assembled complete pack (read-only)
 *   POST /generate?year         persist a DRAFT snapshot (versioned)
 *   GET  /                       list persisted reports
 *   GET  /{id}                   one persisted report
 *   POST /{id}/submit            DRAFT → IN_REVIEW
 *   POST /{id}/approve           IN_REVIEW → APPROVED (SoD + SHA-256 lock)
 *   POST /{id}/publish           APPROVED → PUBLISHED
 *   GET  /{id}/export.pdf?lang   OpenPDF (Arabic-capable) of the locked snapshot
 *   GET  /{id}/export.xlsx       Excel (POI) of the locked snapshot
 * </pre>
 */
@RestController
// S-M — canonical /api/v1/sustainability/*; legacy /api/v1/climate/* kept as alias.
@RequestMapping({"/api/v1/sustainability/report", "/api/v1/climate/report"})
@RequiredArgsConstructor
@Tag(name = "Annual Climate Report (IFRS S2 / TCFD)")
@SecurityRequirement(name = "bearerAuth")
public class ClimateReportController {

    private static final String READ_GUARD  = "hasPermission('SUST','READ')";
    private static final String WRITE_GUARD = "hasPermission('SUST','WRITE')";

    private final TcfdReportService service;
    private final ClimateReportService reportService;
    private final ClimateReportExportService exportService;

    @GetMapping("/annual")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Live-assembled complete IFRS S2 / TCFD pack for the reporting year.")
    public AnnualClimateReport annual(@RequestParam Integer year) {
        return service.generateAnnualReportData(year);
    }

    @PostMapping("/generate")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Generate + persist a DRAFT snapshot of the pack (versioned).")
    public ResponseEntity<ClimateReport> generate(@RequestParam Integer year,
                                                  @AuthenticationPrincipal GrcUserDetails me) {
        return ResponseEntity.status(201).body(reportService.generate(year, me));
    }

    @GetMapping
    @PreAuthorize(READ_GUARD)
    public List<ClimateReport> list() { return reportService.list(); }

    @GetMapping("/{id}")
    @PreAuthorize(READ_GUARD)
    public ClimateReport get(@PathVariable Long id) { return reportService.get(id); }

    @PostMapping("/{id}/submit")
    @PreAuthorize(WRITE_GUARD)
    public ClimateReport submit(@PathVariable Long id) { return reportService.submit(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Approve + content-lock (SHA-256); separation of duties — approver ≠ generator.")
    public ClimateReport approve(@PathVariable Long id, @AuthenticationPrincipal GrcUserDetails me) {
        return reportService.approve(id, me);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize(WRITE_GUARD)
    public ClimateReport publish(@PathVariable Long id) { return reportService.publish(id); }

    @GetMapping("/{id}/export.pdf")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Export the locked snapshot as an Arabic-capable PDF (OpenPDF).")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id,
                                            @RequestParam(defaultValue = "en") String lang) {
        ClimateReport r = reportService.get(id);
        byte[] pdf = exportService.pdf(r, "ar".equalsIgnoreCase(lang));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + r.getReportCode() + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/{id}/export.xlsx")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Export the locked snapshot as an Excel workbook (POI).")
    public ResponseEntity<byte[]> exportXlsx(@PathVariable Long id) {
        ClimateReport r = reportService.get(id);
        byte[] xlsx = exportService.xlsx(r);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + r.getReportCode() + ".xlsx\"")
                .body(xlsx);
    }
}
