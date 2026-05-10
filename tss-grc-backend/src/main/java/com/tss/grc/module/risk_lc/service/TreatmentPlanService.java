package com.tss.grc.module.risk_lc.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.platform.realtime.RealtimeBroadcaster;
import jakarta.persistence.EntityNotFoundException;
import com.tss.grc.module.risk_lc.dto.TreatmentPlanDto;
import com.tss.grc.module.risk_lc.entity.TreatmentPlan;
import com.tss.grc.module.risk_lc.repository.TreatmentPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Treatment plan service — guards the state machine + computes derived fields.
 *
 * Allowed transitions (mirrors the UI client logic so they stay in sync):
 *   DRAFT       → SUBMITTED
 *   SUBMITTED   → APPROVED | REJECTED
 *   APPROVED    → IN_PROGRESS
 *   IN_PROGRESS → COMPLETED
 *   REJECTED    → DRAFT
 */
@Service
@RequiredArgsConstructor
public class TreatmentPlanService {

    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "DRAFT",       Set.of("SUBMITTED"),
        "SUBMITTED",   Set.of("APPROVED", "REJECTED"),
        "APPROVED",    Set.of("IN_PROGRESS"),
        "IN_PROGRESS", Set.of("COMPLETED"),
        "REJECTED",    Set.of("DRAFT")
    );

    private final TreatmentPlanRepository repo;

    /** Optional — null in unit tests, auto-wired by Spring at runtime. */
    @Autowired(required = false)
    private RealtimeBroadcaster broadcaster;

    public List<TreatmentPlanDto> listForOrg() {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdOrderByCreatedAtDesc(orgId).stream()
            .map(TreatmentPlanDto::from)
            .collect(Collectors.toList());
    }

    public List<TreatmentPlanDto> listForRisk(Long riskId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(orgId, riskId).stream()
            .map(TreatmentPlanDto::from)
            .collect(Collectors.toList());
    }

    public TreatmentPlanDto get(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        return repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .map(TreatmentPlanDto::from)
            .orElseThrow(() -> new EntityNotFoundException("Treatment plan not found: " + externalId));
    }

    @Transactional
    public TreatmentPlanDto create(TreatmentPlanDto req) {
        Long orgId = TenantContext.requireOrganisationId();
        TreatmentPlan p = new TreatmentPlan();
        p.setExternalId(req.getId() != null ? req.getId() : generateExternalId(orgId));
        p.setOrganisationId(orgId);
        p.setRiskId(req.getRiskId());
        p.setStrategy(req.getStrategy());
        p.setRationaleEn(req.getRationaleEn());
        p.setRationaleAr(req.getRationaleAr());
        p.setCostSarK(req.getCostSarK());
        p.setCostBenefitEn(req.getCostBenefitEn());
        p.setCostBenefitAr(req.getCostBenefitAr());
        p.setStatus("DRAFT");
        return TreatmentPlanDto.from(repo.save(p));
    }

    @Transactional
    public TreatmentPlanDto transition(String externalId, TreatmentPlanDto.TransitionRequest req) {
        Long orgId = TenantContext.requireOrganisationId();
        TreatmentPlan p = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Treatment plan not found: " + externalId));
        Set<String> allowed = ALLOWED.getOrDefault(p.getStatus(), Set.of());
        if (!allowed.contains(req.getStatus())) {
            throw new IllegalTransitionException(p.getStatus(), req.getStatus(), allowed);
        }
        p.setStatus(req.getStatus());
        if ("SUBMITTED".equals(req.getStatus()))   p.setSubmittedAt(LocalDateTime.now());
        if ("APPROVED".equals(req.getStatus())) {
            p.setApprovedBy(req.getApprovedBy());
            p.setApprovedAt(LocalDateTime.now());
        }
        if ("REJECTED".equals(req.getStatus())) {
            p.setRejectedReason(req.getRejectedReason());
        }
        TreatmentPlanDto out = TreatmentPlanDto.from(repo.save(p));
        // Sprint 4 §2.7 — push state-machine transition to subscribed clients
        // (open detail pages, kanban watchers). No-op when broadcaster missing.
        if (broadcaster != null) broadcaster.broadcastEntityUpdate(orgId, "treatment_plan", externalId, out);
        return out;
    }

    @Transactional
    public void softDelete(String externalId) {
        Long orgId = TenantContext.requireOrganisationId();
        TreatmentPlan p = repo.findByOrganisationIdAndExternalId(orgId, externalId)
            .orElseThrow(() -> new EntityNotFoundException("Treatment plan not found: " + externalId));
        p.setIsDeleted((short) 1);
        repo.save(p);
    }

    /** TP-2026-001 generator. */
    private String generateExternalId(Long orgId) {
        long count = repo.findByOrganisationIdOrderByCreatedAtDesc(orgId).size();
        return String.format("TP-%d-%03d", LocalDateTime.now().getYear(), count + 1);
    }
}
