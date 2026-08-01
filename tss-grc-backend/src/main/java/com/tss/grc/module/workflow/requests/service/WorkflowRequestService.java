package com.tss.grc.module.workflow.requests.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.BranchRuleDto;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.CreateRequest;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.EscalationTierDto;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.EventResponse;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.PublicRequestView;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientDto;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientSuggestion;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
import java.util.UUID;

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
    static final Set<String> COMPLETION_RULES = Set.of("ALL", "ANY", "QUORUM", "MAJORITY");
    static final Set<String> PRIORITIES      = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    static final Set<String> RECIPIENT_TYPES = Set.of("USER", "ROLE", "DEPARTMENT", "COMMITTEE", "EXTERNAL");
    static final Set<String> ACTOR_ROLES     = Set.of("ASSIGNEE", "REVIEWER", "APPROVER", "OBSERVER", "ESCALATION");
    /** WF-6 — conditional-routing branch actions (§7 #5). */
    static final Set<String> BRANCH_ACTIONS  = Set.of("GOTO_STEP", "RETURN", "CLOSE", "ESCALATE");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** WF-7 — external token lives until the due date + this grace window (or a flat default). */
    private static final int EXTERNAL_TOKEN_GRACE_DAYS = 14;
    private static final int EXTERNAL_TOKEN_DEFAULT_DAYS = 30;

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
    /** WF-8 — expand a saved distribution list into a request's recipients. */
    private final WorkflowRecipientListService       recipientLists;

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
        r.setCompletionThreshold(dto.completionThreshold());
        r.setStatus("DRAFT");

        // WF-10 — multi-tier escalation ladder (set before validate: a ladder satisfies the
        // HIGH/CRITICAL escalation-path gate).
        List<EscalationTierDto> tiers = normalizeTiers(dto.escalationTiers());
        validateTiers(tiers);
        if (!tiers.isEmpty()) r.setEscalationTiers(writeTiers(tiers));

        // WF-8/WF-9 — the effective recipient set: explicit rows + an expanded saved distribution
        // list (recipientListId), with COMMITTEE rows optionally materialized into per-member gates.
        List<RecipientDto> recipients = buildRecipients(dto, org);
        List<BranchRuleDto> rules = normalizeRules(dto.branchRules());
        validate(r, recipients, rules, org);
        r.setRecipientCategory(categoryOf(recipients));
        if (!rules.isEmpty()) r.setBranchRules(writeRules(rules));
        r = requestRepo.save(r);

        saveRecipients(r, recipients, org);

        appendEvent(r, "CREATED", null, "DRAFT", "Request drafted (" + recipients.size() + " recipient(s), "
            + r.getRoutingMode() + (r.getRoutingMode().equals("PARALLEL") ? "/" + r.getCompletionRule() : "") + ")");
        audit.log("WORKFLOW_REQUEST_CREATED", "WorkflowRequest", r.getId(),
            code(r) + " · " + r.getTitleEn() + " · " + r.getRoutingMode());
        return toResponse(r);
    }

    private void saveRecipients(WorkflowRequest r, List<RecipientDto> recipients, Long org) {
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
    }

    /** WF-9 — edit a request while it is still a DRAFT (the one §6 API missing before). Once sent,
     *  a request is immutable except via the lifecycle transitions (→ 409). Initiator/admin only. */
    @Transactional
    public RequestResponse update(Long id, CreateRequest dto) {
        WorkflowRequest r = get(id);
        if (!"DRAFT".equals(r.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a DRAFT request can be edited; use the lifecycle actions instead.");
        requireInitiatorOrAdmin(r, "edit");
        Long org = r.getOrganisationId();

        r.setSourceModule(dto.sourceModule().trim().toUpperCase());
        r.setSourceRecordType(dto.sourceRecordType());
        r.setSourceRecordId(dto.sourceRecordId());
        r.setRequestType(dto.requestType().trim().toUpperCase());
        r.setTitleEn(dto.titleEn());
        r.setTitleAr(dto.titleAr());
        r.setDescription(dto.description());
        r.setRoutingMode(upperOr(dto.routingMode(), "PARALLEL"));
        r.setCompletionRule(upperOr(dto.completionRule(), "ALL"));
        r.setCompletionThreshold(dto.completionThreshold());
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

        List<EscalationTierDto> tiers = normalizeTiers(dto.escalationTiers());
        validateTiers(tiers);
        r.setEscalationTiers(tiers.isEmpty() ? null : writeTiers(tiers));

        List<RecipientDto> recipients = buildRecipients(dto, org);
        List<BranchRuleDto> rules = normalizeRules(dto.branchRules());
        validate(r, recipients, rules, org);
        r.setRecipientCategory(categoryOf(recipients));
        r.setBranchRules(rules.isEmpty() ? null : writeRules(rules));
        requestRepo.save(r);

        // Replace the recipient set (still DRAFT — none have acted).
        recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(org, r.getId())
            .forEach(rec -> { rec.setIsDeleted(true); recipientRepo.save(rec); });
        saveRecipients(r, recipients, org);

        appendEvent(r, "UPDATED", "DRAFT", "DRAFT", "Draft edited (" + recipients.size() + " recipient(s))");
        audit.log("WORKFLOW_REQUEST_UPDATED", "WorkflowRequest", r.getId(), code(r) + " · draft edited");
        return toResponse(r);
    }

    /** §5 validation — every gate throws IllegalArgumentException → 400. */
    private void validate(WorkflowRequest r, List<RecipientDto> recipients, List<BranchRuleDto> rules, Long org) {
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

        // Escalation path is required for high-priority requests (§5). A multi-tier ladder
        // (WF-10) also satisfies this.
        boolean hasTierLadder = r.getEscalationTiers() != null && !r.getEscalationTiers().isBlank();
        if (("HIGH".equals(r.getPriority()) || "CRITICAL".equals(r.getPriority()))
                && r.getEscalationRole() == null && r.getEscalationUserId() == null
                && !hasEscalationRecipient && !hasTierLadder)
            throw new IllegalArgumentException(
                "HIGH/CRITICAL priority requires an escalation path (escalationRole, escalationUserId, an ESCALATION recipient, or an escalation tier ladder)");

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

        // §5 — an APPROVAL request must have an approval gate (named approver or an APPROVER recipient).
        if ("APPROVAL".equals(r.getRequestType())
                && r.getApproverId() == null
                && recipients.stream().noneMatch(x -> "APPROVER".equals(upperOr(x.actorRole(), "ASSIGNEE"))))
            throw new IllegalArgumentException(
                "APPROVAL requests require an approver (approverId or a recipient with the APPROVER actor role)");

        /*
         * F2-86 — four eyes has to be structural, not conventional. The rule above requires *an*
         * approver and never asked who: measured on a clean tenant, a request naming its own
         * initiator as approverId was accepted, and that initiator then submitted and approved it —
         * 200 APPROVED, no second pair of eyes anywhere in the trail. The workflow engine is the
         * approval path 26 module screens raise requests through, so this was the generic version
         * of F2-29 (a raiser approving their own emergency change).
         *
         * Refused at the door rather than only at approve() time, because refusing there would
         * leave a request whose only designated approver is barred from approving it — a dead end a
         * user could create and not escape. Covers the recipient gate too: a USER recipient carrying
         * APPROVER or REVIEWER is exactly as load-bearing as the named column.
         */
        Long initiator = r.getInitiatedBy();
        if (initiator != null) {
            if (initiator.equals(r.getApproverId()))
                throw new IllegalArgumentException(
                    "The initiator cannot be the approver of their own request (four-eyes)");
            if (initiator.equals(r.getReviewerId()))
                throw new IllegalArgumentException(
                    "The initiator cannot be the reviewer of their own request (four-eyes)");
            if (recipients.stream().anyMatch(x ->
                    "USER".equals(upperOr(x.recipientType(), ""))
                        && initiator.equals(x.recipientId())
                        && Set.of("APPROVER", "REVIEWER").contains(upperOr(x.actorRole(), "ASSIGNEE"))))
                throw new IllegalArgumentException(
                    "The initiator cannot be their own APPROVER or REVIEWER recipient (four-eyes)");
        }

        // WF-9 — QUORUM/MAJORITY completion applies to PARALLEL routing; QUORUM needs a valid threshold.
        if (Set.of("QUORUM", "MAJORITY").contains(r.getCompletionRule())) {
            if (!"PARALLEL".equals(r.getRoutingMode()))
                throw new IllegalArgumentException("QUORUM/MAJORITY completion requires PARALLEL routing");
            long gates = recipients.stream().filter(x -> "ASSIGNEE".equals(upperOr(x.actorRole(), "ASSIGNEE"))).count();
            if ("QUORUM".equals(r.getCompletionRule())) {
                if (r.getCompletionThreshold() == null || r.getCompletionThreshold() < 1)
                    throw new IllegalArgumentException("QUORUM completion requires completionThreshold >= 1");
                if (r.getCompletionThreshold() > gates)
                    throw new IllegalArgumentException(
                        "completionThreshold (" + r.getCompletionThreshold() + ") exceeds the number of assignee recipients (" + gates + ")");
            }
        }

        // WF-6 — conditional-routing rules (§7 #5). GOTO_STEP is forward-only, which keeps
        // the §7 #19 no-circular-routing guarantee; RETURN/CLOSE/ESCALATE reuse machine paths.
        for (BranchRuleDto b : rules) {
            if (!BRANCH_ACTIONS.contains(b.action()))
                throw new IllegalArgumentException("branch action must be one of " + BRANCH_ACTIONS);
            if (b.stepNo() == null || !steps.contains(b.stepNo()))
                throw new IllegalArgumentException(
                    "branch rule stepNo " + b.stepNo() + " is not a step of this request");
            if ("GOTO_STEP".equals(b.action())) {
                if (!"SEQUENTIAL".equals(r.getRoutingMode()))
                    throw new IllegalArgumentException("GOTO_STEP branch rules require SEQUENTIAL routing");
                if (b.targetStep() == null || !steps.contains(b.targetStep()))
                    throw new IllegalArgumentException("GOTO_STEP requires an existing targetStep");
                if (b.targetStep() <= b.stepNo())
                    throw new IllegalArgumentException(
                        "GOTO_STEP must jump forward (targetStep > stepNo) — circular routing is blocked");
            }
        }
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
     *
     * <p>WF-6 — if the responder supplies an {@code outcome} that matches one of the request's
     * branch rules for their step, the branch (GOTO_STEP / RETURN / CLOSE / ESCALATE) overrides
     * the default routing (§7 #5: "if rejected → return to submitter; if high risk → executive
     * approval").
     */
    @Transactional
    public RequestResponse respond(Long id, String note, String outcome) {
        WorkflowRequest r = get(id);
        if (!Set.of("SENT", "IN_PROGRESS", "REOPENED", "ESCALATED", "OVERDUE").contains(r.getStatus()))
            throw new IllegalTransitionException(r.getStatus(), "RESPONDED(recipient)", TRANSITIONS.get(r.getStatus()));

        List<WorkflowRequestRecipient> all =
            recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId());
        WorkflowRequestRecipient mine = actionableRowFor(r, all);
        return completeRecipientResponse(r, all, mine, note, outcome, CurrentActor.userId());
    }

    /**
     * Shared post-identification response handling: mark the recipient row RESPONDED, apply any
     * matching WF-6 branch rule, else advance the default routing. Called by the authenticated
     * {@link #respond} and by the WF-7 token path {@link #publicRespondByToken} (respondedBy is
     * the authenticated user id, or {@code null} for an external token responder). Follows the
     * ROWVERSION rule: finalize every request mutation before any flush-triggering query.
     */
    private RequestResponse completeRecipientResponse(WorkflowRequest r, List<WorkflowRequestRecipient> all,
                                                      WorkflowRequestRecipient mine, String note,
                                                      String outcome, Long respondedBy) {
        String from = r.getStatus();
        mine.setStatus("RESPONDED");
        mine.setRespondedAt(LocalDateTime.now());
        mine.setRespondedBy(respondedBy);
        mine.setResponse(note);
        mine.setResponseOutcome(outcome == null || outcome.isBlank() ? null : outcome.trim().toUpperCase());
        recipientRepo.save(mine);

        BranchRuleDto branch = matchBranchRule(r, mine.getStepNo(), mine.getResponseOutcome());
        if (branch != null) return applyBranch(r, all, mine, branch, from);

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
                + (mine.getResponseOutcome() == null ? "" : " [" + mine.getResponseOutcome() + "]")
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
        requireActor(reviewGate(r), r, "review");
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
        requireActor(approvalGate(r), r, "approve");
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
        requireActor(approvalGate(r), r, "reject");
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

    /** Manual escalation. With a multi-tier ladder (WF-10) each call steps up ONE tier and notifies
     *  that rung; without tiers it fires the single escalation path (WF-2's sweep auto-escalates the
     *  same way). All r mutations happen before the single save (ROWVERSION rule); notify last. */
    @Transactional
    public RequestResponse escalate(Long id, String note) {
        WorkflowRequest r = get(id);
        String from = r.getStatus();
        if (!"ESCALATED".equals(r.getStatus())) transition(r, "ESCALATED");   // re-escalate steps the tier, not the state
        r.setEscalatedAt(LocalDateTime.now());
        List<EscalationTierDto> tiers = readTiers(r.getEscalationTiers());
        EscalationTierDto tier = null;
        if (!tiers.isEmpty()) {
            int idx = Math.min(r.getEscalationLevel() == null ? 0 : r.getEscalationLevel(), tiers.size() - 1);
            r.setEscalationLevel(idx + 1);
            tier = tiers.get(idx);
        }
        requestRepo.save(r);
        appendEvent(r, "ESCALATED", from, "ESCALATED",
            (tier != null ? "Escalated to tier " + (tier.level() != null ? tier.level() : r.getEscalationLevel())
                + (tier.role() != null ? " (" + tier.role() + ")" : "") : "Escalated") + noteSuffix(note));
        audit.log("WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId(), code(r) + noteSuffix(note));
        if (tier != null) notifyTier(r, tier, note); else notifyEscalationPath(r, note);
        return toResponse(r);
    }

    /** WF-10 — notify one escalation tier (role broadcast + named user) + an F8 event carrying the
     *  reached level. Pure: mutates nothing on the request (call after the save). */
    private void notifyTier(WorkflowRequest r, EscalationTierDto tier, String note) {
        String title = "Workflow request escalated"; String titleAr = "تم تصعيد طلب سير العمل";
        String msg = code(r) + " \"" + r.getTitleEn() + "\" escalated to tier "
            + (tier.level() != null ? tier.level() : r.getEscalationLevel()) + noteSuffix(note);
        if (tier.userId() != null)
            notifications.notifyUser(r.getOrganisationId(), tier.userId(), title, titleAr, msg,
                "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
        if (tier.role() != null && !tier.role().isBlank())
            notifications.broadcastToRole(r.getOrganisationId(), tier.role(), title, titleAr, msg,
                "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
        publisher.publishEvent(DomainEvent.of(r.getOrganisationId(), "WORKFLOW_REQUEST_ESCALATED",
            "WorkflowRequest", r.getId(),
            Map.of("priority", r.getPriority() == null ? "" : r.getPriority(),
                   "requestType", r.getRequestType() == null ? "" : r.getRequestType(),
                   "escalationLevel", r.getEscalationLevel() == null ? 0 : r.getEscalationLevel())));
    }

    /** WF-10 — parse the escalation-tier ladder JSON (empty on absent/parse failure). */
    static List<EscalationTierDto> readTiers(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<EscalationTierDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String writeTiers(List<EscalationTierDto> tiers) {
        try {
            return MAPPER.writeValueAsString(tiers);
        } catch (Exception e) {
            throw new IllegalArgumentException("escalationTiers could not be serialized: " + e.getMessage());
        }
    }

    /** WF-10 — assign contiguous levels (1..N in list order) + upper-case roles + default afterDays. */
    private static List<EscalationTierDto> normalizeTiers(List<EscalationTierDto> tiers) {
        if (tiers == null || tiers.isEmpty()) return List.of();
        List<EscalationTierDto> out = new java.util.ArrayList<>();
        int level = 1;
        for (EscalationTierDto t : tiers) {
            out.add(new EscalationTierDto(level++,
                t.role() == null || t.role().isBlank() ? null : t.role().trim().toUpperCase(),
                t.userId(),
                t.afterDays() == null ? 0 : t.afterDays()));
        }
        return out;
    }

    /** WF-10 — each rung needs a target; afterDays must be non-negative and non-decreasing. */
    private static void validateTiers(List<EscalationTierDto> tiers) {
        int prev = -1;
        for (EscalationTierDto t : tiers) {
            if (t.role() == null && t.userId() == null)
                throw new IllegalArgumentException("each escalation tier requires a role or a userId");
            if (t.afterDays() < 0)
                throw new IllegalArgumentException("escalation tier afterDays must be >= 0");
            if (t.afterDays() < prev)
                throw new IllegalArgumentException("escalation tiers must have non-decreasing afterDays (a rising ladder)");
            prev = t.afterDays();
        }
    }

    /** Escalation-path fan-out: escalation user + role broadcast + F8 event. Shared by the
     *  manual /escalate action and the WF-6 ESCALATE branch rule (WF-2's sweep has its own copy
     *  with overdue wording). */
    private void notifyEscalationPath(WorkflowRequest r, String note) {
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
        return list(status, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<RequestResponse> list(String status, String sourceModule, Pageable pageable) {
        return list(status, sourceModule, null, null, pageable);
    }

    /** List engine requests for the tenant, optionally narrowed by status, source module, and/or a
     *  specific source record. sourceModule powers each module page's live panel; the
     *  sourceRecordType+sourceRecordId pair (both required together) powers a detail page's
     *  "requests about this record" panel and takes precedence when supplied. */
    @Transactional(readOnly = true)
    public Page<RequestResponse> list(String status, String sourceModule,
                                      String sourceRecordType, Long sourceRecordId, Pageable pageable) {
        Long org = TenantContext.getCurrentTenant();
        if (sourceRecordType != null && !sourceRecordType.isBlank() && sourceRecordId != null) {
            return requestRepo.findByOrganisationIdAndSourceRecordTypeAndSourceRecordIdOrderByCreatedAtDesc(
                    org, sourceRecordType.trim(), sourceRecordId, pageable)
                .map(this::toResponseShallow);
        }
        boolean hasStatus = status != null && !status.isBlank();
        String st = hasStatus ? status.toUpperCase() : null;
        String mod = (sourceModule != null && !sourceModule.isBlank()) ? sourceModule.trim().toUpperCase() : null;
        Page<WorkflowRequest> page;
        if (mod != null && hasStatus)
            page = requestRepo.findByOrganisationIdAndSourceModuleAndStatusOrderByCreatedAtDesc(org, mod, st, pageable);
        else if (mod != null)
            page = requestRepo.findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(org, mod, pageable);
        else if (hasStatus)
            page = requestRepo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(org, st, pageable);
        else
            page = requestRepo.findByOrganisationIdOrderByCreatedAtDesc(org, pageable);
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

    // ── WF-8 recipient suggestions ─────────────────────────────────────────────

    /** Owner/role suggestions for composing a request against a source record (§2.1/§2.2): the
     *  module's owner roles + the initiator's department + a compliance reviewer — so the composer
     *  proposes real recipients instead of a blank form. Role-based (portable across tenants);
     *  record-specific owner resolution is a future refinement. */
    private static final Map<String, List<String>> MODULE_OWNER_ROLES = Map.ofEntries(
        Map.entry("RISK",       List.of("RISK_MANAGER", "RISK_OWNER")),
        Map.entry("COMPLIANCE", List.of("COMPLIANCE_OFFICER", "CONTROL_OWNER")),
        Map.entry("CYBER",      List.of("CISO", "SECURITY_ANALYST")),
        Map.entry("SOC",        List.of("SECURITY_ANALYST", "CISO")),
        Map.entry("PRIVACY",    List.of("DPO", "PRIVACY_MANAGER")),
        Map.entry("BCM",        List.of("BCM_MANAGER")),
        Map.entry("SCM",        List.of("SUPPLIER_MANAGER")),
        Map.entry("TPRM",       List.of("SUPPLIER_MANAGER")),
        Map.entry("SUST",       List.of("SUSTAINABILITY_OFFICER")),
        Map.entry("EVIDENCE",   List.of("EVIDENCE_OWNER", "CONTROL_OWNER")),
        Map.entry("NDI",        List.of("DATA_OWNER")),
        Map.entry("AUDIT",      List.of("INTERNAL_AUDITOR"))
    );

    @Transactional(readOnly = true)
    public List<RecipientSuggestion> recipientSuggestions(String sourceModule, String sourceRecordType, Long sourceRecordId) {
        Long org = TenantContext.getCurrentTenant();
        Long userId = CurrentActor.userId();
        List<RecipientSuggestion> out = new java.util.ArrayList<>();
        if (userId != null) {
            Long deptId = userRepo.findById(userId).map(User::getDepartmentId).orElse(null);
            if (deptId != null) {
                String label = departmentRepo.findByIdAndOrganisationId(deptId, org)
                    .map(d -> d.getNameEn()).orElse("dept#" + deptId);
                out.add(new RecipientSuggestion("DEPARTMENT", deptId, null, label, "ASSIGNEE", "Your department"));
            }
        }
        String mod = sourceModule == null ? "" : sourceModule.trim().toUpperCase();
        for (String role : MODULE_OWNER_ROLES.getOrDefault(mod, List.of()))
            out.add(new RecipientSuggestion("ROLE", null, role, role, "ASSIGNEE", mod + " owner role"));
        out.add(new RecipientSuggestion("ROLE", null, "COMPLIANCE_OFFICER", "COMPLIANCE_OFFICER", "REVIEWER", "Compliance reviewer"));
        return out;
    }

    // ── WF-7 public (token-credentialed) surface ──────────────────────────────

    /** The EXTERNAL recipient's own minimal view (§7 #18): no internal ids, no other recipients,
     *  no timeline. Wrong token → 404; expired → 410. */
    @Transactional(readOnly = true)
    public PublicRequestView publicView(String token) {
        WorkflowRequestRecipient rec = requireLiveToken(token);
        WorkflowRequest r = requestRepo.findById(rec.getRequestId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean open = OPEN_FOR_RESPONSE.contains(r.getStatus());
        boolean myTurn = !"SEQUENTIAL".equals(r.getRoutingMode()) || rec.getStepNo().equals(r.getCurrentStep());
        boolean pending = Set.of("PENDING", "NOTIFIED", "IN_PROGRESS").contains(rec.getStatus());
        return new PublicRequestView(code(r), r.getRequestType(), r.getTitleEn(), r.getTitleAr(),
            r.getRequiredAction(), r.getDescription(), r.getDueDate(), r.getStatus(),
            countdownOf(r), r.getPriority(), r.getInitiatedByName(),
            rec.getRecipientLabel() == null ? rec.getRecipientKey() : rec.getRecipientLabel(),
            rec.getStatus(), open && myTurn && pending);
    }

    /** An EXTERNAL recipient acknowledges/responds via their token — feeds the SAME routing
     *  (incl. WF-6 branch rules) as an internal respond, with no authenticated principal.
     *  Wrong token → 404; expired → 410; already responded / request closed / not this step → 409. */
    @Transactional
    public PublicRequestView publicRespondByToken(String token, String note, String outcome) {
        WorkflowRequestRecipient rec = requireLiveToken(token);
        if (!Set.of("PENDING", "NOTIFIED", "IN_PROGRESS").contains(rec.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been answered.");
        WorkflowRequest r = requestRepo.findById(rec.getRequestId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!OPEN_FOR_RESPONSE.contains(r.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is no longer accepting responses.");
        if ("SEQUENTIAL".equals(r.getRoutingMode()) && !rec.getStepNo().equals(r.getCurrentStep()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "It is not yet this recipient's turn to respond.");

        Long previous = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(r.getOrganisationId());   // audit.log reads TenantContext
            List<WorkflowRequestRecipient> all = recipientRepo
                .findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId());
            WorkflowRequestRecipient mine = all.stream()
                .filter(x -> x.getId().equals(rec.getId())).findFirst().orElse(rec);
            completeRecipientResponse(r, all, mine, note, outcome, null);   // respondedBy = null (external)
            return publicView(token);   // re-project the external's own minimal view
        } finally {
            if (previous != null) TenantContext.setCurrentTenant(previous); else TenantContext.clear();
        }
    }

    /** Resolve a live token or fail with a status the public tracker can show (no info oracle
     *  beyond present/expired). */
    private WorkflowRequestRecipient requireLiveToken(String token) {
        WorkflowRequestRecipient rec = recipientRepo.findByAccessToken(token == null ? "" : token.trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (rec.getTokenExpiresAt() != null && rec.getTokenExpiresAt().isBefore(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.GONE, "This access link has expired.");
        return rec;
    }

    /** Statuses in which a recipient may still respond (mirrors the guard in respond()). */
    private static final Set<String> OPEN_FOR_RESPONSE =
        Set.of("SENT", "IN_PROGRESS", "REOPENED", "ESCALATED", "OVERDUE");

    private static String countdownOf(WorkflowRequest r) {
        if (r.getDueDate() == null || Set.of("CLOSED", "ARCHIVED", "APPROVED").contains(r.getStatus())) return "NONE";
        LocalDate today = LocalDate.now();
        return r.getDueDate().isBefore(today) ? "OVERDUE"
            : r.getDueDate().isEqual(today) ? "DUE_TODAY" : "ON_TRACK";
    }

    // ── Routing internals ────────────────────────────────────────────────────

    /** WF-6 — first branch rule matching the responder's step + outcome (or null). */
    private static BranchRuleDto matchBranchRule(WorkflowRequest r, Integer stepNo, String outcome) {
        if (outcome == null || r.getBranchRules() == null || r.getBranchRules().isBlank()) return null;
        for (BranchRuleDto b : readRules(r.getBranchRules())) {
            if (stepNo.equals(b.stepNo()) && outcome.equalsIgnoreCase(b.onOutcome())) return b;
        }
        return null;
    }

    /**
     * WF-6 — apply a matched branch rule instead of the default routing advance. All request
     * mutations happen before the single save (ROWVERSION rule); notifications run last.
     */
    private RequestResponse applyBranch(WorkflowRequest r, List<WorkflowRequestRecipient> all,
                                        WorkflowRequestRecipient mine, BranchRuleDto rule, String from) {
        String outcome = mine.getResponseOutcome();
        String fired = "Branch rule fired: outcome '" + outcome + "' at step " + rule.stepNo();
        switch (rule.action()) {
            case "GOTO_STEP" -> {
                // The jumped-over steps must never block: park their pending assignee gates.
                for (WorkflowRequestRecipient rec : all) {
                    if (!"ASSIGNEE".equals(rec.getActorRole())) continue;
                    if (!Set.of("PENDING", "NOTIFIED", "IN_PROGRESS").contains(rec.getStatus())) continue;
                    if (rec.getStepNo() > mine.getStepNo() && rec.getStepNo() < rule.targetStep()) {
                        rec.setStatus("SKIPPED");
                        recipientRepo.save(rec);
                    }
                }
                r.setCurrentStep(rule.targetStep());
                if ("SENT".equals(r.getStatus())) transition(r, "IN_PROGRESS");
                requestRepo.save(r);
                appendEvent(r, "BRANCHED", from, r.getStatus(),
                    fired + " → jumped to step " + rule.targetStep());
                audit.log("WORKFLOW_REQUEST_BRANCHED", "WorkflowRequest", r.getId(),
                    code(r) + " · " + outcome + " → step " + rule.targetStep());
                notifyStep(r, rule.targetStep());
            }
            case "RETURN" -> {
                if (!"SUBMITTED".equals(r.getStatus())) transition(r, "SUBMITTED");
                transition(r, "RETURNED");
                requestRepo.save(r);
                appendEvent(r, "BRANCHED", from, "RETURNED", fired + " → returned to initiator");
                audit.log("WORKFLOW_REQUEST_BRANCHED", "WorkflowRequest", r.getId(),
                    code(r) + " · " + outcome + " → RETURNED");
                notifyInitiator(r, "Workflow request returned by branch rule",
                    "أُعيد طلب سير العمل بقاعدة تفرع",
                    "was returned to you (branch: " + outcome + ")");
            }
            case "CLOSE" -> {
                if (!"SUBMITTED".equals(r.getStatus())) transition(r, "SUBMITTED");
                transition(r, "CLOSED");
                r.setClosedAt(LocalDateTime.now());
                if (r.getClosureCondition() == null) r.setClosureCondition("BRANCH_RULE");
                requestRepo.save(r);
                appendEvent(r, "BRANCHED", from, "CLOSED", fired + " → closed");
                audit.log("WORKFLOW_REQUEST_BRANCHED", "WorkflowRequest", r.getId(),
                    code(r) + " · " + outcome + " → CLOSED");
            }
            case "ESCALATE" -> {
                transition(r, "ESCALATED");
                r.setEscalatedAt(LocalDateTime.now());
                requestRepo.save(r);
                appendEvent(r, "BRANCHED", from, "ESCALATED", fired + " → escalated");
                audit.log("WORKFLOW_REQUEST_BRANCHED", "WorkflowRequest", r.getId(),
                    code(r) + " · " + outcome + " → ESCALATED");
                notifyEscalationPath(r, "branch rule on outcome '" + outcome + "'");
            }
            default -> throw new IllegalArgumentException("Unknown branch action: " + rule.action());
        }
        return toResponse(r);
    }

    /** WF-6 — normalize rules for storage: upper-case action + outcome tokens. */
    private static List<BranchRuleDto> normalizeRules(List<BranchRuleDto> rules) {
        if (rules == null || rules.isEmpty()) return List.of();
        return rules.stream().map(b -> new BranchRuleDto(
            b.stepNo(),
            b.onOutcome() == null ? null : b.onOutcome().trim().toUpperCase(),
            b.action() == null ? null : b.action().trim().toUpperCase(),
            b.targetStep())).toList();
    }

    private static String writeRules(List<BranchRuleDto> rules) {
        try {
            return MAPPER.writeValueAsString(rules);
        } catch (Exception e) {
            throw new IllegalArgumentException("branchRules could not be serialized: " + e.getMessage());
        }
    }

    private static List<BranchRuleDto> readRules(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<BranchRuleDto>>() {});
        } catch (Exception e) {
            return List.of();   // rules were validated at create; a parse failure must not break reads
        }
    }

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
        String rule = r.getCompletionRule();
        if ("ALL".equals(rule)) {
            boolean allDone = gates.stream()
                .allMatch(x -> Set.of("RESPONDED", "SKIPPED", "APPROVED").contains(x.getStatus()));
            return new RoutingAdvance(allDone, null);
        }
        // ANY / QUORUM / MAJORITY — a threshold on the gates that actually answered (WF-9).
        long answered = gates.stream().filter(x -> Set.of("RESPONDED", "APPROVED").contains(x.getStatus())).count();
        int total = gates.size();
        int need = switch (rule) {
            case "ANY"      -> 1;
            case "MAJORITY" -> (total / 2) + 1;
            case "QUORUM"   -> r.getCompletionThreshold() != null ? r.getCompletionThreshold() : (total / 2) + 1;
            default         -> total;
        };
        boolean complete = answered >= need;
        if (complete) gates.stream().filter(x -> Set.of("PENDING", "NOTIFIED", "IN_PROGRESS").contains(x.getStatus()))
            .forEach(x -> { x.setStatus("SKIPPED"); recipientRepo.save(x); });
        return new RoutingAdvance(complete, null);
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
        // WF-7 — externals freshly tokenized this pass; their link events fire AFTER the save loop
        // so the recipient row is UPDATEd exactly once (token + NOTIFIED together — ROWVERSION rule).
        List<WorkflowRequestRecipient> freshExternals = new java.util.ArrayList<>();
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
                case "EXTERNAL" -> {                             // WF-7 — mint token (pure field-set)
                    if (rec.getAccessToken() == null) {
                        rec.setAccessToken(UUID.randomUUID().toString().replace("-", ""));
                        rec.setTokenExpiresAt(r.getDueDate() != null
                            ? r.getDueDate().plusDays(EXTERNAL_TOKEN_GRACE_DAYS).atStartOfDay()
                            : LocalDateTime.now().plusDays(EXTERNAL_TOKEN_DEFAULT_DAYS));
                        freshExternals.add(rec);
                    }
                }
                default -> { }
            }
            rec.setStatus("NOTIFIED");
            recipientRepo.save(rec);                             // one UPDATE per row (token + status)
        }
        // Surface each external tracking link (timeline + initiator notification) — the initiator
        // relays it out-of-band; no in-platform outbound mail yet (documented). Runs post-save so
        // these flushes don't re-UPDATE a recipient row mid-mutation.
        for (WorkflowRequestRecipient rec : freshExternals) {
            String link = "/public/workflow-requests/" + rec.getAccessToken();
            appendEvent(r, "EXTERNAL_LINK", r.getStatus(), r.getStatus(),
                "External access issued for " + (rec.getRecipientKey() == null ? "external recipient" : rec.getRecipientKey())
                    + " → " + link + " (expires " + rec.getTokenExpiresAt().toLocalDate() + ")");
            if (r.getInitiatedBy() != null)
                notifications.notifyUser(r.getOrganisationId(), r.getInitiatedBy(),
                    "External recipient tracking link ready", "رابط تتبّع المستلم الخارجي جاهز",
                    code(r) + " — relay this link to " + rec.getRecipientKey() + ": " + link,
                    "WORKFLOW_REQUEST_EXTERNAL_LINK", "WorkflowRequest", r.getId());
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
        requireActor(namedActor != null ? Set.of(namedActor) : Set.of(), r, action);
    }

    /**
     * Actor gate over a <b>set</b> of designated actors.
     *
     * <p>F2-86, second half. {@code approve()} resolved its gate as {@code approverId} only, while
     * {@code create()} accepts an APPROVAL request gated on an {@code actorRole=APPROVER} recipient
     * instead. On that shape the fallback below handed the gate to the <em>initiator</em>, so the
     * clean-tenant probe measured the four-eyes rule running backwards: the designated approver got
     * <b>400 "Only the designated actor may approve"</b> and the person who raised the request got
     * <b>200 APPROVED</b>. A validation rule that blesses two gates and an enforcement point that
     * honours one is worse than having only one gate — the request looks controlled and is not.
     *
     * <p>An empty gate keeps the original meaning: nobody was designated, so the initiator drives
     * their own request (a NOTIFICATION or ACK closing out). {@code create()} forbids that shape for
     * APPROVAL requests.
     */
    private void requireActor(Set<Long> gate, WorkflowRequest r, String action) {
        Long userId = CurrentActor.userId();
        if (isPlatformAdmin()) return;
        if (gate.isEmpty()) {
            Long fallback = r.getInitiatedBy();
            if (fallback == null || !fallback.equals(userId))
                throw new IllegalArgumentException("Only the designated actor may " + action + " this request");
            return;
        }
        if (userId == null || !gate.contains(userId))
            throw new IllegalArgumentException("Only the designated actor may " + action + " this request");
        // Four-eyes backstop for rows that predate the create-time rule above. A 409, because an SoD
        // refusal is a conflict with the request's own history, not a malformed call.
        if (userId.equals(r.getInitiatedBy()))
            throw new IllegalStateException(
                "Four-eyes: the initiator of this request may not " + action + " it");
    }

    /**
     * Everyone entitled to act on the approval gate: the named approver, else the named reviewer,
     * else the users carried by {@code APPROVER} recipient rows (F2-86).
     */
    private Set<Long> approvalGate(WorkflowRequest r) {
        if (r.getApproverId() != null) return Set.of(r.getApproverId());
        if (r.getReviewerId() != null) return Set.of(r.getReviewerId());
        return gateFromRecipients(r, "APPROVER");
    }

    /** The review gate: the named reviewer, else {@code REVIEWER} recipient rows. */
    private Set<Long> reviewGate(WorkflowRequest r) {
        if (r.getReviewerId() != null) return Set.of(r.getReviewerId());
        return gateFromRecipients(r, "REVIEWER");
    }

    private Set<Long> gateFromRecipients(WorkflowRequest r, String actorRole) {
        Set<Long> out = new LinkedHashSet<>();
        for (WorkflowRequestRecipient x : recipientRepo
                .findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId())) {
            if ("USER".equals(x.getRecipientType()) && actorRole.equals(x.getActorRole())
                    && x.getRecipientId() != null)
                out.add(x.getRecipientId());
        }
        return out;
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

    /** WF-8/WF-9 — assemble the effective recipient set: explicit rows + an expanded saved
     *  distribution list, then optionally materialize COMMITTEE rows into per-active-member USER
     *  gates (so QUORUM/MAJORITY completion counts real members). */
    private List<RecipientDto> buildRecipients(CreateRequest dto, Long org) {
        List<RecipientDto> base = new java.util.ArrayList<>();
        if (dto.recipients() != null) base.addAll(dto.recipients());
        if (dto.recipientListId() != null) base.addAll(recipientLists.expand(dto.recipientListId(), org));
        if (!Boolean.TRUE.equals(dto.expandCommittees())) return base;

        List<RecipientDto> out = new java.util.ArrayList<>();
        for (RecipientDto in : base) {
            if ("COMMITTEE".equalsIgnoreCase(in.recipientType() == null ? "" : in.recipientType()) && in.recipientId() != null) {
                List<RecipientDto> members = committeeMembers.findActiveByCommittee(org, in.recipientId()).stream()
                    .map(CommitteeMemberTerm::getMemberUserId).filter(Objects::nonNull).distinct()
                    .map(uid -> new RecipientDto("USER", uid, null, null, upperOr(in.actorRole(), "ASSIGNEE"), in.stepNo()))
                    .toList();
                if (members.isEmpty()) out.add(in);   // no linked-user members → keep the committee row
                else out.addAll(members);
            } else {
                out.add(in);
            }
        }
        return out;
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
                    x.getRespondedAt(), x.getRespondedBy(), x.getResponse(), x.getResponseOutcome()))
                .toList();
        return new RequestResponse(r.getId(), code(r),
            r.getSourceModule(), r.getSourceRecordType(), r.getSourceRecordId(),
            r.getRequestType(), r.getTitleEn(), r.getTitleAr(), r.getDescription(),
            r.getInitiatedBy(), r.getInitiatedByName(),
            r.getRecipientCategory(), r.getRoutingMode(), r.getCompletionRule(), r.getCompletionThreshold(), r.getCurrentStep(),
            r.getRequiredAction(), r.getDueDate(), r.getSlaHours(),
            countdown, hoursRemaining,
            r.getPriority(), r.getSeverity(),
            r.getReminderDaysBefore(), r.getLastReminderAt(), r.getReminderCount(),
            r.getEscalationRole(), r.getEscalationUserId(), r.getEscalatedAt(),
            r.getEscalationLevel(), readTiers(r.getEscalationTiers()),
            r.getStatus(), r.getResponseRequired(), r.getEvidenceRequired(), r.getClosureCondition(),
            r.getReviewerId(), r.getApproverId(), r.getEvidenceRequestId(),
            r.getClosedAt(), r.getReopenedAt(),
            r.getCreatedAt(), r.getUpdatedAt(),
            recipients,
            readRules(r.getBranchRules()));
    }

    /** WF-1 exposes routing internals to the WF-2 SLA/reminder/escalation sweep. */
    List<WorkflowRequestRecipient> recipientsOf(WorkflowRequest r) {
        return recipientRepo.findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId());
    }
}
