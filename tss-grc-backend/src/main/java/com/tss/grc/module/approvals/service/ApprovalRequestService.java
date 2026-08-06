package com.tss.grc.module.approvals.service;

import com.tss.grc.common.exception.IllegalTransitionException;
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
        r.setExternalId(req.getId() != null ? req.getId() : generateExternalId());
        r.setOrganisationId(orgId);
        r.setEntityType(req.getEntityType());
        r.setEntityId(req.getEntityId());
        r.setTitleEn(req.getTitleEn());
        r.setTitleAr(req.getTitleAr());
        r.setSummaryEn(req.getSummaryEn());
        r.setSummaryAr(req.getSummaryAr());
        r.setRequestedBy(req.getRequestedBy());
        r.setAssignedTo(req.getAssignedTo());
        r.setOriginalAssignedTo(req.getAssignedTo());
        r.setPriority(req.getPriority());
        r.setSlaDeadline(req.getSlaDeadline());
        r.setStatus("PENDING");
        r = repo.save(r);
        audit.log("CREATE", "ApprovalRequest", r.getId(),
            "Approval requested: " + nullSafe(r.getExternalId())
                + " on " + nullSafe(r.getEntityType()) + " "
                + nullSafe(r.getEntityId()) + " (assigned to " + nullSafe(r.getAssignedTo()) + ")");
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
