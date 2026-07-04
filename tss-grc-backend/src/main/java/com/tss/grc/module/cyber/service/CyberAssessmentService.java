package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.ControlAssessment;
import com.tss.grc.module.cyber.entity.CyberActionPlan;
import com.tss.grc.module.cyber.repository.ControlAssessmentRepository;
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
 * Control assessments + gap → CAPA conversion.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * {@link #convertGapToCapa} spans both the assessment and action-plan
 * aggregates, so it owns the {@link CyberActionPlanRepository} to keep that
 * write atomic in a single transaction — behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberAssessmentService {

    private final ControlAssessmentRepository assessmentRepo;
    private final CyberActionPlanRepository actionPlanRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    @Transactional(readOnly = true)
    public Page<ControlAssessment> getAssessments(Long orgId, Pageable p) {
        return assessmentRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public ControlAssessment getAssessment(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        return assessmentRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("ControlAssessment not found: " + id));
    }

    @Transactional
    public ControlAssessment saveAssessment(ControlAssessment a, Long orgId) {
        a.setOrganisationId(orgId);
        a.setIsDeleted(false);
        return assessmentRepo.save(a);
    }

    @Transactional
    public ControlAssessment updateAssessment(Long id, ControlAssessment req) {
        ControlAssessment existing = getAssessment(id);
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return assessmentRepo.save(req);
    }

    @Transactional
    public ControlAssessment approveAssessment(Long id) {
        ControlAssessment a = getAssessment(id);
        a.setStatus("APPROVED");
        ControlAssessment saved = assessmentRepo.save(a);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "cyber_assessment", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public CyberActionPlan convertGapToCapa(Long assessmentId, Long orgId) {
        ControlAssessment assessment = getAssessment(assessmentId);
        CyberActionPlan ap = new CyberActionPlan();
        ap.setOrganisationId(orgId);
        ap.setSourceType("ASSESSMENT");
        ap.setSourceId(assessmentId);
        ap.setSourceRef(assessment.getExternalId());
        ap.setTitleEn("Gap Remediation - Assessment " + assessmentId);
        ap.setDescriptionEn(assessment.getGapDescription());
        ap.setLinkedControlId(assessment.getControlId());
        ap.setWorkflowStatus("OPEN");
        // CY-E — route the CAPA: assign an owner + a risk-weighted due date + priority
        // so it lands on someone's plate with a clock, and the SLA sweep can chase it.
        ap.setOwnerName(resolveOwner(assessment));
        ap.setPriority(normalisePriority(assessment.getRiskLevel()));
        ap.setDueDate(LocalDate.now().plusDays(dueDaysForRisk(assessment.getRiskLevel())));
        ap.setIsDeleted(false);
        CyberActionPlan saved = actionPlanRepo.save(ap);
        assessment.setCapaId(saved.getId());
        assessmentRepo.save(assessment);
        if (bcast != null) {
            bcast.entity(saved.getOrganisationId(), "cyber_action_plan", saved.getId(), saved);
            bcast.entity(assessment.getOrganisationId(), "cyber_assessment", assessment.getId(), assessment);
        }
        return saved;
    }

    @Transactional
    public void deleteAssessment(Long id) {
        ControlAssessment a = getAssessment(id);
        a.setIsDeleted(true);
        assessmentRepo.save(a);
    }

    // ── CY-E routing helpers ────────────────────────────────────────────────

    /** Owner = the reviewer who signed the gap off, else the assessor who found
     *  it, else the CISO as the accountable default — never null. */
    private static String resolveOwner(ControlAssessment a) {
        if (a.getReviewerName() != null && !a.getReviewerName().isBlank()) return a.getReviewerName();
        if (a.getAssessorName() != null && !a.getAssessorName().isBlank()) return a.getAssessorName();
        return "CISO";
    }

    /** Normalise the assessment risk level onto the CAPA priority scale. */
    private static String normalisePriority(String riskLevel) {
        if (riskLevel == null) return "MEDIUM";
        String r = riskLevel.trim().toUpperCase().replace(' ', '_');
        return switch (r) {
            case "CRITICAL", "VERY_HIGH" -> "CRITICAL";
            case "HIGH"                  -> "HIGH";
            case "LOW", "VERY_LOW"       -> "LOW";
            default                       -> "MEDIUM";
        };
    }

    /** Risk-weighted remediation window (days) — tighter for higher risk. */
    private static int dueDaysForRisk(String riskLevel) {
        return switch (normalisePriority(riskLevel)) {
            case "CRITICAL" -> 7;
            case "HIGH"     -> 14;
            case "LOW"      -> 60;
            default          -> 30;
        };
    }
}
