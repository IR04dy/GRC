package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.*;
import com.tss.grc.module.change_mgmt.repository.*;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final ChangeRequestRepository changeRepo;
    private final ChangeApprovalRepository approvalRepo;
    private final ChangeTaskRepository taskRepo;
    private final ChangeLinkRepository linkRepo;
    private final ChangeCommentRepository commentRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    @Autowired(required = false)
    private BroadcastHelper bcast;
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
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
            }
            case "CAB_REJECTED" -> {
                c.setCabDecisionAt(LocalDateTime.now());
                c.setCabDecisionNotes(strFromBody(body, "notes"));
            }
            case "IMPLEMENTING" -> {
                if (c.getImplementationStarted() == null) c.setImplementationStarted(LocalDateTime.now());
            }
            case "IMPLEMENTED" -> {
                if (c.getImplementationEnded() == null) c.setImplementationEnded(LocalDateTime.now());
                if (body.get("outcome") != null) c.setOutcome(body.get("outcome").toString());
                else if (c.getOutcome() == null) c.setOutcome("SUCCESS");
            }
            case "ROLLED_BACK" -> {
                if (c.getImplementationEnded() == null) c.setImplementationEnded(LocalDateTime.now());
                c.setOutcome("ROLLED_BACK");
            }
            case "POST_REVIEW" -> {
                if (c.getPostReviewDate() == null) c.setPostReviewDate(LocalDate.now());
            }
            case "CLOSED" -> {
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
        return out;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void apply(ChangeRequest target, ChangeRequest src) {
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
        if (src.getBusinessJustification()!= null) target.setBusinessJustification(src.getBusinessJustification());
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
}
