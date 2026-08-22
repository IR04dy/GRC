package com.tss.grc.module.approvals.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.security.CurrentActor;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.approvals.dto.ApprovalRequestDto;
import com.tss.grc.module.approvals.entity.ApprovalRequest;
import com.tss.grc.module.approvals.repository.ApprovalRequestRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import com.tss.grc.common.util.ExternalIds;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.tss.grc.common.exception.DomainAccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Approvals service — universal approval-request facility.
 *
 * State machine:
 *   PENDING   → APPROVED, REJECTED, DELEGATED, EXPIRED
 *   DELEGATED → PENDING        (with new assignee)
 *   APPROVED, REJECTED, EXPIRED → terminal
 *
 * <p>Wired into the F7 (audit log) + F8 (automation rules) loops as
 * service #9 in the parity arc. Every mutation writes an audit row
 * AND publishes a canonical {@link DomainEvent}:
 *
 *   - APPROVAL_CREATED   — new approval request opened
 *   - APPROVAL_DECIDED   — APPROVED or REJECTED (payload carries
 *                          previousStatus + decision + entityType so
 *                          rules can target specific entity domains)
 *   - APPROVAL_DELEGATED — re-assigned, with delegatedFrom +
 *                          newAssignedTo
 *   - APPROVAL_EXPIRED   — SLA timed out without a decision
 *
 * <p>Audit + emit live inside the {@code @Transactional} methods so
 * commit-or-rollback atomicity holds.
 */
