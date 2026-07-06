package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.evidence.dto.EvidenceRequestCreateDto;
import com.tss.grc.module.evidence.dto.EvidenceRequestResponse;
import com.tss.grc.module.evidence.service.EvidenceRequestService;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.repository.DepartmentRepository;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.CreateRequest;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.EventResponse;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientDto;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientResponse;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RequestResponse;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequest;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequestEvent;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequestRecipient;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestEventRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRecipientRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * WF-1 — the platform workflow REQUEST engine (§5 actor selection + §6 request lifecycle of the
 * workflow-logic remediation spec).
 *
 * <p>Every Send/Request/Assign across the platform can route through here instead of being a
 * dead button: a request knows its recipients (by user/role/department/committee/external, each
 * with an actor role and a sequential step), its routing mode (SEQUENTIAL / PARALLEL ALL|ANY /
 * BROADCAST_ACK), its clock (due date, SLA, reminder + escalation path — swept by WF-2), and its
 * lifecycle (16 states, transitions validated — illegal moves are 409, malformed input 400).
 * Every mutation appends a {@link WorkflowRequestEvent} timeline row AND a platform audit-log row.
 */
@Service
@RequiredArgsConstructor
public class WorkflowRequestService {

    static final Set<String> ROUTING_MODES   = Set.of("SEQUENTIAL", "PARALLEL", "BROADCAST_ACK");
    static final Set<String> COMPLETION_RULES = Set.of("ALL", "ANY");
    static final Set<String> PRIORITIES      = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    static final Set<String> RECIPIENT_TYPES = Set.of("USER", "ROLE", "DEPARTMENT", "COMMITTEE", "EXTERNAL");
    static final Set<String> ACTOR_ROLES     = Set.of("ASSIGNEE", "REVIEWER", "APPROVER", "OBSERVER", "ESCALATION");

