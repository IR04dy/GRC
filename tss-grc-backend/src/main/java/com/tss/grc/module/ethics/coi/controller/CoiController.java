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
 * {@link TenantContext}. Authorisation is by permission, not by role name (F2-145(e) slice 6):
 *
 * <ul>
 *   <li>Reads → {@code ETHICS:READ}; campaign transitions and recusal records →
 *       {@code ETHICS:WRITE}; declaration review → {@code ETHICS:APPROVE}. All three are held by
 *       exactly the three roles the single {@code MANAGE_GUARD} role list used to name, so the
 *       population is unchanged and a tenant can now separate reading the register from running it.</li>
 *   <li>Declaration submission is {@code isAuthenticated()} — see below.</li>
 * </ul>
 *
 * <p>The three actions existed in {@code iam.permissions} before this class consulted any of them, with
 * descriptions naming COI declarations and recusals — they were catalogued <em>for</em> these endpoints
 * and never wired to them. V371 makes them faithful and this class makes them observable.</p>
 *
 * <h3>⚠ F2-144 — submission was open to nobody, and the obvious fix would have opened impersonation</h3>
 * <p>This javadoc used to say <i>"Declaration submission additionally allows {@code USER} (any
 * authenticated employee can submit their own attestation)"</i>. <b>That capability had never worked.</b>
 * {@code SUBMIT_GUARD} recited {@code 'ETHICS_OFFICER'} and {@code 'USER'} and <b>neither name existed in
 * any row of {@code iam.roles}</b> at the time — both legs were permanently false — so submission reduced to
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
 * <p>Campaign management, review and recusal were the "correct target for a later F2-05 conversion to
 * {@code ETHICS:*} permissions" that this class's previous comment predicted, and slice 6 is it.
 * {@code ETHICS_OFFICER} is <b>no longer a ghost</b>: V362 seated it (id 10076), and V371 grants it all
 * six ETHICS actions. That matters more as a warning than as trivia — the comment here said the leg was
 * permanently false for as long as it was true, and a future reader deciding whether to drop it would
 * have been reading about a state that had already changed underneath them.
 */
@RestController
@RequestMapping("/api/v1/coi")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "Ethics — Conflict of Interest (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class CoiController {

    // ── Authorisation ────────────────────────────────────────────────────────
    // F2-145(e) slice 6. One hasAnyRole('PLATFORM_ADMIN','ETHICS_OFFICER','BOARD_SECRETARY',
    // 'COMPLIANCE_OFFICER') recitation over 11 endpoints, split by what the endpoint does. Populations
    // measured against iam.role_permissions and preserved exactly (PLATFORM_ADMIN keeps access through
    // ModulePermissionEvaluator's bypass rather than through a name in a list):
    //
    //   READ     3  ETHICS_OFFICER BOARD_SECRETARY COMPLIANCE_OFFICER — the COI register
    //   WRITE    3  …the same three                                   — campaign transitions, recusals
    //   APPROVE  3  …the same three                                   — decide a declaration's outcome
    //
    // Same population, three actions: a tenant that wants a board secretary to read the register without
    // running campaigns can now express it as two grants instead of a jar. ⚠ The three are deliberately
    // NOT the hotline's actions — see WhistleblowerCaseController and V371's header. ETHICS:READ used to
    // be granted to INTERNAL_AUDITOR as well; V371 revoked it, because the role list here never named
    // internal audit and this action is now what that list means.
    private static final String READ_GUARD   = "hasPermission('ETHICS','READ')";
    private static final String WRITE_GUARD  = "hasPermission('ETHICS','WRITE')";
    private static final String REVIEW_GUARD = "hasPermission('ETHICS','APPROVE')";
    /** See the class javadoc. Every employee is under the obligation this endpoint discharges, and the
     *  declarant is chosen by the server (F2-144a), not by the caller's body. */
    private static final String SUBMIT_GUARD = "isAuthenticated()";

    private final CoiService service;

    // ─── Campaigns ──────────────────────────────────────────────────

    @PostMapping("/campaigns")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Create a COI declaration campaign (FY annual or ad-hoc).")
    public ResponseEntity<CampaignResponse> createCampaign(@Valid @RequestBody CreateCampaignRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCampaign(req));
    }

    @GetMapping("/campaigns")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "List COI campaigns for the current tenant.")
    public List<CampaignResponse> listCampaigns() {
        return service.listCampaignsByOrg(TenantContext.requireOrganisationId());
    }

    @GetMapping("/campaigns/{id}")
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get a single COI campaign.")
    public CampaignResponse getCampaign(@PathVariable Long id) {
        return service.getCampaign(id);
    }

    @PostMapping("/campaigns/{id}/open")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition campaign DRAFT → OPEN.")
    public CampaignResponse openCampaign(@PathVariable Long id) {
        return service.openCampaign(id);
    }

    @PostMapping("/campaigns/{id}/close")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition campaign OPEN → CLOSED.")
    public CampaignResponse closeCampaign(@PathVariable Long id) {
        return service.closeCampaign(id);
    }

    @PostMapping("/campaigns/{id}/archive")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Transition campaign CLOSED → ARCHIVED.")
    public CampaignResponse archiveCampaign(@PathVariable Long id) {
        return service.archiveCampaign(id);
    }

    @GetMapping("/campaigns/{id}/declarations")
    @PreAuthorize(READ_GUARD)
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
    @PreAuthorize(READ_GUARD)
    @Operation(summary = "Get a single declaration with its disclosed items.")
    public DeclarationResponse getDeclaration(@PathVariable Long id) {
        return service.getDeclaration(id);
    }

    @PostMapping("/declarations/{id}/review")
    @PreAuthorize(REVIEW_GUARD)
    @Operation(summary = "Ethics Officer reviews a declaration: set outcome + notes.")
    public DeclarationResponse reviewDeclaration(@PathVariable Long id,
                                                   @Valid @RequestBody ReviewDeclarationRequest req,
                                                   Authentication auth) {
        return service.reviewDeclaration(id, req, userId(auth));
    }

    // ─── Recusals ───────────────────────────────────────────────────

    @PostMapping("/recusals")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "Record a recusal event applied at a specific meeting.")
    public ResponseEntity<RecusalEventResponse> recordRecusal(@Valid @RequestBody RecordRecusalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordRecusal(req));
    }

    @GetMapping("/recusals/user/{userId}")
    @PreAuthorize(READ_GUARD)
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
