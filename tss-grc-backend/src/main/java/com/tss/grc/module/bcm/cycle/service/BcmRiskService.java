package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmRisk;
import com.tss.grc.module.bcm.cycle.repository.BcmRiskRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BCM Risk Register service — Sprint BCM-1.
 *
 * <p>Lifecycle: DRAFT → ASSESSED → TREATMENT_PLANNED →
 * (ACCEPTED | MITIGATED) → CLOSED.
 *
 * <p>Scoring math: {@code inherent_risk_score = likelihood × impact}.
 * {@code residual_risk_score} is reduced by {@code control_effectiveness}:
 *   EFFECTIVE → ÷4 · NEEDS_IMPROVEMENT → ÷2 · INEFFECTIVE → ×1 ·
 *   NOT_TESTED → ×1.
 *
 * <p>On {@code ASSESSED → TREATMENT_PLANNED} the service publishes a
 * {@code BCM_RISK_TREATMENT_PLANNED} domain event so the F8
 * automation engine can fan out (rule AR-T1: link to ERM if
 * high/critical).
 */
@Service
@RequiredArgsConstructor
public class BcmRiskService {

    private static final Set<String> STATUSES = Set.of(
        "DRAFT", "ASSESSED", "TREATMENT_PLANNED", "ACCEPTED", "MITIGATED", "CLOSED");
    private static final Set<String> EFFECTIVENESS = Set.of(
        "EFFECTIVE", "NEEDS_IMPROVEMENT", "INEFFECTIVE", "NOT_TESTED");
    private static final Set<String> CATEGORIES = Set.of(
        "TECH_FAILURE", "CYBERATTACK", "DATA_CENTER_OUTAGE", "CLOUD_OUTAGE",
        "TELECOM_OUTAGE", "POWER_FAILURE", "FACILITY_UNAVAILABILITY",
        "WORKFORCE_UNAVAILABILITY", "SUPPLIER_FAILURE", "PANDEMIC",
        "NATURAL_HAZARD", "FIRE", "FLOOD", "REGIONAL_DISRUPTION",
        "REGULATORY_DISRUPTION", "DATA_LOSS", "THIRD_PARTY_DEPENDENCY",
        "CRITICAL_INFRASTRUCTURE");

    private final BcmRiskRepository risks;
    private final AuditLogService audit;
    private final ApplicationEventPublisher events;

    private Long org() { return TenantContext.getCurrentTenant(); }

    @Transactional(readOnly = true)
    public List<BcmRisk> list(Long cycleId, Long serviceId, String status) {
        Long o = org();
        if (cycleId != null)   return risks.findByOrganisationIdAndCycleIdOrderByIdDesc(o, cycleId);
        if (serviceId != null) return risks.findByOrganisationIdAndRelatedServiceIdOrderByIdDesc(o, serviceId);
        if (status != null && !status.isBlank())
            return risks.findByOrganisationIdAndStatusOrderByIdDesc(o, status);
        return risks.findByOrganisationIdOrderByIdDesc(o);
    }

