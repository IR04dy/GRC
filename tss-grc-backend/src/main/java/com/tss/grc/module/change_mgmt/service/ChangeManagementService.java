package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.*;
import com.tss.grc.module.change_mgmt.repository.*;
import com.tss.grc.module.incident.dto.IncidentRequest;
import com.tss.grc.module.incident.dto.IncidentResponse;
import com.tss.grc.module.incident.service.IncidentService;
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
     *  Optional (field-injected) so unit tests that construct the service
     *  directly need not supply it — the rollback hook then no-ops. */
    @Autowired(required = false)
    private IncidentService incidentService;

    /** CH-17: per-tenant (type x risk) authorisation matrix driving the CAB
     *  quorum. Optional — when absent the flat cab-min-approvals default applies
     *  (keeps unit tests that construct the service directly working). */
    @Autowired(required = false)
    private ChangeAuthorisationMatrixRepository authMatrixRepo;

    /** CH-17: freeze/blackout windows. Optional — when absent, no freeze gate
     *  is enforced (keeps directly-constructed unit tests working). */
    @Autowired(required = false)
    private ChangeFreezeWindowRepository freezeRepo;

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
        guard(c, target);
        switch (target) {
            case "EMERGENCY_APPROVED" -> {
                if (!"EMERGENCY".equals(c.getChangeType()))
                    throw new IllegalArgumentException("Emergency-approval path is only valid for EMERGENCY changes");
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
            }
            case "CAB_APPROVED" -> {
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
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
            }
            case "CAB_REJECTED" -> {
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
            }
            case "SCHEDULED" ->
                // CH-17: block scheduling into an active freeze/blackout window
                // (emergencies exempt where the window permits).
                enforceFreeze(c);
            case "IMPLEMENTING" -> {
                if (c.getImplementationStarted() == null) c.setImplementationStarted(LocalDateTime.now());
            }
            case "IMPLEMENTED" -> {
                if (c.getImplementationEnded() == null) c.setImplementationEnded(LocalDateTime.now());
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
    public ChangeApproval recordApproval(Long changeId, ChangeApproval body) {
        ChangeRequest c = require(changeId);
        if (!"CAB_PENDING".equals(c.getStatus()) && !"IMPACT_ASSESSED".equals(c.getStatus()))
            throw new IllegalArgumentException("CAB votes can only be recorded while the change is in IMPACT_ASSESSED or CAB_PENDING");
        body.setChangeRequestId(changeId);
        if (body.getDecision() == null) throw new IllegalArgumentException("decision is required");
        if (body.getApproverName() == null || body.getApproverName().isBlank())
            throw new IllegalArgumentException("approverName is required");
        // CM-C2 (SoD): the change requester / owner cannot cast a CAB vote on their
        // own change. (Name-based check; principal-id capture is a follow-up.)
        String approver = body.getApproverName().trim();
        if (approver.equalsIgnoreCase(nullSafeStr(c.getRequestedByName()).trim())
                || approver.equalsIgnoreCase(nullSafeStr(c.getChangeOwnerName()).trim()))
            throw new IllegalStateException(
                "Segregation of duty: the change requester/owner cannot cast a CAB vote on their own change");
        ChangeApproval saved = approvalRepo.save(body);
        audit.log("APPROVAL", "ChangeRequest", changeId,
            "CAB vote on change " + nullSafeMsg(c.getChangeCode()) + ": "
                + nullSafeMsg(saved.getDecision()) + " by " + nullSafeMsg(saved.getApproverName())
                + (saved.getApproverRole() != null ? " (" + saved.getApproverRole() + ")" : ""));
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
        return linkRepo.findByEntityTypeAndEntityId(entityType, entityId).stream()
            .map(l -> changeRepo.findById(l.getChangeRequestId())
                .filter(c -> c.getOrganisationId().equals(org) && !Boolean.TRUE.equals(c.getIsDeleted()))
                .map(c -> {
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
                .orElse(null))
            .filter(java.util.Objects::nonNull)
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
        return out;
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
     * INCIDENT TRIGGERED_BY link. No-op when the incident module is not wired
     * (unit tests that construct the service directly leave incidentService null).
     */
    private void autoRaiseRollbackIncident(ChangeRequest c) {
        if (incidentService == null) return;
        boolean already = linkRepo.findByChangeRequestId(c.getId()).stream()
            .anyMatch(l -> "INCIDENT".equals(l.getEntityType()) && "TRIGGERED_BY".equals(l.getLinkType()));
        if (already) return;

        String code = nullSafeStr(c.getChangeCode());
        IncidentResponse inc = incidentService.create(new IncidentRequest(
            "Failed change " + code + " — rolled back",
            "تغيير فاشل " + code + " — تم التراجع",
            mapRiskToSeverity(c.getRiskLevel()), "INFRASTRUCTURE", "OPEN",
            blank(c.getChangeOwnerName()),
            LocalDate.now(), null,
            "Auto-raised from rolled-back change " + code
                + " (" + nullSafeStr(c.getTitleEn()) + "). Investigate root cause "
                + "(test-plan gap, missing rollback step) and confirm service restoration.",
            null, null, null));

        ChangeLink link = new ChangeLink();
        link.setChangeRequestId(c.getId());
        link.setEntityType("INCIDENT");
        link.setEntityId(inc.id());
        link.setLinkType("TRIGGERED_BY");
        link.setNotes("Auto-linked: incident raised on rollback of " + code);
        linkRepo.save(link);

        audit.log("ROLLBACK_INCIDENT", "ChangeRequest", c.getId(),
            "Rolled-back change " + nullSafeMsg(code) + " auto-raised incident #" + inc.id()
                + " and linked it (INCIDENT / TRIGGERED_BY)");
    }
}
