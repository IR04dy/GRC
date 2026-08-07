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
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
 *   <li>Authenticated — guarded by {@code hasPermission('CONSUMER', READ│WRITE│APPROVE)} since
 *       F2-145(e) slice 3. It read <em>PLATFORM_ADMIN / CONSUMER_PROTECTION_OFFICER /
 *       COMPLIANCE_OFFICER only</em> until then, on all nine endpoints identically; the population is
 *       unchanged, but it is now a grant rather than a name list. See the guard constants below.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "Customer Complaints",
     description = "SAMA Banking Consumer Protection — complaint intake, lifecycle, SAMA escalation")
public class ComplaintController {

    /*
     * F2-145(e) slice 3 — these guards authorise by PERMISSION, not by role name.
     *
     * WHAT THEY WERE. The same nine-times-repeated literal
     * hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER') on every
     * authenticated endpoint — GET and POST alike. F2-145 recorded this module as the severe case of the
     * six because CONSUMER_PROTECTION_OFFICER existed in no row of iam.roles until V362, leaving
     * PLATFORM_ADMIN and COMPLIANCE_OFFICER and nobody else on all nine.
     *
     * POPULATION-PRESERVING, MEASURED PER ACTION BEFORE THE EDIT: the catalogue grants CONSUMER READ,
     * WRITE **and** APPROVE to exactly {COMPLIANCE_OFFICER, CONSUMER_PROTECTION_OFFICER} — the guard's
     * two surviving names — so every tiering below admits precisely who the flat guard admitted.
     * PLATFORM_ADMIN is absent from the grants on purpose: hasPermission gets the in-code bypass.
     *
     * ⚠ WHY TIER AT ALL, IF THE POPULATION IS THE SAME. Because the flat guard could not express the
     * module's own authority model, and CONSUMER:APPROVE was catalogued with nothing in the platform
     * consulting it — a permission no guard reads is inert (the lesson V362/V367 taught in `sharia`).
     * Tiering costs nothing today and makes the distinction grantable tomorrow without a code change,
     * which is the capability this whole conversion buys.
     *
     * escalateToSama is the one APPROVE. Telling the regulator is an outbound act with legal
     * consequence, and it is the direct analogue of ObController#notifySama, which this programme put on
     * OPENBANKING:APPROVE in slice 2. Complaint transitions stay WRITE for the same reason
     * transitionIncident does: handling the complaint is the officer's day job.
     *
     * The two /public endpoints keep permitAll() — they are the anonymous SAMA-mandated intake and
     * tracking surface, and F2-83 is why the tracking response carries no PII.
     */
    private static final String READ_GUARD = "hasPermission('CONSUMER','READ')";
    private static final String WRITE_GUARD = "hasPermission('CONSUMER','WRITE')";
    private static final String APPROVE_GUARD = "hasPermission('CONSUMER','APPROVE')";

    private final ComplaintService service;

    // ─── Public (anonymous) ─────────────────────────────────────────

    @PostMapping("/public")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Submit a new customer complaint (anonymous public endpoint)",
               description = "The portal link MUST carry the tenant's public short code as ?org=. "
                           + "There is no JWT on this path, so nothing else identifies the receiving "
                           + "organisation; an unknown or inactive code is refused with 404 and never "
                           + "defaulted (F2-83).")
    public ResponseEntity<SubmitComplaintResponse> submitPublic(
            @Valid @RequestBody SubmitComplaintRequest req,
            @RequestParam(name = "org", required = false) String org) {
        return ResponseEntity.ok(service.submitPublicComplaint(req, org));
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
    @PreAuthorize(READ_GUARD)
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
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get a single complaint by ID")
    public ResponseEntity<ComplaintResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @GetMapping("/{id}/updates")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List the timeline of updates for a complaint")
    public ResponseEntity<List<ComplaintUpdateResponse>> listUpdates(@PathVariable Long id) {
        return ResponseEntity.ok(service.listUpdates(id));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Acknowledge receipt of a complaint (within the 5 working-day SAMA SLA)")
    public ResponseEntity<ComplaintResponse> acknowledge(
            @PathVariable Long id,
            @RequestBody(required = false) AcknowledgeRequest req) {
        return ResponseEntity.ok(service.acknowledge(id, req));
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition a complaint to a new status (state-machine enforced)")
    public ResponseEntity<ComplaintResponse> transition(
            @PathVariable Long id,
            @Valid @RequestBody TransitionRequest req) {
        return ResponseEntity.ok(service.transition(id, req));
    }

    @PostMapping("/{id}/escalate-sama")
    @PreAuthorize(APPROVE_GUARD)
    @Operation(summary = "Escalate a complaint to SAMA with the SAMA reference number")
    public ResponseEntity<ComplaintResponse> escalateToSama(
            @PathVariable Long id,
            @Valid @RequestBody EscalateToSamaRequest req) {
        return ResponseEntity.ok(service.escalateToSama(id, req));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Assign a complaint to an investigator")
    public ResponseEntity<ComplaintResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignRequest req) {
        return ResponseEntity.ok(service.assign(id, req));
    }

    // ─── Quarterly reports ──────────────────────────────────────────

    @GetMapping("/reports/quarterly")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List quarterly aggregate reports (most recent first)")
    public ResponseEntity<List<QuarterlyReportResponse>> listReports() {
        return ResponseEntity.ok(service.listQuarterlyReports());
    }

    @PostMapping("/reports/quarterly/generate")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Generate (or refresh) the quarterly aggregate report for the given period")
    public ResponseEntity<QuarterlyReportResponse> generateReport(
            @Valid @RequestBody GenerateQuarterlyReportRequest req) {
        return ResponseEntity.ok(service.generateQuarterlyReport(req.reportPeriod()));
    }
}
