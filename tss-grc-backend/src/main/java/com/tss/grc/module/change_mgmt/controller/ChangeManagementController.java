package com.tss.grc.module.change_mgmt.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;

import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.security.GrcUserDetails;

import com.tss.grc.module.change_mgmt.dto.CabVoteRequest;
import com.tss.grc.module.change_mgmt.dto.ChangeConditionRequest;
import com.tss.grc.module.change_mgmt.dto.RatificationDtos;

import com.tss.grc.module.change_mgmt.entity.*;
import com.tss.grc.module.change_mgmt.service.ChangeGovernanceHealthService;
import com.tss.grc.module.change_mgmt.service.ChangeManagementService;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import com.tss.grc.module.change_mgmt.spi.EvidencePort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Change Management REST surface.
 *
 * Permission key is "CHANGE" (CM-C1, V301) — a dedicated module, so change
 * authorship/approval is no longer granted implicitly by the platform-shared
 * "WORKFLOW" gate. Read-only/observer/external and telemetry-ingest personas
 * are excluded from CHANGE write/approve/delete by the V301 grant matrix.
 */
@RestController
@RequestMapping("/api/v1/changes")
@RequiredArgsConstructor
@RequireModule("CHANGE")
@Tag(name = "Change Management — ITIL v4")
@SecurityRequirement(name = "bearerAuth")
public class ChangeManagementController {

    private final ChangeManagementService service;
    private final ChangeGovernanceHealthService governanceHealth;