    /** §6 lifecycle — allowed transitions. Unknown target → 400; known-but-illegal → 409. */
    static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
        Map.entry("DRAFT",        Set.of("SENT", "CLOSED")),
        Map.entry("SENT",         Set.of("IN_PROGRESS", "SUBMITTED", "ESCALATED", "OVERDUE", "CLOSED")),
        Map.entry("ASSIGNED",     Set.of("IN_PROGRESS", "SUBMITTED", "ESCALATED", "OVERDUE", "CLOSED")),
        Map.entry("IN_PROGRESS",  Set.of("SUBMITTED", "ESCALATED", "OVERDUE", "CLOSED")),
        Map.entry("WAITING_RESPONSE", Set.of("IN_PROGRESS", "SUBMITTED", "ESCALATED", "OVERDUE", "CLOSED")),
        Map.entry("SUBMITTED",    Set.of("UNDER_REVIEW", "APPROVED", "REJECTED", "RETURNED", "CLOSED")),
        Map.entry("UNDER_REVIEW", Set.of("APPROVED", "REJECTED", "RETURNED", "CLOSED")),
        Map.entry("RETURNED",     Set.of("RESUBMITTED", "CLOSED")),
        Map.entry("RESUBMITTED",  Set.of("UNDER_REVIEW", "APPROVED", "REJECTED", "RETURNED", "CLOSED")),
        Map.entry("APPROVED",     Set.of("CLOSED", "REOPENED")),
        Map.entry("REJECTED",     Set.of("RESUBMITTED", "REOPENED", "CLOSED")),
        Map.entry("ESCALATED",    Set.of("IN_PROGRESS", "SUBMITTED", "CLOSED")),
        Map.entry("OVERDUE",      Set.of("IN_PROGRESS", "SUBMITTED", "ESCALATED", "CLOSED")),
        Map.entry("CLOSED",       Set.of("REOPENED", "ARCHIVED")),
        Map.entry("REOPENED",     Set.of("IN_PROGRESS", "SUBMITTED", "ESCALATED", "CLOSED")),
        Map.entry("ARCHIVED",     Set.of())
    );

    private final WorkflowRequestRepository          requestRepo;
    private final WorkflowRequestRecipientRepository recipientRepo;
    private final WorkflowRequestEventRepository     eventRepo;
    private final UserRepository                     userRepo;
    private final DepartmentRepository               departmentRepo;
    /** WF-5 — COMMITTEE recipients resolve + fan out through the committees module. */
    private final CommitteeRepository                committeeRepo;
    private final CommitteeMemberTermRepository      committeeMembers;
    private final NotificationService                notifications;
    private final AuditLogService                    audit;
    private final ApplicationEventPublisher          publisher;
    /** WF-4 — sending an EVIDENCE_REQUEST opens a real evidence.evidence_requests row. */
    private final EvidenceRequestService             evidenceRequests;

    // ── Create (§5 validation gates) ─────────────────────────────────────────

    @Transactional
    public RequestResponse create(CreateRequest dto) {
        Long org = TenantContext.getCurrentTenant();

        WorkflowRequest r = new WorkflowRequest();
        r.setOrganisationId(org);
        r.setSourceModule(dto.sourceModule().trim().toUpperCase());
        r.setSourceRecordType(dto.sourceRecordType());
        r.setSourceRecordId(dto.sourceRecordId());
        r.setRequestType(dto.requestType().trim().toUpperCase());
        r.setTitleEn(dto.titleEn());
        r.setTitleAr(dto.titleAr());
        r.setDescription(dto.description());
        r.setInitiatedBy(CurrentActor.userId());
        r.setInitiatedByName(CurrentActor.username());
        r.setRoutingMode(upperOr(dto.routingMode(), "PARALLEL"));
        r.setCompletionRule(upperOr(dto.completionRule(), "ALL"));
        r.setRequiredAction(dto.requiredAction());
        r.setDueDate(dto.dueDate());
        r.setSlaHours(dto.slaHours());
        r.setPriority(upperOr(dto.priority(), "MEDIUM"));
        r.setSeverity(dto.severity());
        if (dto.reminderDaysBefore() != null) r.setReminderDaysBefore(dto.reminderDaysBefore());
        r.setEscalationRole(dto.escalationRole());
        r.setEscalationUserId(dto.escalationUserId());
        if (dto.responseRequired() != null) r.setResponseRequired(dto.responseRequired());
        if (dto.evidenceRequired() != null) r.setEvidenceRequired(dto.evidenceRequired());
        r.setClosureCondition(dto.closureCondition());
        r.setReviewerId(dto.reviewerId());
        r.setApproverId(dto.approverId());
        r.setStatus("DRAFT");

        List<RecipientDto> recipients = dto.recipients() == null ? List.of() : dto.recipients();
        validate(r, recipients, org);
        r.setRecipientCategory(categoryOf(recipients));
        r = requestRepo.save(r);

        for (RecipientDto in : recipients) {
            WorkflowRequestRecipient rec = new WorkflowRequestRecipient();
            rec.setOrganisationId(org);
            rec.setRequestId(r.getId());
            rec.setRecipientType(in.recipientType().trim().toUpperCase());
            rec.setRecipientId(in.recipientId());
            rec.setRecipientKey(in.recipientKey());
            rec.setRecipientLabel(resolveLabel(org, in));
            rec.setActorRole(upperOr(in.actorRole(), "ASSIGNEE"));
            rec.setStepNo("SEQUENTIAL".equals(r.getRoutingMode()) && in.stepNo() != null ? in.stepNo() : 1);
            rec.setStatus("PENDING");
            recipientRepo.save(rec);
        }

        appendEvent(r, "CREATED", null, "DRAFT", "Request drafted (" + recipients.size() + " recipient(s), "
            + r.getRoutingMode() + (r.getRoutingMode().equals("PARALLEL") ? "/" + r.getCompletionRule() : "") + ")");
        audit.log("WORKFLOW_REQUEST_CREATED", "WorkflowRequest", r.getId(),
            code(r) + " · " + r.getTitleEn() + " · " + r.getRoutingMode());
        return toResponse(r);
    }

    /** §5 validation — every gate throws IllegalArgumentException → 400. */
    private void validate(WorkflowRequest r, List<RecipientDto> recipients, Long org) {
        if (!ROUTING_MODES.contains(r.getRoutingMode()))
            throw new IllegalArgumentException("routingMode must be one of " + ROUTING_MODES);
        if (!COMPLETION_RULES.contains(r.getCompletionRule()))
            throw new IllegalArgumentException("completionRule must be one of " + COMPLETION_RULES);
        if (!PRIORITIES.contains(r.getPriority()))
            throw new IllegalArgumentException("priority must be one of " + PRIORITIES);

        // At least one recipient is required — a request must never be sent "in the air".
        if (recipients.isEmpty())
            throw new IllegalArgumentException("At least one recipient is required");

        boolean hasEscalationRecipient = false;
        TreeSet<Integer> steps = new TreeSet<>();
        for (RecipientDto in : recipients) {
            String type = in.recipientType() == null ? "" : in.recipientType().trim().toUpperCase();
            if (!RECIPIENT_TYPES.contains(type))
                throw new IllegalArgumentException("recipientType must be one of " + RECIPIENT_TYPES);
            String role = upperOr(in.actorRole(), "ASSIGNEE");
            if (!ACTOR_ROLES.contains(role))
                throw new IllegalArgumentException("actorRole must be one of " + ACTOR_ROLES);
            if ("ESCALATION".equals(role)) hasEscalationRecipient = true;

            switch (type) {
                case "USER" -> {
                    if (in.recipientId() == null)
                        throw new IllegalArgumentException("USER recipient requires recipientId");
                    userRepo.findById(in.recipientId())
                        .filter(u -> org.equals(u.getOrganisationId()))
                        .orElseThrow(() -> new IllegalArgumentException(
                            "USER recipient " + in.recipientId() + " not found in this organisation"));
                }
                case "DEPARTMENT" -> {
                    if (in.recipientId() == null)
                        throw new IllegalArgumentException("DEPARTMENT recipient requires recipientId");
                    departmentRepo.findByIdAndOrganisationId(in.recipientId(), org)
                        .orElseThrow(() -> new IllegalArgumentException(
                            "DEPARTMENT recipient " + in.recipientId() + " not found"));
                }
                case "ROLE" -> {
                    if (in.recipientKey() == null || in.recipientKey().isBlank())
                        throw new IllegalArgumentException("ROLE recipient requires recipientKey (role name)");
                }
                case "COMMITTEE" -> {
                    if (in.recipientId() == null)
                        throw new IllegalArgumentException("COMMITTEE recipient requires recipientId");
                    committeeRepo.findByIdAndOrganisationId(in.recipientId(), org)
                        .orElseThrow(() -> new IllegalArgumentException(
                            "COMMITTEE recipient " + in.recipientId() + " not found in this organisation"));
                }
                case "EXTERNAL" -> {
                    // External recipient must have a valid contact method (§5).
                    if (in.recipientKey() == null || !in.recipientKey().contains("@"))
                        throw new IllegalArgumentException("EXTERNAL recipient requires a contact email in recipientKey");
                }
                default -> { }
            }
            steps.add("SEQUENTIAL".equals(r.getRoutingMode()) && in.stepNo() != null ? in.stepNo() : 1);
        }

        // Due date is required for a request that expects a response (§5).
        if (Boolean.TRUE.equals(r.getResponseRequired()) && r.getDueDate() == null)
            throw new IllegalArgumentException("dueDate is required when a response is required");

        // Escalation path is required for high-priority requests (§5).
        if (("HIGH".equals(r.getPriority()) || "CRITICAL".equals(r.getPriority()))
                && r.getEscalationRole() == null && r.getEscalationUserId() == null && !hasEscalationRecipient)
            throw new IllegalArgumentException(
                "HIGH/CRITICAL priority requires an escalation path (escalationRole, escalationUserId, or an ESCALATION recipient)");

        // Sequential workflow must have contiguous step order 1..N (§5 — also blocks circular routing).
        if ("SEQUENTIAL".equals(r.getRoutingMode())) {
            int expected = 1;
            for (Integer s : steps) {
                if (s == null || s != expected++)
                    throw new IllegalArgumentException(
                        "SEQUENTIAL routing requires contiguous step numbers starting at 1 (got " + steps + ")");
            }
        }

        if (r.getReviewerId() != null) requireOrgUser(org, r.getReviewerId(), "reviewerId");
        if (r.getApproverId() != null) requireOrgUser(org, r.getApproverId(), "approverId");
    }

    // ── Lifecycle transitions (§6 APIs) ──────────────────────────────────────

    /** DRAFT → SENT: snapshot labels, notify every step-1 (sequential) or all (parallel) recipients.
     *  An EVIDENCE_REQUEST with evidenceRequired also opens a REAL evidence-subsystem request
     *  (WF-4 bridge) so the evidence team works their normal queue. */
    @Transactional
    public RequestResponse send(Long id) {
        WorkflowRequest r = get(id);
        // WF-4 — open the bridged evidence request BEFORE mutating r (its toResponse queries
        // flush; the ROWVERSION rule allows exactly one UPDATE of r per transaction).
        Long bridgedEvidenceId = null;
        if ("DRAFT".equals(r.getStatus()) && "EVIDENCE_REQUEST".equals(r.getRequestType())
                && Boolean.TRUE.equals(r.getEvidenceRequired())) {
            bridgedEvidenceId = openBridgedEvidenceRequest(r);
        }
        transition(r, "SENT");
        if (bridgedEvidenceId != null) r.setEvidenceRequestId(bridgedEvidenceId);
        notifyStep(r, "SEQUENTIAL".equals(r.getRoutingMode()) ? r.getCurrentStep() : null);
        requestRepo.save(r);
        if (bridgedEvidenceId != null)
            appendEvent(r, "EVIDENCE_BRIDGED", "DRAFT", "SENT",
                "Opened evidence request REQ-" + String.format("%05d", bridgedEvidenceId)
                    + " in the evidence subsystem");
        appendEvent(r, "SENT", "DRAFT", "SENT", "Request sent to recipients");
        audit.log("WORKFLOW_REQUEST_SENT", "WorkflowRequest", r.getId(), code(r) + " · " + r.getTitleEn());
        publisher.publishEvent(DomainEvent.of(r.getOrganisationId(), "WORKFLOW_REQUEST_SENT",
            "WorkflowRequest", r.getId(),
            Map.of("requestType", r.getRequestType(), "priority", r.getPriority(),
                   "dueDate", String.valueOf(r.getDueDate()), "routingMode", r.getRoutingMode())));
        return toResponse(r);
    }

    /**
     * A recipient responds. The caller must match an actionable recipient row — USER row addressed
     * to them, a ROLE row whose role they hold, or their DEPARTMENT's row — in the CURRENT step
     * (sequential) or any step (parallel). Sequential order is enforced by construction: rows
     * outside the current step are not actionable. Completing the last gate flips the request to
     * SUBMITTED (and auto-closes when no reviewer/approver gate exists).
     */
    @Transactional
    public RequestResponse respond(Long id, String note) {
        WorkflowRequest r = get(id);
        if (!Set.of("SENT", "IN_PROGRESS", "REOPENED", "ESCALATED", "OVERDUE").contains(r.getStatus()))
            throw new IllegalTransitionException(r.getStatus(), "RESPONDED(recipient)", TRANSITIONS.get(r.getStatus()));

        List<WorkflowRequestRecipient> all =
            recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId());
        WorkflowRequestRecipient mine = actionableRowFor(r, all);

        String from = r.getStatus();
        mine.setStatus("RESPONDED");
        mine.setRespondedAt(LocalDateTime.now());
        mine.setRespondedBy(CurrentActor.userId());
        mine.setResponse(note);
        recipientRepo.save(mine);

        // Finalize EVERY mutation of the request BEFORE any flush-triggering query (the SQL Server
        // ROWVERSION @Version tolerates exactly one UPDATE per transaction — a second flush after an
        // intermediate auto-flush fails with a stale-object error). Notifications run last.
        RoutingAdvance adv = advanceRouting(r, all);
        if (adv.nextStep() != null) r.setCurrentStep(adv.nextStep());
        if (adv.complete()) {
            transition(r, "SUBMITTED");
            if (r.getReviewerId() == null && r.getApproverId() == null) {
                // No review/approval gate — completion closes the request.
                transition(r, "CLOSED");
                r.setClosedAt(LocalDateTime.now());
                if (r.getClosureCondition() == null) r.setClosureCondition("AUTO_ON_COMPLETION");
            }
        } else if ("SENT".equals(r.getStatus())) {
            transition(r, "IN_PROGRESS");
        }
        requestRepo.save(r);

        appendEvent(r, "RESPONDED", from, r.getStatus(),
            (mine.getRecipientLabel() == null ? "recipient" : mine.getRecipientLabel()) + " responded"
                + (note == null || note.isBlank() ? "" : ": " + note));
        if (adv.nextStep() != null)
            appendEvent(r, "STEP_ADVANCED", r.getStatus(), r.getStatus(), "Routing advanced to step " + adv.nextStep());
        audit.log("WORKFLOW_REQUEST_RESPONDED", "WorkflowRequest", r.getId(),
            code(r) + " · " + from + "→" + r.getStatus());
        if (adv.nextStep() != null) notifyStep(r, adv.nextStep());   // hand the baton to the next step
        if ("SUBMITTED".equals(r.getStatus()) && r.getReviewerId() != null)
            notifications.notifyUser(r.getOrganisationId(), r.getReviewerId(),
                "Workflow request ready for review", "طلب سير عمل جاهز للمراجعة",
                code(r) + " \"" + r.getTitleEn() + "\" is ready for your review.",
                "WORKFLOW_REQUEST_SUBMITTED", "WorkflowRequest", r.getId());
        return toResponse(r);
    }

    /** Initiator/admin force-submits (e.g. responses collected offline). */
    @Transactional
    public RequestResponse submit(Long id, String note) {
        WorkflowRequest r = get(id);
        requireInitiatorOrAdmin(r, "submit");
        String from = r.getStatus();
        transition(r, "SUBMITTED");
        requestRepo.save(r);
        appendEvent(r, "SUBMITTED", from, "SUBMITTED", note);
        audit.log("WORKFLOW_REQUEST_SUBMITTED", "WorkflowRequest", r.getId(), code(r));
        return toResponse(r);
    }

    /** Reviewer gate: outcome APPROVE (→ APPROVED, or UNDER_REVIEW awaiting approver) or RETURN. */
    @Transactional
    public RequestResponse review(Long id, String outcome, String note) {
        WorkflowRequest r = get(id);
        requireActor(r.getReviewerId(), r, "review");
        String from = r.getStatus();
        String verdict = upperOr(outcome, "");
        switch (verdict) {
            case "APPROVE" -> {
                if (r.getApproverId() != null) {
                    if (!"UNDER_REVIEW".equals(r.getStatus())) transition(r, "UNDER_REVIEW");
                    appendEvent(r, "REVIEWED", from, r.getStatus(),
                        "Review passed — awaiting approver" + noteSuffix(note));
                    notifications.notifyUser(r.getOrganisationId(), r.getApproverId(),
                        "Workflow request awaiting approval", "طلب سير عمل بانتظار الاعتماد",
                        code(r) + " \"" + r.getTitleEn() + "\" passed review and awaits your approval.",
                        "WORKFLOW_REQUEST_REVIEWED", "WorkflowRequest", r.getId());
                } else {
                    if (!"UNDER_REVIEW".equals(r.getStatus())) transition(r, "UNDER_REVIEW");
                    transition(r, "APPROVED");
                    appendEvent(r, "APPROVED", from, "APPROVED", "Approved by reviewer" + noteSuffix(note));
                }
            }
            case "RETURN" -> {
                if (!"UNDER_REVIEW".equals(r.getStatus())) transition(r, "UNDER_REVIEW");
                transition(r, "RETURNED");
                appendEvent(r, "RETURNED", from, "RETURNED", "Returned for changes" + noteSuffix(note));
                notifyInitiator(r, "Workflow request returned for changes",
                    "طلب سير العمل أُعيد للتعديل", "was returned for changes" + noteSuffix(note));
            }
            default -> throw new IllegalArgumentException("outcome must be APPROVE or RETURN");
        }
        requestRepo.save(r);
        audit.log("WORKFLOW_REQUEST_REVIEWED", "WorkflowRequest", r.getId(),
            code(r) + " · " + verdict + " · " + from + "→" + r.getStatus());
        return toResponse(r);
    }

    @Transactional
    public RequestResponse approve(Long id, String note) {
        WorkflowRequest r = get(id);
        requireActor(r.getApproverId() != null ? r.getApproverId() : r.getReviewerId(), r, "approve");
        String from = r.getStatus();
        if ("SUBMITTED".equals(r.getStatus())) transition(r, "UNDER_REVIEW");
        transition(r, "APPROVED");
        requestRepo.save(r);
        appendEvent(r, "APPROVED", from, "APPROVED", note);
        audit.log("WORKFLOW_REQUEST_APPROVED", "WorkflowRequest", r.getId(), code(r));
        notifyInitiator(r, "Workflow request approved", "تم اعتماد طلب سير العمل", "was approved" + noteSuffix(note));
        return toResponse(r);
    }

    @Transactional
    public RequestResponse reject(Long id, String note) {
        WorkflowRequest r = get(id);
        requireActor(r.getApproverId() != null ? r.getApproverId() : r.getReviewerId(), r, "reject");
        String from = r.getStatus();
        if ("SUBMITTED".equals(r.getStatus())) transition(r, "UNDER_REVIEW");
        transition(r, "REJECTED");
        requestRepo.save(r);
        appendEvent(r, "REJECTED", from, "REJECTED", note);
        audit.log("WORKFLOW_REQUEST_REJECTED", "WorkflowRequest", r.getId(), code(r) + noteSuffix(note));
        notifyInitiator(r, "Workflow request rejected", "رُفض طلب سير العمل", "was rejected" + noteSuffix(note));
        return toResponse(r);
    }

    /** Rejected/returned work comes back: RESUBMITTED → straight to UNDER_REVIEW, reviewer notified. */
    @Transactional
    public RequestResponse resubmit(Long id, String note) {
        WorkflowRequest r = get(id);
        String from = r.getStatus();
        transition(r, "RESUBMITTED");
        transition(r, "UNDER_REVIEW");
        requestRepo.save(r);
        appendEvent(r, "RESUBMITTED", from, "UNDER_REVIEW", note);
        audit.log("WORKFLOW_REQUEST_RESUBMITTED", "WorkflowRequest", r.getId(), code(r));
        Long gate = r.getReviewerId() != null ? r.getReviewerId() : r.getApproverId();
        if (gate != null) notifications.notifyUser(r.getOrganisationId(), gate,
            "Workflow request resubmitted", "أُعيد تقديم طلب سير العمل",
            code(r) + " \"" + r.getTitleEn() + "\" was resubmitted for review.",
            "WORKFLOW_REQUEST_RESUBMITTED", "WorkflowRequest", r.getId());
        return toResponse(r);
    }

    /** Manual escalation (WF-2's sweep escalates overdue requests automatically via the same path). */
    @Transactional
    public RequestResponse escalate(Long id, String note) {
        WorkflowRequest r = get(id);
        String from = r.getStatus();
        transition(r, "ESCALATED");
        r.setEscalatedAt(LocalDateTime.now());
        requestRepo.save(r);
        appendEvent(r, "ESCALATED", from, "ESCALATED", note);
        audit.log("WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId(), code(r) + noteSuffix(note));
        if (r.getEscalationUserId() != null)
            notifications.notifyUser(r.getOrganisationId(), r.getEscalationUserId(),
                "Workflow request escalated", "تم تصعيد طلب سير العمل",
                code(r) + " \"" + r.getTitleEn() + "\" was escalated" + noteSuffix(note),
                "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
        if (r.getEscalationRole() != null && !r.getEscalationRole().isBlank())
            notifications.broadcastToRole(r.getOrganisationId(), r.getEscalationRole(),
                "Workflow request escalated", "تم تصعيد طلب سير العمل",
                code(r) + " \"" + r.getTitleEn() + "\" was escalated" + noteSuffix(note),
                "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
        publisher.publishEvent(DomainEvent.of(r.getOrganisationId(), "WORKFLOW_REQUEST_ESCALATED",
            "WorkflowRequest", r.getId(),
            Map.of("priority", r.getPriority(), "requestType", r.getRequestType())));
        return toResponse(r);
    }

    @Transactional
    public RequestResponse close(Long id, String note) {
        WorkflowRequest r = get(id);
        requireInitiatorOrAdmin(r, "close");
        String from = r.getStatus();
        transition(r, "CLOSED");
        r.setClosedAt(LocalDateTime.now());
        requestRepo.save(r);
        appendEvent(r, "CLOSED", from, "CLOSED", note);
        audit.log("WORKFLOW_REQUEST_CLOSED", "WorkflowRequest", r.getId(), code(r));
        return toResponse(r);
    }

    @Transactional
    public RequestResponse reopen(Long id, String note) {
        WorkflowRequest r = get(id);
        requireInitiatorOrAdmin(r, "reopen");
        String from = r.getStatus();
        transition(r, "REOPENED");
        r.setReopenedAt(LocalDateTime.now());
        r.setClosedAt(null);
        requestRepo.save(r);
        appendEvent(r, "REOPENED", from, "REOPENED", note);
        audit.log("WORKFLOW_REQUEST_REOPENED", "WorkflowRequest", r.getId(), code(r));
        return toResponse(r);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RequestResponse> list(String status, Pageable pageable) {
        Long org = TenantContext.getCurrentTenant();
        Page<WorkflowRequest> page = (status != null && !status.isBlank())
            ? requestRepo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(org, status.toUpperCase(), pageable)
            : requestRepo.findByOrganisationIdOrderByCreatedAtDesc(org, pageable);
        return page.map(this::toResponseShallow);
    }

    @Transactional(readOnly = true)
    public RequestResponse getDetail(Long id) { return toResponse(get(id)); }

    /** Inbox — requests where the current user is addressed directly, by role, or by department. */
    @Transactional(readOnly = true)
    public List<RequestResponse> myInbox() {
        Long org = TenantContext.getCurrentTenant();
        Long userId = CurrentActor.userId();
        LinkedHashSet<Long> requestIds = new LinkedHashSet<>();
        if (userId != null) {
            recipientRepo.findByOrganisationIdAndRecipientTypeAndRecipientId(org, "USER", userId)
                .forEach(x -> requestIds.add(x.getRequestId()));
            List<String> myRoles = myRoleNames();
            if (!myRoles.isEmpty())
                recipientRepo.findByOrganisationIdAndRecipientTypeAndRecipientKeyIn(org, "ROLE", myRoles)
                    .forEach(x -> requestIds.add(x.getRequestId()));
            Long deptId = userRepo.findById(userId).map(User::getDepartmentId).orElse(null);
            if (deptId != null)
                recipientRepo.findByOrganisationIdAndRecipientTypeAndRecipientId(org, "DEPARTMENT", deptId)
                    .forEach(x -> requestIds.add(x.getRequestId()));
            for (Long committeeId : committeeMembers.findActiveCommitteeIdsForUser(org, userId))
                recipientRepo.findByOrganisationIdAndRecipientTypeAndRecipientId(org, "COMMITTEE", committeeId)
                    .forEach(x -> requestIds.add(x.getRequestId()));
        }
        return requestIds.stream()
            .map(rid -> requestRepo.findByIdAndOrganisationId(rid, org).orElse(null))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(WorkflowRequest::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(200)
            .map(this::toResponseShallow)
            .toList();
    }

    /** The request's append-only audit timeline (item 8). */
    @Transactional(readOnly = true)
    public List<EventResponse> auditLog(Long id) {
        WorkflowRequest r = get(id);   // validates existence + tenant scope
        return eventRepo.findByOrganisationIdAndRequestIdOrderByCreatedAtAscIdAsc(
                r.getOrganisationId(), r.getId())
            .stream().map(e -> new EventResponse(e.getId(), e.getEventType(), e.getFromStatus(),
                e.getToStatus(), e.getActor(), e.getActorUserId(), e.getNote(), e.getCreatedAt()))
            .toList();
    }

    // ── Routing internals ────────────────────────────────────────────────────

    /** Routing decision after a response — computed in memory; the caller applies mutations. */
    record RoutingAdvance(boolean complete, Integer nextStep) {}

    private RoutingAdvance advanceRouting(WorkflowRequest r, List<WorkflowRequestRecipient> all) {
        List<WorkflowRequestRecipient> gates = all.stream()
            .filter(x -> "ASSIGNEE".equals(x.getActorRole())).toList();
        if (gates.isEmpty()) return new RoutingAdvance(true, null);

        if ("SEQUENTIAL".equals(r.getRoutingMode())) {
            List<WorkflowRequestRecipient> current = gates.stream()
                .filter(x -> x.getStepNo().equals(r.getCurrentStep())).toList();
            boolean stepDone = current.stream()
                .allMatch(x -> Set.of("RESPONDED", "SKIPPED", "APPROVED").contains(x.getStatus()));
            if (!stepDone) return new RoutingAdvance(false, null);
            int next = gates.stream().map(WorkflowRequestRecipient::getStepNo)
                .filter(s -> s > r.getCurrentStep()).min(Integer::compareTo).orElse(-1);
            if (next == -1) return new RoutingAdvance(true, null);   // last step completed
            return new RoutingAdvance(false, next);
        }
        // PARALLEL / BROADCAST_ACK
        if ("ANY".equals(r.getCompletionRule())) {
            boolean any = gates.stream().anyMatch(x -> "RESPONDED".equals(x.getStatus()));
            if (any) gates.stream().filter(x -> Set.of("PENDING", "NOTIFIED", "IN_PROGRESS").contains(x.getStatus()))
                .forEach(x -> { x.setStatus("SKIPPED"); recipientRepo.save(x); });
            return new RoutingAdvance(any, null);
        }
        boolean allDone = gates.stream()
            .allMatch(x -> Set.of("RESPONDED", "SKIPPED", "APPROVED").contains(x.getStatus()));
        return new RoutingAdvance(allDone, null);
    }

    /** WF-4 — open the real evidence-subsystem request this workflow request routes (anchored
     *  entityType=WORKFLOW_REQUEST so the evidence lifecycle can feed status back). Assignee /
     *  department are taken from the first USER / DEPARTMENT assignee recipient. */
    private Long openBridgedEvidenceRequest(WorkflowRequest r) {
        List<WorkflowRequestRecipient> recipients = recipientsOf(r);
        Long assigneeId = recipients.stream()
            .filter(x -> "ASSIGNEE".equals(x.getActorRole()) && "USER".equals(x.getRecipientType()))
            .map(WorkflowRequestRecipient::getRecipientId).filter(Objects::nonNull).findFirst().orElse(null);
        Long departmentId = recipients.stream()
            .filter(x -> "ASSIGNEE".equals(x.getActorRole()) && "DEPARTMENT".equals(x.getRecipientType()))
            .map(WorkflowRequestRecipient::getRecipientId).filter(Objects::nonNull).findFirst().orElse(null);
        EvidenceRequestCreateDto dto = new EvidenceRequestCreateDto(
            r.getTitleEn(), r.getTitleAr(),
            (r.getDescription() == null ? "" : r.getDescription() + "\n\n")
                + "Opened by workflow request " + code(r) + " (" + r.getSourceModule() + ").",
            null,                                   // subControlId
            "WORKFLOW_REQUEST", r.getId(),          // anchor — the feed-back bridge keys on this
            assigneeId, departmentId,
            r.getDueDate(), r.getPriority(),
            "Auto-opened by the workflow request engine (WF-4 bridge).");
        EvidenceRequestResponse resp = evidenceRequests.create(dto, r.getInitiatedBy());
        return resp.id();
    }

    /** Notify recipients (of one sequential step, or all when step == null) and mark them NOTIFIED. */
    private void notifyStep(WorkflowRequest r, Integer step) {
        List<WorkflowRequestRecipient> all =
            recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId());
        String title = "New workflow request: " + r.getRequestType();
        String titleAr = "طلب سير عمل جديد";
        String msg = code(r) + " \"" + r.getTitleEn() + "\" — action: "
            + (r.getRequiredAction() == null ? r.getRequestType() : r.getRequiredAction())
            + (r.getDueDate() != null ? " · due " + r.getDueDate() : "");
        for (WorkflowRequestRecipient rec : all) {
            if (step != null && !rec.getStepNo().equals(step)) continue;
            if (!"PENDING".equals(rec.getStatus())) continue;
            switch (rec.getRecipientType()) {
                case "USER" -> notifications.notifyUser(r.getOrganisationId(), rec.getRecipientId(),
                    title, titleAr, msg, "WORKFLOW_REQUEST_ASSIGNED", "WorkflowRequest", r.getId());
                case "ROLE" -> notifications.broadcastToRole(r.getOrganisationId(), rec.getRecipientKey(),
                    title, titleAr, msg, "WORKFLOW_REQUEST_ASSIGNED", "WorkflowRequest", r.getId());
                case "DEPARTMENT" -> userRepo.findByOrganisationIdAndDepartmentIdAndIsActiveTrue(
                        r.getOrganisationId(), rec.getRecipientId())
                    .forEach(u -> notifications.notifyUser(r.getOrganisationId(), u.getId(),
                        title, titleAr, msg, "WORKFLOW_REQUEST_ASSIGNED", "WorkflowRequest", r.getId()));
                case "COMMITTEE" -> committeeMembers.findActiveByCommittee(
                        r.getOrganisationId(), rec.getRecipientId()).stream()
                    .map(CommitteeMemberTerm::getMemberUserId).filter(Objects::nonNull).distinct()
                    .forEach(uid -> notifications.notifyUser(r.getOrganisationId(), uid,
                        title, titleAr, msg, "WORKFLOW_REQUEST_ASSIGNED", "WorkflowRequest", r.getId()));
                default -> { /* EXTERNAL — recorded + §5-validated; no outbound-mail portal in the platform yet */ }
            }
            rec.setStatus("NOTIFIED");
            recipientRepo.save(rec);
        }
    }

    /** The recipient row the current actor may act on now (enforces sequential order + actor match). */
    private WorkflowRequestRecipient actionableRowFor(WorkflowRequest r, List<WorkflowRequestRecipient> all) {
        Long userId = CurrentActor.userId();
        List<String> roles = myRoleNames();
        Long deptId = userId == null ? null
            : userRepo.findById(userId).map(User::getDepartmentId).orElse(null);
        // Committee membership is only looked up when the request actually addresses a committee.
        List<Long> myCommittees = userId != null
                && all.stream().anyMatch(x -> "COMMITTEE".equals(x.getRecipientType()))
            ? committeeMembers.findActiveCommitteeIdsForUser(r.getOrganisationId(), userId)
            : List.of();
        boolean admin = isPlatformAdmin();

        for (WorkflowRequestRecipient rec : all) {
            if (!"ASSIGNEE".equals(rec.getActorRole())) continue;
            if (!Set.of("PENDING", "NOTIFIED", "IN_PROGRESS").contains(rec.getStatus())) continue;
            if ("SEQUENTIAL".equals(r.getRoutingMode()) && !rec.getStepNo().equals(r.getCurrentStep())) continue;
            boolean match = admin
                || ("USER".equals(rec.getRecipientType()) && rec.getRecipientId() != null && rec.getRecipientId().equals(userId))
                || ("ROLE".equals(rec.getRecipientType()) && rec.getRecipientKey() != null && roles.contains(rec.getRecipientKey()))
                || ("DEPARTMENT".equals(rec.getRecipientType()) && rec.getRecipientId() != null && rec.getRecipientId().equals(deptId))
                || ("COMMITTEE".equals(rec.getRecipientType()) && rec.getRecipientId() != null && myCommittees.contains(rec.getRecipientId()));
            if (match) return rec;
        }
        throw new IllegalArgumentException(
            "You are not an actionable recipient of this request at its current step");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private WorkflowRequest get(Long id) {
        return requestRepo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("WorkflowRequest " + id));
    }

    private void transition(WorkflowRequest r, String to) {
        Set<String> allowed = TRANSITIONS.getOrDefault(r.getStatus(), Set.of());
        if (!TRANSITIONS.containsKey(to))
            throw new IllegalArgumentException("Unknown status: " + to);
        if (!allowed.contains(to))
            throw new IllegalTransitionException(r.getStatus(), to, allowed);
        r.setStatus(to);
    }

    private void appendEvent(WorkflowRequest r, String type, String from, String to, String note) {
        WorkflowRequestEvent e = new WorkflowRequestEvent();
        e.setOrganisationId(r.getOrganisationId());
        e.setRequestId(r.getId());
        e.setEventType(type);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setActor(CurrentActor.username());
        e.setActorUserId(CurrentActor.userId());
        e.setNote(note);
        eventRepo.save(e);
    }

    private void requireOrgUser(Long org, Long userId, String field) {
        userRepo.findById(userId).filter(u -> org.equals(u.getOrganisationId()))
            .orElseThrow(() -> new IllegalArgumentException(field + " " + userId + " not found in this organisation"));
    }

    /** Actor gate for review/approve: named actor, or the initiator when no actor named, or admin. */
    private void requireActor(Long namedActor, WorkflowRequest r, String action) {
        Long userId = CurrentActor.userId();
        if (isPlatformAdmin()) return;
        Long gate = namedActor != null ? namedActor : r.getInitiatedBy();
        if (gate == null || !gate.equals(userId))
            throw new IllegalArgumentException("Only the designated actor may " + action + " this request");
    }

    private void requireInitiatorOrAdmin(WorkflowRequest r, String action) {
        Long userId = CurrentActor.userId();
        if (isPlatformAdmin()) return;
        if (r.getInitiatedBy() == null || !r.getInitiatedBy().equals(userId))
            throw new IllegalArgumentException("Only the initiator may " + action + " this request");
    }

    private static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
    }

    private static List<String> myRoleNames() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length()))
            .toList();
    }

    private String resolveLabel(Long org, RecipientDto in) {
        if (in.recipientLabel() != null && !in.recipientLabel().isBlank()) return in.recipientLabel();
        return switch (in.recipientType().trim().toUpperCase()) {
            case "USER" -> userRepo.findById(in.recipientId()).map(User::getFullNameEn).orElse("user#" + in.recipientId());
            case "DEPARTMENT" -> departmentRepo.findByIdAndOrganisationId(in.recipientId(), org)
                .map(d -> d.getNameEn()).orElse("dept#" + in.recipientId());
            case "COMMITTEE" -> committeeRepo.findByIdAndOrganisationId(in.recipientId(), org)
                .map(Committee::getNameEn).orElse("committee#" + in.recipientId());
            case "ROLE" -> in.recipientKey();
            case "EXTERNAL" -> in.recipientKey();
            default -> in.recipientKey() != null ? in.recipientKey() : "#" + in.recipientId();
        };
    }

    private static String categoryOf(List<RecipientDto> recipients) {
        Set<String> types = new LinkedHashSet<>();
        recipients.forEach(x -> types.add(x.recipientType().trim().toUpperCase()));
        return types.size() == 1 ? types.iterator().next() : "MIXED";
    }

    private void notifyInitiator(WorkflowRequest r, String titleEn, String titleAr, String verb) {
        notifications.notifyUser(r.getOrganisationId(), r.getInitiatedBy(), titleEn, titleAr,
            code(r) + " \"" + r.getTitleEn() + "\" " + verb,
            "WORKFLOW_REQUEST_UPDATE", "WorkflowRequest", r.getId());
    }

    private static String upperOr(String v, String dflt) {
        return v == null || v.isBlank() ? dflt : v.trim().toUpperCase();
    }

    private static String noteSuffix(String note) {
        return note == null || note.isBlank() ? "" : " — " + note;
    }

    private static String code(WorkflowRequest r) { return "WR-" + String.format("%05d", r.getId()); }

    private RequestResponse toResponseShallow(WorkflowRequest r) { return toResponse(r, false); }

    private RequestResponse toResponse(WorkflowRequest r) { return toResponse(r, true); }

    private RequestResponse toResponse(WorkflowRequest r, boolean withRecipients) {
        String countdown = "NONE";
        Long hoursRemaining = null;
        if (r.getDueDate() != null && !Set.of("CLOSED", "ARCHIVED", "APPROVED").contains(r.getStatus())) {
            LocalDate today = LocalDate.now();
            hoursRemaining = ChronoUnit.HOURS.between(LocalDateTime.now(), r.getDueDate().plusDays(1).atStartOfDay());
            countdown = r.getDueDate().isBefore(today) ? "OVERDUE"
                : r.getDueDate().isEqual(today) ? "DUE_TODAY" : "ON_TRACK";
        }
        List<RecipientResponse> recipients = !withRecipients ? null
            : recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId())
                .stream().map(x -> new RecipientResponse(x.getId(), x.getRecipientType(), x.getRecipientId(),
                    x.getRecipientKey(), x.getRecipientLabel(), x.getActorRole(), x.getStepNo(), x.getStatus(),
                    x.getRespondedAt(), x.getRespondedBy(), x.getResponse()))
                .toList();
        return new RequestResponse(r.getId(), code(r),
            r.getSourceModule(), r.getSourceRecordType(), r.getSourceRecordId(),
            r.getRequestType(), r.getTitleEn(), r.getTitleAr(), r.getDescription(),
            r.getInitiatedBy(), r.getInitiatedByName(),
            r.getRecipientCategory(), r.getRoutingMode(), r.getCompletionRule(), r.getCurrentStep(),
            r.getRequiredAction(), r.getDueDate(), r.getSlaHours(),
            countdown, hoursRemaining,
            r.getPriority(), r.getSeverity(),
            r.getReminderDaysBefore(), r.getLastReminderAt(), r.getReminderCount(),
            r.getEscalationRole(), r.getEscalationUserId(), r.getEscalatedAt(),
            r.getStatus(), r.getResponseRequired(), r.getEvidenceRequired(), r.getClosureCondition(),
            r.getReviewerId(), r.getApproverId(), r.getEvidenceRequestId(),
            r.getClosedAt(), r.getReopenedAt(),
            r.getCreatedAt(), r.getUpdatedAt(),
            recipients);
    }

    /** WF-1 exposes routing internals to the WF-2 SLA/reminder/escalation sweep. */
    List<WorkflowRequestRecipient> recipientsOf(WorkflowRequest r) {
        return recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId());
    }
}
