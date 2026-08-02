package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.exception.ChangeAuthorityNotConfiguredException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.util.SaudiWorkdayUtil;
import com.tss.grc.module.change_mgmt.model.RatificationStates;
import com.tss.grc.module.platform.calendar.service.SaudiWorkdayCalendar;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.tss.grc.module.change_mgmt.dto.CabVoteRequest;
import com.tss.grc.module.change_mgmt.entity.*;
import com.tss.grc.module.change_mgmt.repository.*;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import com.tss.grc.module.change_mgmt.spi.IncidentPort;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Change Management lifecycle service. Implements ITIL v4 Change Enablement
 * states + transitions, enforces type-aware path (NORMAL goes through CAB,
 * EMERGENCY skips it, STANDARD is pre-approved).
 */
@Service
@RequiredArgsConstructor
public class ChangeManagementService {

    /** Forward transitions allowed from a given status (NORMAL/STANDARD path).
     *  Map.of caps at 10 entries — use Map.ofEntries for 13. */
    private static final Map<String, Set<String>> NORMAL_TRANSITIONS = Map.ofEntries(
        Map.entry("DRAFT",              Set.of("SUBMITTED", "EMERGENCY_APPROVED", "CANCELLED")),
        Map.entry("SUBMITTED",          Set.of("IMPACT_ASSESSED", "CANCELLED")),
        Map.entry("IMPACT_ASSESSED",    Set.of("CAB_PENDING", "CANCELLED")),
        Map.entry("CAB_PENDING",        Set.of("CAB_APPROVED", "CAB_REJECTED")),
        Map.entry("CAB_APPROVED",       Set.of("SCHEDULED", "CANCELLED")),
        Map.entry("SCHEDULED",          Set.of("IMPLEMENTING", "CANCELLED")),
        Map.entry("EMERGENCY_APPROVED", Set.of("IMPLEMENTING", "CANCELLED")),
        Map.entry("IMPLEMENTING",       Set.of("IMPLEMENTED", "ROLLED_BACK")),
        Map.entry("IMPLEMENTED",        Set.of("POST_REVIEW")),
        Map.entry("POST_REVIEW",        Set.of("CLOSED")),
        Map.entry("ROLLED_BACK",        Set.of("POST_REVIEW", "CLOSED")),
        Map.entry("CAB_REJECTED",       Set.of("DRAFT")),    // can be reworked
        Map.entry("CANCELLED",          Set.<String>of()),
        Map.entry("CLOSED",             Set.<String>of())
    );

    // CH-2: allowed enum values, mirrored by the V300 DB CHECK constraints.
    private static final Set<String> CHANGE_TYPES = Set.of("STANDARD", "NORMAL", "EMERGENCY");
    private static final Set<String> RISK_LEVELS  = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> OUTCOMES     = Set.of("SUCCESS", "PARTIAL_SUCCESS", "FAILED", "ROLLED_BACK");

    /** DF-4: CAB decisions a vote may carry. */
    private static final Set<String> CAB_DECISIONS = Set.of("APPROVE", "REJECT", "DEFER", "ABSTAIN");

    /**
     * F2-29 — the transition targets that <b>are CAB decisions</b> rather than operational moves.
     *
     * <p>Raising a change and authorising one are different privileges. {@code POST /{id}/approvals}
     * has always said so ({@code CHANGE:APPROVE}); {@code POST /{id}/transition} did not, and it can
     * reach the same outcome. <b>Measured before the fix</b> with {@code ranalyst} — a persona holding
     * {@code CHANGE:WRITE} and <em>not</em> {@code CHANGE:APPROVE} (read off {@code iam.role_permissions},
     * not inferred from the username), against the running stack:
     *
     * <pre>
     *   POST /api/v1/changes/{id}/approvals            -> 403  "you may not decide this change"
     *   POST /api/v1/changes  {changeType: EMERGENCY}  -> 201  DRAFT
     *   POST /api/v1/changes/{id}/transition
     *        {"target":"EMERGENCY_APPROVED"}           -> 200  status=EMERGENCY_APPROVED,
     *                                                          cabDecisionAt stamped
     *   POST /api/v1/changes/{id}/transition
     *        {"target":"IMPLEMENTING"}                 -> 200  ...and on into implementation
     * </pre>
     *
     * <p>Same actor, same privileges, two routes to a CAB decision: one refused it, the other took it.
     * The raiser approved their own emergency change in one hop from {@code DRAFT}, with zero recorded
     * votes and no committee — and the row then <em>claims</em> a CAB decision, because
     * {@code cabDecisionAt} is stamped either way. {@code CAB_REJECTED} behaved the same way: a change
     * killed at CAB with no votes on record.
     *
     * <p><b>How wide.</b> 33 of 57 roles hold {@code CHANGE:WRITE} without {@code CHANGE:APPROVE} —
     * among them {@code DPO}, {@code HR_LEAD}, {@code DLP_REVIEWER}, {@code EVIDENCE_OWNER} and
     * {@code BRAND_MONITORING}. Nine roles hold {@code CHANGE:APPROVE}, and every one of them also
     * holds {@code WRITE} and {@code READ}, so this needed <b>no migration</b>: the V301 grant matrix
     * was already right, only the endpoint disagreed with it.
     *
     * <p><b>Why {@code CAB_APPROVED} is in this set even though it was already protected.</b> Its
     * second leg held up when probed — a no-votes attempt returns 409 ("requires at least 2 recorded
     * APPROVE vote(s)"), because CM-C2 demands recorded votes or a carried committee resolution, and
     * those are cast through {@code APPROVE}-guarded endpoints. So the *decision* was sound; what was
     * missing is that the actor who flips the status also authors {@code cabDecisionNotes}. It is
     * listed for that reason, and because the plan row names all three.
     * ⚠ <b>One operational consequence to be aware of:</b> a clerk who merely records an already-taken
     * committee decision now needs {@code CHANGE:APPROVE}. {@code BOARD_SECRETARY} is exactly that
     * persona and is currently {@code WRITE}-only. If a tenant wants that split, the answer is a
     * recording endpoint, not a broader {@code APPROVE} grant.
     *
     * <p><b>What is deliberately NOT here.</b> {@code SUBMITTED}, {@code IMPACT_ASSESSED},
     * {@code CAB_PENDING}, {@code SCHEDULED}, {@code IMPLEMENTING}, {@code IMPLEMENTED},
     * {@code ROLLED_BACK}, {@code POST_REVIEW}, {@code CLOSED}, {@code CANCELLED} and the
     * {@code CAB_REJECTED → DRAFT} rework hop. Those are operational moves — advancing, executing,
     * withdrawing or documenting a change, not deciding it — and they keep {@code CHANGE:WRITE}. This
     * mirrors the F2-13b call to leave the workflow engine's {@code /review} and finalising endpoints
     * on {@code WRITE}: over-tightening 403s people on their own screens.
     */
    private static final Set<String> DECISION_TARGETS =
        Set.of("CAB_APPROVED", "CAB_REJECTED", "EMERGENCY_APPROVED");

    private final ChangeRequestRepository changeRepo;
    private final ChangeApprovalRepository approvalRepo;
    private final ChangeTaskRepository taskRepo;
    private final ChangeLinkRepository linkRepo;
    private final ChangeCommentRepository commentRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    /** CH-13: auto-open a downstream incident when a change is rolled back.
     *  S5/CE-1: reached through {@link IncidentPort}, so this module no longer imports
     *  `module.incident`. Optional (field-injected) so unit tests that construct the service
     *  directly need not supply it — the rollback hook then no-ops. */
    @Autowired(required = false)
    private IncidentPort incidentPort;

    /** CH-17: per-tenant (type x risk) authorisation matrix driving the CAB
     *  quorum. Optional — when absent the flat cab-min-approvals default applies
     *  (keeps unit tests that construct the service directly working). */
    @Autowired(required = false)
    private ChangeAuthorisationMatrixRepository authMatrixRepo;

    /** CH-17: freeze/blackout windows. Optional — when absent, no freeze gate
     *  is enforced (keeps directly-constructed unit tests working). */
    @Autowired(required = false)
    private ChangeFreezeWindowRepository freezeRepo;

    /** Track D: committee re-route, S5/CE-1: through {@link CommitteePort} rather than the
     *  peer service, so `module.admin.committee` is no longer on this module's import list.
     *  Optional (field-injected) so directly-constructed unit tests need not supply it —
     *  referral endpoints then no-op. */
    @Autowired(required = false)
    private CommitteePort committeePort;

    /** ADR-017 D-2: owns the "is this tenant's CAB/ECAB gate configured?" predicate,
     *  shared with the startup health report so the two cannot drift. Optional
     *  (field-injected) so directly-constructed unit tests need not supply it — but note
     *  that when it IS null the fail-closed check no-ops, so a mocked test that forgets to
     *  set it passes vacuously. {@code ChangeAuthorityFailClosedTest} sets it with
     *  {@code ReflectionTestUtils} for exactly that reason; three of its assertions were
     *  green-because-absent on the first run. */
    @Autowired(required = false)
    private ChangeGovernanceHealthService governanceHealth;

