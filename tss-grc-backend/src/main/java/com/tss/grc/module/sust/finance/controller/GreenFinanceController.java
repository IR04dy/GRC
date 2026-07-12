package com.tss.grc.module.sust.finance.controller;

import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.CreateInstrumentRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.CreateProjectRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.CreateReportRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.InstrumentSummaryResponse;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.ProposeAllocationRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.RejectAllocationRequest;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.TransitionInstrumentRequest;
import com.tss.grc.module.sust.finance.entity.AllocationReport;
import com.tss.grc.module.sust.finance.entity.GreenInstrument;
import com.tss.grc.module.sust.finance.entity.GreenProject;
import com.tss.grc.module.sust.finance.entity.InstrumentAllocation;
import com.tss.grc.module.sust.finance.service.GreenFinancePackageService;
import com.tss.grc.module.sust.finance.service.GreenFinanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Green-Finance uplift G1 (plan §4/§8) — Green Sukuk & sustainable-finance register.
 * Reads = SUST READ; register mutations + allocation proposals = SUST WRITE;
 * allocation decisions (approve/reject) = SUST APPROVE (maker-checker; the service
 * additionally blocks self-approval).
 */
@RestController
@RequestMapping("/api/v1/sustainability/finance")
@RequiredArgsConstructor
@Tag(name = "Sustainability — Green finance (sukuk / use-of-proceeds)")
public class GreenFinanceController {

    private static final String READ_GUARD    = "hasPermission('SUST','READ')";
    private static final String WRITE_GUARD   = "hasPermission('SUST','WRITE')";
    private static final String APPROVE_GUARD = "hasPermission('SUST','APPROVE')";

    private final GreenFinanceService service;
    private final GreenFinancePackageService packageService;
    private final com.tss.grc.module.reporting.service.TabularExportService tabularExport;

    // ── instruments ─────────────────────────────────────────────────────

    @GetMapping("/instruments")
    @PreAuthorize(READ_GUARD)
    public List<GreenInstrument> listInstruments() { return service.listInstruments(); }

    @PostMapping("/instruments")
    @PreAuthorize(WRITE_GUARD)
    public GreenInstrument createInstrument(@Valid @RequestBody CreateInstrumentRequest r) {
        return service.createInstrument(r);
    }

    @PostMapping("/instruments/{id}/transition")
    @PreAuthorize(APPROVE_GUARD)
    public GreenInstrument transitionInstrument(@PathVariable Long id,
                                                @Valid @RequestBody TransitionInstrumentRequest r) {
        return service.transitionInstrument(id, r);
    }

    @GetMapping("/instruments/{id}/summary")
    @PreAuthorize(READ_GUARD)
    public InstrumentSummaryResponse summary(@PathVariable Long id) { return service.summary(id); }

    @GetMapping("/instruments/{id}/allocations")
    @PreAuthorize(READ_GUARD)
    public List<InstrumentAllocation> allocations(@PathVariable Long id) { return service.listAllocations(id); }

    // ── projects ────────────────────────────────────────────────────────

    @GetMapping("/projects")
    @PreAuthorize(READ_GUARD)
    public List<GreenProject> listProjects() { return service.listProjects(); }

    @PostMapping("/projects")
    @PreAuthorize(WRITE_GUARD)
    public GreenProject createProject(@Valid @RequestBody CreateProjectRequest r) {
        return service.createProject(r);
    }

    // ── use-of-proceeds ledger (maker-checker) ──────────────────────────

    @PostMapping("/allocations")
    @PreAuthorize(WRITE_GUARD)
    public InstrumentAllocation propose(@Valid @RequestBody ProposeAllocationRequest r) {
        return service.proposeAllocation(r);
    }

    @PostMapping("/allocations/{id}/approve")
    @PreAuthorize(APPROVE_GUARD)
    public InstrumentAllocation approve(@PathVariable Long id) { return service.approveAllocation(id); }

    @PostMapping("/allocations/{id}/reject")
    @PreAuthorize(APPROVE_GUARD)
    public InstrumentAllocation reject(@PathVariable Long id, @Valid @RequestBody RejectAllocationRequest r) {
        return service.rejectAllocation(id, r.reason());
    }

    // ── post-issuance reports (G2) ──────────────────────────────────────

    @GetMapping("/instruments/{id}/reports")
    @PreAuthorize(READ_GUARD)
    public List<AllocationReport> reports(@PathVariable Long id) { return service.listReports(id); }

    @PostMapping("/reports")
    @PreAuthorize(WRITE_GUARD)
    public AllocationReport createReport(@Valid @RequestBody CreateReportRequest r) {
        return service.createReport(r);
    }

    @PostMapping("/reports/{id}/approve")
    @PreAuthorize(APPROVE_GUARD)
    public AllocationReport approveReport(@PathVariable Long id) { return service.approveReport(id); }

    /** V295 — standalone per-report exports via the shared tabular engine. */
    @GetMapping("/reports/{id}/export.xlsx")
    @PreAuthorize(READ_GUARD)
    public org.springframework.http.ResponseEntity<byte[]> reportXlsx(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "en") String lang) {
        var payload = service.reportExportPayload(id, lang);
        return attachment(tabularExport.xlsx(payload),
            com.tss.grc.module.reporting.service.ExportNaming.build("GreenFinance", payload.exportType(), lang, "xlsx"));
    }

    @GetMapping("/reports/{id}/export.docx")
    @PreAuthorize(READ_GUARD)
    public org.springframework.http.ResponseEntity<byte[]> reportDocx(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "en") String lang) {
        var payload = service.reportExportPayload(id, lang);
        return attachment(tabularExport.docx(payload),
            com.tss.grc.module.reporting.service.ExportNaming.build("GreenFinance", payload.exportType(), lang, "docx"));
    }

    private static org.springframework.http.ResponseEntity<byte[]> attachment(byte[] bytes, String filename) {
        return org.springframework.http.ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"")
            .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
            .body(bytes);
    }

    @PostMapping("/reports/{id}/publish")
    @PreAuthorize(APPROVE_GUARD)
    public AllocationReport publishReport(@PathVariable Long id) { return service.publishReport(id); }

    // ── evidence package (G2, §6.7) ─────────────────────────────────────

    @GetMapping("/evidence-package.zip")
    @PreAuthorize(READ_GUARD)
    public org.springframework.http.ResponseEntity<byte[]> evidencePackage() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication();
        GreenFinancePackageService.Package pkg = packageService.generate(auth == null ? null : auth.getName());
        return org.springframework.http.ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + pkg.filename() + "\"")
            .body(pkg.bytes());
    }
}