    // ── Listing + CRUD ──────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public Page<ChangeRequest> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String risk,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.list(status, type, risk, category, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public ChangeRequest get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    // F2-30: @Valid so the new @NotBlank on titleAr reports as a 400 naming the field
    // through the MethodArgumentNotValidException handler, rather than failing at flush and
    // arriving as a TransactionSystemException. ⚠ @Valid fires BEFORE @PreAuthorize, so
    // permission probes against this endpoint now need a valid body (CLAUDE.md landmine).
    // The entity binding itself is F2-22a's scope, not this slice's.
    public ChangeRequest create(@Valid @RequestBody ChangeRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    public ChangeRequest update(@PathVariable Long id, @Valid @RequestBody ChangeRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('CHANGE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // ── Lifecycle transition ────────────────────────────────────────────────

    /**
     * Move a change to a target status.
     *
     * <p><b>F2-29 — the annotation below is the baseline, not the whole rule.</b> {@code CHANGE:WRITE}
     * authorises the operational moves (submit, assess, schedule, implement, review, close, cancel).
     * The three targets that <em>are</em> CAB decisions — {@code EMERGENCY_APPROVED},
     * {@code CAB_APPROVED}, {@code CAB_REJECTED} — additionally require {@code CHANGE:APPROVE},
     * enforced in {@code ChangeManagementService.requireDecisionAuthority} because the target arrives
     * in the body and cannot be read by a legible SpEL expression. See that method for the
     * measurement: a {@code WRITE}-only persona could raise an EMERGENCY change and approve it
     * itself in one hop, while the same persona was correctly refused by {@code POST /{id}/approvals}.
     *
     * <p>F2-29's remaining half is presentational — splitting this endpoint into named commands
     * ({@code /approve-emergency}, {@code /cab-decide}) so the URL states the privilege and a
     * {@code @PreAuthorize} scan can see it. That is an API change with FE churn and no additional
     * authorisation value, so it stayed out of this slice.
     */
    @PostMapping("/{id}/transition")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @Operation(summary = "Move a change request to a target status; validates ITIL v4 transitions. "
        + "CAB-decision targets (EMERGENCY_APPROVED / CAB_APPROVED / CAB_REJECTED) also require CHANGE:APPROVE.")
    public ChangeRequest transition(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String target = body.get("target") != null ? body.get("target").toString() : null;
        if (target == null) throw new IllegalArgumentException("target is required");
        return service.transition(id, target, body);
    }

    // ── Approvals (CAB) ─────────────────────────────────────────────────────

    @GetMapping("/{id}/approvals")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeApproval> approvals(@PathVariable Long id) {
        return service.approvals(id);
    }

    /**
     * Record the caller's CAB vote on a change.
     *
     * <p><b>DF-4 / F2-24.</b> The approver is the <b>authenticated principal</b>. The body
     * is a {@link CabVoteRequest} carrying only the decision, the voting capacity and a
     * rationale — it no longer accepts an approver name or id, because the CM-C2
     * segregation-of-duties gate used to compare that claimed name and a requester could
     * therefore approve their own change by naming somebody else.
     *
     * <p>The guard is {@code CHANGE:APPROVE}, not {@code CHANGE:WRITE}: raising a change
     * and authorising one are different privileges, and the permission already exists and
     * is granted by the V301 matrix.
     */
    @PostMapping("/{id}/approvals")
    @PreAuthorize("hasPermission('CHANGE','APPROVE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeApproval recordApproval(@PathVariable Long id,
                                         @Valid @RequestBody CabVoteRequest body,
                                         Authentication auth) {
        return service.recordApproval(id, body, userIdOf(auth), usernameOf(auth));
    }

    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }

    private static String usernameOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUsername();
        return auth != null ? auth.getName() : null;
    }

    // ── Evidence (S8a / CE-9) ───────────────────────────────────────────────
    //
    // Attaches, never uploads: the item is created, hashed, versioned and four-eyes reviewed inside
    // the evidence subsystem. Guarded CHANGE — the subject is a change record, and the evidence
    // module guards its own store — and WRITE rather than APPROVE, because attaching an artefact is
    // clerical work; what makes it count is the evidence subsystem's own review.

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<EvidencePort.EvidenceRef> evidence(@PathVariable Long id) {
        return service.evidenceForChange(id);
    }

    @PostMapping("/{id}/evidence")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public EvidencePort.EvidenceRef attachEvidence(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body) {
        Object ev = body == null ? null : body.get("evidenceId");
        if (ev == null) throw new IllegalArgumentException("evidenceId is required");
        return service.attachEvidenceToChange(id, Long.valueOf(ev.toString()), strFrom(body, "notes"));
    }

    @GetMapping("/conditions/{conditionId}/evidence")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<EvidencePort.EvidenceRef> conditionEvidence(@PathVariable Long conditionId) {
        return service.evidenceForCondition(conditionId);
    }

    @PostMapping("/conditions/{conditionId}/evidence")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public EvidencePort.EvidenceRef attachConditionEvidence(@PathVariable Long conditionId,
                                                           @RequestBody Map<String, Object> body) {
        Object ev = body == null ? null : body.get("evidenceId");
        if (ev == null) throw new IllegalArgumentException("evidenceId is required");
        return service.attachEvidenceToCondition(conditionId, Long.valueOf(ev.toString()),
                                                 strFrom(body, "notes"));
    }

    // ── Conditions register (S7e / CE-5) ────────────────────────────────────
    //
    // ⚠ Note the guard split, which is the whole authorisation statement of this feature.
    // Imposing a condition and waiving one are APPROVAL acts — one adds an obligation as the price
    // of approval, the other forgives it — so both demand CHANGE:APPROVE (9 roles). Recording that
    // an obligation was met is operational work, so it needs only CHANGE:WRITE (42 roles). F2-29's
    // classification, applied to a new surface rather than discovered on it later.

    @GetMapping("/{id}/conditions")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeCondition> conditions(@PathVariable Long id) { return service.conditions(id); }

    /** The tenant-wide blocker list: every open obligation, soonest due first, undated last. */
    @GetMapping("/conditions/open")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeCondition> openConditions() { return service.openConditions(); }

    @PostMapping("/{id}/conditions")
    @PreAuthorize("hasPermission('CHANGE','APPROVE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeCondition addCondition(@PathVariable Long id,
                                        @Valid @RequestBody ChangeConditionRequest body) {
        return service.addCondition(id, body);
    }

    @PostMapping("/conditions/{conditionId}/satisfy")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    public ChangeCondition satisfyCondition(@PathVariable Long conditionId,
                                            @RequestBody(required = false) Map<String, Object> body) {
        return service.satisfyCondition(conditionId, strFrom(body, "notes"));
    }

    @PostMapping("/conditions/{conditionId}/waive")
    @PreAuthorize("hasPermission('CHANGE','APPROVE')")
    public ChangeCondition waiveCondition(@PathVariable Long conditionId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        // The reason is validated in the service (and by ck_cc_waiver_reason), not with @NotBlank on
        // a wrapper DTO: @Valid fires BEFORE @PreAuthorize, so a bean-validated body would answer 400
        // to a caller who should have been told 403 — the landmine CLAUDE.md records.
        return service.waiveCondition(conditionId, strFrom(body, "reason"));
    }

    private static String strFrom(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    // ── Tasks ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/tasks")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeTask> tasks(@PathVariable Long id) { return service.tasks(id); }

    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeTask addTask(@PathVariable Long id, @Valid @RequestBody ChangeTask body) { return service.addTask(id, body); }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    public ChangeTask updateTask(@PathVariable Long taskId, @Valid @RequestBody ChangeTask body) { return service.updateTask(taskId, body); }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasPermission('CHANGE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long taskId) { service.deleteTask(taskId); }

    // ── Links (cross-module integration) ────────────────────────────────────

    @GetMapping("/{id}/links")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeLink> links(@PathVariable Long id) { return service.links(id); }

    @PostMapping("/{id}/links")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeLink addLink(@PathVariable Long id, @RequestBody ChangeLink body) { return service.addLink(id, body); }

    @DeleteMapping("/links/{linkId}")
    @PreAuthorize("hasPermission('CHANGE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(@PathVariable Long linkId) { service.deleteLink(linkId); }

    /** Inverse lookup — used by other module pages to render their "Linked Changes" panel. */
    @GetMapping("/links/by-entity")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "List all changes linked to a given (entityType, entityId) pair")
    public List<Map<String, Object>> linksForEntity(
            @RequestParam String entityType,
            @RequestParam Long entityId) {
        return service.linksForEntity(entityType, entityId);
    }

    // ── Track D: route a change's CAB decision through the committee engine ───
    @PostMapping("/{id}/refer-to-committee")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public CommitteePort.ReferralView referToCommittee(@PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body, Principal principal) {
        Long committeeId = (body != null && body.get("committeeId") != null)
            ? Long.valueOf(body.get("committeeId").toString()) : null;
        return service.referToCommittee(id, committeeId, principal != null ? principal.getName() : null);
    }

    @GetMapping("/{id}/committee")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public Map<String, Object> committeeReferral(@PathVariable Long id) {
        return service.committeeReferral(id);
    }

    @PostMapping("/{id}/committee/resolution")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    public CommitteePort.ReferralView linkChangeResolution(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long resolutionId = Long.valueOf(body.get("resolutionId").toString());
        return service.linkChangeResolution(id, resolutionId);
    }

    // ── Comments ────────────────────────────────────────────────────────────

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeComment> comments(@PathVariable Long id) { return service.comments(id); }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeComment addComment(@PathVariable Long id, @RequestBody ChangeComment body) { return service.addComment(id, body); }

    // ── Analytics ───────────────────────────────────────────────────────────

    @GetMapping("/analytics")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public Map<String, Object> analytics() { return service.analytics(); }

    // ── CH-17: authorisation matrix ──────────────────────────────────────────

    /**
     * S4/W1-4 (finding F-4): is this tenant's committee routing actually configured?
     * The authorisation matrix can demand CAB/ECAB while no committee is mapped — an
     * absent configuration row, not an error, so it needs to be asked about explicitly.
     *
     * <p><b>ADR-017 D-2 corrected the second half of this sentence.</b> It used to end
     * "…referrals fail and changes fall back to the legacy tally", which was true and was
     * the defect: a gate the tenant could not fill was answered by approving anyway.
     * Those approvals are now refused (409 {@code change-authority-not-configured}), so
     * this endpoint reports a condition that blocks work rather than one that silently
     * downgrades it — and {@code configured} here is the same predicate the refusal uses.
     */
    @GetMapping("/governance-health")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "Committee gate-map configuration health for this organisation")
    public Map<String, Object> governanceHealth() {
        return governanceHealth.currentTenantHealth();
    }

    @GetMapping("/authorisation-matrix")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "The tenant's (change type x risk) authorisation matrix — the gate + quorum per cell.")
    public List<ChangeAuthorisationMatrix> authorisationMatrix() { return service.authorisationMatrix(); }

    @GetMapping("/{id}/authorisation")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "Resolve the authorisation required for a specific change and whether it is satisfied.")
    public Map<String, Object> authorisation(@PathVariable Long id) { return service.resolveAuthorisation(id); }

