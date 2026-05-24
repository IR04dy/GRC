package com.tss.grc.module.privacy.subprocessor.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.LifecycleTransitionRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.MaterialChangeRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.PublicSubprocessorResponse;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorEventRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorEventResponse;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorResponse;
import com.tss.grc.module.privacy.subprocessor.service.SubprocessorEventService;
import com.tss.grc.module.privacy.subprocessor.service.SubprocessorPublicListService;
import com.tss.grc.module.privacy.subprocessor.service.SubprocessorService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for PDPL sub-processor tracking — Sprint 11.
 *
 * <p>Read guard is wide (DPO, Compliance, Privacy, CISO, Risk, Audit,
 * Platform Admin). Write guard is narrow (DPO + Platform Admin)
 * because every write is an audited PDPL Article 30 record.
 *
 * <p>The public-list endpoint
 * {@code GET /api/v1/privacy/subprocessors/public} is wired to
 * {@code permitAll} in {@code SecurityConfig} — no auth, no
 * {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/v1/privacy/subprocessors")
@RequiredArgsConstructor
@Tag(name = "PDPL — Sub-processor Tracking")
@SecurityRequirement(name = "bearerAuth")
public class SubprocessorController {

    private static final String READ_GUARD =
            "hasAnyRole('DPO','COMPLIANCE_OFFICER','PRIVACY_OFFICER','CISO','RISK_MANAGER','AUDITOR','INTERNAL_AUDITOR','PLATFORM_ADMIN')";
    private static final String WRITE_GUARD =
            "hasAnyRole('DPO','PLATFORM_ADMIN')";

    private final SubprocessorService service;
    private final SubprocessorEventService eventService;
    private final SubprocessorPublicListService publicListService;

    // ─── List + get ───────────────────────────────────────────────

    @GetMapping
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List all sub-processors for the current tenant.")
    public List<SubprocessorResponse> list() {
        return service.list();
    }

    @GetMapping("/{externalId}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get one sub-processor by external id.")
    public SubprocessorResponse get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @GetMapping("/by-country/{country}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List sub-processors filtered by ISO 3166-1 alpha-2 country.")
    public List<SubprocessorResponse> byCountry(@PathVariable String country) {
        return service.findByCountry(country);
    }

    @GetMapping("/overdue-reviews")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Sub-processors whose 12-month review is overdue.")
    public List<SubprocessorResponse> overdueReviews() {
        Long orgId = TenantContext.getCurrentTenant();
        return service.findOverdueReviews(orgId);
    }

    // ─── Mutations ────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Create a new sub-processor (defaults to PROPOSED).")
    public ResponseEntity<SubprocessorResponse> create(@Valid @RequestBody SubprocessorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{externalId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Update a sub-processor's mutable fields.")
    public SubprocessorResponse update(@PathVariable UUID externalId,
                                       @Valid @RequestBody SubprocessorRequest req) {
        return service.update(externalId, req);
    }

    @PostMapping("/{externalId}/transition")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition the sub-processor's lifecycle stage.")
    public SubprocessorResponse transition(@PathVariable UUID externalId,
                                            @Valid @RequestBody LifecycleTransitionRequest req) {
        return service.transitionLifecycle(externalId, req);
    }

    @PostMapping("/{externalId}/approve")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "DPO-approve the sub-processor (gates: DPA + signed date + purpose + cross-border safeguard if non-SA).")
    public SubprocessorResponse approve(@PathVariable UUID externalId, Authentication auth) {
        return service.dpoApprove(externalId, userId(auth));
    }

    @PostMapping("/{externalId}/review")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a 12-month review completion — bumps next-review-due.")
    public SubprocessorResponse review(@PathVariable UUID externalId, Authentication auth) {
        return service.recordReview(externalId, userId(auth));
    }

    @PostMapping("/{externalId}/material-change")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a material change — forces the sub-processor back into DPO review.")
    public SubprocessorResponse materialChange(@PathVariable UUID externalId,
                                                @Valid @RequestBody MaterialChangeRequest req) {
        return service.materialChange(externalId, req);
    }

    // ─── Events ────────────────────────────────────────────────────

    @GetMapping("/{externalId}/events")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List events recorded against this sub-processor (newest first).")
    public List<SubprocessorEventResponse> listEvents(@PathVariable UUID externalId) {
        return eventService.listForSubprocessor(externalId);
    }

    @PostMapping("/{externalId}/events")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Append an event to the sub-processor's ledger.")
    public ResponseEntity<SubprocessorEventResponse> recordEvent(@PathVariable UUID externalId,
                                                                  @Valid @RequestBody SubprocessorEventRequest req,
                                                                  Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                eventService.recordEvent(externalId, req, userId(auth)));
    }

    // ─── Public transparency list ────────────────────────────────
    // Wired to permitAll in SecurityConfig — no @PreAuthorize here.

    @GetMapping("/public")
    @Operation(summary = "Public PDPL Article 12 transparency list — APPROVED sub-processors only, internal fields stripped.")
    public List<PublicSubprocessorResponse> publicList(@RequestParam(name = "org", required = false) Long orgId) {
        // Default to org 1 when not supplied — for the demo bank.
        return publicListService.publicList(orgId == null ? 1L : orgId);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private static Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}
