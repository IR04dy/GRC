package com.tss.grc.module.reporting.controller;

import com.tss.grc.module.reporting.service.BoardPackPptxService;
import com.tss.grc.module.reporting.service.BoardPackService;
import com.tss.grc.module.reporting.service.DocxExportService;
import com.tss.grc.module.reporting.service.ExportNaming;
import com.tss.grc.module.reporting.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Bilingual PDF (Q1) + Excel-with-formulas (Q2) export endpoints.
 *
 * Pass ?lang=ar to switch PDF output to Arabic + RTL. Excel output is
 * inherently bilingual — both EN and AR title columns are emitted.
 */
@RestController
@RequestMapping("/api/v1/reporting")
@RequiredArgsConstructor
@Tag(name = "Reporting — PDF & Excel exports")
@SecurityRequirement(name = "bearerAuth")
public class ExportController {

    private final ExportService exportService;
    private final BoardPackService boardPackService;
    private final BoardPackPptxService boardPackPptxService;
    private final DocxExportService docxExportService;

    private static final MediaType XLSX_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType PPTX_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
    private static final MediaType DOCX_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    // ── PDF (Q1) ──────────────────────────────────────────────────────────

    @GetMapping("/risk-register.pdf")
    @PreAuthorize("hasPermission('RISK','READ')")
    @Operation(summary = "Risk register PDF — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> riskRegisterPdf(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = exportService.riskRegisterPdf(lang);
        return pdf(body, ExportNaming.build("ERM", "RiskRegister", lang, "pdf"));
    }

    @GetMapping("/findings-aging.pdf")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    public ResponseEntity<byte[]> findingsAgingPdf(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = exportService.findingsAgingPdf(lang);
        return pdf(body, ExportNaming.build("Compliance", "FindingsAging", lang, "pdf"));
    }

    @GetMapping("/kri-dashboard.pdf")
    @PreAuthorize("hasPermission('RISK','READ')")
    public ResponseEntity<byte[]> kriDashboardPdf(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = exportService.kriDashboardPdf(lang);
        return pdf(body, ExportNaming.build("ERM", "KRIDashboard", lang, "pdf"));
    }

    // ── Excel (Q2) ────────────────────────────────────────────────────────

    @GetMapping("/risk-register.xlsx")
    @PreAuthorize("hasPermission('RISK','READ')")
    @Operation(summary = "Risk register XLSX with live residual-score formulas")
    public ResponseEntity<byte[]> riskRegisterXlsx() {
        return xlsx(exportService.riskRegisterXlsx(), ExportNaming.build("ERM", "RiskRegister", "en", "xlsx"));
    }

    @GetMapping("/findings-aging.xlsx")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "Findings aging XLSX with live TODAY()-based days-open formula")
    public ResponseEntity<byte[]> findingsXlsx() {
        return xlsx(exportService.findingsXlsx(), ExportNaming.build("Compliance", "FindingsAging", "en", "xlsx"));
    }

    @GetMapping("/kri-dashboard.xlsx")
    @PreAuthorize("hasPermission('RISK','READ')")
    @Operation(summary = "KRI dashboard XLSX with COUNTIF summary sheet")
    public ResponseEntity<byte[]> kriXlsx() {
        return xlsx(exportService.kriDashboardXlsx(), ExportNaming.build("ERM", "KRIDashboard", "en", "xlsx"));
    }

    // ── Word (.docx) ──────────────────────────────────────────────────────

    @GetMapping("/risk-register.docx")
    @PreAuthorize("hasPermission('RISK','READ')")
    @Operation(summary = "Risk register DOCX — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> riskRegisterDocx(@RequestParam(defaultValue = "en") String lang) {
        return docx(docxExportService.riskRegisterDocx(lang),
                ExportNaming.build("ERM", "RiskRegister", lang, "docx"));
    }

    @GetMapping("/findings-aging.docx")
    @PreAuthorize("hasPermission('COMPLIANCE','READ')")
    @Operation(summary = "Findings aging DOCX — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> findingsAgingDocx(@RequestParam(defaultValue = "en") String lang) {
        return docx(docxExportService.findingsAgingDocx(lang),
                ExportNaming.build("Compliance", "FindingsAging", lang, "docx"));
    }

    @GetMapping("/kri-dashboard.docx")
    @PreAuthorize("hasPermission('RISK','READ')")
    @Operation(summary = "KRI dashboard DOCX — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> kriDashboardDocx(@RequestParam(defaultValue = "en") String lang) {
        return docx(docxExportService.kriDashboardDocx(lang),
                ExportNaming.build("ERM", "KRIDashboard", lang, "docx"));
    }

    // ── Board pack (S1) ───────────────────────────────────────────────────

    @GetMapping("/board-pack.pdf")
    @PreAuthorize("hasPermission('REPORTING','READ') or hasPermission('RISK','READ')")
    @Operation(summary = "Multi-section board pack PDF combining risks, findings, KRIs, " +
        "framework coverage, change management and ethics highlights. Pass ?lang=ar for RTL.")
    public ResponseEntity<byte[]> boardPackPdf(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = boardPackService.generatePdf(lang);
        return pdf(body, ExportNaming.build("BoardPack", "BoardPack", lang, "pdf"));
    }

    @GetMapping("/board-pack.pptx")
    @PreAuthorize("hasPermission('REPORTING','READ') or hasPermission('RISK','READ')")
    @Operation(summary = "Multi-slide 16:9 board pack PowerPoint deck. Pass ?lang=ar for RTL.")
    public ResponseEntity<byte[]> boardPackPptx(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = boardPackPptxService.generatePptx(lang);
        return pptx(body, ExportNaming.build("BoardPack", "BoardPack", lang, "pptx"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(body);
    }
    private ResponseEntity<byte[]> xlsx(byte[] body, String filename) {
        return ResponseEntity.ok()
            .contentType(XLSX_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(body);
    }
    private ResponseEntity<byte[]> pptx(byte[] body, String filename) {
        return ResponseEntity.ok()
            .contentType(PPTX_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(body);
    }
    private ResponseEntity<byte[]> docx(byte[] body, String filename) {
        return ResponseEntity.ok()
            .contentType(DOCX_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(body);
    }
}
