package com.tss.grc.module.reporting.controller;

import com.tss.grc.module.platform.export.XlsxStyler;

import com.tss.grc.module.reporting.dto.BoardPackAggregate;
import com.tss.grc.module.reporting.service.BoardPackAggregateService;
import com.tss.grc.module.reporting.service.BoardPackPptxService;
import com.tss.grc.module.reporting.service.BoardPackService;
import com.tss.grc.module.reporting.service.DocxExportService;
import com.tss.grc.module.platform.export.ExportAuditContext;
import com.tss.grc.module.platform.export.ExportNaming;
import com.tss.grc.module.reporting.service.ExportService;
import com.tss.grc.module.reporting.service.RegulatorPackageService;
import com.tss.grc.module.platform.export.TabularExportService;
import com.tss.grc.module.platform.export.TabularExportRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
    private final BoardPackAggregateService boardPackAggregateService;
    private final DocxExportService docxExportService;
    private final RegulatorPackageService regulatorPackageService;
    private final TabularExportService tabularExportService;

    private static final MediaType XLSX_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType ZIP_TYPE = MediaType.parseMediaType("application/zip");
    private static final MediaType PPTX_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
    private static final MediaType DOCX_TYPE =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    // ── PDF (Q1) ──────────────────────────────────────────────────────────

    @GetMapping("/risk-register.pdf")
    @PreAuthorize("hasPermission('RISK','EXPORT')")
    @Operation(summary = "Risk register PDF — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> riskRegisterPdf(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = exportService.riskRegisterPdf(lang);
        return pdf(body, ExportNaming.build("ERM", "RiskRegister", lang, "pdf"));
    }

    @GetMapping("/findings-aging.pdf")
    @PreAuthorize("hasPermission('COMPLIANCE','EXPORT')")
    public ResponseEntity<byte[]> findingsAgingPdf(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = exportService.findingsAgingPdf(lang);
        return pdf(body, ExportNaming.build("Compliance", "FindingsAging", lang, "pdf"));
    }

    @GetMapping("/kri-dashboard.pdf")
    @PreAuthorize("hasPermission('RISK','EXPORT')")
    public ResponseEntity<byte[]> kriDashboardPdf(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = exportService.kriDashboardPdf(lang);
        return pdf(body, ExportNaming.build("ERM", "KRIDashboard", lang, "pdf"));
    }

    // ── Excel (Q2) ────────────────────────────────────────────────────────

    @GetMapping("/risk-register.xlsx")
    @PreAuthorize("hasPermission('RISK','EXPORT')")
    @Operation(summary = "Risk register XLSX with live residual-score formulas")
    public ResponseEntity<byte[]> riskRegisterXlsx() {
        return xlsx(exportService.riskRegisterXlsx(), ExportNaming.build("ERM", "RiskRegister", "en", "xlsx"));
    }

    @GetMapping("/findings-aging.xlsx")
    @PreAuthorize("hasPermission('COMPLIANCE','EXPORT')")
    @Operation(summary = "Findings aging XLSX with live TODAY()-based days-open formula")
    public ResponseEntity<byte[]> findingsXlsx() {
        return xlsx(exportService.findingsXlsx(), ExportNaming.build("Compliance", "FindingsAging", "en", "xlsx"));
    }

    @GetMapping("/kri-dashboard.xlsx")
    @PreAuthorize("hasPermission('RISK','EXPORT')")
    @Operation(summary = "KRI dashboard XLSX with COUNTIF summary sheet")
    public ResponseEntity<byte[]> kriXlsx() {
        return xlsx(exportService.kriDashboardXlsx(), ExportNaming.build("ERM", "KRIDashboard", "en", "xlsx"));
    }

    // ── Word (.docx) ──────────────────────────────────────────────────────

    @GetMapping("/risk-register.docx")
    @PreAuthorize("hasPermission('RISK','EXPORT')")
    @Operation(summary = "Risk register DOCX — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> riskRegisterDocx(@RequestParam(defaultValue = "en") String lang) {
        return docx(docxExportService.riskRegisterDocx(lang),
                ExportNaming.build("ERM", "RiskRegister", lang, "docx"));
    }

    @GetMapping("/findings-aging.docx")
    @PreAuthorize("hasPermission('COMPLIANCE','EXPORT')")
    @Operation(summary = "Findings aging DOCX — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> findingsAgingDocx(@RequestParam(defaultValue = "en") String lang) {
        return docx(docxExportService.findingsAgingDocx(lang),
                ExportNaming.build("Compliance", "FindingsAging", lang, "docx"));
    }

    @GetMapping("/kri-dashboard.docx")
    @PreAuthorize("hasPermission('RISK','EXPORT')")
    @Operation(summary = "KRI dashboard DOCX — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> kriDashboardDocx(@RequestParam(defaultValue = "en") String lang) {
        return docx(docxExportService.kriDashboardDocx(lang),
                ExportNaming.build("ERM", "KRIDashboard", lang, "docx"));
    }

    // ── Change register (CH-4) ────────────────────────────────────────────

    @GetMapping("/change-register.pdf")
    @PreAuthorize("hasPermission('CHANGE','EXPORT')")
    @Operation(summary = "Change register PDF — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> changeRegisterPdf(@RequestParam(defaultValue = "en") String lang) {
        return pdf(exportService.changeRegisterPdf(lang), ExportNaming.build("Change", "ChangeRegister", lang, "pdf"));
    }

    @GetMapping("/change-register.xlsx")
    @PreAuthorize("hasPermission('CHANGE','EXPORT')")
    @Operation(summary = "Change register XLSX")
    public ResponseEntity<byte[]> changeRegisterXlsx() {
        return xlsx(exportService.changeRegisterXlsx(), ExportNaming.build("Change", "ChangeRegister", "en", "xlsx"));
    }

    @GetMapping("/change-register.docx")
    @PreAuthorize("hasPermission('CHANGE','EXPORT')")
    @Operation(summary = "Change register DOCX — pass ?lang=ar for Arabic RTL")
    public ResponseEntity<byte[]> changeRegisterDocx(@RequestParam(defaultValue = "en") String lang) {
        return docx(docxExportService.changeRegisterDocx(lang), ExportNaming.build("Change", "ChangeRegister", lang, "docx"));
    }

    @GetMapping("/change/{id}/cab-pack.pdf")
    @PreAuthorize("hasPermission('CHANGE','EXPORT')")
    @Operation(summary = "CH-11: consolidated CAB pack for one change (details, plans, votes, "
        + "tasks, links, PIR). Pass ?lang=ar for Arabic RTL.")
    public ResponseEntity<byte[]> cabPackPdf(@PathVariable Long id, @RequestParam(defaultValue = "en") String lang) {
        return pdf(exportService.cabPackPdf(id, lang), ExportNaming.build("Change", "CABPack-" + id, lang, "pdf"));
    }

    @GetMapping("/policy/{id}/document.pdf")
    @PreAuthorize("hasPermission('COMPLIANCE','EXPORT')")
    @Operation(summary = "CH-10: controlled policy document PDF rendered from the authored inline "
        + "content (content_md). Pass ?lang=ar for Arabic RTL.")
    public ResponseEntity<byte[]> policyDocumentPdf(@PathVariable Long id, @RequestParam(defaultValue = "en") String lang) {
        return pdf(exportService.policyDocumentPdf(id, lang), ExportNaming.build("Policy", "PolicyDoc-" + id, lang, "pdf"));
    }

    // ── Board pack (S1) ───────────────────────────────────────────────────
    //
    // ADR-017 D-10a / V332 — these two handlers used to be guarded by
    // `hasPermission('REPORTING','EXPORT') or hasPermission('RISK','EXPORT')`, a
    // disjunction satisfied by 24 of 59 roles in dev and 24 of 57 in PRODUCTION —
    // including VIEWER, SUPPLIER_MANAGER, MA_LEAD, EMERGENCY_RESPONDER,
    // REGULATOR_VIEWER and RISK_ANALYST. A single call returns up to 200 itemised
    // risks and 1,000 itemised audit findings (F2-55), so that was not a cosmetic
    // over-grant.
    //
    // D-10a prescribed revoking the four indefensible grants. Measuring the guard
    // rather than the grant table showed why that instrument does not fit: because
    // the guard is an OR, stopping a role means taking BOTH permissions from it,
    // and those two authorise every other export the platform has. On prod the
    // EXPORT ledger is risk-register 12, packet 7, evidence-package 7,
    // ai-model-registry 3, regulator-package 2, cyber-incidents 1, board-pack 1 —
    // so the collateral of the strip is the most-used export and the beneficiary
    // is the least-used one. Taking EXPORT from EXTERNAL_AUDITOR carries the
    // inverse risk: inability to furnish under Banking Control Law art. 18.
    //
    // So the defect was in the guard, not the grant — the mirror of ADR-015. The
    // pack now carries its own permission, catalogued and granted by V332 FIRST
    // (F2-23's rule: never annotate against a permission no role holds), narrowed
    // to 8 roles. Nothing else loses an export. Two of the 8 are GLOBAL roles
    // (ORG_ADMIN, INTERNAL_AUDITOR) because F2-56 measured `iam.roles` as
    // tenant-scoped in practice: a grant list of organisation-1 role names alone
    // would ship a board pack that tenants 2 and 3 could never download.

    @GetMapping("/board-pack.pdf")
    @PreAuthorize("hasPermission('BOARD','EXPORT')")
    @Operation(summary = "Multi-section board pack PDF combining risks, findings, KRIs, " +
        "framework coverage, change management and ethics highlights. Pass ?lang=ar for RTL.")
    public ResponseEntity<byte[]> boardPackPdf(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = boardPackService.generatePdf(lang);
        return pdf(body, ExportNaming.build("BoardPack", "BoardPack", lang, "pdf"));
    }

    @GetMapping("/board-pack.pptx")
    @PreAuthorize("hasPermission('BOARD','EXPORT')")
    @Operation(summary = "Multi-slide 16:9 board pack PowerPoint deck. Pass ?lang=ar for RTL.")
    public ResponseEntity<byte[]> boardPackPptx(@RequestParam(defaultValue = "en") String lang) {
        byte[] body = boardPackPptxService.generatePptx(lang);
        return pptx(body, ExportNaming.build("BoardPack", "BoardPack", lang, "pptx"));
    }

    /**
     * D-5 — the board pack as data, so a screen can render what the PDF and the deck render.
     *
     * <p>Three things about this endpoint were decided before it was written, and each is a
     * constraint rather than a preference:
     *
     * <p><b>It carries the same permission as the PDF and the deck.</b> The same figures leave the
     * building either way, so a JSON aggregate authorised more loosely than its own PDF would be a
     * hole with a content-type in front of it. {@code BoardPackGuardTest} asserts all three routes
     * carry the identical guard — and it is the <em>only</em> test that can, because the export
     * ratchet cannot see this route: its rule keys on a {@code byte[]}/{@code Resource} return type,
     * and this returns an object.
     *
     * <p><b>It is audited like an export</b> — {@code ExportAuditContext.declareExport} makes the
     * interceptor record it, since a response with no {@code Content-Disposition} was invisible to
     * it. JSON of an aggregate is an export in substance.
     *
     * <p><b>It takes no {@code lang}.</b> Every name in the aggregate is carried in both languages
     * and the consumer chooses, which is what lets one build serve an Arabic PDF and an English
     * deck. A language parameter here would imply a choice the response does not make.
     */
    @GetMapping("/board-pack.json")
    @PreAuthorize("hasPermission('BOARD','EXPORT')")
    @Operation(summary = "Board pack as a bilingual JSON aggregate — the same figures the PDF and "
        + "PPTX render, computed by the same builder. Board-confidential: requires BOARD:EXPORT.")
    public BoardPackAggregate boardPackJson() {
        BoardPackAggregate pack = boardPackAggregateService.build(BoardPackAggregate.Scope.FULL);
        ExportAuditContext.declareExport("json");
        return pack;
    }

    // ── Regulator / audit evidence package (P2/P3) ────────────────────────

    /**
     * F2-60 / ADR-017 D-10b — the assurance position of the tenant, narrowed from 37 roles to 13.
     *
     * <p>This was guarded by {@code hasPermission('COMPLIANCE','EXPORT') or
     * hasPermission('REPORTING','EXPORT')}: a disjunction satisfied by <b>37 of 59 roles in dev and
     * 37 of 57 in production</b>, including ASSET_OWNER, PROCUREMENT_OWNER, SUPPLIER_MANAGER,
     * EMERGENCY_RESPONDER, MA_LEAD, DR_OWNER, BIA_OWNER, VULN_MANAGER and VIEWER. What they could
     * take is not a register extract — it is the requirement-to-control mapping, the evidence index
     * <em>and</em> the audit trail, hash-manifested so a recipient can prove it was not altered.
     *
     * <p>Third instance of D-10a's shape, so the instrument is the same and for the same measured
     * reason: because the guard is an OR, narrowing it by revoking grants would mean taking
     * COMPLIANCE:EXPORT and REPORTING:EXPORT from 24 roles, and those two authorise the
     * findings-aging export, the policy-document PDF, the KRI dashboard, the packet builder and the
     * risk register. The collateral would dwarf the fix. The defect is in the guard.
     *
     * <p>{@code REGULATORY:EXPORT} is the destination because it was <em>measured</em> to fit, not
     * because it reads well: it is already catalogued and already held by 14 roles that are almost
     * exactly the assurance audience, so no role needed granting for the new guard to be satisfiable
     * (F2-23's rule). ⚠ And it previously guarded <b>nothing</b> — zero {@code @PreAuthorize} uses
     * anywhere in the backend — so it was a catalogued permission enforcing nothing, the F2-23 shape
     * again. That makes it a safe destination and makes this change also give it a job.
     *
     * <p>V333 removes the one grant row that would otherwise have survived the narrowing: VIEWER,
     * the generic read-only persona, held {@code REGULATORY:EXPORT} too. VIEWER holds <b>17
     * {@code *:EXPORT} permissions</b> in dev and in production, which is F2-27's grant side never
     * having been reviewed after its guard side was fixed — that whole population is F2-61 and needs
     * a stated policy, not a sweep here. Exactly one row moves, and because this endpoint is now the
     * only consumer of {@code REGULATORY:EXPORT}, its blast radius is exactly this endpoint.
     */
    @GetMapping("/regulator-package.zip")
    @PreAuthorize("hasPermission('REGULATORY','EXPORT')")
    @Operation(summary = "Regulator/audit evidence package (ZIP): branded README.pdf + " +
        "Requirement_Mapping.xlsx + Evidence_Index.xlsx + Audit_Trail.xlsx + manifest.json (SHA-256). " +
        "Assurance-confidential: requires REGULATORY:EXPORT.")
    public ResponseEntity<byte[]> regulatorPackage() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        String by = auth == null ? null : auth.getName();
        RegulatorPackageService.Package pkg = regulatorPackageService.generate(by);
        return ResponseEntity.ok()
            .contentType(ZIP_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pkg.filename() + "\"")
            .body(pkg.bytes());
    }

    // ── Tabular XLSX from client-supplied data (P5) ─────────────────────────

    @PostMapping("/packet.xlsx")
    @PreAuthorize("hasPermission('REPORTING','EXPORT')")
    @Operation(summary = "Branded .xlsx from client-supplied tabular data (sheets/headers/rows). " +
        "Reuses the house XlsxStyler treatment + tenant BrandTheme; filename via ExportNaming.")
    public ResponseEntity<byte[]> packetXlsx(@Valid @RequestBody TabularExportRequest request) {
        byte[] body = tabularExportService.xlsx(request);
        String lang = request.lang() == null || request.lang().isBlank() ? "en" : request.lang();
        return xlsx(body, ExportNaming.build(request.module(), request.exportType(), lang, "xlsx"));
    }

    @PostMapping("/packet.docx")
    @PreAuthorize("hasPermission('REPORTING','EXPORT')")
    @Operation(summary = "Branded .docx from client-supplied tabular data (title + heading+table per " +
        "sheet). Reuses the tenant BrandTheme + ExportNaming filename; RTL when lang=ar.")
    public ResponseEntity<byte[]> packetDocx(@Valid @RequestBody TabularExportRequest request) {
        byte[] body = tabularExportService.docx(request);
        String lang = request.lang() == null || request.lang().isBlank() ? "en" : request.lang();
        return docx(body, ExportNaming.build(request.module(), request.exportType(), lang, "docx"));
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