    // ── A17-D3: emergency-change retrospective ratification ───────────────────

    /**
     * Record the CAB's retrospective decision on an emergency change.
     *
     * <p>Guard is {@code CHANGE:APPROVE}, matching the CAB-vote endpoint: ratification
     * IS a CAB decision, taken after the fact. {@code CHANGE:WRITE} authorises raising
     * and editing a change, not authorising one.
     *
     * <p>Returns 409 when the caller emergency-approved, raised or owns the change — the
     * segregation-of-duties gate is the whole point of retrospective ratification, and a
     * 409 with an SoD message is the feature working.
     */
    @PostMapping("/{id}/ratify")
    @PreAuthorize("hasPermission('CHANGE','APPROVE')")
    @Operation(summary = "Retrospectively ratify (or refuse to ratify) an implemented emergency change — ADR-017 D-3.")
    public ChangeRequest ratify(@PathVariable Long id,
                               @Valid @RequestBody RatificationDtos.RatifyRequest body,
                               Authentication auth) {
        return service.ratify(id, body.decision(), body.notes(), userIdOf(auth), usernameOf(auth));
    }

    /**
     * The interim unauthorised-change register: emergency changes carrying a
     * ratification duty, overdue first. CE-11 will widen this surface, not replace it.
     */
    @GetMapping("/ratification-register")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "Emergency changes awaiting or past their CAB ratification deadline.")
    public Map<String, Object> ratificationRegister() { return service.ratificationRegister(); }

    @GetMapping("/ratification-policy")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "This tenant's emergency-ratification SLA (business days) and escalation role.")
    public ChangeRatificationPolicy ratificationPolicy() { return service.ratificationPolicy(); }

    /**
     * Change the tenant's ratification SLA, within the ADR-017 D-3 amendment-2 ceiling.
     *
     * <p>{@code CHANGE:APPROVE} rather than {@code CHANGE:WRITE}: this field decides how
     * long an unauthorised production change may stand before the CISO is told, so it is
     * a governance setting, not change authorship. Editing it must not be inside the
     * privilege of the people whose changes it measures.
     */
    @PutMapping("/ratification-policy")
    @PreAuthorize("hasPermission('CHANGE','APPROVE')")
    @Operation(summary = "Set the emergency-ratification SLA (1..10 business days) and escalation role.")
    public ChangeRatificationPolicy updateRatificationPolicy(
            @Valid @RequestBody RatificationDtos.RatificationPolicyRequest body,
            Authentication auth) {
        return service.updateRatificationPolicy(body.slaBusinessDays(), body.escalationRole(), userIdOf(auth));
    }

    // ── CH-17: freeze windows + change calendar + conflicts ───────────────────

    @GetMapping("/freeze-windows")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    public List<ChangeFreezeWindow> freezeWindows() { return service.freezeWindows(); }

    @PostMapping("/freeze-windows")
    @PreAuthorize("hasPermission('CHANGE','WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeFreezeWindow createFreezeWindow(@Valid @RequestBody ChangeFreezeWindow body) {
        return service.createFreezeWindow(body);
    }

    @DeleteMapping("/freeze-windows/{id}")
    @PreAuthorize("hasPermission('CHANGE','DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFreezeWindow(@PathVariable Long id) { service.deleteFreezeWindow(id); }

    @GetMapping("/calendar")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "Scheduled changes + active freeze windows intersecting [from,to].")
    public Map<String, Object> calendar(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        java.time.LocalDateTime f = parseDt(from, java.time.LocalDateTime.now().minusDays(7));
        java.time.LocalDateTime t = parseDt(to, f.plusDays(120));
        return service.calendar(f, t);
    }

    @GetMapping("/{id}/conflicts")
    @PreAuthorize("hasPermission('CHANGE','READ')")
    @Operation(summary = "Other live changes whose scheduled window overlaps this change's.")
    public List<Map<String, Object>> conflicts(@PathVariable Long id) { return service.conflicts(id); }

    /** Accept either an ISO date-time ("2026-12-25T09:00:00") or a plain date ("2026-12-25"). */
    private static java.time.LocalDateTime parseDt(String s, java.time.LocalDateTime dflt) {
        if (s == null || s.isBlank()) return dflt;
        try { return java.time.LocalDateTime.parse(s); }
        catch (Exception e) { return java.time.LocalDate.parse(s).atStartOfDay(); }
    }
}
