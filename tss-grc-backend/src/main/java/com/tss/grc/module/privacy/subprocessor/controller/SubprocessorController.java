package com.tss.grc.module.privacy.subprocessor.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.LifecycleTransitionRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.MaterialChangeRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.PublicSubprocessorResponse;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorEventRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorEventResponse;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorRequest;
import com.tss.grc.module.privacy.subprocessor.dto.SubprocessorDtos.SubprocessorResponse;
import com.tss.grc.module.platform.tenancy.PublicPortalTenantResolver;
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
 * {@code @PreAuthorize}. SEC-PRIV-003: it resolves the tenant from the
 * opaque organisation short code ({@code ?org=<shortCode>}) and 404s on
 * an unknown/inactive org, rather than trusting an enumerable numeric id
 * (which previously defaulted to org 1).
 */
@RestController
@RequestMapping("/api/v1/privacy/subprocessors")
@RequiredArgsConstructor
@RequireModule("PDPL")
@Tag(name = "PDPL — Sub-processor Tracking")
@SecurityRequirement(name = "bearerAuth")
public class SubprocessorController {

    /**
     * F2-52: {@code PRIVACY_OFFICER} was corrected to {@code PRIVACY_MANAGER}, not dropped.
     *
     * <p>{@code PRIVACY_OFFICER} matches no row in {@code iam.roles} (67 active, measured), so the leg
     * was permanently false and this list's effective size was 7, not 8. The real privacy job family is
     * {@code PRIVACY_MANAGER} — <i>"Day-to-day privacy ops under the DPO"</i>, {@code is_privileged} — and
     * {@code DPO} is already named beside it here, which is what makes this a misspelling rather than a
     * policy: a register the DPO reads is exactly what their privacy manager reads. All five guarded
     * endpoints are GETs over the sub-processor register, so the correction widens <em>reads</em> only.
     *
     * <p>⚠ Correcting a ghost widens access and dropping one does not, so the two are not
     * interchangeable — F2-144 found a case where the dead leg was the only thing holding a write shut.
     * The reason to correct here rather than drop is that a real role was intended and exists; where no
     * such role exists the honest move is to drop the leg and say so. Whoever changes this must change
     * {@code AttachmentAccessPolicy}'s {@code SUBPROCESSOR} entry in the same edit, or a privacy manager
     * opens the register and 403s on its files.
     */
    private static final String READ_GUARD =
            "hasAnyRole('DPO','COMPLIANCE_OFFICER','PRIVACY_MANAGER','CISO','RISK_MANAGER','AUDITOR','INTERNAL_AUDITOR','PLATFORM_ADMIN')";
    private static final String WRITE_GUARD =
            "hasAnyRole('DPO','PLATFORM_ADMIN')";

    private final SubprocessorService service;
    private final SubprocessorEventService eventService;
    private final SubprocessorPublicListService publicListService;
    private final PublicPortalTenantResolver publicTenant;

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
    public List<PublicSubprocessorResponse> publicList(
            @RequestParam(name = "org", required = false) String orgCode) {
        // SEC-PRIV-003: this endpoint is permitAll, so there is no JWT and
        // no TenantContext to scope the read. Previously it trusted a
        // client-supplied *numeric* org id (defaulting to 1), which let an
        // unauthenticated caller iterate org=1,2,3,… and harvest every
        // tenant's approved sub-processor / vendor map.
        //
        // The hardening it received — demand the opaque, non-enumerable short
        // code, require it to exist and be active, 404 otherwise, never fall
        // back to org 1 — was inlined here and nowhere else. F2-83 then found
        // the identical defect still live in the two channels where the data is
        // most confidential (whistleblower reports, consumer complaints), both
        // defaulting to org 1. A fix applied to one endpoint is not a pattern
        // until it is applied to the others that share the flaw, so the logic
        // now lives in PublicPortalTenantResolver and this call site uses it.
        //
        // resolveNamed, NOT resolve: this is a read of a *named* tenant's
        // published list, so it must ignore TenantContext. The submission
        // variant prefers the caller's own tenant, which is right for a form an
        // employee might be logged in to but wrong here — it would silently
        // hand an authenticated caller their own list instead of the one they
        // asked for. Behaviour at this endpoint is unchanged: same 404, same
        // silence about whether the org exists.
        return publicListService.publicList(publicTenant.resolveNamed(orgCode));
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private static Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}
