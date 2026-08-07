package com.tss.grc.module.ethics.whistleblower.controller;

import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CloseRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.InvestigationUpdateRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.PublicCaseResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.ReporterReplyRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.TriageRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.UpdateResponse;
import com.tss.grc.module.ethics.whistleblower.service.WhistleblowerCaseService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST surface for the Sprint 13 Whistleblower / Ethics Hotline.
 *
 * <h3>Public surface (permitAll in SecurityConfig)</h3>
 * <ul>
 *   <li>{@code POST /api/v1/ethics/whistleblower/public/submit}</li>
 *   <li>{@code GET  /api/v1/ethics/whistleblower/public/status/{token}}</li>
 *   <li>{@code POST /api/v1/ethics/whistleblower/public/reply/{token}}</li>
 * </ul>
 *
 * <h3>Internal triage surface</h3>
 * <p>Authorisation is by permission, not by role name (F2-145(e) slice 6). Read is
 * {@code ETHICS:READ_CASES} and write (triage / investigation / close) is {@code ETHICS:TRIAGE} — two
 * actions V371 created, because the hotline is <em>not</em> the conflict-of-interest register and the
 * module's pre-existing {@code ETHICS:READ} / {@code ETHICS:WRITE} are.
 *
 * <p><b>Why a separate read action rather than {@code ETHICS:READ}.</b> {@code ETHICS:READ} is held by
 * {@code BOARD_SECRETARY}, and this controller's role list never was. A hotline case may name — or be
 * about — the board, so the exclusion is the control; reusing the register's action would have put a
 * board secretary inside every case file. ⚠ That widening was <b>invisible to a population diff</b>: with
 * {@code BOARD_SECRETARY} at zero active users, before and after are the same set of people. See V371's
 * header for the full measurement.
 *
 * <p>{@code AUDIT_COMMITTEE_MEMBER} was the fifth name in the old read list and has no row in
 * {@code iam.roles} — a leg that could never be true. Dropped rather than laundered into a grant (F2-52):
 * the intent, an audit committee reading the hotline, needs committee membership to be a <em>seat</em>
 * (F2-26), which is where it is filed.
 */
@RestController
@RequestMapping("/api/v1/ethics/whistleblower")
@RequiredArgsConstructor
@RequireModule("ETHICS")
@Tag(name = "Ethics — Whistleblower Hotline (Sprint 13)")
@SecurityRequirement(name = "bearerAuth")
public class WhistleblowerCaseController {

    // ── Authorisation ────────────────────────────────────────────────────────
    // F2-145(e) slice 6. Two hasAnyRole(...) recitations over 9 endpoints. Populations measured against
    // iam.role_permissions and preserved exactly (PLATFORM_ADMIN keeps access through
    // ModulePermissionEvaluator's bypass; AUDIT_COMMITTEE_MEMBER is absent from iam.roles and was never
    // access at all):
    //
    //   READ_CASES  3  ETHICS_OFFICER COMPLIANCE_OFFICER INTERNAL_AUDITOR — the case file
    //   TRIAGE      2  …minus INTERNAL_AUDITOR, which reads a case without directing it
    //
    // ⚠ Neither is ETHICS:READ / ETHICS:WRITE, and the difference is one role — BOARD_SECRETARY, who
    // curates the COI register and is not inside hotline case files. Both are unstaffed in dev, so the
    // distinction does not appear in a user-population diff; the guard's shape is the only record of it.
    private static final String READ_GUARD   = "hasPermission('ETHICS','READ_CASES')";
    private static final String TRIAGE_GUARD = "hasPermission('ETHICS','TRIAGE')";

    private final WhistleblowerCaseService service;

    // ─── Public submitter-facing endpoints ──────────────────────────

    @PostMapping("/public/submit")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Public submission — anonymous-by-default. Token returned ONCE; not recoverable.",
               description = "The portal link MUST carry the tenant's public short code as ?org=. "
                           + "There is no JWT on this path, so nothing else identifies the receiving "
                           + "organisation; an unknown or inactive code is refused with 404 and never "
                           + "defaulted (F2-83). A logged-in employee filing through the same form is "
                           + "scoped by their own tenant and the parameter is ignored.")
    public ResponseEntity<CaseSubmissionResponse> publicSubmit(
            @Valid @RequestBody CaseSubmissionRequest req,
            @RequestParam(name = "org", required = false) String org) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submitCase(req, org));
    }

    @GetMapping("/public/status/{token}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Reporter-facing status check by opaque tracking token.")
    public PublicCaseResponse publicStatus(@PathVariable UUID token) {
        return service.lookupByToken(token);
    }

    @PostMapping("/public/reply/{token}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Reporter posts a follow-up message via their tracking token.")
    public PublicCaseResponse publicReply(@PathVariable UUID token,
                                           @Valid @RequestBody ReporterReplyRequest req) {
        return service.reportByToken(token, req);
    }

    // ─── Internal triage / investigation / close ────────────────────

    @GetMapping("/cases")
    @PreAuthorize(READ_GUARD)
    public List<CaseResponse> list(@RequestParam(value = "status", required = false) String status,
                                    @RequestParam(value = "severity", required = false) String severity) {
        if (status != null && !status.isBlank()) {
            return service.listByStatus(status);
        }
        if (severity != null && !severity.isBlank()) {
            return service.listBySeverity(severity);
        }
        return service.list();
    }

    @GetMapping("/cases/stats")
    @PreAuthorize(READ_GUARD)
    public Map<String, Long> stats() {
        return service.stats();
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize(READ_GUARD)
    public CaseResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/cases/{id}/updates")
    @PreAuthorize(READ_GUARD)
    public List<UpdateResponse> updates(@PathVariable Long id) {
        return service.listUpdates(id);
    }

    @PostMapping("/cases/{id}/triage")
    @PreAuthorize(TRIAGE_GUARD)
    public CaseResponse triage(@PathVariable Long id,
                                @Valid @RequestBody TriageRequest req,
                                Authentication auth) {
        return service.triage(id, req, userId(auth));
    }

    @PostMapping("/cases/{id}/start-investigation")
    @PreAuthorize(TRIAGE_GUARD)
    public CaseResponse startInvestigation(@PathVariable Long id,
                                             @RequestParam Long investigatorUserId,
                                             @RequestParam(required = false) String openingNoteEn,
                                             @RequestParam(required = false) String openingNoteAr,
                                             Authentication auth) {
        return service.startInvestigation(id, investigatorUserId,
                openingNoteEn, openingNoteAr, userId(auth));
    }

    @PostMapping("/cases/{id}/investigation-update")
    @PreAuthorize(TRIAGE_GUARD)
    public UpdateResponse investigationUpdate(@PathVariable Long id,
                                                @Valid @RequestBody InvestigationUpdateRequest req,
                                                Authentication auth) {
        return service.investigationUpdate(id, req, userId(auth));
    }

    @PostMapping("/cases/{id}/close")
    @PreAuthorize(TRIAGE_GUARD)
    public CaseResponse close(@PathVariable Long id,
                                @Valid @RequestBody CloseRequest req,
                                Authentication auth) {
        return service.close(id, req, userId(auth));
    }

    @PostMapping("/cases/{id}/link-capa")
    @PreAuthorize(TRIAGE_GUARD)
    public CaseResponse linkCapa(@PathVariable Long id,
                                   @RequestParam Long capaTaskId) {
        return service.escalateToCapa(id, capaTaskId);
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private static Long userId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails ud) return ud.getUserId();
        return null;
    }
}
