package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberActionPlan;
import com.tss.grc.module.cyber.repository.CyberActionPlanRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Cyber CAPA / action-plan tracker.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberActionPlanService {

    private final CyberActionPlanRepository actionPlanRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    @Transactional(readOnly = true)
    public Page<CyberActionPlan> getActionPlans(Long orgId, Pageable p) {
        return actionPlanRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public CyberActionPlan getActionPlan(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        return actionPlanRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberActionPlan not found: " + id));
    }

    @Transactional
    public CyberActionPlan saveActionPlan(CyberActionPlan ap, Long orgId) {
        ap.setOrganisationId(orgId);
        ap.setIsDeleted(false);
        return actionPlanRepo.save(ap);
    }

    @Transactional
    public CyberActionPlan updateActionPlan(Long id, CyberActionPlan req) {
        CyberActionPlan existing = getActionPlan(id);
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return actionPlanRepo.save(req);
    }

    @Transactional
    public CyberActionPlan completeActionPlan(Long id) {
        CyberActionPlan ap = getActionPlan(id);
        ap.setWorkflowStatus("COMPLETED");
        ap.setCompletedAt(LocalDate.now());
        ap.setProgressPct(100);
        CyberActionPlan saved = actionPlanRepo.save(ap);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "cyber_action_plan", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public void deleteActionPlan(Long id) {
        CyberActionPlan ap = getActionPlan(id);
        ap.setIsDeleted(true);
        actionPlanRepo.save(ap);
    }
}
