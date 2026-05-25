package com.tss.grc.module.consumer.complaints.controller;

import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.AcknowledgeRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.AssignRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.ComplaintResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.ComplaintUpdateResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.EscalateToSamaRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.GenerateQuarterlyReportRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.PublicTrackingResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.QuarterlyReportResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintRequest;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.SubmitComplaintResponse;
import com.tss.grc.module.consumer.complaints.dto.ComplaintDtos.TransitionRequest;
import com.tss.grc.module.consumer.complaints.service.ComplaintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Customer Complaints REST controller — Sprint 13.
 *
 * <p>Two surfaces:
 * <ul>
 *   <li>Public ({@code /api/v1/complaints/public/**}) — anonymous,
 *       no JWT, used by the customer-facing complaint form +
 *       tracking page.</li>
 *   <li>Authenticated — PLATFORM_ADMIN / CONSUMER_PROTECTION_OFFICER /
 *       COMPLIANCE_OFFICER only.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@Tag(name = "Customer Complaints",
     description = "SAMA Banking Consumer Protection — complaint intake, lifecycle, SAMA escalation")
public class ComplaintController {

    private final ComplaintService service;

    // ─── Public (anonymous) ─────────────────────────────────────────

    @PostMapping("/public")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Submit a new customer complaint (anonymous public endpoint)")
    public ResponseEntity<SubmitComplaintResponse> submitPublic(
            @Valid @RequestBody SubmitComplaintRequest req) {
        return ResponseEntity.ok(service.submitPublicComplaint(req));
    }

    @GetMapping("/public/track/{token}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Track a complaint by its submission token (anonymous public endpoint)",
               description = "Returns status, severity, milestone timestamps, customer initials, "
                           + "and customer-visible timeline only. NO PII (phone, email, ID, account).")
    public ResponseEntity<PublicTrackingResponse> trackByToken(@PathVariable UUID token) {
        return ResponseEntity.ok(service.trackByToken(token));
    }

    // ─── Authenticated (CPO / Compliance / Platform Admin) ──────────

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')")
    @Operation(summary = "List complaints in the current tenant, optionally filtered")
    public ResponseEntity<List<ComplaintResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(service.listByStatus(status));
        }
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(service.listByCategory(category));
        }
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')")
    @Operation(summary = "Get a single complaint by ID")
    public ResponseEntity<ComplaintResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping("/{id}/updates")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')")
    @Operation(summary = "List the timeline of updates for a complaint")
    public ResponseEntity<List<ComplaintUpdateResponse>> listUpdates(@PathVariable Long id) {
        return ResponseEntity.ok(service.listUpdates(id));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')")
    @Operation(summary = "Acknowledge receipt of a complaint (within the 5 working-day SAMA SLA)")
    public ResponseEntity<ComplaintResponse> acknowledge(
            @PathVariable Long id,
            @RequestBody(required = false) AcknowledgeRequest req) {
        return ResponseEntity.ok(service.acknowledge(id, req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')")
    @Operation(summary = "Transition a complaint to a new status (state-machine enforced)")
    public ResponseEntity<ComplaintResponse> transition(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest req) {
        return ResponseEntity.ok(service.transition(id, req));
    }

    @PostMapping("/{id}/escalate-sama")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')")
    @Operation(summary = "Escalate a complaint to SAMA with the SAMA reference number")
    public ResponseEntity<ComplaintResponse> escalateToSama(
            @PathVariable Long id,
            @Valid @RequestBody EscalateToSamaRequest req) {
        return ResponseEntity.ok(service.escalateToSama(id, req));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')")
    @Operation(summary = "Assign a complaint to an investigator")
    public ResponseEntity<ComplaintResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignRequest req) {
        return ResponseEntity.ok(service.assign(id, req));
    }

    // ─── Quarterly reports ──────────────────────────────────────────

    @GetMapping("/reports/quarterly")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')")
    @Operation(summary = "List quarterly aggregate reports (most recent first)")
    public ResponseEntity<List<QuarterlyReportResponse>> listReports() {
        return ResponseEntity.ok(service.listQuarterlyReports());
    }

    @PostMapping("/reports/quarterly/generate")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')")
    @Operation(summary = "Generate (or refresh) the quarterly aggregate report for the given period")
    public ResponseEntity<QuarterlyReportResponse> generateReport(
            @Valid @RequestBody GenerateQuarterlyReportRequest req) {
        return ResponseEntity.ok(service.generateQuarterlyReport(req.reportPeriod()));
    }
}
