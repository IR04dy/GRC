package com.tss.grc.module.regreporting.controller;

import com.tss.grc.module.regreporting.dto.RegReportingDtos.CalendarResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.CreateObligationRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.DashboardResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.ExemptFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.FilingEventResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.FilingResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.GenerateFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.ObligationResponse;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.RejectFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.SubmitFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.TransitionFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.UpdateFilingRequest;
import com.tss.grc.module.regreporting.dto.RegReportingDtos.UpdateObligationRequest;
import com.tss.grc.module.regreporting.service.RegReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST surface for the Sprint 16 Mandatory Regulatory Reporting Calendar.
 * Tenant-scoped via JWT-derived {@code TenantContext}.
 */
@RestController
@RequestMapping("/api/v1/regreporting")
@RequiredArgsConstructor
@Tag(name = "Mandatory Regulatory Reporting (Sprint 16)")
@SecurityRequirement(name = "bearerAuth")
public class RegReportingController {

    private static final String READ_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','RISK_MANAGER','COMPLIANCE_OFFICER',"
            + "'INTERNAL_AUDITOR','BOARD_SECRETARY','CFO','CISO','DPO')";
    private static final String WRITE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','RISK_MANAGER')";
    private static final String APPROVE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','CFO','CRO')";

    private final RegReportingService service;

    // ─── Obligations ───────────────────────────────────────────────

    @PostMapping("/obligations")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Add a recurring statutory return to the catalog.")
    public ResponseEntity<ObligationResponse> createObligation(
            @Valid @RequestBody CreateObligationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createObligation(req));
    }

    @PutMapping("/obligations/{id}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update an obligation catalog entry.")
    public ObligationResponse updateObligation(
            @PathVariable Long id,
            @Valid @RequestBody UpdateObligationRequest req) {
        return service.updateObligation(id, req);
    }

    @GetMapping("/obligations")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Paginated obligation catalog.")
    public Page<ObligationResponse> listObligations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listObligations(page, size);
    }

    @GetMapping("/obligations/{id}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one obligation.")
    public ObligationResponse getObligation(@PathVariable Long id) {
        return service.getObligation(id);
    }

    @GetMapping("/obligations/{id}/filings")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "All filings for one obligation, newest first.")
    public List<FilingResponse> listFilingsForObligation(@PathVariable Long id) {
        return service.listFilingsForObligation(id);
    }

    @PostMapping("/obligations/{id}/filings")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Generate the next filing cycle for this obligation.")
    public ResponseEntity<FilingResponse> generateFiling(
            @PathVariable Long id,
            @Valid @RequestBody GenerateFilingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.generateFiling(id, req));
    }

    // ─── Filings ───────────────────────────────────────────────────

    @GetMapping("/filings")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Paginated filings list (default sort: due date asc).")
    public Page<FilingResponse> listFilings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listFilings(page, size);
    }

    @GetMapping("/filings/{id}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one filing.")
    public FilingResponse getFiling(@PathVariable Long id) {
        return service.getFiling(id);
    }

    @PutMapping("/filings/{id}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update filing metadata (prep notes, ownership, artifact ref).")
    public FilingResponse updateFiling(@PathVariable Long id,
                                       @Valid @RequestBody UpdateFilingRequest req) {
        return service.updateFiling(id, req);
    }

    @PostMapping("/filings/{id}/transition")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Move filing to next status via the generic state machine.")
    public FilingResponse transitionFiling(@PathVariable Long id,
                                            @Valid @RequestBody TransitionFilingRequest req) {
        return service.transitionFiling(id, req);
    }

    @PostMapping("/filings/{id}/submit")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Lodge the filing with the regulator (captures ack reference).")
    public FilingResponse submitFiling(@PathVariable Long id,
                                        @Valid @RequestBody SubmitFilingRequest req) {
        return service.submitFiling(id, req);
    }

    @PostMapping("/filings/{id}/acknowledge")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record regulator acknowledgment receipt (SUBMITTED → ACKNOWLEDGED).")
    public FilingResponse acknowledgeFiling(@PathVariable Long id) {
        return service.acknowledgeFiling(id);
    }

    @PostMapping("/filings/{id}/reject")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a regulator rejection (forces rework cycle).")
    public FilingResponse rejectFiling(@PathVariable Long id,
                                        @Valid @RequestBody RejectFilingRequest req) {
        return service.rejectFiling(id, req);
    }

    @PostMapping("/filings/{id}/exempt")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Mark a filing exempted (formal waiver with reference).")
    public FilingResponse exemptFiling(@PathVariable Long id,
                                        @Valid @RequestBody ExemptFilingRequest req) {
        return service.exemptFiling(id, req);
    }

    @GetMapping("/filings/{id}/events")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Chronological event timeline for a filing.")
    public List<FilingEventResponse> listEvents(@PathVariable Long id) {
        return service.listEvents(id);
    }

    // ─── Calendar + dashboard ──────────────────────────────────────

    @GetMapping("/calendar")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Calendar window of filings keyed by due date.")
    public CalendarResponse calendar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calendar(from, to);
    }

    @GetMapping("/dashboard")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Calendar dashboard — KPIs + upcoming + overdue list.")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
