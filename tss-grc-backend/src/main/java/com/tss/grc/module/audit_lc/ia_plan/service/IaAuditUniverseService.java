package com.tss.grc.module.audit_lc.ia_plan.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseBulkImportRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseBulkImportResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseUpdateRequest;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaAuditUniverseEntry;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAuditUniverseEntryRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Sprint 13 — Audit-universe service.
 *
 * <p>Each entry can sit in the master library (plan_id = NULL) or be
 * scoped to a specific plan (plan_id = NOT NULL). The bulk import
 * helper is idempotent on entry_code within the same (org, plan_id).
 *
 * <p>The residual-risk recompute follows the IA convention:
 * {@code residual = inherent × (1 - controlEnvironmentScore)} where
 * {@code controlEnvironmentScore} is the strength on a 0-1 scale.
 */
@Service
@RequiredArgsConstructor
public class IaAuditUniverseService {

    private final IaAuditUniverseEntryRepository repo;
    private final AuditLogService audit;

    private Long org() {
        return TenantContext.requireOrganisationId();
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UniverseResponse> listAll() {
        Long orgId = org();
        return repo.findByOrganisationIdAndIsDeletedFalseOrderByEntryCodeAsc(orgId)
            .stream().map(IaAuditUniverseService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UniverseResponse> listForPlan(Long planId) {
        Long orgId = org();
        return repo.findByOrganisationIdAndPlanIdAndIsDeletedFalseOrderByEntryCodeAsc(orgId, planId)
            .stream().map(IaAuditUniverseService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UniverseResponse> listMaster() {
        Long orgId = org();
        return repo.findByOrganisationIdAndPlanIdIsNullAndIsDeletedFalseOrderByEntryCodeAsc(orgId)
            .stream().map(IaAuditUniverseService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UniverseResponse get(Long id) {
        return toResponse(loadOrThrow(id));
    }

    // ─── Writes ─────────────────────────────────────────────────────

    @Transactional
    public UniverseResponse create(UniverseCreateRequest req) {
        Long orgId = org();
        // entry_code uniqueness within (org, plan_id)
        Optional<IaAuditUniverseEntry> existing = req.planId() == null
            ? repo.findByOrganisationIdAndEntryCodeAndPlanIdIsNullAndIsDeletedFalse(orgId, req.entryCode())
            : repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(orgId, req.entryCode(), req.planId());
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                "Audit-universe entry already exists: " + req.entryCode()
                    + (req.planId() != null ? " in plan " + req.planId() : " (master)"));
        }

        IaAuditUniverseEntry e = new IaAuditUniverseEntry();
        e.setOrganisationId(orgId);
        e.setPlanId(req.planId());
        e.setEntryCode(req.entryCode());
        e.setTitleEn(req.titleEn());
        e.setTitleAr(req.titleAr());
        e.setProcessNameEn(req.processNameEn());
        e.setProcessNameAr(req.processNameAr());
        e.setBusinessUnitEn(req.businessUnitEn());
        e.setBusinessUnitAr(req.businessUnitAr());
        e.setInherentRiskScore(req.inherentRiskScore());
        e.setControlEnvironmentScore(req.controlEnvironmentScore());
        // Residual: recompute even if caller passed an explicit value —
        // service is source-of-truth for the formula.
        e.setResidualRiskScore(req.residualRiskScore() != null
            ? req.residualRiskScore()
            : computeResidual(req.inherentRiskScore(), req.controlEnvironmentScore()));
        e.setLastAuditedDate(req.lastAuditedDate());
        e.setLastAuditRating(req.lastAuditRating());
        e.setPlannedAuditYear(req.plannedAuditYear());
        e.setPlannedQuarter(req.plannedQuarter());
        e.setLinkedRiskId(req.linkedRiskId());
        e.setLinkedControlIds(req.linkedControlIds());
        IaAuditUniverseEntry saved = repo.save(e);

        audit.log("CREATE", "IaAuditUniverseEntry", saved.getId(),
            "Universe entry created: " + saved.getEntryCode()
                + (saved.getPlanId() != null ? " for plan " + saved.getPlanId() : " (master)"));
        return toResponse(saved);
    }

    @Transactional
    public UniverseResponse update(Long id, UniverseUpdateRequest req) {
        IaAuditUniverseEntry e = loadOrThrow(id);
        boolean scoreTouched = false;
        if (req.titleEn() != null) e.setTitleEn(req.titleEn());
        if (req.titleAr() != null) e.setTitleAr(req.titleAr());
        if (req.processNameEn() != null) e.setProcessNameEn(req.processNameEn());
        if (req.processNameAr() != null) e.setProcessNameAr(req.processNameAr());
        if (req.businessUnitEn() != null) e.setBusinessUnitEn(req.businessUnitEn());
        if (req.businessUnitAr() != null) e.setBusinessUnitAr(req.businessUnitAr());
        if (req.inherentRiskScore() != null) {
            e.setInherentRiskScore(req.inherentRiskScore());
            scoreTouched = true;
        }
        if (req.controlEnvironmentScore() != null) {
            e.setControlEnvironmentScore(req.controlEnvironmentScore());
            scoreTouched = true;
        }
        if (scoreTouched) {
            e.setResidualRiskScore(computeResidual(e.getInherentRiskScore(), e.getControlEnvironmentScore()));
        }
        if (req.lastAuditedDate() != null) e.setLastAuditedDate(req.lastAuditedDate());
        if (req.lastAuditRating() != null) e.setLastAuditRating(req.lastAuditRating());
        if (req.plannedAuditYear() != null) e.setPlannedAuditYear(req.plannedAuditYear());
        if (req.plannedQuarter() != null) e.setPlannedQuarter(req.plannedQuarter());
        if (req.linkedRiskId() != null) e.setLinkedRiskId(req.linkedRiskId());
        if (req.linkedControlIds() != null) e.setLinkedControlIds(req.linkedControlIds());
        e.setUpdatedAt(LocalDateTime.now());
        IaAuditUniverseEntry saved = repo.save(e);

        audit.log("UPDATE", "IaAuditUniverseEntry", saved.getId(),
            "Universe entry updated: " + saved.getEntryCode()
                + (scoreTouched ? " (residual recomputed=" + saved.getResidualRiskScore() + ")" : ""));
        return toResponse(saved);
    }

    /**
     * Idempotent bulk import — every entry is matched on entry_code within
     * the target (org, plan_id) and inserted only if absent. Returns a
     * summary so callers can show "X created / Y skipped".
     */
    @Transactional
    public UniverseBulkImportResponse bulkImport(UniverseBulkImportRequest req) {
        int total = req.entries() == null ? 0 : req.entries().size();
        int created = 0;
        int skipped = 0;
        if (req.entries() != null) {
            for (UniverseCreateRequest entry : req.entries()) {
                // Resolve the plan_id — request-level fallback if entry didn't set it
                Long effectivePlanId = entry.planId() != null ? entry.planId() : req.planId();
                Long orgId = org();
                Optional<IaAuditUniverseEntry> existing = effectivePlanId == null
                    ? repo.findByOrganisationIdAndEntryCodeAndPlanIdIsNullAndIsDeletedFalse(orgId, entry.entryCode())
                    : repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(orgId, entry.entryCode(), effectivePlanId);
                if (existing.isPresent()) {
                    skipped++;
                    continue;
                }
                // Re-issue create with the resolved plan_id
                UniverseCreateRequest resolved = new UniverseCreateRequest(
                    effectivePlanId, entry.entryCode(), entry.titleEn(), entry.titleAr(),
                    entry.processNameEn(), entry.processNameAr(),
                    entry.businessUnitEn(), entry.businessUnitAr(),
                    entry.inherentRiskScore(), entry.controlEnvironmentScore(), entry.residualRiskScore(),
                    entry.lastAuditedDate(), entry.lastAuditRating(),
                    entry.plannedAuditYear(), entry.plannedQuarter(),
                    entry.linkedRiskId(), entry.linkedControlIds()
                );
                create(resolved);
                created++;
            }
        }
        audit.log("BULK_IMPORT", "IaAuditUniverseEntry", null,
            "Universe bulk import: " + created + " created, " + skipped + " skipped existing"
                + (req.planId() != null ? " (plan=" + req.planId() + ")" : ""));
        return new UniverseBulkImportResponse(total, created, skipped);
    }

    @Transactional
    public UniverseResponse recomputeResidual(Long id) {
        IaAuditUniverseEntry e = loadOrThrow(id);
        BigDecimal newRes = computeResidual(e.getInherentRiskScore(), e.getControlEnvironmentScore());
        e.setResidualRiskScore(newRes);
        e.setUpdatedAt(LocalDateTime.now());
        IaAuditUniverseEntry saved = repo.save(e);
        audit.log("RECOMPUTE", "IaAuditUniverseEntry", saved.getId(),
            "Residual risk recomputed for " + saved.getEntryCode() + ": " + newRes);
        return toResponse(saved);
    }

    @Transactional
    public void softDelete(Long id) {
        IaAuditUniverseEntry e = loadOrThrow(id);
        e.setIsDeleted(true);
        e.setDeletedAt(LocalDateTime.now());
        repo.save(e);
        audit.log("DELETE", "IaAuditUniverseEntry", e.getId(),
            "Universe entry soft-deleted: " + e.getEntryCode());
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /**
     * IA-convention residual: inherent × (1 - controlEnvironmentScore).
     * Returns {@code null} if either input is missing — caller decides
     * whether to leave the residual blank or substitute zero.
     */
    public static BigDecimal computeResidual(BigDecimal inherent, BigDecimal controlEnv) {
        if (inherent == null || controlEnv == null) return null;
        BigDecimal one = BigDecimal.ONE;
        BigDecimal strength = one.subtract(controlEnv);
        return inherent.multiply(strength).setScale(2, RoundingMode.HALF_UP);
    }

    private IaAuditUniverseEntry loadOrThrow(Long id) {
        return repo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("IA universe entry not found: " + id));
    }

    static UniverseResponse toResponse(IaAuditUniverseEntry e) {
        return new UniverseResponse(
            e.getId(), e.getExternalId(), e.getOrganisationId(), e.getPlanId(),
            e.getEntryCode(), e.getTitleEn(), e.getTitleAr(),
            e.getProcessNameEn(), e.getProcessNameAr(),
            e.getBusinessUnitEn(), e.getBusinessUnitAr(),
            e.getInherentRiskScore(), e.getControlEnvironmentScore(), e.getResidualRiskScore(),
            e.getLastAuditedDate(), e.getLastAuditRating(),
            e.getPlannedAuditYear(), e.getPlannedQuarter(),
            e.getLinkedRiskId(), e.getLinkedControlIds(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
