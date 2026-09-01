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
        // CY-H — title_en is NOT NULL; reject a blank/empty body with 400 not a DB 500.
        ap.setTitleEn(CyberInputValidation.requireText(ap.getTitleEn(), "titleEn"));
        ap.setOrganisationId(orgId);
        ap.setIsDeleted(false);
        // F2-329 — the escalation + reminder trail (escalatedAt / lastReminderAt / reminderCount) is
        // the scheduler's (CY-E) and completedAt is the complete route's; a create owns none of them.
        // The controller binds the entity, so without this a caller could POST escalatedAt already set
        // — which the overdue sweep reads as "already escalated once" and so NEVER escalates the plan
        // (CY-E) — or a reminderCount/lastReminderAt chase history that never happened, or be born
        // COMPLETED with a forged completedAt, skipping completeActionPlan. F2-213 scrubbed exactly
        // these on the UPDATE path (see applyEditable) and left the create — the two-of-N; F2-328 is
        // the same shape on the vuln-exception register. The 5-state lifecycle is undesigned (no CHECK,
        // no machine — F2-221), so only the terminal COMPLETED is withheld; the review states stay
        // caller-settable.
        if ("COMPLETED".equalsIgnoreCase(ap.getWorkflowStatus()))
            throw new IllegalArgumentException(
                "workflowStatus: an action plan cannot be created already COMPLETED — completion goes "
                + "through POST /api/v1/cyber/action-plans/{id}/complete, which stamps the completion date.");
        ap.setEscalatedAt(null);
        ap.setLastReminderAt(null);
        ap.setReminderCount(0);
        ap.setCompletedAt(null);
        return actionPlanRepo.save(ap);
    }

    /**
     * F2-213 — the fields an operator can actually edit through this endpoint.
     *
     * Persisting the REQUEST OBJECT erased everything `CyberActionPlanRequest` does not
     * declare: source_id / source_ref (the finding this plan answers), linked_control_id,
     * validation_notes, completed_at, and the escalation trail — escalated_at nulled,
     * reminder_count and last_reminder_at reset to their initialisers, so a plan that had
     * been chased 14 times read as never chased. Measured on 13 live rows, dev = prod:
     * source_ref on 17, linked_control_id on 14, reminder_count > 0 on 14, escalated_at
     * on 5.
     *
     * Editable set == `CyberActionPlanRequest`; the reminder and escalation columns are
     * written by the scheduler, and the source link at creation.
     */
    private void applyEditable(CyberActionPlan existing, CyberActionPlan req) {
        existing.setTitleEn(req.getTitleEn());
        existing.setTitleAr(req.getTitleAr());
        existing.setDescriptionEn(req.getDescriptionEn());
        existing.setSourceType(req.getSourceType());
        existing.setPriority(req.getPriority());
        existing.setOwnerName(req.getOwnerName());
        existing.setDueDate(req.getDueDate());
        existing.setWorkflowStatus(req.getWorkflowStatus());
        existing.setProgressPct(req.getProgressPct());
        existing.setCompletionEvidence(req.getCompletionEvidence());
    }

    @Transactional
    public CyberActionPlan updateActionPlan(Long id, CyberActionPlan req) {
        CyberActionPlan existing = getActionPlan(id);
        applyEditable(existing, req);
        return actionPlanRepo.save(existing);
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