    /** A17-D3: per-tenant emergency-ratification SLA (V345). Optional — but note the
     *  polarity, which is the opposite of every other optional field above: when this
     *  is absent the arming does NOT no-op, it falls back to
     *  {@link RatificationStates#DEFAULT_SLA_BUSINESS_DAYS}. A missing config row must
     *  never mean "no ratification clock" — that is how a control becomes dead
     *  (F2-62), and it is why the V345 self-check THROWs if any live tenant lacks a
     *  policy row rather than letting the default silently cover for it. */
    @Autowired(required = false)
    private ChangeRatificationPolicyRepository ratificationPolicyRepo;

    /** A17-D3: Saudi business-day arithmetic — the working week is Sunday–Thursday and
     *  the tenant's public holidays must not consume the ratification window. Optional;
     *  when absent the clock falls back to plain Fri/Sat-skipping arithmetic
     *  ({@code SaudiWorkdayUtil}) rather than to calendar days, so a directly-
     *  constructed unit test still gets a business-day answer. */
    @Autowired(required = false)
    private SaudiWorkdayCalendar workdayCalendar;

    /** CM-C2: minimum recorded APPROVE votes required before a change may move to
     *  CAB_APPROVED. Configurable per deployment; defaults to 1 (four-eyes). */
    @Value("${grc.change.cab-min-approvals:1}")
    private int cabMinApprovals = 1;

    private void emitChange(ChangeRequest cr) {
        if (bcast != null) bcast.entity(cr.getOrganisationId(), "change_request", cr.getId(), cr);
    }

    // ── F8 payload + emit ─────────────────────────────────────────────────────
    //
    // Change Management is the ITIL v4 Change Enablement workflow,
    // high-visibility for the audit committee. F8 events let downstream
    // consumers (CMDB sync, BCM-impact notifications, post-implementation
    // reviews) react to specific lifecycle moments. The state machine
    // has 14 states; events are emitted for the 7 milestones that
    // matter most to downstream consumers.

