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
 *   <li>Declaration submission is {@code isAuthenticated()} — see below.</li>
 *   <li>Read endpoints share the management role set — a future
 *       slice will scope per-user reads via row-level checks.</li>
 * </ul>
 *
 * <h3>⚠ F2-144 — submission was open to nobody, and the obvious fix would have opened impersonation</h3>
 * <p>This javadoc used to say <i>"Declaration submission additionally allows {@code USER} (any
 * authenticated employee can submit their own attestation)"</i>. <b>That capability had never worked.</b>
 * {@code SUBMIT_GUARD} recited {@code 'ETHICS_OFFICER'} and {@code 'USER'} and <b>neither name exists in
 * any row of {@code iam.roles}</b> — both legs were permanently false — so submission reduced to
 * {@code PLATFORM_ADMIN │ BOARD_SECRETARY │ COMPLIANCE_OFFICER}: a Nazaha conflict-of-interest campaign
 * ran with every declarant locked out and three administrators filing on everyone's behalf.
 *
 * <p><b>The order of the fix was not optional.</b> Widening this guard was unsafe while
 * {@code submitDeclaration} took {@code userId} from the request body and stored it verbatim, because any
 * authenticated caller could then have attested <i>in another person's name</i> — the one outcome a COI
 * process exists to prevent. F2-144(a) moved the choice of declarant to the server
 * ({@code CoiService#declarantFor}: a holder of a COI management authority may still file on an
 * employee's behalf, everyone else declares for themselves and the body's {@code userId} is ignored).
 * Only then did (b) open the guard.
 *
 * <p><b>Why {@code isAuthenticated()} and not a permission.</b> ADR-010 makes
 * {@code hasPermission(MODULE, ACTION)} the rule for writes; its §Sanctioned exceptions carves out
 * endpoints that discharge an obligation every employee is under, following ADR-017 D-7's ruling on
 * knowledge-base reads. Declaring a conflict of interest is such an obligation: a grant list can only
 * ever wrongly <em>exclude</em> a declarant, and an employee who cannot file is a compliance failure
 * that looks like the employee's fault. Reading how to declare is already open for that reason — the
 * <i>"Disclosing a Conflict of Interest (Nazaha)"</i> article is one of D-7's worked examples — so gating
 * the act itself behind a grant would have left the platform explaining the duty to people it then
 * refused. Pinned by {@code CoiSubmitGuardTest} and enumerated in
 * {@code ControllerAuthorizationRatchetTest.SELF_SCOPED} so a uniformity sweep cannot quietly "fix" it.
 *
 * <p>Campaign management, review and recusal keep the role set: those are authority acts over
 * <em>other people's</em> declarations, and they are the correct target for a later F2-05 conversion to
 * {@code ETHICS:*} permissions. {@code ETHICS_OFFICER} stays a ghost leg there — dropping it would
 * silently ratify compliance-officer ownership of ethics oversight, which is F2-145's reasoning, and no
 * such decision has been made.
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
    /** See the class javadoc. Every employee is under the obligation this endpoint discharges, and the
     *  declarant is chosen by the server (F2-144a), not by the caller's body. */
    private static final String SUBMIT_GUARD = "isAuthenticated()";

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
