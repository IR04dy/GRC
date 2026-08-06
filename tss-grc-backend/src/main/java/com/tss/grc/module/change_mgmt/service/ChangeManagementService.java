package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.exception.ChangeAuthorityNotConfiguredException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.util.SaudiWorkdayUtil;
import com.tss.grc.module.change_mgmt.model.RatificationStates;
import com.tss.grc.module.platform.calendar.service.SaudiWorkdayCalendar;
import com.tss.grc.module.platform.service.RoleDirectoryService;
import com.tss.grc.common.exception.DomainAccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.tss.grc.module.change_mgmt.dto.CabVoteRequest;
import com.tss.grc.module.change_mgmt.dto.ChangeConditionRequest;
import com.tss.grc.module.change_mgmt.entity.*;
import com.tss.grc.module.change_mgmt.repository.*;
import com.tss.grc.module.change_mgmt.spi.CapaPort;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import com.tss.grc.module.change_mgmt.spi.EvidencePort;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        // S7c / X-2 — a CAB may also DEFER or ask for MORE INFORMATION. Both are decisions the
        // committee can already reach (CommitteeResolution.outcome has permitted DEFERRED since
        // V154) and the change previously could not represent: it stayed at CAB_PENDING, with no
        // cab_decision_at, byte-identical to a change nobody had looked at.
        Map.entry("CAB_PENDING",        Set.of("CAB_APPROVED", "CAB_REJECTED",
                                               "CAB_DEFERRED", "MORE_INFO_REQUIRED")),
        // Both route back into the queue rather than forward: a deferred motion is re-tabled
        // (AR-152 already tells the secretary to), and a change whose questions have been answered
        // goes back before the same CAB. Neither is terminal, and neither may jump the gate — the
        // only way on from here is through CAB_PENDING again.
        Map.entry("CAB_DEFERRED",       Set.of("CAB_PENDING", "CANCELLED")),
        Map.entry("MORE_INFO_REQUIRED", Set.of("CAB_PENDING", "CANCELLED")),
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
        Set.of("CAB_APPROVED", "CAB_REJECTED", "EMERGENCY_APPROVED",
               // S7c / X-2 — these are CAB DECISIONS, not edits. Recording "the committee deferred
               // this" or "the committee wants more information" is an assertion about what a
               // governance body concluded, so it needs CHANGE:APPROVE like every other decision.
               // Leaving them on the silent CHANGE:WRITE default would let anyone who can edit a
               // change record a committee conclusion — the F2-29 defect, reintroduced.
               // `ChangeDecisionAuthorityTest` predicted this exact question and is what forced it
               // to be answered here rather than defaulted.
               "CAB_DEFERRED", "MORE_INFO_REQUIRED");

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

    /**
     * S7e/CE-5: the conditions register (V352).
     *
     * <p>⚠ <b>Field-injected, and the no-op when absent is a test affordance that must never reach
     * production.</b> Six test classes construct this service positionally, so an eighth constructor
     * argument would break them all — but a null repository means {@code assertConditionsSatisfied}
     * cannot see the blockers, which is the fail-OPEN direction. That is acceptable only because it
     * is unreachable in the running app (Spring Data always provides the bean) and because
     * {@code ChangeConditionRegisterTest.theRegisterRepositoryIsActuallyWired} asserts the injection
     * point exists rather than trusting it — the exact "green-because-absent" trap
     * {@code ChangeAuthorityFailClosedTest} documented after three of its assertions passed
     * vacuously.
     */
    @Autowired(required = false)
    private ChangeConditionRepository conditionRepo;

    /**
     * S8a/CE-9: a change and its conditions carry real evidence — hashed, versioned and
     * four-eyes reviewed — instead of prose in {@code test_plan} / {@code rollback_plan}.
     *
     * <p>⚠ <b>The absent-port polarity is SPLIT here, and the split is the control.</b> Reads and
     * attaches no-op when the adapter is missing (a directly-constructed unit test must still work).
     * But {@link #requireVerifiedEvidence} treats a null port as <em>cannot verify</em> and
     * REFUSES — because a satisfaction gate that passes when it cannot see the evidence is the
     * fail-open direction, which is F2-95's defect exactly ("satisfied" reading true over an
     * unverifiable requirement). Same reasoning as A17-D3's ratification policy field, whose javadoc
     * flags the same inversion.
     */
    @Autowired(required = false)
    private EvidencePort evidencePort;

    /**
     * S8b/CE-12: the §12 KPI pack. Optional for the same reason as the ports — 50-odd unit tests
     * construct this service directly, and analytics() must keep working without a Spring context.
     * When absent the {@code kpis} key is simply not present, which the frontend already tolerates
     * (it renders the pack only when the server sends one).
     */
    @Autowired(required = false)
    private ChangeKpiService kpiService;

    /** S7d/CE-7: a PIR follow-up becomes a tracked CAPA in the Base universal tracker.
     *  S5/CE-1: reached through {@link CapaPort} — {@code module.capa} is a peer, so the same
     *  boundary rule applies as for incidents. Optional (field-injected) so unit tests that
     *  construct the service directly need not supply it; the follow-up hook then no-ops, which
     *  is the right polarity — losing the derived CAPA must not block recording the review. */
    @Autowired(required = false)
    private CapaPort capaPort;

    /** CH-17: per-tenant (type x risk) authorisation matrix driving the CAB
     *  quorum. Optional — when absent the flat cab-min-approvals default applies
     *  (keeps unit tests that construct the service directly working). */
    @Autowired(required = false)
    private ChangeAuthorisationMatrixRepository authMatrixRepo;

    /** F2-95: resolves the matrix's {@code required_roles} against the platform's own role
     *  vocabulary. A {@code platform} service rather than an `iam` import, because ADR-012
     *  forbids this module a peer — see {@link RoleDirectoryService}. Optional, so a required
     *  role reads as unverifiable rather than satisfied when the directory is absent: the
     *  fail-safe direction, since claiming satisfied is the defect being fixed. */
    @Autowired(required = false)
    private RoleDirectoryService roleDirectory;

    /** CH-17: freeze/blackout windows. Optional — when absent, no freeze gate
     *  is enforced (keeps directly-constructed unit tests working). */
    @Autowired(required = false)
    private ChangeFreezeWindowRepository freezeRepo;

    /**
     * S8e / CE-2: the change model catalogue. Optional — directly-constructed unit tests need not
     * supply it.
     *
     * <p>⚠ <b>The polarity is deliberate and it is the opposite of S8a's evidence gate.</b> When
     * this is null the model requirement cannot be evaluated, and the transition is ALLOWED. That is
     * fail-open, which S8a refused for verified evidence — the difference is what the two guards
     * protect. The evidence gate stands between a CAB obligation and its discharge, so a guard that
     * cannot see the evidence must refuse. This one enforces an <b>opt-in tenant preference</b> whose
     * flag defaults to off; refusing every AUTO-gated change in a deployment that has no catalogue
     * wired would break work in a configuration nobody asked to be strict.
     */
    @Autowired(required = false)
    private ChangeModelService modelService;

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
        // CE-15/F2-112 — capture the raiser's identity, because nothing did and the self-review gates
        // compare it. Only the ID: `requested_by_name` stays whatever the tenant typed, since a team
        // ('CloudOps') is a legitimate thing to record as the requesting party even though it is not a
        // person. The id is the part a gate can stand on, and only the server can know it.
        ChangeSoD.Actor raiser = ChangeSoD.actor();
        if (c.getRequestedById() == null && raiser.id() != null) c.setRequestedById(raiser.id());
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
        // S7d/CE-7: CH-11 lets a reviewer refine the retrospective after POST_REVIEW, and the
        // frontend's PIR card saves through exactly this path — so a follow-up first written here
        // must raise its CAPA here too, or the hook would only ever fire for reviewers who got the
        // text right on the transition body. Idempotent, so the transition and the edit cannot
        // both raise one.
        syncPirFollowUpCapa(saved);
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
                requireRequiredRolesPresent(id);
                // S8e/CE-2: when the tenant has switched this cell to "pre-approved procedure
                // required", the change must cite a LIVE model — APPROVED, unexpired, not deviated.
                requireModelIfCellDemandsIt(c);
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
                // ⚠ F2-103 — THIS LINE WAS MISSING, on the one decision that matters most.
                // F2-93 added cab_decision_by_id/_name because "the record says when the CAB
                // decided and never who", and it was found and fixed while A17-D3 was in flight —
                // so the author got stamped on the two paths that slice touched (EMERGENCY_APPROVED
                // and CAB_REJECTED) and not on ordinary CAB approval. Measured 2026-08-03:
                // **zero** rows in dev have cab_decision_by_id set, and 2 have cab_decision_at
                // without it. The only test asserting the column lives in EmergencyRatificationTest.
                // So the platform's central authorisation act recorded a timestamp and an
                // anonymous approver, which is precisely the gap F2-93 was raised to close.
                stampDecisionAuthor(c);
            }
            case "CAB_REJECTED" -> {
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
                stampDecisionAuthor(c);
            }
            // ── S7c / X-2 — the committee considered it and did not authorise it ──────────────
            // The whole point of these two states is the stamp: a change that carries
            // cab_decision_at + the author is one a named person recorded a committee conclusion
            // about, and that is what distinguishes it from CAB_PENDING-since-forever.
            //
            // Deliberately NOT calling requireConfiguredChangeAuthority: neither state authorises
            // anything, so refusing to record a deferral because the tenant's gate map is broken
            // would trap the change in exactly the limbo this slice exists to end. Same reasoning
            // that made CAB_REJECTED "deliberately open" under A17-D2, and
            // ChangeAuthorityFailClosedTest forces the classification to be stated.
            case "CAB_DEFERRED", "MORE_INFO_REQUIRED" -> {
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
                stampDecisionAuthor(c);
            }
            case "SCHEDULED" ->
                // CH-17: block scheduling into an active freeze/blackout window
                // (emergencies exempt where the window permits).
                enforceFreeze(c);
            case "IMPLEMENTING" -> {
                // S7e/CE-5: the conditions gate. FIRST in this branch, before any state is
                // mutated — a refusal must leave the change exactly as it was, and arming the
                // ratification clock for an implementation that is then refused would start an SLA
                // against work that never began.
                assertConditionsSatisfied(c);
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
                // CE-15/§10 — the gate, then the stamp. Both need the principal: the reviewer is who
                // is signed in, not who the caller typed (see capturePirReviewer).
                assertPirReviewerIsNotImplementer(c);
                c.setPirReviewedAt(LocalDateTime.now());
                capturePirReviewer(c, body);
                // S7d/CE-7: the follow-up the reviewer just wrote becomes tracked work.
                syncPirFollowUpCapa(c);
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
                // ── S7d / CE-7 — a change that failed its objectives may not close silently ──────
                // Without this, the follow-up CAPA is opt-in on the one path where it matters: a
                // reviewer records "objectives not met", leaves the follow-up blank, closes the
                // change, and the platform holds a recorded failure with nothing tracking it. That
                // is the "configured but never enforced" shape (F2-62/F2-95), and the gate is what
                // makes syncPirFollowUpCapa reachable rather than optional.
                //
                // Deliberately NOT at POST_REVIEW: refusing the transition would stop the reviewer
                // recording the honest finding at all, the trap S7c avoided for CAB_DEFERRED. State
                // the failure freely; say what you are doing about it before you close.
                //
                // Deliberately NOT exempting STANDARD, unlike the summary gate above. That exemption
                // exists because a pre-approved low-risk change needs no retrospective — but this
                // gate fires only on a review someone volunteered AND recorded as failed, so the
                // exemption's reason does not reach it. Measured safe: zero rows in either database
                // carry any PIR field, so nothing already closed is affected.
                if (Boolean.FALSE.equals(c.getPirObjectivesMet())
                        && (c.getPirFollowUp() == null || c.getPirFollowUp().isBlank()))
                    throw new IllegalStateException(
                        "Cannot close: the post-implementation review records that this change did "
                            + "not meet its objectives, so a follow-up action is required before "
                            + "closure. Record it in the PIR follow-up field — it is raised as a "
                            + "tracked CAPA. If no further action is needed, say why there.");
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
            throw new DomainAccessDeniedException("A CAB vote must be cast by an identified user.");
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

    // ── Conditions register (S7e / CE-5) ────────────────────────────────────
    //
    // G-5: "no approve-with-conditions; conditions not recorded or enforced before
    // implementation". A CAB that approved "subject to the DR rehearsal being re-run" had one place
    // to put that — `cab_decision_notes`, free text nothing reads — and the change then proceeded to
    // IMPLEMENTING with the obligation unrecorded, unowned and unchecked. Measured 2026-08-04: zero
    // conditions concept anywhere in either database.
    //
    // The register is a table, not a status word: conditionality is DERIVED from carrying
    // conditions, so it cannot disagree with them (see ChangeConditionStatus).

    @Transactional(readOnly = true)
    public List<ChangeCondition> conditions(Long changeId) {
        require(changeId);
        return conditionRepo.findByOrganisationIdAndChangeRequestIdOrderBySortOrderAscIdAsc(
            TenantContext.getCurrentTenant(), changeId);
    }

    /** The tenant-wide blocker list — every open obligation, soonest due first, undated last. */
    @Transactional(readOnly = true)
    public List<ChangeCondition> openConditions() {
        return conditionRepo.findOpenForOrg(TenantContext.getCurrentTenant());
    }

    /**
     * Attach a condition to a change.
     *
     * <p>An approval act, so it is guarded by {@code CHANGE:APPROVE} at the controller and stamps its
     * author here — F2-93/F2-103's lesson applied at the point a new authored record is introduced
     * rather than after someone notices the column is empty. An obligation with no named imposer
     * cannot be appealed, varied or audited.
     *
     * <p>Deliberately permitted at any lifecycle state. A CAB imposes conditions when it approves,
     * but an ECAB ratifying an emergency change after the fact imposes them on an already-implemented
     * one, and a condition discovered during implementation is exactly the case a register exists to
     * catch. Restricting the state would lose the governance fact to keep the model tidy — S7c's rule.
     */
    @Transactional
    public ChangeCondition addCondition(Long changeId, ChangeConditionRequest req) {
        ChangeRequest c = require(changeId);
        ChangeCondition body = new ChangeCondition();
        body.setTextEn(req.textEn());
        body.setTextAr(req.textAr());
        body.setOwnerName(req.ownerName());
        body.setDueAt(req.dueAt());
        body.setSortOrder(req.sortOrder());
        // The entity is built HERE from a narrow DTO, never bound from the body — MassAssignmentRatchet
        // rejected the first draft by name, and it was right: a scrub is a list of fields somebody
        // remembered, a DTO is the set of fields that exist. These four lines stay because addCondition
        // is reachable from inside the module too, and a caller that builds the entity itself must not
        // be able to hand over a closed one.
        body.setId(null);
        body.setChangeRequestId(changeId);
        body.setOrganisationId(c.getOrganisationId());
        body.setStatus(ChangeConditionStatus.OPEN);
        body.setClosedById(null);
        body.setClosedByName(null);
        body.setClosedAt(null);
        body.setClosureNotes(null);
        body.setImposedAt(LocalDateTime.now());
        stampImposer(body);
        if (body.getSortOrder() == null) body.setSortOrder(0);
        ChangeCondition saved = conditionRepo.save(body);
        audit.log("CONDITION_IMPOSED", "ChangeRequest", changeId,
            "Condition #" + saved.getId() + " imposed on change " + nullSafeMsg(c.getChangeCode())
                + " by " + nullSafeMsg(saved.getImposedByName()) + ": "
                + nullSafeMsg(saved.getTextEn())
                + (saved.getDueAt() != null ? " (due " + saved.getDueAt() + ")" : ""));
        return saved;
    }

    /**
     * Record that a condition was met. Operational — {@code CHANGE:WRITE}.
     *
     * <p>S8's {@code ConditionsSatisfiedGuard} will require a <em>verified</em> evidence link here;
     * {@code closureNotes} is the seam it grows into. Until then the notes are the reference, and the
     * author is stamped either way so the eventual four-eyes check has something to compare.
     */
    @Transactional
    public ChangeCondition satisfyCondition(Long conditionId, String notes) {
        return closeCondition(conditionId, ChangeConditionStatus.SATISFIED, notes);
    }

    /**
     * Remove a condition without it being met. An approval act — {@code CHANGE:APPROVE} — because it
     * forgives an obligation a governance body imposed.
     *
     * <p>A reason is mandatory (`ck_cc_waiver_reason` enforces it in the schema too): satisfaction
     * can stand on its evidence, but removing an obligation cannot stand on nothing. And the waiver
     * may not be the change's requester — the same self-review refusal as the CAB vote's SoD gate,
     * for the same reason: otherwise anyone able to raise a change could impose a condition on it
     * and forgive it in the next request, which is a conditional approval that means nothing.
     */
    @Transactional
    public ChangeCondition waiveCondition(Long conditionId, String reason) {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException(
                "A waiver reason is required — removing an obligation the approving authority "
                    + "imposed cannot be recorded without one");
        return closeCondition(conditionId, ChangeConditionStatus.WAIVED, reason);
    }

    private ChangeCondition closeCondition(Long conditionId, String target, String notes) {
        Long org = TenantContext.getCurrentTenant();
        ChangeCondition cond = conditionRepo.findByIdAndOrganisationId(conditionId, org)
            .orElseThrow(() -> new EntityNotFoundException("Condition not found: " + conditionId));
        if (ChangeConditionStatus.isClosed(cond.getStatus()))
            throw new IllegalStateException(
                "Condition #" + conditionId + " is already " + cond.getStatus()
                    + " — reopening a closed condition would erase who closed it and when. "
                    + "Impose a new condition instead.");
        ChangeRequest c = require(cond.getChangeRequestId());

        if (ChangeConditionStatus.WAIVED.equals(target)) assertNotSelfWaiver(c);
        // S8a/CE-9 — the `ConditionsSatisfiedGuard` half S7e deferred to S8: SATISFIED now means
        // "verified evidence says so", not "somebody typed a note". Waiving is untouched, because a
        // waiver is an authority act with a reason and an independent approver — so the path is
        // strengthened, never trapped.
        if (ChangeConditionStatus.SATISFIED.equals(target)) {
            // CE-15/§10: the owner may not close their own condition. Ordered AFTER the waiver check
            // and BEFORE the evidence read, because "you may not do this at all" is a clearer answer
            // than "your evidence is not approved yet" to someone who cannot do it either way.
            assertNotSelfSatisfaction(cond);
            requireVerifiedEvidence(conditionId);
        }

        cond.setStatus(target);
        cond.setClosedAt(LocalDateTime.now());
        cond.setClosureNotes(notes);
        stampCloser(cond);
        // ck_cc_closure refuses a closed row with no author, so a principal-less context (a
        // scheduler, a test that forgot to authenticate) must fail loudly here rather than at the
        // database with an opaque constraint violation.
        if (cond.getClosedByName() == null || cond.getClosedByName().isBlank())
            throw new IllegalStateException(
                "Cannot close condition #" + conditionId + ": no authenticated principal to record "
                    + "as the author, and a condition closed by nobody is the defect this register "
                    + "replaces");
        ChangeCondition saved = conditionRepo.save(cond);
        audit.log("CONDITION_" + target, "ChangeRequest", cond.getChangeRequestId(),
            "Condition #" + conditionId + " on change " + nullSafeMsg(c.getChangeCode()) + " "
                + target.toLowerCase(Locale.ROOT) + " by " + nullSafeMsg(saved.getClosedByName())
                + (notes != null && !notes.isBlank() ? " — " + notes : ""));
        return saved;
    }

    /**
     * CE-5's enforcement half — {@code SCHEDULED → IMPLEMENTING} is refused while any condition is
     * {@code OPEN}.
     *
     * <p><b>Why the guard ships with the register and not with S8.</b> The plan lists
     * {@code ConditionsSatisfiedGuard} under S8, and S8's version adds a real requirement — the
     * satisfaction must carry <em>verified</em> evidence. But a register nothing consults is a dead
     * control, and this programme has spent four slices closing exactly that shape (F2-62's invented
     * role, F2-95's 33 configured requirements enforced zero times, F2-89's outcome nothing could
     * represent). So S7e ships the gate and S8 strengthens what counts as satisfying it.
     *
     * <p>Measured safe: zero conditions exist in either database, so this can refuse no change that
     * exists today. Enforcement begins with the first condition a CAB actually imposes — which is
     * also why V352 refuses to seed the table.
     */
    private void assertConditionsSatisfied(ChangeRequest c) {
        if (conditionRepo == null) return;   // see the field's javadoc — test affordance only
        long open = conditionRepo.countByOrganisationIdAndChangeRequestIdAndStatus(
            c.getOrganisationId(), c.getId(), ChangeConditionStatus.OPEN);
        if (open == 0) return;
        List<ChangeCondition> blocking =
            conditionRepo.findByOrganisationIdAndChangeRequestIdOrderBySortOrderAscIdAsc(
                    c.getOrganisationId(), c.getId())
                .stream().filter(x -> ChangeConditionStatus.OPEN.equals(x.getStatus())).toList();
        // Name them. The A17-D2/F2-96 remedy rule: an operator told "conditions are outstanding"
        // has to go looking, and the thing they must do is in the message.
        String list = blocking.stream()
            .map(x -> "#" + x.getId() + " " + nullSafeStr(x.getTextEn()))
            .collect(java.util.stream.Collectors.joining("; "));
        throw new IllegalStateException(
            "Cannot start implementation: " + open + " condition"
                + (open == 1 ? "" : "s") + " attached to this approval "
                + (open == 1 ? "is" : "are") + " still open — " + list
                + ". Record each as satisfied, or have an approver waive it with a reason.");
    }

    /** True when this change was approved subject to conditions — derived, never stored. */
    @Transactional(readOnly = true)
    public boolean isConditional(Long changeId) {
        require(changeId);
        return ChangeConditionStatus.isConditional(
            conditionRepo.countByOrganisationIdAndChangeRequestId(
                TenantContext.getCurrentTenant(), changeId));
    }

    // ── S8a / CE-9: evidence on a change and on its conditions ──────────────────────────────────

    /** Everything attached to this change. Empty when the evidence subsystem is unavailable. */
    @Transactional(readOnly = true)
    public List<EvidencePort.EvidenceRef> evidenceForChange(Long changeId) {
        require(changeId);
        return evidencePort == null ? List.of()
            : evidencePort.attached(EvidencePort.Subject.CHANGE, changeId);
    }

    /** Everything attached to this condition — the artefacts that discharge the obligation. */
    @Transactional(readOnly = true)
    public List<EvidencePort.EvidenceRef> evidenceForCondition(Long conditionId) {
        requireCondition(conditionId);
        return evidencePort == null ? List.of()
            : evidencePort.attached(EvidencePort.Subject.CONDITION, conditionId);
    }

    /**
     * Attach an existing evidence item to a change.
     *
     * <p>Attaches, never uploads: the item is created, hashed, versioned and reviewed inside the
     * evidence subsystem. A change-module upload path would be a second door into the same store with
     * none of that — the deficiency G-9 describes rather than its remedy.
     */
    @Transactional
    public EvidencePort.EvidenceRef attachEvidenceToChange(Long changeId, Long evidenceId, String notes) {
        ChangeRequest c = require(changeId);
        EvidencePort.EvidenceRef ref = attachOrThrow(EvidencePort.Subject.CHANGE, changeId, evidenceId, notes);
        audit.log("EVIDENCE_ATTACHED", "ChangeRequest", changeId,
            "Evidence #" + evidenceId + " (" + nullSafeStr(ref.titleEn()) + ", " + nullSafeStr(ref.status())
                + ") attached to change " + nullSafeMsg(c.getChangeCode()));
        return ref;
    }

    /** Attach an existing evidence item to one condition on an approval. */
    @Transactional
    public EvidencePort.EvidenceRef attachEvidenceToCondition(Long conditionId, Long evidenceId, String notes) {
        ChangeCondition cond = requireCondition(conditionId);
        EvidencePort.EvidenceRef ref =
            attachOrThrow(EvidencePort.Subject.CONDITION, conditionId, evidenceId, notes);
        audit.log("EVIDENCE_ATTACHED", "ChangeRequest", cond.getChangeRequestId(),
            "Evidence #" + evidenceId + " (" + nullSafeStr(ref.titleEn()) + ", " + nullSafeStr(ref.status())
                + ") attached to condition #" + conditionId);
        return ref;
    }

    private EvidencePort.EvidenceRef attachOrThrow(EvidencePort.Subject subject, Long subjectId,
                                                   Long evidenceId, String notes) {
        if (evidencePort == null)
            throw new IllegalStateException(
                "The evidence subsystem is not available, so evidence cannot be attached");
        return evidencePort.attach(subject, subjectId, evidenceId, notes)
            // The adapter returns empty for an id that does not resolve inside the tenant. A 404 is
            // the honest answer: silently succeeding would leave a change claiming evidence it has
            // not got, which is worse than the free text this slice replaces.
            .orElseThrow(() -> new EntityNotFoundException(
                "Evidence item " + evidenceId + " was not found in this organisation, so it was not "
                    + "attached. Create and review the evidence in the evidence workspace first."));
    }

    private ChangeCondition requireCondition(Long conditionId) {
        Long org = TenantContext.getCurrentTenant();
        ChangeCondition cond = conditionRepo == null ? null
            : conditionRepo.findByIdAndOrganisationId(conditionId, org).orElse(null);
        if (cond == null) throw new EntityNotFoundException("Condition not found: " + conditionId);
        require(cond.getChangeRequestId());   // tenant + existence of the parent change
        return cond;
    }

    /**
     * S8a — a condition may only be recorded SATISFIED when <b>verified</b> evidence says so.
     *
     * <p>This is the "verified" half of {@code ConditionsSatisfiedGuard} that S7e shipped the gate
     * without and explicitly left to S8. Verified means the subsystem's {@code APPROVED} state and
     * nothing else: measured across both databases, 34 of 104 items are APPROVED while 29 are
     * PENDING_REVIEW and 29 are EXPIRED, so accepting anything else would let evidence somebody
     * merely uploaded — or evidence that has aged out — discharge an obligation the CAB imposed.
     *
     * <p>⚠ <b>Fails closed when the port is absent</b>, unlike every read above. A gate that passes
     * because it cannot see the evidence is F2-95's defect verbatim ("satisfied" reading true over an
     * unverifiable requirement), and the escape hatch already exists and is auditable: an independent
     * {@code CHANGE:APPROVE} holder may <em>waive</em> the condition with a stated reason.
     *
     * <p>Measured safe to introduce: zero conditions exist in either database, so no recorded
     * satisfaction is invalidated and no change is stranded.
     */
    private void requireVerifiedEvidence(Long conditionId) {
        List<EvidencePort.EvidenceRef> attached = evidencePort == null ? List.of()
            : evidencePort.attached(EvidencePort.Subject.CONDITION, conditionId);
        if (attached.stream().anyMatch(EvidencePort.EvidenceRef::verified)) return;

        String seen = attached.isEmpty() ? "none attached"
            : attached.stream()
                .map(e -> "#" + e.id() + " " + nullSafeStr(e.titleEn()) + " [" + nullSafeStr(e.status()) + "]")
                .collect(java.util.stream.Collectors.joining("; "));
        throw new IllegalStateException(
            "Cannot record condition #" + conditionId + " as satisfied: it carries no APPROVED "
                + "evidence. Attach the artefact that discharges it and have it reviewed in the "
                + "evidence workspace — uploaded-but-unreviewed evidence does not count. Currently: "
                + seen + ". If the obligation no longer applies, an approver other than the "
                + "requester may waive it with a reason.");
    }

    private void stampImposer(ChangeCondition cond) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;
        if (auth.getPrincipal() instanceof com.tss.grc.security.GrcUserDetails u) {
            cond.setImposedById(u.getUserId());
            cond.setImposedByName(u.getUsername());
        } else if (auth.getName() != null) {
            cond.setImposedByName(auth.getName());
        }
    }

    private void stampCloser(ChangeCondition cond) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;
        if (auth.getPrincipal() instanceof com.tss.grc.security.GrcUserDetails u) {
            cond.setClosedById(u.getUserId());
            cond.setClosedByName(u.getUsername());
        } else if (auth.getName() != null) {
            cond.setClosedByName(auth.getName());
        }
    }

    /**
     * A waiver may not come from the change's own requester or owner.
     *
     * <p>Resolved on the user id where the change captured one, falling back to the name only for
     * legacy rows — the ordering DF-4/F2-24 established for the CAB vote's SoD gate, because a
     * name-only comparison is exactly what let a requester approve their own change by claiming
     * somebody else's name.
     */
    /**
     * ⚠ CE-15 — the comparison moved to {@link ChangeSoD}, unchanged in behaviour and no longer the
     * only copy of it. There are now three self-review refusals over a change's people (this waiver,
     * the PIR review, the condition closure), and three inline copies of one rule is the mirror
     * maintained by assertion that S8h and CE-13 each refused.
     *
     * <p>⚠ And the extraction is what exposed <b>F2-112</b>: this refusal has never been able to fire.
     * {@code requested_by_id} and {@code change_owner_id} are NULL on 17 of 17 dev changes because
     * nothing wrote them, so the rule always took its name branch and compared a username against a
     * team label ('CloudOps', 'IAM Team') — 0 of 28 such labels resolve to any user. {@link #create}
     * now captures the principal's id, so this begins to bite for changes raised from here on, and
     * {@code /sod-coverage} publishes how much of the population it still cannot check.
     */
    private void assertNotSelfWaiver(ChangeRequest c) {
        ChangeSoD.Verdict v = ChangeSoD.compareAny(ChangeSoD.actor(), List.of(
            new ChangeSoD.Party("requestedBy", c.getRequestedById(), c.getRequestedByName()),
            new ChangeSoD.Party("changeOwner", c.getChangeOwnerId(), c.getChangeOwnerName())));
        if (v.refuses())
            throw new IllegalStateException(
                "You raised or own this change, so you may not waive a condition attached to its "
                    + "approval — a condition the requester can forgive is not a condition. Ask "
                    + "another CHANGE:APPROVE holder to waive it, or record it as satisfied.");
    }

    /**
     * CE-15 — what each self-review gate can actually decide, and over how much of its population.
     *
     * <p>The instrument F2-112 needed and nobody had. Measured on dev at build time, the waiver refusal
     * could decide <b>0 of 17</b> changes because the ids it compares were never written and the labels
     * are teams. A green test suite says the rule is correct; only this says whether it can run.
     *
     * <p>Deliberately one row per gate rather than one number: the remedies differ. A label-only
     * record needs its people captured as users; an absent one needs the field filled in at all.
     *
     * <p><b>F2-113 added a fourth row, and finding the gate it reports is what the first three were
     * for.</b> Track D's bar on the deciding committee vote was the same defect one layer up — a
     * name-to-label comparison that could never match — and it went unnoticed through S6, S7 and S8
     * because nothing asked the question this endpoint asks. That row is the only one this module does
     * not compute: see {@link CommitteePort#recusalCoverage}.
     */
    @Transactional(readOnly = true)
    public List<ChangeSoD.Coverage> sodCoverage() {
        Long org = TenantContext.getCurrentTenant();
        // Simple name deliberately: the local `org` shadows the `org.springframework` package,
        // so the fully-qualified form does not compile here.
        List<ChangeRequest> all = changeRepo
            .findByOrganisationIdOrderByCreatedAtDesc(org, Pageable.unpaged())
            .getContent();

        long waiverPop = all.size();
        long waiverCheckable = all.stream()
            .filter(c -> c.getRequestedById() != null || c.getChangeOwnerId() != null).count();
        long waiverLabel = all.stream()
            .filter(c -> c.getRequestedById() == null && c.getChangeOwnerId() == null)
            .filter(c -> notBlank(c.getRequestedByName()) || notBlank(c.getChangeOwnerName())).count();

        // The PIR gate's population is the changes that can still reach a review — one already reviewed
        // cannot be re-gated, and counting it would report coverage the gate will never be asked for.
        List<ChangeRequest> pirPop = all.stream().filter(c -> c.getPirReviewedAt() == null).toList();
        long pirLabel = pirPop.stream().filter(c -> notBlank(c.getImplementerName())).count();

        // `findOpenForOrg` is the register's own definition of an open condition — reusing it rather
        // than re-filtering on status here keeps one answer to "which conditions are outstanding".
        List<ChangeCondition> openConds = conditionRepo == null ? List.of()
            : conditionRepo.findOpenForOrg(org);
        long condCheckable = openConds.stream().filter(k -> k.getOwnerId() != null).count();
        long condLabel = openConds.stream()
            .filter(k -> k.getOwnerId() == null && notBlank(k.getOwnerName())).count();

        return List.of(
            new ChangeSoD.Coverage(ChangeSoD.GATE_SELF_WAIVER, waiverPop, waiverCheckable, waiverLabel,
                waiverPop - waiverCheckable - waiverLabel),
            // implementer_name has no id column at all (V357 did not add one — see its header), so this
            // gate's checkable count is 0 by construction and its honest number is the label population.
            new ChangeSoD.Coverage(ChangeSoD.GATE_PIR_NOT_IMPLEMENTER, pirPop.size(), 0, pirLabel,
                pirPop.size() - pirLabel),
            new ChangeSoD.Coverage(ChangeSoD.GATE_CONDITION_NOT_OWNER, openConds.size(), condCheckable,
                condLabel, openConds.size() - condCheckable - condLabel),
            // F2-113 — the fourth gate, and the one this module does not enforce. Its population is
            // referrals rather than changes, and its reach depends on the committee's roster as much
            // as on the change's captured people, so the count comes from the engine (see
            // CommitteePort.recusalCoverage for why computing it here would have overstated it).
            committeePort == null
                ? new ChangeSoD.Coverage(ChangeSoD.GATE_COMMITTEE_VOTE_NOT_REQUESTER, 0, 0, 0, 0)
                : committeePort.recusalCoverage("CHANGE"));
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /**
     * CE-15 / §10 — <b>the PIR approver may not be the change's implementer.</b>
     *
     * <p>ITIL's post-implementation review asks whether the change achieved its objective; the person
     * who performed it reviewing their own work is the self-review this platform refuses everywhere
     * else (the CAB vote, the condition waiver, break-glass).
     *
     * <p>⚠ The gate is only meaningful because {@link #capturePirReviewer} now takes the reviewer from
     * the authenticated principal. Before CE-15 it came from {@code strFromBody(body, "reviewer")} — a
     * string the same caller types in the same request, so the "gate" would have been defeated by
     * typing a different name. That is not a weak control; it is a control that cannot fail.
     *
     * <p>Refuses on a positive match only. {@code implementer_name} is free text and 0 of its 4 dev
     * values resolve to a user — they are teams ('AppSec on-call', 'HR Comms') — so most comparisons
     * are UNVERIFIABLE, and refusing on those would block every review of every existing change.
     * {@code /sod-coverage} reports that population rather than letting a green path imply coverage.
     */
    private void assertPirReviewerIsNotImplementer(ChangeRequest c) {
        ChangeSoD.Verdict v = ChangeSoD.compare(ChangeSoD.actor(),
            new ChangeSoD.Party("implementer", null, c.getImplementerName()));
        if (v.refuses())
            throw new IllegalStateException(
                "You implemented this change, so you may not record its post-implementation review — "
                    + "a review of your own work is not an independent one. Ask another CHANGE:WRITE "
                    + "holder to record the PIR.");
    }

    /**
     * CE-15 / §10 — <b>a condition may not be recorded satisfied by its own owner.</b>
     *
     * <p>Unconditional, and §10's qualifier "where the model demands independence" is deliberately not
     * honoured: <b>no such flag exists</b>. The only independence columns on the platform are
     * {@code admin.committee_templates.independence_required},
     * {@code admin.committee_member_terms.is_independent} and four on {@code compliance.findings} —
     * nothing for a change model. Inventing one would be F2-62's shape (a control keyed on a field
     * nobody sets). A condition its own owner can close is not a condition, which is the sentence
     * {@code assertNotSelfWaiver} already uses for the waiver, so the rule stands on its own.
     *
     * <p>Safe to ship enabled: {@code change_condition} holds <b>0 rows</b> in both databases, so this
     * can refuse nothing that exists — the S7e pattern, where enforcement begins with the first
     * condition a CAB actually imposes.
     */
    private void assertNotSelfSatisfaction(ChangeCondition cond) {
        ChangeSoD.Verdict v = ChangeSoD.compare(ChangeSoD.actor(),
            new ChangeSoD.Party("conditionOwner", cond.getOwnerId(), cond.getOwnerName()));
        if (v.refuses())
            throw new IllegalStateException(
                "You are the owner of condition #" + cond.getId() + ", so you may not record it "
                    + "satisfied — a condition its own owner can close is not a condition. Ask another "
                    + "CHANGE:WRITE holder to record the satisfaction, with the evidence.");
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

        // F2-107 — the denominator was `rows that have an outcome`, which in dev is 2 of 17, so this
        // rendered 100% on the dashboard while 15 changes had no recorded outcome at all. The honest
        // denominator is changes that REACHED AN END and therefore should have one; a change sitting
        // at IMPLEMENTED with a null outcome is a missing record, and dividing it away hid it.
        // The key stays (the FE reads it) and now carries the population beside it.
        long success = byOutcome.getOrDefault("SUCCESS", 0L);
        long withOutcome = byOutcome.values().stream().mapToLong(Long::longValue).sum();
        long reachedEnd = 0;
        for (String s : ChangeKpiService.TERMINAL_STATUSES) reachedEnd += byStatus.getOrDefault(s, 0L);
        out.put("successRatePct", reachedEnd == 0 ? 0 : Math.round(success * 1000.0 / reachedEnd) / 10.0);
        out.put("successRateDenominator", reachedEnd);
        out.put("successRateOutcomesRecorded", withOutcome);

        // S8b/CE-12 — the full §12 pack, each metric carrying its own coverage and provenance.
        if (kpiService != null) out.put("kpis", kpiService.kpis());

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
            throw new DomainAccessDeniedException("A ratification decision must be recorded by an identified user.");
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

    /**
     * CE-15 — the PIR reviewer is the authenticated principal, not a name from the body.
     *
     * <p>⚠ It used to be {@code strFromBody(body, "reviewer")}. That made the reviewer a claim the
     * caller made about themselves, which is why "PIR approver != implementer" could not be enforced:
     * the same request supplied both sides of the comparison. Same reasoning as F2-93/F2-103's
     * {@code stampDecisionAuthor} for CAB decisions — a governance act records who performed it, and
     * only the server knows that.
     *
     * <p>A client-supplied name is still accepted <b>as a note</b> when there is no principal at all
     * (an off-request path), so nothing that used to record a reviewer stops recording one — but it can
     * never override a real principal, or the override would be the bypass.
     */
    private void capturePirReviewer(ChangeRequest c, Map<String, Object> body) {
        ChangeSoD.Actor actor = ChangeSoD.actor();
        if (actor.id() != null || (actor.name() != null && !actor.name().isBlank())) {
            c.setPirReviewedById(actor.id());
            c.setPirReviewedBy(actor.name());
            return;
        }
        String claimed = strFromBody(body, "reviewer");
        if (claimed == null) claimed = strFromBody(body, "reviewedBy");
        if (claimed != null && !claimed.isBlank()) c.setPirReviewedBy(claimed);
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
            throw new DomainAccessDeniedException(
                "A CAB decision must be recorded by an identified user.");
        boolean authorised = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> "CHANGE_APPROVE".equals(a) || "ROLE_PLATFORM_ADMIN".equals(a));
        if (!authorised)
            throw new DomainAccessDeniedException(
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
        String why = switch (fault) {
            case ChangeAuthorityNotConfiguredException.COMMITTEE_UNAVAILABLE ->
                "the committee mapped to the " + gate + " gate is dissolved or no longer exists";
            // F2-96: the mapping is correct and the body is empty. Saying "no committee is mapped"
            // here would send the administrator to re-map a gate that is already right.
            case ChangeAuthorityNotConfiguredException.ROSTER_EMPTY ->
                "the committee mapped to the " + gate + " gate has no ACTIVE voting member, so it "
                + "can never be quorate and any decision recorded in its name would be invalid";
            default ->
                "no committee is mapped to the " + gate + " gate for this organisation";
        };
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
    /**
     * One required role from the matrix, and whether the recorded approvals meet it.
     *
     * @param role       the name as configured on the matrix row
     * @param status     {@link #ROLE_SATISFIED} · {@link #ROLE_MISSING} ·
     *                   {@link RoleDirectoryService#NO_ACTIVE_HOLDER} ·
     *                   {@link RoleDirectoryService#NOT_A_ROLE}
     * @param satisfiedBy the approver who supplied it, when satisfied
     */
    public record RequiredRoleState(String role, String status, String satisfiedBy) {}

    /** A required role that some approver holds. */
    public static final String ROLE_SATISFIED = "SATISFIED";
    /** Checkable here — somebody holds it — and no approver did. A real authorisation gap. */
    public static final String ROLE_MISSING = "MISSING";

    /**
     * F2-95 — resolve {@code required_roles} against the approvals actually recorded.
     *
     * <p><b>The defect.</b> `required_roles` is configured on <b>33 of 36 matrix rows in dev and
     * production alike</b> ({@code CHANGE_MANAGER}, {@code CISO}, {@code CTO}) and was read in
     * exactly one place: to echo the CSV back beside a {@code satisfied} flag computed purely
     * from vote <em>counts</em>. So the API asserted "a CRITICAL change requires the CISO" and
     * reported it satisfied with no CISO anywhere near it — worse than not having the field,
     * because a reviewer reads the two together.
     *
     * <p><b>Why through {@code approver_id}.</b> Three vocabularies name roles on this one
     * decision path: the matrix uses <em>platform role names</em>; committee rosters use
     * governance <em>seat titles</em> ({@code Chair}, {@code Member}); and
     * {@code change_approvals.approver_role} is <em>free text the voter types</em> — measured,
     * 7 distinct values in both databases and <b>not one</b> of them a platform role name. So
     * the requirement is resolved through {@code approver_id → iam.user_roles}, the platform's
     * own authorisation vocabulary, and no fourth vocabulary is invented.
     *
     * <p><b>Why unmet is not one status but three.</b> Measured 2026-08-03, identically in dev
     * and prod: {@code CISO} has one active holder and is checkable; {@code CHANGE_MANAGER} — on
     * all 33 rows — is a real role <b>nobody holds</b>; {@code CTO} is not a role at all
     * ([[F2-52]]'s ghost shape). Collapsing those into "unsatisfied" would tell an administrator
     * to go and find a CHANGE_MANAGER who cannot exist, and the same collapse is what made a
     * literal enforcement gate refuse <b>9 of 11</b> already-decided changes in dev and 8 of 10
     * in prod. Only {@link #ROLE_MISSING} is an authorisation gap; the other two are
     * configuration faults, and the remedies are different.
     */
    @Transactional(readOnly = true)
    public List<RequiredRoleState> requiredRoleStates(Long id) {
        ChangeRequest c = require(id);
        ChangeAuthorisationMatrix m = resolveMatrix(c);
        String csv = m != null ? m.getRequiredRoles() : null;
        if (csv == null || csv.isBlank()) return List.of();

        Map<String, String> enforceability = roleDirectory == null
            ? Map.of()
            : roleDirectory.enforceabilityByRole(c.getOrganisationId());

        // Who approved, and what platform roles do they actually hold. Resolved once per
        // approver, not once per (approver x required role).
        Map<Long, String> approverNames = new LinkedHashMap<>();
        Set<String> rolesHeldByApprovers = new LinkedHashSet<>();
        Map<String, String> firstHolderOfRole = new LinkedHashMap<>();
        for (ChangeApproval v : approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(id)) {
            if (!"APPROVE".equalsIgnoreCase(v.getDecision()) || v.getApproverId() == null) continue;
            approverNames.putIfAbsent(v.getApproverId(), v.getApproverName());
            if (roleDirectory == null) continue;
            for (String held : roleDirectory.rolesOf(v.getApproverId())) {
                rolesHeldByApprovers.add(held);
                firstHolderOfRole.putIfAbsent(held, v.getApproverName());
            }
        }

        List<RequiredRoleState> out = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String role = raw.trim();
            if (role.isEmpty()) continue;
            String key = role.toUpperCase(Locale.ROOT);
            if (rolesHeldByApprovers.contains(key)) {
                out.add(new RequiredRoleState(role, ROLE_SATISFIED, firstHolderOfRole.get(key)));
                continue;
            }
            String checkable = roleDirectory == null
                ? RoleDirectoryService.NOT_A_ROLE
                : roleDirectory.enforceability(enforceability, role);
            out.add(new RequiredRoleState(role,
                RoleDirectoryService.ENFORCEABLE.equals(checkable) ? ROLE_MISSING : checkable,
                null));
        }
        return out;
    }

    /**
     * S6b-ii — refuse a CAB approval when a role the matrix requires is <b>checkable here and was
     * not held by any approver</b>. The other half of F2-95: S6b-i made the payload honest, this
     * makes the requirement bite.
     *
     * <p><b>Only {@link #ROLE_MISSING} blocks, and that is the whole design.</b> Measured 2026-08-03
     * before deciding: a <em>literal</em> gate — one that refused whenever any required role was
     * unsatisfied — would have refused <b>9 of 11</b> already-decided changes in dev and <b>8 of
     * 10</b> in production. The reason is not the vocabulary mismatch the finding first blamed:
     * {@code CHANGE_MANAGER} is required on all 33 configured rows and <b>no active user in either
     * database holds it</b>. Refusing on that leaves the approver no action that could ever succeed
     * — F2-92's shape, a control whose first act is to refuse everything — so
     * {@code NO_ACTIVE_HOLDER} and {@code NOT_A_ROLE} are reported as configuration faults and
     * never block. V347 removed the one {@code NOT_A_ROLE} in seeded config ({@code CTO}, a ghost),
     * so the two halves no longer overlap.
     *
     * <p><b>Not enforced at {@code EMERGENCY_APPROVED}, deliberately.</b> An emergency change is
     * authorised fast and <em>ratified</em> afterwards — that is what A17-D3's ratification SLA
     * exists for. Putting a role gate in front of emergency authorisation would add friction at the
     * exact moment the platform is meant to get out of the way, and the control is not lost: the
     * ratification path is where the after-the-fact check belongs. Filed as the follow-up rather
     * than smuggled in here.
     *
     * <p>Throws {@link IllegalStateException}, which maps to <b>409</b> — the same status and shape
     * as the vote-count and REJECT-on-record gates immediately above it, so the caller sees one
     * consistent "this decision cannot be recorded yet" contract.
     */
    void requireRequiredRolesPresent(Long id) {   // package-private: asserted directly, as
                                                  // TenantWriteGuardInterceptor.decide is
        List<String> gaps = requiredRoleStates(id).stream()
            .filter(r -> ROLE_MISSING.equals(r.status()))
            .map(RequiredRoleState::role)
            .toList();
        if (gaps.isEmpty()) return;
        throw new IllegalStateException(
            "Cannot CAB-approve: this change's authorisation matrix requires an APPROVE from a "
          + "holder of " + String.join(", ", gaps) + ", and none of the recorded approvers holds "
          + "that role. Either have a holder approve, or amend the authorisation matrix for this "
          + "change type and risk level.");
    }

    /**
     * S8e / CE-2 — refuse {@code → CAB_APPROVED} when this cell requires a pre-approved model and
     * the change does not have a live one.
     *
     * <p>Measured safe on arrival: V354 asserts {@code requires_model = 0} on all 36 matrix rows in
     * each database, so this refuses nothing until a tenant switches a cell on — deliberately,
     * because 5 STANDARD changes exist and 3 are in flight.
     *
     * <p>The message names the reason from {@code backingFor}, not a bare "no model": an
     * administrator told only that something is missing cannot tell an expired pre-approval from an
     * absent one, and those have different remedies.
     */
    private void requireModelIfCellDemandsIt(ChangeRequest c) {
        if (modelService == null) return;                 // see the field javadoc on polarity
        ChangeAuthorisationMatrix cell = resolveMatrix(c);
        if (cell == null || !Boolean.TRUE.equals(cell.getRequiresModel())) return;
        if (modelService.liveModelFor(c.getId()).isPresent()) return;

        Object reason = modelService.backingFor(c.getId()).get("reason");
        throw new IllegalStateException(
            "Cannot CAB-approve: this tenant requires a pre-approved change model for "
            + nullSafeStr(c.getChangeType()) + " / " + nullSafeStr(c.getRiskLevel())
            + " changes, and this one has no live pre-approval (" + reason + "). Apply an APPROVED, "
            + "unexpired model whose procedure the change actually followed, or amend the "
            + "authorisation matrix cell to route this change to the CAB instead.");
    }

    public Map<String, Object> resolveAuthorisation(Long id) {
        ChangeRequest c = require(id);
        ChangeAuthorisationMatrix m = resolveMatrix(c);
        long approves = approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(id).stream()
            .filter(v -> "APPROVE".equalsIgnoreCase(v.getDecision())).count();
        int required = requiredApprovals(c);
        List<RequiredRoleState> roleStates = requiredRoleStates(id);

        boolean approvalsSatisfied = approves >= required;
        List<String> roleGaps = roleStates.stream()
            .filter(r -> ROLE_MISSING.equals(r.status())).map(RequiredRoleState::role).toList();
        List<String> unverifiableRoles = roleStates.stream()
            .filter(r -> RoleDirectoryService.NOT_A_ROLE.equals(r.status())
                      || RoleDirectoryService.NO_ACTIVE_HOLDER.equals(r.status()))
            .map(RequiredRoleState::role).toList();
        boolean rolesSatisfied = roleGaps.isEmpty() && unverifiableRoles.isEmpty();

        Map<String, Object> out = new HashMap<>();
        out.put("changeType",      c.getChangeType());
        out.put("riskLevel",       c.getRiskLevel());
        out.put("gate",            m != null ? m.getGate() : "CAB");
        out.put("minApprovals",    required);
        out.put("requiredRoles",   m != null ? m.getRequiredRoles() : null);
        // S8e/CE-2 — what backs the pre-approval, and when it is not backed, why not. Reported
        // whether or not the cell demands it, because "this AUTO-gated change rests on nothing" is
        // the fact G-2 is about and it is true regardless of the flag.
        out.put("requiresModel",   m != null && Boolean.TRUE.equals(m.getRequiresModel()));
        if (modelService != null) out.put("modelBacking", modelService.backingFor(id));
        out.put("approvesRecorded", approves);
        // Split, because one flag could not carry both facts and was silently carrying only
        // the cheaper one. `satisfied` keeps its key for existing callers but is now the
        // conjunction — it must never read true while a configured role requirement is
        // unmet or unverifiable, which was the whole of F2-95.
        out.put("approvalsSatisfied", approvalsSatisfied);
        out.put("rolesSatisfied",     rolesSatisfied);
        out.put("requiredRoleStates", roleStates);
        out.put("roleGaps",           roleGaps);
        out.put("unverifiableRoles",  unverifiableRoles);
        out.put("satisfied",          approvalsSatisfied && rolesSatisfied);
        out.put("roleRemedy",         roleRemedy(roleGaps, roleStates));
        return out;
    }

    /**
     * Name the action, and name which action is <em>not</em> it — F2-96's rule. Telling an
     * administrator to "assign the CHANGE_MANAGER role" is useless when the role has no holder
     * anywhere in the tenant, and actively wrong for {@code CTO}, which is not a role.
     */
    private static String roleRemedy(List<String> gaps, List<RequiredRoleState> states) {
        if (states.isEmpty()) return null;
        List<String> unheld = states.stream()
            .filter(r -> RoleDirectoryService.NO_ACTIVE_HOLDER.equals(r.status()))
            .map(RequiredRoleState::role).toList();
        List<String> ghosts = states.stream()
            .filter(r -> RoleDirectoryService.NOT_A_ROLE.equals(r.status()))
            .map(RequiredRoleState::role).toList();
        if (gaps.isEmpty() && unheld.isEmpty() && ghosts.isEmpty())
            return "Every role this change's authorisation matrix requires was held by an approver.";
        StringBuilder b = new StringBuilder();
        if (!gaps.isEmpty())
            b.append("Needs an APPROVE from a holder of ").append(String.join(", ", gaps))
             .append(". ");
        if (!unheld.isEmpty())
            b.append(String.join(", ", unheld)).append(" is required but no active user in this "
              + "tenant holds it, so no approval can satisfy it — grant the role, or amend the "
              + "authorisation matrix. ");
        if (!ghosts.isEmpty())
            b.append(String.join(", ", ghosts)).append(" is not a role in this tenant at all; "
              + "granting is impossible — create the role or remove it from the matrix row. ");
        return b.toString().trim();
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
        // ── SoD: the requester/owner is force-RECUSED from the committee vote ────────────────────
        // ⚠ F2-113 — this list used to carry names ONLY, and the gate on the far side compared them
        // to committee_member_terms.member_name. A change's people are team labels ('CloudOps',
        // 'IAM Team', 'AppSec'); a committee's members are people ('Khalid Al-Rashidi'). Measured
        // 2026-08-05: 15 of 15 owner labels and 13 of 13 requester labels resolved to no user, and
        // 0 of 4 live referrals carried a name at all — so the bar had never engaged for any change
        // in either database. The third instance of F2-112's shape: a correct rule over data that
        // cannot satisfy it.
        //
        // The ids are what can match. CE-15 made `create` capture requested_by_id and b901faec
        // seated the CAB/ECAB with member_user_id, so both sides of the comparison exist for the
        // first time — which is why this was unfixable yesterday and is fixable now.
        //
        // ⚠ The names still travel, and must. 29 of dev's 42 active seats carry no member_user_id
        // (committee_member_terms has a `no_platform_user` column because an external director
        // legitimately never gets one), and for those the label is the only comparable thing there
        // is. Adding the ids must not retire the comparison the unlinked seats depend on.
        CommitteePort.Recusal recusal = new CommitteePort.Recusal(
            java.util.stream.Stream.of(c.getRequestedById(), c.getChangeOwnerId())
                .filter(java.util.Objects::nonNull).distinct().toList(),
            java.util.stream.Stream.of(c.getRequestedByName(), c.getChangeOwnerName())
                .filter(x -> x != null && !x.isBlank()).map(String::trim).distinct().toList());
        // S7b / CE-4 — state what the committee is being asked to decide. Without this the
        // referral routes correctly and never appears on the committee's agenda, which is how a
        // change could be "before the CAB" and invisible to it.
        CommitteePort.SubjectRef subject = new CommitteePort.SubjectRef(
            c.getChangeCode(), c.getTitleEn(), c.getTitleAr());
        CommitteePort.ReferralView r = committeePort.refer("CHANGE", id, gate, mode, committeeId, referredBy,
            recusal, subject);
        // The audit line says what the referral can actually enforce, not merely that it happened.
        // "Referred to committee 13" over an empty recusal list is the sentence that let F2-113 sit
        // undetected: it reads identically whether the SoD bar is armed or unarmed.
        audit.log("REFER_TO_COMMITTEE", "ChangeRequest", id,
            "Referred to committee " + r.committeeId() + " (gate " + gate + ", mode " + mode + ")"
                + (recusal.isPresent()
                    ? " — recusing " + recusal.userIds().size() + " user id(s) and "
                      + recusal.names().size() + " label(s) from the vote"
                    : " — NO party recused: this change records neither a requester nor an owner, "
                      + "so nobody can be barred from casting its deciding vote"));
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

    /**
     * S8g / CE-8 — declare, amend or withdraw a change's projected service outage.
     *
     * <p>Validated here as well as by {@code ck_change_pso} and the DTO's {@code @Min}, because this
     * method is reachable from server-side callers that never see a DTO — the three-layer rule V345's
     * SLA setter records.
     *
     * <p><b>Withdrawing clears the detail.</b> Leaving {@code outage_minutes} and
     * {@code services_affected} behind after {@code outageExpected = false} would let the FSC show a
     * duration for a change that no longer claims an outage, and the next reader could not tell whether
     * the declaration was withdrawn or the flag was lost.
     */
    @Transactional
    public ChangeRequest declareOutage(Long id, Boolean expected, Integer minutes,
                                       String services, Long actorUserId) {
        ChangeRequest c = require(id);
        boolean on = Boolean.TRUE.equals(expected);
        if (on) {
            if (minutes == null || minutes <= 0)
                throw new IllegalArgumentException(
                    "A declared outage needs an expected duration in minutes. \"There will be an outage\" "
                  + "with no length is not something a service owner can plan around — and "
                  + "ck_change_pso refuses the row.");
            if (services == null || services.isBlank())
                throw new IllegalArgumentException(
                    "A declared outage must name the services that lose availability. An unnamed outage "
                  + "tells every service owner to assume it is theirs.");
            c.setOutageExpected(true);
            c.setOutageMinutes(minutes);
            c.setServicesAffected(services.trim());
        } else {
            c.setOutageExpected(false);
            c.setOutageMinutes(null);
            c.setServicesAffected(null);
        }
        c.setUpdatedAt(LocalDateTime.now());
        c.setUpdatedBy(actorUserId);
        ChangeRequest saved = changeRepo.save(c);
        audit.log(on ? "OUTAGE_DECLARED" : "OUTAGE_WITHDRAWN", "ChangeRequest", saved.getId(),
                  on ? (minutes + " min — " + services.trim()) : "outage declaration withdrawn");
        return saved;
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

    // ── S7d / CE-7 — the PIR follow-up becomes tracked work ─────────────────────────────────────

    /**
     * How long a PIR follow-up gets before it counts as overdue.
     *
     * <p>A stated constant rather than a derivation or a setting. The change module has no CAPA SLA
     * config surface, and inventing a per-risk ladder here would be a governance judgement with no
     * owner and no UI — while the CAPA's own screen lets the owner move the date the moment it has
     * one. Thirty days says "this is improvement work with a deadline", which is the honest
     * description of a retrospective action.
     */
    static final int PIR_FOLLOW_UP_TARGET_DAYS = 30;

    /**
     * Classify a PIR follow-up from what the reviewer actually recorded.
     *
     * <p>Objectives met → the change worked and the follow-up is improvement, so {@code PREVENTIVE}.
     * Anything else → {@code CORRECTIVE}. Note the polarity on <b>unset</b>: {@code null} means the
     * reviewer did not say, and "we do not know whether it worked" is not evidence that it did, so an
     * unset objective classifies corrective. That also matches the sustainability tracker's
     * convention that an auto-raised action is corrective unless something says otherwise.
     */
    static String pirCapaType(Boolean objectivesMet) {
        return Boolean.TRUE.equals(objectivesMet) ? "PREVENTIVE" : "CORRECTIVE";
    }

    /**
     * CE-7: raise a CAPA carrying the change's PIR follow-up, and back-link it
     * ({@code CAPA} / {@code FOLLOW_UP}) so the change record shows its own remediation.
     *
     * <p>CH-11 shipped {@code pir_follow_up} as free text and nothing consumed it: measured across
     * dev and prod, <b>no CAPA has ever been raised from a PIR</b> — and with zero rows carrying any
     * PIR field, the field had never been used at all. A reviewer's stated commitment lived in a
     * text column that no rollup, no overdue sweep and no committee pack ever read.
     *
     * <p>Called from two places, because the PIR can be written in two: the {@code POST_REVIEW}
     * transition (where {@code capturePir} first records it) and {@code update()} (CH-11 lets a
     * reviewer refine the retrospective afterwards). Hooking only the transition would have meant a
     * follow-up added on the second pass — the likely pass, since the first is often written in
     * haste — silently raised nothing.
     *
     * <p>Idempotent by the same marker CH-13 uses: a change carrying a {@code CAPA} /
     * {@code FOLLOW_UP} link already has its action, so repeated saves and a re-entered
     * {@code POST_REVIEW} do not multiply CAPAs. The consequence is deliberate and worth stating: the
     * hook opens <b>one</b> CAPA per change, not one per edit. Later follow-up text refines the
     * change record; it does not fork new tracked work, because a reviewer rewording a sentence is
     * not a reviewer committing to a second action. Raising more is a manual act with a named author.
     *
     * <p>No-op when the port is absent, and it never throws: the review must be recordable even if
     * the CAPA tracker is not.
     *
     * <p><b>Deliberately does not demand the actor's {@code CAPA:WRITE}.</b> A derived record raised
     * as a consequence of an authorised act is not the actor exercising a second privilege — the same
     * reason CH-13's rollback hook opens an incident without {@code INCIDENT:WRITE} and every
     * sustainability auto-CAPA opens one without asking. Requiring it would mean a reviewer with
     * {@code CHANGE:WRITE} alone could record "we will fix the runbook" and have it silently dropped,
     * which is the gap this method exists to close. (Not demonstrable against dev either way: of the
     * seven seeded users only {@code admin} lacks {@code CAPA:WRITE}, and PLATFORM_ADMIN bypasses.)
     */
    private void syncPirFollowUpCapa(ChangeRequest c) {
        if (capaPort == null) return;
        String followUp = c.getPirFollowUp();
        if (followUp == null || followUp.isBlank()) return;
        boolean already = linkRepo.findByChangeRequestId(c.getId()).stream()
            .anyMatch(l -> "CAPA".equals(l.getEntityType()) && "FOLLOW_UP".equals(l.getLinkType()));
        if (already) return;

        String code = nullSafeStr(c.getChangeCode());
        // owner NOT NULL on capa.tasks — prefer the person who signed the review off, then the
        // change owner, then the requester. The final fallback is a label, never a blank: an
        // unowned CAPA is a CAPA nobody chases, so it must at least say it is unassigned.
        String owner = firstNonBlank(c.getPirReviewedBy(), c.getChangeOwnerName(),
                                     c.getRequestedByName(), "Unassigned (change PIR)");
        var raised = capaPort.raiseFromChangePir(
            code,
            "PIR follow-up: " + code,
            "متابعة مراجعة ما بعد التنفيذ: " + code,
            "Raised from the post-implementation review of change " + code
                + " (" + nullSafeStr(c.getTitleEn()) + "). Reviewer's follow-up: " + followUp,
            "أُنشئ من مراجعة ما بعد التنفيذ للتغيير " + code + ". متابعة المراجع: " + followUp,
            pirCapaType(c.getPirObjectivesMet()),
            mapRiskToSeverity(c.getRiskLevel()),
            owner,
            LocalDate.now().plusDays(PIR_FOLLOW_UP_TARGET_DAYS));
        if (raised.isEmpty()) return;

        ChangeLink link = new ChangeLink();
        link.setChangeRequestId(c.getId());
        link.setEntityType("CAPA");
        link.setEntityId(raised.get().id());
        link.setLinkType("FOLLOW_UP");
        link.setNotes("Auto-linked: CAPA " + nullSafeStr(raised.get().capaCode())
            + " raised from the PIR follow-up of " + code);
        linkRepo.save(link);

        audit.log("PIR_FOLLOW_UP_CAPA", "ChangeRequest", c.getId(),
            "PIR follow-up on change " + nullSafeMsg(code) + " raised CAPA "
                + nullSafeStr(raised.get().capaCode()) + " (#" + raised.get().id() + ", "
                + pirCapaType(c.getPirObjectivesMet()) + ") and linked it (CAPA / FOLLOW_UP)");
    }

    private static String firstNonBlank(String... candidates) {
        for (String s : candidates) if (s != null && !s.isBlank()) return s;
        return null;   // unreachable: the last candidate is always a literal
    }
}
