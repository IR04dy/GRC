package com.tss.grc.module.ethics.coi.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.CampaignResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.CreateCampaignRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.DeclarationResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.RecordRecusalRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.RecusalEventResponse;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.ReviewDeclarationRequest;
import com.tss.grc.module.ethics.coi.dto.CoiDtos.SubmitDeclarationRequest;
import com.tss.grc.module.ethics.coi.service.CoiService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for the Sprint 13 Conflict-of-Interest (COI) register.
 *
 * <p>All endpoints are tenant-scoped via the JWT-derived
 * {@link TenantContext}. Role guards:
 *
 * <ul>
 *   <li>Campaign management (create/open/close), review,
 *       recusal record → {@code PLATFORM_ADMIN}, {@code ETHICS_OFFICER},
 *       {@code BOARD_SECRETARY}, {@code COMPLIANCE_OFFICER}.</li>
 *   <li>Declaration submission additionally allows {@code USER} (any
 *       authenticated employee can submit their own attestation).</li>
 *   <li>Read endpoints share the management role set — a future
 *       slice will scope per-user reads via row-level checks.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/coi")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "Ethics — Conflict of Interest (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class CoiController {

    private static final String MANAGE_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','ETHICS_OFFICER','BOARD_SECRETARY','COMPLIANCE_OFFICER')";
    private static final String SUBMIT_GUARD =
            "hasAnyRole('PLATFORM_ADMIN','ETHICS_OFFICER','BOARD_SECRETARY','COMPLIANCE_OFFICER','USER')";

    private final CoiService service;

    // ─── Campaigns ──────────────────────────────────────────────────

    @PostMapping("/campaigns")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "Create a COI declaration campaign (FY annual or ad-hoc).")
    public ResponseEntity<CampaignResponse> createCampaign(@Valid @RequestBody CreateCampaignRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCampaign(req));
    }

    @GetMapping("/campaigns")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "List COI campaigns for the current tenant.")
    public List<CampaignResponse> listCampaigns() {
        return service.listCampaignsByOrg(TenantContext.requireOrganisationId());
    }

    @GetMapping("/campaigns/{id}")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "Get a single COI campaign.")
    public CampaignResponse getCampaign(@PathVariable Long id) {
        return service.getCampaign(id);
    }

    @PostMapping("/campaigns/{id}/open")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "Transition campaign DRAFT → OPEN.")
    public CampaignResponse openCampaign(@PathVariable Long id) {
        return service.openCampaign(id);
    }

    @PostMapping("/campaigns/{id}/close")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "Transition campaign OPEN → CLOSED.")
    public CampaignResponse closeCampaign(@PathVariable Long id) {
        return service.closeCampaign(id);
    }

    @PostMapping("/campaigns/{id}/archive")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "Transition campaign CLOSED → ARCHIVED.")
    public CampaignResponse archiveCampaign(@PathVariable Long id) {
        return service.archiveCampaign(id);
    }

    @GetMapping("/campaigns/{id}/declarations")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "List declarations attached to a campaign.")
    public List<DeclarationResponse> listDeclarationsByCampaign(@PathVariable Long id) {
        return service.listDeclarationsByCampaign(id);
    }

    // ─── Declarations ───────────────────────────────────────────────

    @PostMapping("/declarations")
    @PreAuthorize(SUBMIT_GUARD)
    @Operation(summary = "Submit a COI declaration with disclosed items — auto-flags review_outcome on save.")
    public ResponseEntity<DeclarationResponse> submitDeclaration(@Valid @RequestBody SubmitDeclarationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submitDeclaration(req));
    }

    @GetMapping("/declarations/{id}")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "Get a single declaration with its disclosed items.")
    public DeclarationResponse getDeclaration(@PathVariable Long id) {
        return service.getDeclaration(id);
    }

    @PostMapping("/declarations/{id}/review")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "Ethics Officer reviews a declaration: set outcome + notes.")
    public DeclarationResponse reviewDeclaration(@PathVariable Long id,
                                                   @Valid @RequestBody ReviewDeclarationRequest req,
                                                   Authentication auth) {
        return service.reviewDeclaration(id, req, userId(auth));
    }

    // ─── Recusals ───────────────────────────────────────────────────

    @PostMapping("/recusals")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "Record a recusal event applied at a specific meeting.")
    public ResponseEntity<RecusalEventResponse> recordRecusal(@Valid @RequestBody RecordRecusalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordRecusal(req));
    }

    @GetMapping("/recusals/user/{userId}")
    @PreAuthorize(MANAGE_GUARD)
    @Operation(summary = "List recusal events for a user, most recent first.")
    public List<RecusalEventResponse> recusalsByUser(@PathVariable Long userId) {
        return service.listRecusalsByUser(userId);
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private static Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}
