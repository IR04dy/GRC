package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.ContinuityStrategy;
import com.tss.grc.module.bcm.cycle.repository.ContinuityStrategyRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Continuity Strategy service — Sprint BCM-1.
 *
 * <p>Approval lifecycle: DRAFT → UNDER_REVIEW → APPROVED | REJECTED →
 * ARCHIVED. Implementation lifecycle (independent):
 * NOT_STARTED → IN_PROGRESS → COMPLETED | ON_HOLD.
 *
 * <p>Rule: a strategy cannot be APPROVED if neither RTO requirement
 * nor RPO requirement is provided (the strategy must commit to a
 * recovery target). High-cost strategies (≥ 1,000,000) require an
 * executive approver — captured in audit metadata.
 */
@Service
@RequiredArgsConstructor
public class ContinuityStrategyService {

    private static final Set<String> APPROVAL_STATUSES =
        Set.of("DRAFT", "UNDER_REVIEW", "APPROVED", "REJECTED", "ARCHIVED");
    /** F2-220 — the approval state a strategy is CREATED in; APPROVED is
     *  transitionApproval()'s, which refuses a strategy with no RTO or RPO. */
    private static final String INITIAL_APPROVAL_STATUS = "DRAFT";
    private static final Set<String> IMPL_STATUSES =
        Set.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "ON_HOLD");
    private static final Set<String> STRATEGIES = Set.of(
        "ACTIVE_ACTIVE", "ACTIVE_PASSIVE", "BACKUP_SITE", "ALTERNATE_WORKPLACE",
        "REMOTE_WORK", "MANUAL_WORKAROUND", "ALTERNATE_SUPPLIER", "STOCKPILING",
        "CLOUD_FAILOVER", "DATA_REPLICATION", "MUTUAL_AID", "PROCESS_TRANSFER",
        "WORKFORCE_CROSS_TRAINING", "EMERGENCY_PROCUREMENT", "CRISIS_COMMUNICATION");
    private static final Set<String> FEASIBILITY = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> RESIDUAL = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private static final java.math.BigDecimal HIGH_COST_THRESHOLD = new java.math.BigDecimal("1000000");

    private final ContinuityStrategyRepository strategies;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    @Transactional(readOnly = true)
    public List<ContinuityStrategy> list(Long cycleId, Long serviceId, String approvalStatus) {
        Long o = org();
        if (cycleId != null)   return strategies.findByOrganisationIdAndCycleIdOrderByIdDesc(o, cycleId);
        if (serviceId != null) return strategies.findByOrganisationIdAndRelatedServiceIdOrderByIdDesc(o, serviceId);
        if (approvalStatus != null && !approvalStatus.isBlank())
            return strategies.findByOrganisationIdAndApprovalStatusOrderByIdDesc(o, approvalStatus);
        return strategies.findByOrganisationIdOrderByIdDesc(o);
    }

    @Transactional(readOnly = true)
    public ContinuityStrategy get(Long id) {
        return strategies.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Continuity strategy not found: " + id));
    }

    @Transactional
    public ContinuityStrategy create(ContinuityStrategy body) {
        if (body.getSelectedStrategy() == null || !STRATEGIES.contains(body.getSelectedStrategy()))
            throw new IllegalArgumentException("selectedStrategy must be one of " + STRATEGIES);
        if (body.getFeasibility() != null && !FEASIBILITY.contains(body.getFeasibility()))
            throw new IllegalArgumentException("feasibility must be HIGH/MEDIUM/LOW");
        if (body.getResidualRisk() != null && !RESIDUAL.contains(body.getResidualRisk()))
            throw new IllegalArgumentException("residualRisk must be LOW/MEDIUM/HIGH/CRITICAL");
        if (body.getRtoRequirementHours() != null && body.getRtoRequirementHours() < 0)
            throw new IllegalArgumentException("rtoRequirementHours cannot be negative");
        if (body.getRpoRequirementHours() != null && body.getRpoRequirementHours() < 0)
            throw new IllegalArgumentException("rpoRequirementHours cannot be negative");
        body.setId(null);
        body.setOrganisationId(org());
        // F2-220 — a DEFAULT, not a guard: the membership test admitted APPROVED, so a strategy
        // could be created already approved — defeating the one thing transitionApproval() checks
        // on the way in, that a strategy has an RTO or an RPO. An approved continuity strategy
        // with no recovery objective is the case its own guard comment names.
        body.setApprovalStatus(ServerOwnedState.requireInitial(
            body.getApprovalStatus(), INITIAL_APPROVAL_STATUS, "a continuity strategy",
            "POST /api/v1/bcm/strategies/{id}/transition"));
        if (body.getImplementationStatus() != null && !IMPL_STATUSES.contains(body.getImplementationStatus()))
            throw new IllegalArgumentException("Invalid implementation status: " + body.getImplementationStatus());
        ContinuityStrategy saved = strategies.save(body);
        auditEvent(saved, "CREATE", null, saved.getApprovalStatus(),
            "Continuity strategy created: " + saved.getSelectedStrategy());
        return saved;
    }

    @Transactional
    public ContinuityStrategy update(Long id, ContinuityStrategy body) {
        ContinuityStrategy s = get(id);
        if ("APPROVED".equals(s.getApprovalStatus()) || "ARCHIVED".equals(s.getApprovalStatus()))
            throw new IllegalStateException("Cannot edit a " + s.getApprovalStatus() + " strategy");
        if (body.getSelectedStrategy() != null) {
            if (!STRATEGIES.contains(body.getSelectedStrategy()))
                throw new IllegalArgumentException("Invalid strategy type");
            s.setSelectedStrategy(body.getSelectedStrategy());
        }
        if (body.getStrategyDescriptionEn() != null) s.setStrategyDescriptionEn(body.getStrategyDescriptionEn());
        if (body.getStrategyDescriptionAr() != null) s.setStrategyDescriptionAr(body.getStrategyDescriptionAr());
        if (body.getCostEstimate() != null) s.setCostEstimate(body.getCostEstimate());
        if (body.getFeasibility() != null) {
            if (!FEASIBILITY.contains(body.getFeasibility()))
                throw new IllegalArgumentException("Invalid feasibility");
            s.setFeasibility(body.getFeasibility());
        }
        if (body.getResidualRisk() != null) {
            if (!RESIDUAL.contains(body.getResidualRisk()))
                throw new IllegalArgumentException("Invalid residual risk");
            s.setResidualRisk(body.getResidualRisk());
        }
        if (body.getRequiredResources() != null) s.setRequiredResources(body.getRequiredResources());
        if (body.getDependencies() != null)      s.setDependencies(body.getDependencies());
        if (body.getOwnerId() != null)           s.setOwnerId(body.getOwnerId());
        if (body.getEvidenceId() != null)        s.setEvidenceId(body.getEvidenceId());
        if (body.getRtoRequirementHours() != null) s.setRtoRequirementHours(body.getRtoRequirementHours());
        if (body.getRpoRequirementHours() != null) s.setRpoRequirementHours(body.getRpoRequirementHours());
        if (body.getImplementationStatus() != null) {
            if (!IMPL_STATUSES.contains(body.getImplementationStatus()))
                throw new IllegalArgumentException("Invalid implementation status");
            s.setImplementationStatus(body.getImplementationStatus());
        }
        s.setUpdatedAt(LocalDateTime.now());
        return strategies.save(s);
    }

    /** DRAFT → UNDER_REVIEW → APPROVED | REJECTED → ARCHIVED. */
    @Transactional
    public ContinuityStrategy transitionApproval(Long id, String to, String actor) {
        ContinuityStrategy s = get(id);
        if (!APPROVAL_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = s.getApprovalStatus();
        boolean ok = switch (from) {
            case "DRAFT"        -> "UNDER_REVIEW".equals(to);
            case "UNDER_REVIEW" -> "APPROVED".equals(to) || "REJECTED".equals(to);
            case "APPROVED"     -> "ARCHIVED".equals(to);
            case "REJECTED"     -> "DRAFT".equals(to) || "ARCHIVED".equals(to);
            default             -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        if ("APPROVED".equals(to)) {
            // A strategy without any recovery target shouldn't be approved.
            if (s.getRtoRequirementHours() == null && s.getRpoRequirementHours() == null)
                throw new IllegalArgumentException(
                    "Strategy cannot be APPROVED without an RTO or RPO requirement");
        }
        s.setApprovalStatus(to);
        s.setUpdatedAt(LocalDateTime.now());
        ContinuityStrategy saved = strategies.save(s);
        String msg = "Continuity strategy " + saved.getId() + " " + from + " → " + to;
        if ("APPROVED".equals(to) && saved.getCostEstimate() != null
            && saved.getCostEstimate().compareTo(HIGH_COST_THRESHOLD) >= 0) {
            msg += " · HIGH_COST — executive approval required (cost=" + saved.getCostEstimate() + ")";
        }
        auditEvent(saved, "TRANSITION", from, to, msg);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ContinuityStrategy s = get(id);
        if ("APPROVED".equals(s.getApprovalStatus()))
            throw new IllegalStateException("Cannot delete an APPROVED strategy — archive it first");
        s.setIsDeleted(true);
        strategies.save(s);
        auditEvent(s, "DELETE", s.getApprovalStatus(), null,
            "Continuity strategy " + s.getId() + " soft-deleted");
    }

    private void auditEvent(ContinuityStrategy s, String action, String before, String after, String message) {
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType(action)
            .entityType("ContinuityStrategy")
            .entityId(s.getId())
            .entityCode(s.getSelectedStrategy())
            .workflowStatusBefore(before)
            .workflowStatusAfter(after)
            .sourceModule("bcm")
            .message(message));
    }
}
