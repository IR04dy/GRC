package com.tss.grc.module.approvals.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.approvals.dto.ApprovalRequestDto;
import com.tss.grc.module.approvals.entity.ApprovalRequest;
import com.tss.grc.module.approvals.repository.ApprovalRequestRepository;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Approvals service — universal approval-request facility.
 *
 * State machine:
 *   PENDING   → APPROVED, REJECTED, DELEGATED, EXPIRED
 *   DELEGATED → PENDING        (with new assignee)
 *   APPROVED, REJECTED, EXPIRED → terminal
 */
@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "PENDING",   Set.of("APPROVED", "REJECTED", "DELEGATED", "EXPIRED"),
        "DELEGATED", Set.of("PENDING")
    );

    private final ApprovalRequestRepository repo;

    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

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
        ApprovalRequestDto out = ApprovalRequestDto.from(repo.save(r));
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "approval", r.getExternalId(), out);
        return out;
    }

    @Transactional
    public ApprovalRequestDto transition(String externalId, ApprovalRequestDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        ApprovalRequest r = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Approval request not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(r.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(r.getStatus(), req.getStatus(), allowed);
        }
        LocalDateTime now = LocalDateTime.now();
        r.setStatus(req.getStatus());

        if ("APPROVED".equals(req.getStatus()) || "REJECTED".equals(req.getStatus())) {
            r.setDecidedAt(now);
            if (req.getDecidedBy() != null) r.setDecidedBy(req.getDecidedBy());
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
        if ("PENDING".equals(req.getStatus())) {
            // From DELEGATED back to PENDING — assignedTo is already updated by previous DELEGATED.
        }
        if ("EXPIRED".equals(req.getStatus())) {
            r.setDecidedAt(now);
        }

        ApprovalRequestDto out = ApprovalRequestDto.from(repo.save(r));
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
    }

    private String generateExternalId() {
        return String.format("APR-%d-%s",
            LocalDateTime.now().getYear(),
            Long.toHexString(System.currentTimeMillis()).toUpperCase().substring(0, 6));
    }
}