    private Map<String, Object> payloadFor(ChangeRequest c, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("changeId",         nullSafeStr(c.getId()));
        m.put("changeCode",       nullSafeStr(c.getChangeCode()));
        m.put("status",           nullSafeStr(c.getStatus()));
        m.put("previousStatus",   nullSafeStr(previousStatus));
        m.put("changeType",       nullSafeStr(c.getChangeType()));
        m.put("category",         nullSafeStr(c.getCategory()));
        m.put("riskLevel",        nullSafeStr(c.getRiskLevel()));
        m.put("outcome",          nullSafeStr(c.getOutcome()));
        m.put("requestedByName",  nullSafeStr(c.getRequestedByName()));
        m.put("changeOwnerName",  nullSafeStr(c.getChangeOwnerName()));
        m.put("scheduledStart",   nullSafeStr(c.getScheduledStart()));
        return m;
    }

    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }
    private static String nullSafeMsg(Object o) { return o == null ? "<not provided>" : o.toString(); }

    private void emit(String trigger, ChangeRequest c, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.getCurrentTenant(), trigger, "ChangeRequest", c.getId(),
            "change:" + nullSafeStr(c.getChangeCode()), payloadFor(c, previousStatus)));
    }

    /** Map the new status to the canonical F8 trigger, or null for states
     *  that don't deserve a dedicated event (intermediate routing states). */
    private String triggerFor(String newStatus) {
        return switch (newStatus) {
            case "SUBMITTED"          -> "CHANGE_SUBMITTED";
            case "IMPACT_ASSESSED"    -> "CHANGE_IMPACT_ASSESSED";
            case "CAB_APPROVED"       -> "CHANGE_CAB_APPROVED";
            case "CAB_REJECTED"       -> "CHANGE_CAB_REJECTED";
            case "EMERGENCY_APPROVED" -> "CHANGE_EMERGENCY_APPROVED";
            case "IMPLEMENTED"        -> "CHANGE_IMPLEMENTED";
            case "ROLLED_BACK"        -> "CHANGE_FAILED";
            case "CLOSED"             -> "CHANGE_CLOSED";
            default                   -> "CHANGE_TRANSITIONED";
        };
    }

    // ── Listing + CRUD ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ChangeRequest> list(String status, String type, String risk, String category, Pageable pageable) {
        return changeRepo.findWithFilters(
            TenantContext.getCurrentTenant(),
            blank(status), blank(type), blank(risk), blank(category),
            pageable);
    }

    @Transactional(readOnly = true)
    public ChangeRequest get(Long id) { return require(id); }

    @Transactional
    public ChangeRequest create(ChangeRequest req) {
        ChangeRequest c = new ChangeRequest();
        c.setOrganisationId(TenantContext.getCurrentTenant());
        apply(c, req);
        if (c.getStatus() == null) c.setStatus("DRAFT");
        if (c.getChangeType() == null) c.setChangeType("NORMAL");
        if (c.getRequestedDate() == null) c.setRequestedDate(LocalDate.now());
        ChangeRequest saved = changeRepo.save(c);
        audit.log("CREATE", "ChangeRequest", saved.getId(),
            "Change drafted: " + nullSafeMsg(saved.getChangeCode())
                + " — " + nullSafeMsg(saved.getTitleEn())
                + " (" + nullSafeMsg(saved.getChangeType()) + ", risk " + nullSafeMsg(saved.getRiskLevel()) + ")");
        emit("CHANGE_CREATED", saved, null);
        emitChange(saved);
        return saved;
    }

    @Transactional
    public ChangeRequest update(Long id, ChangeRequest req) {
        ChangeRequest c = require(id);
        apply(c, req);
        ChangeRequest saved = changeRepo.save(c);
        audit.log("UPDATE", "ChangeRequest", saved.getId(),
            "Change " + nullSafeMsg(saved.getChangeCode()) + " metadata updated");
        emitChange(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ChangeRequest c = require(id);
        c.setIsDeleted(true);
        changeRepo.save(c);
        audit.log("DELETE", "ChangeRequest", c.getId(),
            "Change " + nullSafeMsg(c.getChangeCode()) + " soft-deleted");
        emitChange(c);
    }

    // ── Lifecycle transitions ───────────────────────────────────────────────

    @Transactional
    public ChangeRequest transition(Long id, String target, Map<String, Object> body) {
        ChangeRequest c = require(id);
        // F2-29: authority BEFORE the state machine, so an unauthorised caller learns nothing
        // about which transitions would have been legal for this change.
        requireDecisionAuthority(target);
        guard(c, target);
        switch (target) {
            case "EMERGENCY_APPROVED" -> {
                if (!"EMERGENCY".equals(c.getChangeType()))
                    throw new IllegalArgumentException("Emergency-approval path is only valid for EMERGENCY changes");
                requireConfiguredChangeAuthority(c, target);
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
                stampDecisionAuthor(c);
                // A17-D3: the change is authorised but the clock has not started —
                // by decision it starts at implementation, not here.
                if (RatificationStates.NOT_REQUIRED.equals(c.getRatificationState()))
                    c.setRatificationState(RatificationStates.AWAITING_IMPLEMENTATION);
            }
            case "CAB_APPROVED" -> {
                requireConfiguredChangeAuthority(c, target);
                // Track D: a change referred to a committee is decided THERE — the
                // committee's carried resolution is the CAB decision (system-of-
                // record), asserted on quorate (MINUTES_APPROVED) minutes. A
                // non-referred change takes the legacy recorded-votes gate below,
                // byte-for-byte, so nothing changes for the existing flow.
                if (committeePort != null && committeePort.activeReferral("CHANGE", id).isPresent()) {
                    committeePort.assertDecisionApproves("CHANGE", id, requiredApprovals(c));
                } else {
                    // CM-C2 (CAB quorum): approval must be backed by recorded votes —
                    // at least `cabMinApprovals` APPROVE and zero REJECT. Closes the
                    // rubber-stamp path where a change reached CAB_APPROVED with no
                    // votes, or despite a recorded rejection.
                    List<ChangeApproval> votes = approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(id);
                    if (votes.stream().anyMatch(v -> "REJECT".equalsIgnoreCase(v.getDecision())))
                        throw new IllegalStateException(
                            "Cannot CAB-approve: a REJECT vote is on record — route to CAB_REJECTED");
                    long approves = votes.stream().filter(v -> "APPROVE".equalsIgnoreCase(v.getDecision())).count();
                    // CH-17: the required count comes from the (type x risk) authorisation
                    // matrix cell, falling back to the flat cab-min-approvals default.
                    int required = requiredApprovals(c);
                    if (approves < required)
                        throw new IllegalStateException(
                            "Cannot CAB-approve: requires at least " + required
                                + " recorded APPROVE vote(s) (have " + approves + ")");
                }
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
            }
            case "CAB_REJECTED" -> {
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
                stampDecisionAuthor(c);
            }
            case "SCHEDULED" ->
                // CH-17: block scheduling into an active freeze/blackout window
                // (emergencies exempt where the window permits).
                enforceFreeze(c);
            case "IMPLEMENTING" -> {
                if (c.getImplementationStarted() == null) c.setImplementationStarted(LocalDateTime.now());
                // A17-D3: the clock starts HERE — first moment the change is live.
                startRatificationClock(c);
            }
            case "IMPLEMENTED" -> {
                if (c.getImplementationEnded() == null) c.setImplementationEnded(LocalDateTime.now());
                // A17-D3 belt-and-braces: EMERGENCY_APPROVED -> IMPLEMENTING is the only
                // legal route to IMPLEMENTED for an emergency change, so the clock is
                // normally already running. Arm here too, because a future transition-map
                // edit that adds a shortcut must not silently drop the ratification duty.
                startRatificationClock(c);
                if (body.get("outcome") != null) {
                    String oc = body.get("outcome").toString();
                    if (!OUTCOMES.contains(oc))
                        throw new IllegalArgumentException("Invalid outcome '" + oc + "' — allowed: " + OUTCOMES);
                    c.setOutcome(oc);
                }
                else if (c.getOutcome() == null) c.setOutcome("SUCCESS");
            }
            case "ROLLED_BACK" -> {
                if (c.getImplementationEnded() == null) c.setImplementationEnded(LocalDateTime.now());
                c.setOutcome("ROLLED_BACK");
                // CH-13: a rolled-back change is a textbook incident precursor —
                // auto-open an incident and back-link it (INCIDENT / TRIGGERED_BY)
                // so the failure enters the incident lifecycle, not just the log.
                autoRaiseRollbackIncident(c);
            }
            case "POST_REVIEW" -> {
                if (c.getPostReviewDate() == null) c.setPostReviewDate(LocalDate.now());
                // CH-11: capture the PIR content at the review moment. Any field may
                // be supplied via the transition body; the review sign-off (who/when)
                // is system-stamped so it cannot be back-dated or forged by the client.
                capturePir(c, body);
                c.setPirReviewedAt(LocalDateTime.now());
                String reviewer = strFromBody(body, "reviewer");
                if (reviewer == null) reviewer = strFromBody(body, "reviewedBy");
                if (reviewer != null && !reviewer.isBlank()) c.setPirReviewedBy(reviewer);
            }
            case "CLOSED" -> {
                // CH-11 (mandatory retrospective): a NORMAL/EMERGENCY change cannot be
                // closed without a recorded PIR summary — this is the "mandatory
                // retrospective review" governance gate (CH-1). STANDARD changes are
                // pre-approved/low-risk and exempt. A 409 (IllegalStateException).
                if (!"STANDARD".equals(c.getChangeType())
                        && (c.getPirSummary() == null || c.getPirSummary().isBlank()))
                    throw new IllegalStateException(
                        "Cannot close: a post-implementation review (PIR) summary is required "
                            + "for " + nullSafeStr(c.getChangeType()) + " changes before closure");
                if (c.getClosureDate() == null) c.setClosureDate(LocalDate.now());
            }
            default -> { /* no extra side-effects */ }
        }
        String previousStatus = c.getStatus();
        c.setStatus(target);
        ChangeRequest saved = changeRepo.save(c);
        audit.log("TRANSITION", "ChangeRequest", saved.getId(),
            "Change " + nullSafeMsg(saved.getChangeCode())
                + " status " + previousStatus + " → " + target
                + (saved.getOutcome() != null ? " (outcome: " + saved.getOutcome() + ")" : ""));
        emit(triggerFor(target), saved, previousStatus);
        emitChange(saved);
        return saved;
    }

    // ── Approvals ───────────────────────────────────────────────────────────

    @Transactional
    /**
     * Record one CAB vote on a change.
     *
     * <p><b>DF-4 / F2-24 — actor provenance.</b> This method previously took the
     * {@code ChangeApproval} entity straight from the request body and trusted
     * {@code approverName}. Because the CM-C2 segregation-of-duties gate compared that
     * <em>claimed</em> name against the change's requester and owner, **a requester could
     * approve their own change by submitting any other name** — and `approver_id` was
     * client-settable, nothing enforced one vote per approver, and the audit line repeated
     * the claim. The in-code comment conceded it: "(Name-based check; principal-id capture
     * is a follow-up.)" This is that follow-up, and it mirrors the committee-vote fix
     * (DF-3) so the two paths now behave the same way.
     *
     * <p>The ordering below is the control: tenant scope, then referral routing, then
     * lifecycle state, then <em>identity</em>, then SoD <em>on the resolved user id</em>,
     * then duplicate detection. The SoD check falls back to name comparison only for
     * legacy changes whose {@code requestedById}/{@code changeOwnerId} were never captured
     * — a fallback, never the primary test.
     */
    public ChangeApproval recordApproval(Long changeId, CabVoteRequest req,
                                         Long actorUserId, String actorName) {
        ChangeRequest c = require(changeId);
        // Track D: a referred change is decided in committee — block the direct
        // CAB vote path so the two can't both satisfy CAB_APPROVED (no dual-count).
        if (committeePort != null && committeePort.activeReferral("CHANGE", changeId).isPresent())
            throw new IllegalStateException(
                "This change is routed through a committee — record the decision as a committee vote, not a direct CAB vote");
        if (!"CAB_PENDING".equals(c.getStatus()) && !"IMPACT_ASSESSED".equals(c.getStatus()))
            throw new IllegalArgumentException("CAB votes can only be recorded while the change is in IMPACT_ASSESSED or CAB_PENDING");
        if (req == null || req.decision() == null || req.decision().isBlank())
            throw new IllegalArgumentException("decision is required");
        String decision = req.decision().trim().toUpperCase();
        if (!CAB_DECISIONS.contains(decision))
            throw new IllegalArgumentException("decision must be one of " + CAB_DECISIONS);

        // The approver is the authenticated caller — never a name from the body.
        if (actorUserId == null && nullSafeStr(actorName).isBlank())
            throw new AccessDeniedException("A CAB vote must be cast by an identified user.");
        String approver = nullSafeStr(actorName).isBlank() ? ("user#" + actorUserId) : actorName.trim();

        // CM-C2 (SoD): the change requester / owner cannot cast a CAB vote on their own
        // change. Now evaluated on the resolved USER ID, which the caller cannot choose.
        boolean selfById = actorUserId != null
            && (actorUserId.equals(c.getRequestedById()) || actorUserId.equals(c.getChangeOwnerId()));
        // Legacy fallback only: changes recorded before requester/owner ids were captured.
        boolean selfByName = c.getRequestedById() == null && c.getChangeOwnerId() == null
            && (approver.equalsIgnoreCase(nullSafeStr(c.getRequestedByName()).trim())
                || approver.equalsIgnoreCase(nullSafeStr(c.getChangeOwnerName()).trim()));
        if (selfById || selfByName)
            throw new IllegalStateException(
                "Segregation of duty: the change requester/owner cannot cast a CAB vote on their own change");

        // One vote per approver per change — the DB enforces this too (V316), so a race
        // surfaces as a constraint violation rather than a double-counted majority.
        if (actorUserId != null
                && approvalRepo.findByChangeRequestIdAndApproverId(changeId, actorUserId).isPresent())
            throw new IllegalStateException(
                "You have already recorded a CAB vote on this change — withdraw or amend the existing vote "
              + "rather than casting a second one.");

        ChangeApproval body = new ChangeApproval();
        body.setChangeRequestId(changeId);
        body.setApproverId(actorUserId);
        body.setApproverName(approver);
        body.setApproverRole(req.approverRole());
        body.setDecision(decision);
        body.setComments(req.comments());
        ChangeApproval saved = approvalRepo.save(body);
        audit.log("APPROVAL", "ChangeRequest", changeId,
            "CAB vote on change " + nullSafeMsg(c.getChangeCode()) + ": "
                + nullSafeMsg(saved.getDecision()) + " by " + nullSafeMsg(saved.getApproverName())
                + " (user#" + saved.getApproverId() + ")"
                + (saved.getApproverRole() != null ? " as " + saved.getApproverRole() : ""));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ChangeApproval> approvals(Long changeId) {
        require(changeId);
        return approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(changeId);
    }

    // ── Tasks ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChangeTask> tasks(Long changeId) {
        require(changeId);
        return taskRepo.findByChangeRequestIdOrderBySortOrderAsc(changeId);
    }

    @Transactional
    public ChangeTask addTask(Long changeId, ChangeTask body) {
        require(changeId);
        body.setChangeRequestId(changeId);
        if (body.getStatus() == null) body.setStatus("PENDING");
        ChangeTask saved = taskRepo.save(body);
        audit.log("TASK_ADD", "ChangeRequest", changeId,
            "Task added to change " + changeId + ": " + nullSafeMsg(saved.getTitleEn()));
        return saved;
    }

    @Transactional
    public ChangeTask updateTask(Long taskId, ChangeTask body) {
        ChangeTask t = taskRepo.findById(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        require(t.getChangeRequestId());
        if (body.getTitleEn()    != null) t.setTitleEn(body.getTitleEn());
        if (body.getTitleAr()    != null) t.setTitleAr(body.getTitleAr());
        if (body.getOwnerName()  != null) t.setOwnerName(body.getOwnerName());
        if (body.getStatus()     != null) {
            t.setStatus(body.getStatus());
            if ("DONE".equals(body.getStatus()) && t.getCompletedAt() == null) t.setCompletedAt(LocalDateTime.now());
        }
        if (body.getSortOrder()  != null) t.setSortOrder(body.getSortOrder());
        if (body.getDueAt()      != null) t.setDueAt(body.getDueAt());
        if (body.getNotes()      != null) t.setNotes(body.getNotes());
        ChangeTask saved = taskRepo.save(t);
        audit.log("TASK_UPDATE", "ChangeRequest", t.getChangeRequestId(),
            "Task " + taskId + " updated (status " + nullSafeMsg(saved.getStatus()) + ")");
        return saved;
    }

    @Transactional
    public void deleteTask(Long taskId) {
        ChangeTask t = taskRepo.findById(taskId)
            .orElseThrow(() -> new EntityNotFoundException("Task not found: " + taskId));
        require(t.getChangeRequestId());
        audit.log("TASK_DELETE", "ChangeRequest", t.getChangeRequestId(),
            "Task " + taskId + " deleted: " + nullSafeMsg(t.getTitleEn()));
        taskRepo.deleteById(taskId);
    }

    // ── Links (cross-module integration) ────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChangeLink> links(Long changeId) {
        require(changeId);
        return linkRepo.findByChangeRequestId(changeId);
    }

    /** Inverse lookup — used by other modules to render "linked changes" panels. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> linksForEntity(String entityType, Long entityId) {
        Long org = TenantContext.getCurrentTenant();
        // CM-L3: the tenant filter now runs in SQL via the join (was an
        // org-unscoped fetch + per-row post-filter). @Where on both entities
        // drops soft-deleted links and changes.
        return linkRepo.findLinkedChangesForEntity(entityType, entityId, org).stream()
            .map(row -> {
                ChangeLink l = (ChangeLink) row[0];
                ChangeRequest c = (ChangeRequest) row[1];
                Map<String, Object> m = new HashMap<>();
                m.put("linkId",      l.getId());
                m.put("linkType",    l.getLinkType());
                m.put("changeId",    c.getId());
                m.put("changeCode",  c.getChangeCode());
                m.put("titleEn",     c.getTitleEn());
                m.put("titleAr",     c.getTitleAr());
                m.put("status",      c.getStatus());
                m.put("changeType",  c.getChangeType());
                m.put("riskLevel",   c.getRiskLevel());
                m.put("scheduledStart", c.getScheduledStart());
                return m;
            })
            .toList();
    }

    @Transactional
    public ChangeLink addLink(Long changeId, ChangeLink body) {
        require(changeId);
        body.setChangeRequestId(changeId);
        if (body.getEntityType() == null || body.getEntityId() == null)
            throw new IllegalArgumentException("entityType and entityId are required");
        if (body.getLinkType() == null) body.setLinkType("AFFECTS");
        ChangeLink saved = linkRepo.save(body);
        audit.log("LINK_ADD", "ChangeRequest", changeId,
            "Link added to change " + changeId + ": " + nullSafeMsg(saved.getLinkType())
                + " → " + nullSafeMsg(saved.getEntityType()) + " #" + saved.getEntityId());
        return saved;
    }

    @Transactional
    public void deleteLink(Long linkId) {
        ChangeLink l = linkRepo.findById(linkId)
            .orElseThrow(() -> new EntityNotFoundException("Link not found: " + linkId));
        require(l.getChangeRequestId());
        l.setIsDeleted(true);
        linkRepo.save(l);
        audit.log("LINK_DELETE", "ChangeRequest", l.getChangeRequestId(),
            "Link " + linkId + " removed (" + nullSafeMsg(l.getEntityType()) + " #" + l.getEntityId() + ")");
    }

    // ── Comments ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChangeComment> comments(Long changeId) {
        require(changeId);
        return commentRepo.findByChangeRequestIdOrderByCreatedAtAsc(changeId);
    }

    @Transactional
    public ChangeComment addComment(Long changeId, ChangeComment body) {
        require(changeId);
        body.setChangeRequestId(changeId);
        if (body.getBody() == null || body.getBody().isBlank())
            throw new IllegalArgumentException("body is required");
        if (body.getAuthorName() == null) body.setAuthorName("Unknown");
        ChangeComment saved = commentRepo.save(body);
        audit.log("COMMENT_ADD", "ChangeRequest", changeId,
            "Comment added to change " + changeId + " by " + nullSafeMsg(saved.getAuthorName()));
        return saved;
    }

    // ── Analytics ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> analytics() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Object> out = new HashMap<>();
        out.put("total",  changeRepo.countByOrganisationId(org));

        Map<String, Long> byStatus = new HashMap<>();
        for (String s : List.of("DRAFT","SUBMITTED","IMPACT_ASSESSED","CAB_PENDING","CAB_APPROVED",
                                "CAB_REJECTED","SCHEDULED","EMERGENCY_APPROVED","IMPLEMENTING",
                                "IMPLEMENTED","ROLLED_BACK","POST_REVIEW","CLOSED","CANCELLED"))
            byStatus.put(s, changeRepo.countByOrganisationIdAndStatus(org, s));
        out.put("byStatus", byStatus);

        Map<String, Long> byType = new HashMap<>();
        for (String t : List.of("STANDARD", "NORMAL", "EMERGENCY"))
            byType.put(t, changeRepo.countByOrganisationIdAndChangeType(org, t));
        out.put("byType", byType);

        Map<String, Long> byOutcome = new HashMap<>();
        for (String o : List.of("SUCCESS", "PARTIAL_SUCCESS", "FAILED", "ROLLED_BACK"))
            byOutcome.put(o, changeRepo.countByOrganisationIdAndOutcome(org, o));
        out.put("byOutcome", byOutcome);

        long success = byOutcome.getOrDefault("SUCCESS", 0L);
        long withOutcome = byOutcome.values().stream().mapToLong(Long::longValue).sum();
        out.put("successRatePct", withOutcome == 0 ? 0 : Math.round(success * 1000.0 / withOutcome) / 10.0);

        // CH-5: overdue open tasks (dueAt passed, not DONE/SKIPPED) — the SLA KPI.
        out.put("overdueTasks", taskRepo.countOverdueOpen(org, java.time.LocalDateTime.now()));

        // A17-D3: emergency-ratification standing. `ratificationBreaches` is the number
        // that belongs on a CISO dashboard — emergency changes live in production that
        // the CAB never retrospectively authorised, past the tenant's SLA. Reported
        // separately from `ratificationPending` because "owed" and "overdue" are
        // different governance facts, and reporting only the total would let a breach
        // hide inside a healthy-looking backlog.
        Map<String, Long> byRatification = new HashMap<>();
        for (String s : RatificationStates.VALUES) byRatification.put(s, 0L);
        for (Object[] row : changeRepo.countByRatificationStateGrouped(org))
            byRatification.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        out.put("byRatificationState", byRatification);
        out.put("ratificationPending",
            byRatification.getOrDefault(RatificationStates.PENDING, 0L)
          + byRatification.getOrDefault(RatificationStates.AWAITING_IMPLEMENTATION, 0L));
        out.put("ratificationBreaches",
            (long) changeRepo.findRatificationBreaches(org, java.time.LocalDateTime.now()).size());
        out.put("ratificationSlaBusinessDays", slaBusinessDays(org));
        return out;
    }

    // ── A17-D3: emergency-change retrospective ratification ─────────────────────
    //
    // ADR-017 D-3. An emergency change is implemented on ECAB authority and then
    // retrospectively ratified by the CAB within the tenant's SLA (default 5 Saudi
    // business days from implementation start). Breach escalates to the CISO and is
    // recorded — "an SLA with no consequence is a report, not a control".
    //
    // Three things here are amendments to the decision as first written, each because
    // the decision could not be built as specified:
    //   1. The escalation destination is the F8 engine + the audit ledger, because
    //      CE-11's unauthorised-change register does not exist as a table yet.
    //   2. The per-tenant override is bounded (1..10) in the DTO *and* the database.
    //   3. Pre-existing emergency rows are GRANDFATHERED by V345, not retro-clocked.

    /**
     * Resolve the tenant's ratification SLA in business days.
     *
     * <p>Falls back to the ADR default rather than to "no clock". A missing policy row
     * must not mean a missing control — see the field comment on
     * {@code ratificationPolicyRepo}.
     */
    int slaBusinessDays(Long org) {
        if (ratificationPolicyRepo == null || org == null) return RatificationStates.DEFAULT_SLA_BUSINESS_DAYS;
        return ratificationPolicyRepo.findByOrganisationId(org)
            .map(ChangeRatificationPolicy::getSlaBusinessDays)
            .filter(d -> d != null && d >= RatificationStates.MIN_SLA_BUSINESS_DAYS
                                   && d <= RatificationStates.MAX_SLA_BUSINESS_DAYS)
            .orElse(RatificationStates.DEFAULT_SLA_BUSINESS_DAYS);
    }

    /**
     * Move an ECAB-approved emergency change from AWAITING_IMPLEMENTATION to PENDING,
     * computing the due date from the implementation start.
     *
     * <p>No-ops for anything that is not awaiting implementation, so re-entry is safe
     * and a NORMAL change is never dragged into the ratification register.
     *
     * <p>The due date is computed <b>once</b> and stored. Recomputing it on read would
     * mean a later edit to the tenant's SLA silently moved historical deadlines, which
     * is precisely the property an examiner tests for.
     */
    private void startRatificationClock(ChangeRequest c) {
        if (!RatificationStates.AWAITING_IMPLEMENTATION.equals(c.getRatificationState())) return;
        LocalDateTime from = c.getImplementationStarted() != null
            ? c.getImplementationStarted() : LocalDateTime.now();
        int days = slaBusinessDays(c.getOrganisationId());
        c.setRatificationDueAt(addBusinessDays(c.getOrganisationId(), from, days));
        c.setRatificationState(RatificationStates.PENDING);
        audit.log("RATIFICATION_ARMED", "ChangeRequest", c.getId(),
            "Emergency change " + nullSafeMsg(c.getChangeCode()) + " requires CAB ratification by "
                + c.getRatificationDueAt() + " (" + days + " business days from implementation start)");
    }

    /**
     * Saudi business-day arithmetic, holiday-aware when the calendar bean is present.
     *
     * <p>The tenant calendar is preferred because a Saudi tenant's Eid holidays must not
     * consume the ratification window; the static fallback still skips Friday and
     * Saturday, so a directly-constructed unit test gets a business-day answer rather
     * than a calendar-day one. Both agree the working week is Sunday–Thursday.
     */
    private LocalDateTime addBusinessDays(Long org, LocalDateTime from, int days) {
        if (workdayCalendar != null && org != null)
            return workdayCalendar.addBusinessDays(org, from.toLocalDate(), days).atTime(from.toLocalTime());
        return SaudiWorkdayUtil.addBusinessDays(from, days);
    }

    /**
     * Record the CAB's retrospective decision on an emergency change.
     *
     * <p><b>The SoD gate is the point of the feature.</b> D-3 exists because an
     * emergency change goes live without CAB scrutiny; ratification supplies that
     * scrutiny afterwards. If the person who emergency-approved it could also ratify
     * it, the control would certify its own author's decision and the platform would
     * hold evidence saying a second party had reviewed it. That is F2-29 one step
     * later in the lifecycle, so the same three exclusions apply — approver, raiser,
     * owner — evaluated on the <b>resolved user id</b>, never on a name from the body.
     *
     * <p>Late ratification is permitted and is recorded as late:
     * {@code ratificationEscalatedAt} is deliberately not cleared.
     */
    @Transactional
    public ChangeRequest ratify(Long id, String decision, String notes,
                               Long actorUserId, String actorName) {
        ChangeRequest c = require(id);

        if (decision == null || decision.isBlank())
            throw new IllegalArgumentException(
                "decision is required — one of " + RatificationStates.DECISIONS);
        String d = decision.trim().toUpperCase();
        if (!RatificationStates.DECISIONS.contains(d))
            throw new IllegalArgumentException(
                "Invalid ratification decision '" + decision + "' — allowed: " + RatificationStates.DECISIONS);

        String state = c.getRatificationState();
        if (RatificationStates.NOT_REQUIRED.equals(state))
            throw new IllegalStateException(
                "Change " + nullSafeMsg(c.getChangeCode()) + " does not require retrospective ratification. "
              + "Only EMERGENCY changes approved through the ECAB path do — a change approved by the CAB "
              + "in the first place has already had its scrutiny.");
        if (RatificationStates.GRANDFATHERED.equals(state))
            throw new IllegalStateException(
                "Change " + nullSafeMsg(c.getChangeCode()) + " pre-dates the ratification clock (V345) and "
              + "carries no SLA. Reopen it deliberately by re-arming the clock if the governance record "
              + "must be closed — it is not ratified silently, because no clock ever ran for it.");
        if (RatificationStates.RATIFIED.equals(state) || RatificationStates.REJECTED.equals(state))
            throw new IllegalStateException(
                "Change " + nullSafeMsg(c.getChangeCode()) + " was already " + state.toLowerCase()
              + " on " + c.getRatifiedAt() + " by " + nullSafeStr(c.getRatifiedByName())
              + ". A ratification decision is recorded once.");
        if (RatificationStates.AWAITING_IMPLEMENTATION.equals(state))
            throw new IllegalStateException(
                "Change " + nullSafeMsg(c.getChangeCode()) + " has not been implemented yet, so there is "
              + "nothing to ratify retrospectively. The ratification clock starts at implementation "
              + "(ADR-017 D-3); approve or reject it through the normal CAB path instead.");

        if (actorUserId == null && nullSafeStr(actorName).isBlank())
            throw new AccessDeniedException("A ratification decision must be recorded by an identified user.");
        String ratifier = nullSafeStr(actorName).isBlank() ? ("user#" + actorUserId) : actorName.trim();

        requireRatificationSeparation(c, actorUserId, ratifier);

        boolean late = c.getRatificationDueAt() != null
            && LocalDateTime.now().isAfter(c.getRatificationDueAt());

        c.setRatificationState(RatificationStates.DECISION_RATIFY.equals(d)
            ? RatificationStates.RATIFIED : RatificationStates.REJECTED);
        c.setRatifiedAt(LocalDateTime.now());
        c.setRatifiedById(actorUserId);
        c.setRatifiedByName(ratifier);
        c.setRatificationNotes(blank(notes));
        ChangeRequest saved = changeRepo.save(c);

        audit.log("RATIFICATION_" + d, "ChangeRequest", saved.getId(),
            "Emergency change " + nullSafeMsg(saved.getChangeCode()) + " " + saved.getRatificationState().toLowerCase()
                + " by " + ratifier + (late ? " — AFTER the SLA deadline of " + c.getRatificationDueAt() : "")
                + (saved.getRatificationEscalatedAt() != null
                    ? " (breach was escalated on " + saved.getRatificationEscalatedAt() + ")" : ""));
        emitChange(saved);
        return saved;
    }

    /**
     * The ratifier may not be the emergency approver, the raiser or the change owner.
     *
     * <p>Evaluated on user ids. The name fallback is for legacy rows whose ids were never
     * captured — a fallback, never the primary test, exactly as in {@code recordApproval}.
     * A row with a null {@code cabDecisionById} (everything approved before V345) cannot
     * be tested on the approver at all; that is stated in the message rather than passed
     * over, because an SoD gate that silently cannot evaluate is worse than one that says so.
     */
    private void requireRatificationSeparation(ChangeRequest c, Long actorUserId, String ratifier) {
        boolean selfApprover = actorUserId != null && actorUserId.equals(c.getCabDecisionById());
        boolean selfRaiser   = actorUserId != null
            && (actorUserId.equals(c.getRequestedById()) || actorUserId.equals(c.getChangeOwnerId()));
        boolean nameFallback = actorUserId == null && ratifier != null
            && (ratifier.equalsIgnoreCase(nullSafeStr(c.getCabDecisionByName()))
             || ratifier.equalsIgnoreCase(nullSafeStr(c.getRequestedByName()))
             || ratifier.equalsIgnoreCase(nullSafeStr(c.getChangeOwnerName())));

        if (selfApprover)
            throw new IllegalStateException(
                "Segregation of duties: " + ratifier + " emergency-approved change "
              + nullSafeMsg(c.getChangeCode()) + ", so they cannot also ratify it. Retrospective "
              + "ratification exists to supply the independent scrutiny the emergency path skipped — "
              + "self-ratification would certify the approver's own decision and the evidence pack "
              + "would claim a second party had reviewed it.");
        if (selfRaiser || nameFallback)
            throw new IllegalStateException(
                "Segregation of duties: " + ratifier + " raised or owns change "
              + nullSafeMsg(c.getChangeCode()) + ", so they cannot ratify it. Another CHANGE:APPROVE "
              + "holder must record the CAB's retrospective decision.");
    }

    /**
     * A17-D3 interim register: every change carrying a ratification duty, PENDING and
     * overdue first. This is the surface CE-11's unauthorised-change register will
     * widen — not replace, and not duplicate.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> ratificationRegister() {
        Long org = TenantContext.getCurrentTenant();
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (ChangeRequest c : changeRepo.findRatificationRegister(org)) {
            boolean breached = RatificationStates.PENDING.equals(c.getRatificationState())
                && c.getRatificationDueAt() != null && now.isAfter(c.getRatificationDueAt());
            Map<String, Object> row = new HashMap<>();
            row.put("id", c.getId());
            row.put("changeCode", c.getChangeCode());
            row.put("titleEn", c.getTitleEn());
            row.put("titleAr", c.getTitleAr());
            row.put("status", c.getStatus());
            row.put("riskLevel", c.getRiskLevel());
            row.put("ratificationState", c.getRatificationState());
            row.put("implementationStarted", c.getImplementationStarted());
            row.put("ratificationDueAt", c.getRatificationDueAt());
            row.put("ratifiedAt", c.getRatifiedAt());
            row.put("ratifiedByName", c.getRatifiedByName());
            row.put("escalatedAt", c.getRatificationEscalatedAt());
            row.put("approvedByName", c.getCabDecisionByName());
            row.put("breached", breached);
            row.put("businessDaysOverdue", breached
                ? SaudiWorkdayUtil.businessDaysBetween(c.getRatificationDueAt(), now) : 0L);
            rows.add(row);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("slaBusinessDays", slaBusinessDays(org));
        out.put("slaCeilingBusinessDays", RatificationStates.MAX_SLA_BUSINESS_DAYS);
        out.put("rows", rows);
        out.put("breaches", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("breached"))).count());
        // Stated rather than implied: this register is the interim destination while
        // CE-11 does not exist. A consumer must be able to tell an interim sink from
        // the final one without reading the plan.
        out.put("registerScope", "EMERGENCY_RATIFICATION_ONLY");
        out.put("note", "Interim A17-D3 register. CE-11's full unauthorised-change register "
                      + "(unapproved production changes detected from configuration drift) is not built yet.");
        return out;
    }

    @Transactional(readOnly = true)
    public ChangeRatificationPolicy ratificationPolicy() {
        Long org = TenantContext.getCurrentTenant();
        if (ratificationPolicyRepo == null)
            throw new IllegalStateException("Ratification policy storage is not available");
        return ratificationPolicyRepo.findByOrganisationId(org)
            .orElseThrow(() -> new EntityNotFoundException(
                "No ratification policy row for organisation " + org + ". V345 seeds one per tenant and "
              + "THROWs if any live tenant lacks one, so this means the row was deleted after migration."));
    }

    /**
     * Update the tenant's ratification SLA.
     *
     * <p>The bound is re-checked here even though {@code @Max} on the DTO and
     * {@code ck_ratification_sla_bounds} in the database both enforce it, because this
     * method is also reachable from server-side callers that never see a DTO. Three
     * checks for one rule is not redundancy — it is the rule being true at every layer
     * that can write it.
     */
    @Transactional
    public ChangeRatificationPolicy updateRatificationPolicy(Integer slaBusinessDays, String escalationRole,
                                                            Long actorUserId) {
        ChangeRatificationPolicy p = ratificationPolicy();
        int previous = p.getSlaBusinessDays() == null
            ? RatificationStates.DEFAULT_SLA_BUSINESS_DAYS : p.getSlaBusinessDays();
        if (slaBusinessDays != null) {
            if (slaBusinessDays < RatificationStates.MIN_SLA_BUSINESS_DAYS
             || slaBusinessDays > RatificationStates.MAX_SLA_BUSINESS_DAYS)
                throw new IllegalArgumentException(
                    "slaBusinessDays must be between " + RatificationStates.MIN_SLA_BUSINESS_DAYS + " and "
                  + RatificationStates.MAX_SLA_BUSINESS_DAYS + " business days (ADR-017 D-3 amendment 2). "
                  + RatificationStates.MAX_SLA_BUSINESS_DAYS + " business days is two weekly CAB cycles; an "
                  + "emergency change unratified after two consecutive CABs is an unauthorised change, not a "
                  + "slow one. A longer window is a documented, approved deviation — not a settings field.");
            p.setSlaBusinessDays(slaBusinessDays);
        }
        if (escalationRole != null && !escalationRole.isBlank()) p.setEscalationRole(escalationRole.trim());
        p.setUpdatedAt(LocalDateTime.now());
        p.setUpdatedBy(actorUserId);
        ChangeRatificationPolicy saved = ratificationPolicyRepo.save(p);
        audit.log("RATIFICATION_POLICY_UPDATE", "ChangeRatificationPolicy", saved.getId(),
            "Emergency-ratification SLA set to " + saved.getSlaBusinessDays() + " business days (was "
                + previous + "), escalating to " + saved.getEscalationRole()
                + ". Existing deadlines are unchanged — due dates are stamped once, at implementation.");
        return saved;
    }

    /** Stamp the authenticated principal as the author of a CAB decision (F2-93). */
    private void stampDecisionAuthor(ChangeRequest c) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;
        if (auth.getPrincipal() instanceof com.tss.grc.security.GrcUserDetails u) {
            c.setCabDecisionById(u.getUserId());
            c.setCabDecisionByName(u.getUsername());
        } else if (auth.getName() != null) {
            c.setCabDecisionByName(auth.getName());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void apply(ChangeRequest target, ChangeRequest src) {
        // CH-2 (validation): reject invalid enums with a 400 up front, rather than
        // letting them reach the V300 DB CHECK constraints and surface as a 500.
        if (src.getChangeType() != null && !CHANGE_TYPES.contains(src.getChangeType()))
            throw new IllegalArgumentException("Invalid changeType '" + src.getChangeType() + "' — allowed: " + CHANGE_TYPES);
        if (src.getRiskLevel() != null && !RISK_LEVELS.contains(src.getRiskLevel()))
            throw new IllegalArgumentException("Invalid riskLevel '" + src.getRiskLevel() + "' — allowed: " + RISK_LEVELS);
        if (src.getChangeCode()           != null) target.setChangeCode(src.getChangeCode());
        if (src.getTitleEn()              != null) target.setTitleEn(src.getTitleEn());
        if (src.getTitleAr()              != null) target.setTitleAr(src.getTitleAr());
        if (src.getDescriptionEn()        != null) target.setDescriptionEn(src.getDescriptionEn());
        if (src.getDescriptionAr()        != null) target.setDescriptionAr(src.getDescriptionAr());
        if (src.getChangeType()           != null) target.setChangeType(src.getChangeType());
        if (src.getCategory()             != null) target.setCategory(src.getCategory());
        if (src.getRiskLevel()            != null) target.setRiskLevel(src.getRiskLevel());
        if (src.getImpactSummary()        != null) target.setImpactSummary(src.getImpactSummary());
        if (src.getRollbackPlan()         != null) target.setRollbackPlan(src.getRollbackPlan());
        if (src.getTestPlan()             != null) target.setTestPlan(src.getTestPlan());
        if (src.getImplementationPlan()   != null) target.setImplementationPlan(src.getImplementationPlan());
        if (src.getBusinessJustification()!= null) target.setBusinessJustification(src.getBusinessJustification());
        // CH-11: PIR content is editable via PUT (a reviewer can refine the
        // retrospective after it was first captured at the POST_REVIEW transition).
        // pir_reviewed_by / pir_reviewed_at are system-stamped, never client-set.
        if (src.getPirSummary()           != null) target.setPirSummary(src.getPirSummary());
        if (src.getPirObjectivesMet()     != null) target.setPirObjectivesMet(src.getPirObjectivesMet());
        if (src.getPirLessons()           != null) target.setPirLessons(src.getPirLessons());
        if (src.getPirFollowUp()          != null) target.setPirFollowUp(src.getPirFollowUp());
        if (src.getRequestedByName()      != null) target.setRequestedByName(src.getRequestedByName());
        if (src.getChangeOwnerName()      != null) target.setChangeOwnerName(src.getChangeOwnerName());
        if (src.getImplementerName()      != null) target.setImplementerName(src.getImplementerName());
        if (src.getRequestedDate()        != null) target.setRequestedDate(src.getRequestedDate());
        if (src.getScheduledStart()       != null) target.setScheduledStart(src.getScheduledStart());
        if (src.getScheduledEnd()         != null) target.setScheduledEnd(src.getScheduledEnd());
        // CM-C3 (lifecycle integrity): status is DELIBERATELY not copied from the
        // request body. All status changes must go through transition()/guard();
        // create() defaults to DRAFT. This closes the PUT/create bypass where a
        // client could set any status (e.g. DRAFT→CLOSED) skipping the state machine.
    }

    private void guard(ChangeRequest c, String target) {
        Set<String> allowed = NORMAL_TRANSITIONS.getOrDefault(c.getStatus(), Set.of());
        if (!allowed.contains(target))
            throw new IllegalArgumentException("Cannot transition from " + c.getStatus() + " to " + target);
    }

    /**
     * F2-29 — a {@link #DECISION_TARGETS} transition requires {@code CHANGE:APPROVE}.
     *
     * <p>The check lives here rather than in a {@code @PreAuthorize} because the target arrives in the
     * request body, and a SpEL expression indexing into a {@code Map} would be neither readable nor
     * verifiable by a reviewer. It follows the {@code AttachmentAccessPolicy} precedent (SCR-004),
     * where the coarse annotation stays on the endpoint and the finer, argument-dependent rule runs
     * inside. ⚠ <b>The cost of that choice:</b> a scan of {@code @PreAuthorize} annotations — including
     * the authorization ratchet and my own guard scanner — sees only {@code CHANGE:WRITE} on
     * {@code /transition} and will under-report. That is why
     * {@code ChangeDecisionAuthorityTest} pins this behaviour: the annotation cannot tell the reader
     * the whole rule, so a test has to.
     *
     * <p>No authentication in context is <b>refused</b>, not waved through. {@code transition()} has
     * exactly one caller today (the controller, over HTTP) and no scheduler or committee write-back
     * path — verified by grep across the whole backend — so there is no legitimate off-request caller
     * to exempt. A future automated approver should carry its own identity rather than inherit an
     * implicit exemption from an empty {@code SecurityContext}.
     */
    private void requireDecisionAuthority(String target) {
        if (target == null || !DECISION_TARGETS.contains(target)) return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new AccessDeniedException(
                "A CAB decision must be recorded by an identified user.");
        boolean authorised = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> "CHANGE_APPROVE".equals(a) || "ROLE_PLATFORM_ADMIN".equals(a));
        if (!authorised)
            throw new AccessDeniedException(
                "Recording the CAB decision '" + target + "' requires CHANGE:APPROVE. "
              + "CHANGE:WRITE authorises raising and editing a change, not deciding it.");
    }

    private ChangeRequest require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return changeRepo.findById(id)
            .filter(c -> c.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Change request not found: " + id));
    }

    private static String blank(String s) { return (s == null || s.isBlank()) ? null : s; }
    private static String strFromBody(Map<String, Object> body, String key) {
        return body != null && body.get(key) != null ? body.get(key).toString() : null;
    }

    /** CH-11: pull PIR content fields off a transition body when present, leaving
     *  any already-recorded value untouched if the body omits the field. */
    private static void capturePir(ChangeRequest c, Map<String, Object> body) {
        if (body == null) return;
        String summary = strFromBody(body, "pirSummary");
        if (summary != null && !summary.isBlank()) c.setPirSummary(summary);
        String lessons = strFromBody(body, "pirLessons");
        if (lessons != null && !lessons.isBlank()) c.setPirLessons(lessons);
        String followUp = strFromBody(body, "pirFollowUp");
        if (followUp != null && !followUp.isBlank()) c.setPirFollowUp(followUp);
        Object met = body.get("pirObjectivesMet");
        if (met instanceof Boolean b) c.setPirObjectivesMet(b);
        else if (met != null) c.setPirObjectivesMet(Boolean.parseBoolean(met.toString()));
    }

    // ── CH-17: classification-driven authorisation matrix ────────────────────

    /**
     * <b>ADR-017 D-2 — fail closed when the tenant has not named the authority its own matrix
     * demands.</b> Called from the two <em>permissive</em> CAB-dependent transitions.
     *
     * <p><b>The state this closes, measured 2026-08-01.</b> All three tenants' authorisation
     * matrices demanded a committee — CAB on 5 cells, ECAB on 6 — while only org 1 had a
     * {@code admin.committee_gate_map} row. In orgs 2 and 3 the demand therefore could not be
     * met by anything: {@code CommitteeReferralService.refer} throws for an unmapped gate, so
     * no change was ever referred, so the {@code CAB_APPROVED} branch below took its
     * {@code else} — the legacy recorded-vote tally — and the change reached
     * {@code CAB_APPROVED} <b>with no committee involved at all</b>. {@code EMERGENCY_APPROVED}
     * was weaker still: it consulted neither a committee nor a vote, only F2-29's
     * {@code CHANGE:APPROVE} + not-the-raiser check. That is the "silent pass" D-2 forbids, and
     * {@code ChangeGovernanceHealthService} had been WARNing about it at every boot for a week —
     * <b>which is the lesson: F-4 was closed by making the gap loud, and a WARN is not a
     * control.</b>
     *
     * <p><b>Why only the permissive transitions.</b> D-2 says "the CAB-dependent transitions
     * fail closed", and {@code CAB_REJECTED} is nominally one of them — but refusing a
     * <em>rejection</em> because no committee is configured keeps alive a change somebody tried
     * to kill, which fails closed in the direction that adds risk. Fail-closed means refuse the
     * transition that grants authority, not every transition that mentions the CAB. Rejection
     * stays available and remains reworkable via {@code CAB_REJECTED → DRAFT}.
     *
     * <p><b>Why an existing referral is a pass.</b> If this change has been referred, its
     * deciding committee is identified for this change specifically — a stronger fact than the
     * tenant default D-2 is about — so the "unset" condition does not hold. Note what this
     * deliberately does <em>not</em> do: {@code EMERGENCY_APPROVED} still does not <em>require</em>
     * the ECAB's recorded decision. Requiring retrospective ratification is ADR-017 D-3 / S7,
     * which needs ratification columns that do not exist yet; widening it here would ship half
     * of D-3 under D-2's name.
     *
     * <p><b>Why a silent matrix is a pass.</b> {@code resolveMatrix} returns null when the
     * tenant has no cell for this (type, risk). The matrix <em>is</em> the tenant's documented
     * change authority, and the absence of a cell is not a demand for a committee — failing
     * closed there would refuse approvals in tenants that never asked for a CAB. The residual
     * is recorded rather than hidden: a tenant that deletes its own matrix rows escapes this
     * gate, which is why matrix writes are themselves {@code ADMIN}-guarded and audited.
     */
    private void requireConfiguredChangeAuthority(ChangeRequest c, String target) {
        if (governanceHealth == null) return;   // directly-constructed unit tests
        ChangeAuthorisationMatrix m = resolveMatrix(c);
        if (m == null || m.getGate() == null) return;
        String gate = m.getGate().trim().toUpperCase();
        if (!"CAB".equals(gate) && !"ECAB".equals(gate)) return;   // AUTO — no committee demanded
        if (committeePort != null && committeePort.activeReferral("CHANGE", c.getId()).isPresent())
            return;
        String fault = governanceHealth.gateConfigurationFault(c.getOrganisationId(), gate);
        if (fault == null) return;
        String why = ChangeAuthorityNotConfiguredException.COMMITTEE_UNAVAILABLE.equals(fault)
            ? "the committee mapped to the " + gate + " gate is dissolved or no longer exists"
            : "no committee is mapped to the " + gate + " gate for this organisation";
        throw new ChangeAuthorityNotConfiguredException(
            "Change authority is not configured: this change's authorisation matrix ("
                + nullSafeStr(c.getChangeType()) + " / " + nullSafeStr(c.getRiskLevel())
                + ") requires " + gate + " authorisation, but " + why
                + ". Constitute an ACTIVE committee and map it to the gate on the Committees "
                + "screen (" + ChangeAuthorityNotConfiguredException.REMEDY_SCREEN
                + " → Change authority), then retry. The approval was refused rather than "
                + "recorded without the authority the matrix names.",
            gate, c.getOrganisationId(), fault, target);
    }

    /** Look up the (type x risk) authorisation row for a change; null if the
     *  matrix is not wired or no cell matches. */
    private ChangeAuthorisationMatrix resolveMatrix(ChangeRequest c) {
        if (authMatrixRepo == null || c.getChangeType() == null || c.getRiskLevel() == null) return null;
        return authMatrixRepo
            .findByOrganisationIdAndChangeTypeAndRiskLevel(c.getOrganisationId(), c.getChangeType(), c.getRiskLevel())
            .orElse(null);
    }

    /** Minimum APPROVE votes for this change to reach CAB_APPROVED — the matrix
     *  cell for its (type, risk), else the flat cab-min-approvals default. */
    private int requiredApprovals(ChangeRequest c) {
        ChangeAuthorisationMatrix m = resolveMatrix(c);
        return (m != null && m.getMinApprovals() != null) ? m.getMinApprovals() : cabMinApprovals;
    }

    /** CH-17: the tenant's full authorisation matrix (for admin/FE display). */
    @Transactional(readOnly = true)
    public List<ChangeAuthorisationMatrix> authorisationMatrix() {
        if (authMatrixRepo == null) return List.of();
        return authMatrixRepo.findByOrganisationIdOrderByChangeTypeAscRiskLevelAsc(TenantContext.getCurrentTenant());
    }

    /** CH-17: the authorisation required for a specific change (gate / min
     *  approvals / advisory roles) alongside how many APPROVE votes it has. */
    @Transactional(readOnly = true)
    public Map<String, Object> resolveAuthorisation(Long id) {
        ChangeRequest c = require(id);
        ChangeAuthorisationMatrix m = resolveMatrix(c);
        long approves = approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(id).stream()
            .filter(v -> "APPROVE".equalsIgnoreCase(v.getDecision())).count();
        int required = requiredApprovals(c);
        Map<String, Object> out = new HashMap<>();
        out.put("changeType",      c.getChangeType());
        out.put("riskLevel",       c.getRiskLevel());
        out.put("gate",            m != null ? m.getGate() : "CAB");
        out.put("minApprovals",    required);
        out.put("requiredRoles",   m != null ? m.getRequiredRoles() : null);
        out.put("approvesRecorded", approves);
        out.put("satisfied",       approves >= required);
        return out;
    }

    // ── Track D: refer a change to its CAB/ECAB committee ─────────────────────

    /** Route this change's CAB decision THROUGH the committee engine. The gate
     *  comes from the CH-17 matrix (AUTO → no CAB, rejected); the committee id may
     *  be passed explicitly or is resolved from the per-tenant gate map. S1: link
     *  only — this does NOT yet gate the CAB_APPROVED transition. */
    @Transactional
    public CommitteePort.ReferralView referToCommittee(Long id, Long committeeId, String referredBy) {
        if (committeePort == null)
            throw new IllegalStateException("Committee referral is not available");
        ChangeRequest c = require(id);
        ChangeAuthorisationMatrix m = resolveMatrix(c);
        String gate = (m != null && m.getGate() != null) ? m.getGate() : "CAB";
        if ("AUTO".equalsIgnoreCase(gate))
            throw new IllegalArgumentException("This change is AUTO-authorised (no CAB) — nothing to refer");
        String mode = "EMERGENCY".equals(c.getChangeType()) ? "RATIFICATION" : "APPROVAL";
        // SoD: the requester/owner will be force-RECUSED from the committee vote.
        String recusal = java.util.stream.Stream.of(c.getRequestedByName(), c.getChangeOwnerName())
            .filter(x -> x != null && !x.isBlank()).map(String::trim).distinct()
            .collect(java.util.stream.Collectors.joining(","));
        CommitteePort.ReferralView r = committeePort.refer("CHANGE", id, gate, mode, committeeId, referredBy,
            recusal.isBlank() ? null : recusal);
        audit.log("REFER_TO_COMMITTEE", "ChangeRequest", id,
            "Referred to committee " + r.committeeId() + " (gate " + gate + ", mode " + mode + ")");
        return r;
    }

    /** Bind the committee resolution deciding this change (secretary links the
     *  motion after raising it in the meeting). */
    @Transactional
    public CommitteePort.ReferralView linkChangeResolution(Long id, Long resolutionId) {
        if (committeePort == null) throw new IllegalStateException("Committee referral is not available");
        require(id);
        CommitteePort.ReferralView r = committeePort.linkResolution("CHANGE", id, resolutionId);
        audit.log("REFER_LINK_RESOLUTION", "ChangeRequest", id, "Linked committee resolution " + resolutionId);
        return r;
    }

    /** The active committee referral for a change (empty map when none). */
    @Transactional(readOnly = true)
    public Map<String, Object> committeeReferral(Long id) {
        require(id);
        Map<String, Object> out = new HashMap<>();
        if (committeePort == null) return out;
        committeePort.activeReferral("CHANGE", id).ifPresent(r -> {
            out.put("referralId",   r.id());
            out.put("committeeId",  r.committeeId());
            out.put("gate",         r.gate());
            out.put("mode",         r.mode());
            out.put("status",       r.status());
            out.put("meetingId",    r.meetingId());
            out.put("resolutionId", r.resolutionId());
            out.put("referredBy",   r.referredBy());
            committeePort.describe(r, out);   // resolution outcome/tally + meeting status/quorum
        });
        return out;
    }

    // ── CH-17: freeze windows, change calendar, conflict detection ────────────

    /** Reject a move to SCHEDULED whose window intersects an active freeze
     *  (EMERGENCY changes are exempt where the window allows it). No-op if the
     *  freeze repo is not wired or the change has no scheduled start. */
    private void enforceFreeze(ChangeRequest c) {
        if (freezeRepo == null || c.getScheduledStart() == null) return;
        LocalDateTime start = c.getScheduledStart();
        LocalDateTime end = c.getScheduledEnd() != null ? c.getScheduledEnd() : start;
        boolean emergency = "EMERGENCY".equals(c.getChangeType());
        for (ChangeFreezeWindow w : freezeRepo.findByOrganisationIdAndActiveTrue(c.getOrganisationId())) {
            boolean overlaps = !start.isAfter(w.getEndsAt()) && !end.isBefore(w.getStartsAt());
            if (!overlaps) continue;
            boolean appliesToScope = "ALL".equals(w.getScope())
                || ("CATEGORY".equals(w.getScope()) && w.getScopeValue() != null
                    && w.getScopeValue().equalsIgnoreCase(nullSafeStr(c.getCategory())));
            if (!appliesToScope) continue;
            if (emergency && Boolean.TRUE.equals(w.getAllowEmergency())) continue;   // emergency exemption
            throw new IllegalStateException(
                "Cannot schedule: the change window falls within an active freeze — "
                    + nullSafeStr(w.getNameEn()) + " (" + w.getStartsAt() + " to " + w.getEndsAt() + ")"
                    + (emergency ? "" : ". Only EMERGENCY changes may be scheduled during this freeze."));
        }
    }

    /** CH-17: the change calendar — scheduled changes intersecting [from,to] plus
     *  the active freeze windows overlapping that range (for the FE to band). */
    @Transactional(readOnly = true)
    public Map<String, Object> calendar(LocalDateTime from, LocalDateTime to) {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Object> out = new HashMap<>();
        out.put("from", from); out.put("to", to);
        out.put("changes", changeRepo.findScheduledBetween(org, from, to).stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());               m.put("changeCode", c.getChangeCode());
            m.put("titleEn", c.getTitleEn());     m.put("titleAr", c.getTitleAr());
            m.put("changeType", c.getChangeType());m.put("riskLevel", c.getRiskLevel());
            m.put("status", c.getStatus());
            m.put("scheduledStart", c.getScheduledStart()); m.put("scheduledEnd", c.getScheduledEnd());
            return m;
        }).toList());
        out.put("freezes", freezeRepo == null ? List.of()
            : freezeRepo.findByOrganisationIdAndActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(org, to, from));
        return out;
    }

    /** CH-17: schedule conflicts — other live changes whose window overlaps this
     *  change's, flagged where they also share a linked entity or category. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> conflicts(Long id) {
        ChangeRequest c = require(id);
        if (c.getScheduledStart() == null) return List.of();
        LocalDateTime start = c.getScheduledStart();
        LocalDateTime end = c.getScheduledEnd() != null ? c.getScheduledEnd() : start;
        Set<String> myLinks = linkRepo.findByChangeRequestId(id).stream()
            .map(l -> l.getEntityType() + "#" + l.getEntityId())
            .collect(java.util.stream.Collectors.toSet());
        return changeRepo.findScheduleConflicts(c.getOrganisationId(), id, start, end).stream().map(o -> {
            Set<String> theirLinks = linkRepo.findByChangeRequestId(o.getId()).stream()
                .map(l -> l.getEntityType() + "#" + l.getEntityId())
                .collect(java.util.stream.Collectors.toSet());
            Set<String> shared = new java.util.HashSet<>(myLinks); shared.retainAll(theirLinks);
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());                m.put("changeCode", o.getChangeCode());
            m.put("titleEn", o.getTitleEn());      m.put("status", o.getStatus());
            m.put("scheduledStart", o.getScheduledStart()); m.put("scheduledEnd", o.getScheduledEnd());
            m.put("sameCategory", c.getCategory() != null && c.getCategory().equals(o.getCategory()));
            m.put("sharedEntities", new java.util.ArrayList<>(shared));
            return m;
        }).toList();
    }

    // ── CH-17: freeze-window management ───────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChangeFreezeWindow> freezeWindows() {
        if (freezeRepo == null) return List.of();
        return freezeRepo.findByOrganisationIdOrderByStartsAtAsc(TenantContext.getCurrentTenant());
    }

    @Transactional
    public ChangeFreezeWindow createFreezeWindow(ChangeFreezeWindow body) {
        if (freezeRepo == null) throw new IllegalStateException("Freeze windows unavailable");
        body.setId(null);
        body.setIsDeleted(false);
        body.setOrganisationId(TenantContext.getCurrentTenant());
        if (body.getStartsAt() == null || body.getEndsAt() == null)
            throw new IllegalArgumentException("startsAt and endsAt are required");
        if (body.getEndsAt().isBefore(body.getStartsAt()))
            throw new IllegalArgumentException("endsAt must be on or after startsAt");
        if (body.getScope() == null) body.setScope("ALL");
        if (body.getCode() == null || body.getCode().isBlank())
            body.setCode("FW-" + System.currentTimeMillis());
        ChangeFreezeWindow saved = freezeRepo.save(body);
        audit.log("FREEZE_CREATE", "ChangeFreezeWindow", saved.getId(),
            "Freeze window created: " + nullSafeMsg(saved.getNameEn())
                + " (" + saved.getStartsAt() + " → " + saved.getEndsAt() + ")");
        return saved;
    }

    @Transactional
    public void deleteFreezeWindow(Long id) {
        if (freezeRepo == null) throw new IllegalStateException("Freeze windows unavailable");
        ChangeFreezeWindow w = freezeRepo.findById(id)
            .filter(x -> x.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Freeze window not found: " + id));
        w.setIsDeleted(true);
        freezeRepo.save(w);
        audit.log("FREEZE_DELETE", "ChangeFreezeWindow", id,
            "Freeze window removed: " + nullSafeMsg(w.getNameEn()));
    }

    // ── CH-13: rolled-back change → auto-raised incident + back-link ──────────

    /** Map a change risk level onto an incident severity (identical vocabulary;
     *  anything unexpected defaults to MEDIUM). */
    private static String mapRiskToSeverity(String riskLevel) {
        return RISK_LEVELS.contains(riskLevel) ? riskLevel : "MEDIUM";
    }

    /**
     * CH-13: when a change is rolled back, open a downstream Incident and link it
     * back to the change ({@code INCIDENT} / {@code TRIGGERED_BY}) so the failure
     * enters the NIST 800-61r2 incident lifecycle instead of only emitting a
     * CHANGE_FAILED event. Idempotent — skips if the change already carries an
     * INCIDENT TRIGGERED_BY link. No-op when the incident port is not wired
     * (unit tests that construct the service directly leave incidentPort null).
     *
     * <p>S5/CE-1: the eight nulls and the {@code "OPEN"} literal that used to be built here now
     * live in {@code IncidentAdapter}. This method states what it wants; the adapter knows how the
     * incident module wants to be asked. The risk→severity mapping stays here, because that is a
     * statement about change risk and not about incidents.
     */
    private void autoRaiseRollbackIncident(ChangeRequest c) {
        if (incidentPort == null) return;
        boolean already = linkRepo.findByChangeRequestId(c.getId()).stream()
            .anyMatch(l -> "INCIDENT".equals(l.getEntityType()) && "TRIGGERED_BY".equals(l.getLinkType()));
        if (already) return;

        String code = nullSafeStr(c.getChangeCode());
        var raised = incidentPort.raiseFromChange(
            "Failed change " + code + " — rolled back",
            "تغيير فاشل " + code + " — تم التراجع",
            mapRiskToSeverity(c.getRiskLevel()), "INFRASTRUCTURE",
            c.getChangeOwnerName(),
            "Auto-raised from rolled-back change " + code
                + " (" + nullSafeStr(c.getTitleEn()) + "). Investigate root cause "
                + "(test-plan gap, missing rollback step) and confirm service restoration.");
        if (raised.isEmpty()) return;

        ChangeLink link = new ChangeLink();
        link.setChangeRequestId(c.getId());
        link.setEntityType("INCIDENT");
        link.setEntityId(raised.get().id());
        link.setLinkType("TRIGGERED_BY");
        link.setNotes("Auto-linked: incident raised on rollback of " + code);
        linkRepo.save(link);

        audit.log("ROLLBACK_INCIDENT", "ChangeRequest", c.getId(),
            "Rolled-back change " + nullSafeMsg(code) + " auto-raised incident #" + raised.get().id()
                + " and linked it (INCIDENT / TRIGGERED_BY)");
    }
}