@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "PENDING",   Set.of("APPROVED", "REJECTED", "DELEGATED", "EXPIRED"),
        "DELEGATED", Set.of("PENDING")
    );

    private final ApprovalRequestRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    // ── F8 payload + emit helpers ─────────────────────────────────────────────

    private Map<String, Object> payloadFor(ApprovalRequest r, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("approvalId",      nullSafeStr(r.getId()));
        m.put("externalId",      nullSafeStr(r.getExternalId()));
        m.put("entityType",      nullSafeStr(r.getEntityType()));
        m.put("entityId",        nullSafeStr(r.getEntityId()));
        m.put("status",          nullSafeStr(r.getStatus()));
        m.put("previousStatus",  nullSafeStr(previousStatus));
        m.put("priority",        nullSafeStr(r.getPriority()));
        m.put("requestedBy",     nullSafeStr(r.getRequestedBy()));
        m.put("assignedTo",      nullSafeStr(r.getAssignedTo()));
        m.put("originalAssignedTo", nullSafeStr(r.getOriginalAssignedTo()));
        m.put("delegatedFrom",   nullSafeStr(r.getDelegatedFrom()));
        m.put("slaDeadline",     nullSafeStr(r.getSlaDeadline()));
        return m;
    }

    /** Returns "" for null so the rule engine's equality matcher cannot NPE. */
    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }

    private void emit(Long orgId, String trigger, ApprovalRequest r, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            orgId, trigger, "ApprovalRequest", r.getId(),
            "approval:" + r.getExternalId(), payloadFor(r, previousStatus)));
    }

    /**
     * The principal raising this approval request (F2-226).
     *
     * <p>Refuses rather than storing null. That is safe precisely because the field was
     * already mandatory — {@code @NotBlank} on the DTO — so no request that works today
     * starts failing (F2-182: ask what is already refusing). What changes is only WHERE the
     * value comes from.
     *
     * <p>The refusal is not defensive noise either: {@code requestedBy} is what an approver
     * reads to know who asked, and {@code transition()} compares the caller against
     * {@code assignedTo}, so an unattributed request is one nobody can be answerable for.
     */
    private static String requireRaiser() {
        String who = CurrentActor.username();
        if (who == null || who.isBlank()) {
            throw new IllegalStateException(
                "No authenticated principal to record as the raiser of this approval request. "
                + "An approval request with no requester is one nobody can be answerable for.");
        }
        return who;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    public List<ApprovalRequestDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByRequestedAtDesc(orgId).stream()
            .map(ApprovalRequestDto::from).collect(Collectors.toList());
    }

    public List<ApprovalRequestDto> myInbox(String username) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndAssignedToAndStatus(orgId, username, "PENDING").stream()
            .map(ApprovalRequestDto::from).collect(Collectors.toList());
    }

    public ApprovalRequestDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(ApprovalRequestDto::from)
            .orElseThrow(() -> new EntityNotFoundException("Approval request not found: " + externalId));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public ApprovalRequestDto create(ApprovalRequestDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        ApprovalRequest r = new ApprovalRequest();
        // ⚠⚠ F2-206. The caller's `id` is IGNORED — the reference is minted here, always.
        //
        // This was `req.getId() != null ? req.getId() : generateExternalId()`: F2-172(d)'s
        // defect for the SIXTH time, after the intake substrate, F2-202's DSR cases, F2-203's
        // audit findings, F2-204's ERM treatment plans and F2-205's vulnerabilities. The
        // external id is what an approver cites when they say which request they decided, so a
        // client that chooses it can make two requests indistinguishable in an audit trail, or
        // collide with a reference already issued.
        //
        // 🔑 Found by grepping `req.getId() != null ?` before assuming the family was closed —
        // which CLAUDE.md instructs and which is now three slices for three finds.
        r.setExternalId(generateExternalId());
        r.setOrganisationId(orgId);
        r.setEntityType(req.getEntityType());
        r.setEntityId(req.getEntityId());
        r.setTitleEn(req.getTitleEn());
        r.setTitleAr(req.getTitleAr());
        r.setSummaryEn(req.getSummaryEn());
        r.setSummaryAr(req.getSummaryAr());

        // ⚠⚠ F2-226. `requestedBy` is the AUTHENTICATED PRINCIPAL, never the body.
        //
        // This was `r.setRequestedBy(req.getRequestedBy())` — with `@NotBlank` on the DTO,
        // so the server REQUIRED an attribution it never verified (the fifth instance of
        // that shape, after F2-223's IIA annual plan, F2-224's obligation attestation and
        // review, and this slice's exercise response). Meanwhile `requested_at` is a FIELD
        // INITIALISER on the entity — `= LocalDateTime.now()` — so the server owns WHEN with
        // no `setRequestedAt(now())` anywhere for a detector to find. F2-223's rule: by
        // stamping the time itself the server has already declared that it is the recorder
        // of this act, and taking WHO from the caller splits one attribution across two
        // owners. F2-215's third of four filling mechanisms is what hid it.
        //
        // Probed live pre-fix as `viewer` (holds WORKFLOW:WRITE, NOT WORKFLOW:APPROVE):
        // `{"requestedBy": "Nobody Who Raised This"}` was stored verbatim, beside a server
        // clock, on an approval request for a POLICY publication.
        //
        // 🔑 The rule was already written down IN THE CLIENT. `lib/approvalsApi.ts` says of
        // the transition payload: "There is no `decidedBy` here on purpose. The server takes
        // the deciding identity from the authenticated principal (SEC-IAM-002) — sending one
        // would be a field the server ignores." That comment sits one screenful above the
        // `requestedBy: string` this method used to trust. F2-215's headline: generalising an
        // invariant one field at a time leaves the remainder looking covered, because the
        // mechanism that covers them is visibly present.
        r.setRequestedBy(requireRaiser());

        // ⚠⚠ F2-226 leg 2 — four eyes. `assignedTo` is not decoration: it IS the
        // authorisation input `transition()` checks (SEC-IAM-002, `callerUsername.equals(
        // r.getAssignedTo())`). So whoever creates the request decides who may approve it,
        // and until this check existed a WORKFLOW:WRITE+APPROVE holder could raise a request
        // to themselves and approve it — probed live: `compliance` did exactly that, 200 then
        // 200, with `decided_by` correctly recording them both times. The audit trail told
        // the truth about who acted while nothing stopped them (F2-222's leg 2).
        //
        // Compared case-INSENSITIVELY on purpose: the column collates CI (F2-179), so
        // `Compliance` and `compliance` are one principal to the database and a
        // case-sensitive Java `equals` would let a capitalisation walk through the gate.
        // ⚠ `IllegalStateException` → 409, deliberately, and it was MEASURED rather than picked.
        // My first draft threw DomainAccessDeniedException (403); the live probe reported the
        // mismatch and reading the tree settled it. `NdiEvidenceItemService.approve` — the
        // SEC-NDI-003 maker/checker control, the one real self-approval gate in the platform —
        // throws IllegalStateException with a "Segregation of duties:" message, which the handler
        // maps to CONFLICT, and CLAUDE.md's convention says the same: a four-eyes gate is a
        // server-enforced 409. 403 would be wrong in substance too: the caller IS entitled to
        // raise approval requests, so this is a conflict with a rule, not a missing permission.
        // (`BusinessRuleException`'s javadoc claims SoD is 403 — it is used ONCE in all of
        // module/, so it is the outlier and not the convention. F2-205: consistency among copies
        // is not evidence; ask which one the platform actually runs.)
        String assignee = req.getAssignedTo();
        if (assignee != null && assignee.trim().equalsIgnoreCase(r.getRequestedBy())) {
            throw new IllegalStateException(
                "Segregation of duties: an approval request may not be assigned to the person "
                + "raising it. " + r.getRequestedBy() + " would be the only principal permitted "
                + "to decide it, which is a formality rather than an approval. Assign it to a "
                + "reviewer.");
        }
        r.setAssignedTo(assignee);
        r.setOriginalAssignedTo(assignee);
        r.setPriority(req.getPriority());
        r.setSlaDeadline(req.getSlaDeadline());
        r.setStatus("PENDING");
        r = repo.save(r);
        // Every value here is read off the SAVED ROW, never off `req` — F2-223 found audit
        // lines quoting the caller's own claim, which is what makes a forged attribution
        // invisible to whoever audits it.
        audit.log("CREATE", "ApprovalRequest", r.getId(),
            "Approval requested: " + nullSafe(r.getExternalId())
                + " on " + nullSafe(r.getEntityType()) + " "
                + nullSafe(r.getEntityId()) + " by " + nullSafe(r.getRequestedBy())
                + " (assigned to " + nullSafe(r.getAssignedTo()) + ")");
        emit(orgId, "APPROVAL_CREATED", r, null);
        ApprovalRequestDto out = ApprovalRequestDto.from(r);
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "approval", r.getExternalId(), out);
        return out;
    }

    /**
     * Apply an approval-request state transition.
     *
     * <p>SEC-IAM-002 hardening: the transition is bound to the authenticated
     * principal. Only the user the request is currently {@code assignedTo}
     * (or a PLATFORM_ADMIN) may move it to APPROVED / REJECTED / DELEGATED;
     * the decider attribution is taken from {@code callerUsername}, never from
     * the request body (the body's {@code decidedBy} is ignored).
     *
     * @param callerUsername the authenticated principal's username (auth.getName())
     * @param callerIsAdmin  true when the caller holds ROLE_PLATFORM_ADMIN
     */
    @Transactional
    public ApprovalRequestDto transition(String externalId, ApprovalRequestDto.TransitionRequest req,
                                         String callerUsername, boolean callerIsAdmin) {
        Long orgId = TenantContext.requireOrganisationId();
        ApprovalRequest r = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Approval request not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(r.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), req.getStatus(), allowed);
        }

        // SEC-IAM-002: object-level authorization — only the current assignee may
        // decide/delegate. PLATFORM_ADMIN may override (admin reassignment / cleanup).
        boolean isAssignee = callerUsername != null && callerUsername.equals(r.getAssignedTo());
        if (!isAssignee && !callerIsAdmin) {
            throw new DomainAccessDeniedException(
                "Only the assignee may transition this approval request");
        }

        String previousStatus = r.getStatus();
        LocalDateTime now = LocalDateTime.now();
        r.setStatus(req.getStatus());

        if ("APPROVED".equals(req.getStatus()) || "REJECTED".equals(req.getStatus())) {
            r.setDecidedAt(now);
            // SEC-IAM-002: decider attribution is the authenticated principal — the
            // body's decidedBy is never trusted (non-repudiation of the audit trail).
            r.setDecidedBy(callerUsername);
            if (req.getDecisionCommentEn() != null) r.setDecisionCommentEn(req.getDecisionCommentEn());
            if (req.getDecisionCommentAr() != null) r.setDecisionCommentAr(req.getDecisionCommentAr());
        }
        if ("DELEGATED".equals(req.getStatus())) {
            if (req.getNewAssignedTo() == null) {
                throw new IllegalArgumentException("DELEGATED requires newAssignedTo");
            }
            r.setDelegatedFrom(r.getAssignedTo());
            r.setAssignedTo(req.getNewAssignedTo());
            if (req.getDelegationReasonEn() != null) r.setDelegationReasonEn(req.getDelegationReasonEn());
            // F2-275 - the Arabic half of the same reason, under its OWN null guard so an
            // omitted Arabic reason leaves the stored one alone rather than clearing it.
            if (req.getDelegationReasonAr() != null) r.setDelegationReasonAr(req.getDelegationReasonAr());
        }
        // PENDING (re-pending from DELEGATED) keeps assignedTo as set by the
        // prior DELEGATED transition — no field mutation needed here.
        if ("EXPIRED".equals(req.getStatus())) {
            r.setDecidedAt(now);
        }

        r = repo.save(r);

        // Audit + dispatch the right canonical event for this transition.
        String trigger;
        String message;
        switch (req.getStatus()) {
            case "APPROVED", "REJECTED" -> {
                trigger = "APPROVAL_DECIDED";
                message = "Approval " + req.getStatus().toLowerCase()
                    + (r.getDecidedBy() != null ? " by " + r.getDecidedBy() : "");
            }
            case "DELEGATED" -> {
                trigger = "APPROVAL_DELEGATED";
                message = "Approval delegated " + nullSafe(r.getDelegatedFrom())
                    + " → " + nullSafe(r.getAssignedTo());
            }
            case "EXPIRED" -> {
                trigger = "APPROVAL_EXPIRED";
                message = "Approval expired at " + now;
            }
            default -> {
                // PENDING (re-pending from DELEGATED) — emit a transition event
                // for engine observability but no specific business trigger.
                trigger = "APPROVAL_TRANSITIONED";
                message = "Status " + nullSafe(previousStatus) + " → " + req.getStatus();
            }
        }
        audit.log("TRANSITION", "ApprovalRequest", r.getId(), message);
        emit(orgId, trigger, r, previousStatus);

        ApprovalRequestDto out = ApprovalRequestDto.from(r);
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "approval", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        ApprovalRequest r = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Approval request not found: " + externalId));
        r.setIsDeleted((short) 1);
        repo.save(r);
        audit.log("DELETE", "ApprovalRequest", r.getId(),
            "Approval request soft-deleted: " + nullSafe(r.getExternalId()));
    }

    private String generateExternalId() {
        // F2-48: substring(0, 6) kept the HIGH-ORDER hex digits, so this id changed once
        // per 2^20 ms (~17.5 min) and every APR raised in one window collided on the unique
        // index. Shared generator now, so the tenth copy cannot reintroduce it.
        return ExternalIds.generate("APR");
    }

    /** Human-readable null marker for audit messages (distinct from the
     *  payload-side {@link #nullSafeStr} which returns "" for the rule engine). */
    private static String nullSafe(Object o) { return o == null ? "<not provided>" : o.toString(); }
}