    @Transactional(readOnly = true)
    public BcmRisk get(Long id) {
        return risks.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("BCM risk not found: " + id));
    }

    @Transactional
    public BcmRisk create(BcmRisk body) {
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getThreatCategory() == null || !CATEGORIES.contains(body.getThreatCategory()))
            throw new IllegalArgumentException("threatCategory must be one of " + CATEGORIES);
        validateScale("likelihood", body.getLikelihood());
        validateScale("impact",     body.getImpact());
        if (body.getControlEffectiveness() != null && !EFFECTIVENESS.contains(body.getControlEffectiveness()))
            throw new IllegalArgumentException("controlEffectiveness must be one of " + EFFECTIVENESS);
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getStatus() == null) body.setStatus("DRAFT");
        if (!STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid status: " + body.getStatus());
        recalcScores(body);
        BcmRisk saved = risks.save(body);
        auditEvent(saved, "CREATE", null, saved.getStatus(),
            "BCM risk created: " + saved.getTitleEn());
        return saved;
    }

    @Transactional
    public BcmRisk update(Long id, BcmRisk body) {
        BcmRisk r = get(id);
        if ("CLOSED".equals(r.getStatus()))
            throw new IllegalStateException("Cannot edit a CLOSED BCM risk");
        if (body.getTitleEn() != null)            r.setTitleEn(body.getTitleEn());
        if (body.getTitleAr() != null)            r.setTitleAr(body.getTitleAr());
        if (body.getCause() != null)              r.setCause(body.getCause());
        if (body.getEvent() != null)              r.setEvent(body.getEvent());
        if (body.getConsequence() != null)        r.setConsequence(body.getConsequence());
        if (body.getThreatCategory() != null) {
            if (!CATEGORIES.contains(body.getThreatCategory()))
                throw new IllegalArgumentException("Invalid threat category");
            r.setThreatCategory(body.getThreatCategory());
        }
        if (body.getExistingControls() != null)   r.setExistingControls(body.getExistingControls());
        if (body.getLikelihood() != null) {
            validateScale("likelihood", body.getLikelihood());
            r.setLikelihood(body.getLikelihood());
        }
        if (body.getImpact() != null) {
            validateScale("impact", body.getImpact());
            r.setImpact(body.getImpact());
        }
        if (body.getControlEffectiveness() != null) {
            if (!EFFECTIVENESS.contains(body.getControlEffectiveness()))
                throw new IllegalArgumentException("Invalid control effectiveness");
            r.setControlEffectiveness(body.getControlEffectiveness());
        }
        if (body.getRiskOwnerId() != null)        r.setRiskOwnerId(body.getRiskOwnerId());
        if (body.getTreatmentPlan() != null)      r.setTreatmentPlan(body.getTreatmentPlan());
        if (body.getRelatedBiaId() != null)       r.setRelatedBiaId(body.getRelatedBiaId());
        if (body.getRelatedBcpId() != null)       r.setRelatedBcpId(body.getRelatedBcpId());
        if (body.getRelatedDrpId() != null)       r.setRelatedDrpId(body.getRelatedDrpId());
        if (body.getRelatedErmRiskId() != null)   r.setRelatedErmRiskId(body.getRelatedErmRiskId());
        if (body.getRelatedServiceId() != null)   r.setRelatedServiceId(body.getRelatedServiceId());
        if (body.getRelatedProcessId() != null)   r.setRelatedProcessId(body.getRelatedProcessId());
        if (body.getRelatedAssetId() != null)     r.setRelatedAssetId(body.getRelatedAssetId());
        if (body.getRelatedSupplierId() != null)  r.setRelatedSupplierId(body.getRelatedSupplierId());
        recalcScores(r);
        r.setUpdatedAt(LocalDateTime.now());
        return risks.save(r);
    }

    /** Recalculate inherent + residual scores from the current row. */
    public BcmRisk recalcScores(BcmRisk r) {
        if (r.getLikelihood() != null && r.getImpact() != null) {
            int inherent = r.getLikelihood() * r.getImpact();
            r.setInherentRiskScore(inherent);
            int residual = switch (r.getControlEffectiveness() == null ? "" : r.getControlEffectiveness()) {
                case "EFFECTIVE"          -> Math.max(1, inherent / 4);
                case "NEEDS_IMPROVEMENT"  -> Math.max(1, inherent / 2);
                case "INEFFECTIVE"        -> inherent;
                case "NOT_TESTED"         -> inherent;
                default                   -> inherent;
            };
            r.setResidualRiskScore(residual);
        }
        return r;
    }

    /** DRAFT → ASSESSED → TREATMENT_PLANNED → (ACCEPTED|MITIGATED) → CLOSED. */
    @Transactional
    public BcmRisk transition(Long id, String to, String actor) {
        BcmRisk r = get(id);
        if (!STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = r.getStatus();
        boolean ok = switch (from) {
            case "DRAFT"             -> "ASSESSED".equals(to);
            case "ASSESSED"          -> "TREATMENT_PLANNED".equals(to) || "ACCEPTED".equals(to);
            case "TREATMENT_PLANNED" -> "MITIGATED".equals(to) || "ACCEPTED".equals(to);
            case "ACCEPTED"          -> "CLOSED".equals(to);
            case "MITIGATED"         -> "CLOSED".equals(to);
            default                  -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        if ("ASSESSED".equals(to) && (r.getLikelihood() == null || r.getImpact() == null))
            throw new IllegalArgumentException("Cannot move to ASSESSED without likelihood and impact");
        r.setStatus(to);
        r.setUpdatedAt(LocalDateTime.now());
        BcmRisk saved = risks.save(r);
        auditEvent(saved, "TRANSITION", from, to,
            "BCM risk " + saved.getId() + " " + from + " → " + to);
        if ("TREATMENT_PLANNED".equals(to)) {
            // Fan-out for AR-T1 (high/critical residual → link to ERM).
            events.publishEvent(DomainEvent.of(
                saved.getOrganisationId(),
                "BCM_RISK_TREATMENT_PLANNED",
                "BcmRisk",
                saved.getId(),
                Map.of(
                    "residual_risk_score", saved.getResidualRiskScore() == null ? 0 : saved.getResidualRiskScore(),
                    "linked_erm", saved.getRelatedErmRiskId() != null,
                    "actor", actor == null ? "" : actor
                )
            ));
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        BcmRisk r = get(id);
        if ("ASSESSED".equals(r.getStatus()) || "TREATMENT_PLANNED".equals(r.getStatus()))
            throw new IllegalStateException("Cannot delete an active BCM risk — move to CLOSED first");
        r.setIsDeleted(true);
        risks.save(r);
        auditEvent(r, "DELETE", r.getStatus(), null, "BCM risk " + r.getId() + " soft-deleted");
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private static void validateScale(String name, Integer v) {
        if (v == null) return;
        if (v < 1 || v > 5) throw new IllegalArgumentException(name + " must be 1..5, got " + v);
    }

    private void auditEvent(BcmRisk r, String action, String before, String after, String message) {
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType(action)
            .entityType("BcmRisk")
            .entityId(r.getId())
            .entityCode(r.getTitleEn())
            .workflowStatusBefore(before)
            .workflowStatusAfter(after)
            .sourceModule("bcm")
            .message(message));
    }
}
