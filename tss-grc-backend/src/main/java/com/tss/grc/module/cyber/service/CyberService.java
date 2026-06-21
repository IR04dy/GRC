package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.dto.CyberDashboardDto;
import com.tss.grc.module.cyber.entity.*;
import com.tss.grc.module.cyber.repository.*;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CyberService {

    private final CyberFrameworkRepository frameworkRepo;
    private final CyberAssetRepository assetRepo;
    private final CyberControlRepository controlRepo;
    private final CyberSubControlRepository subControlRepo;
    private final ControlAssessmentRepository assessmentRepo;
    private final VulnerabilityRepository vulnerabilityRepo;
    private final VulnExceptionRepository exceptionRepo;
    private final CyberIncidentRepository incidentRepo;
    private final CyberEvidenceRepository evidenceRepo;
    private final CyberActionPlanRepository actionPlanRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    // ── Vulnerability escalation workflow ────────────────────────────────────
    private static final List<String> VULN_WORKFLOW = List.of(
            "REGISTERED", "CLASSIFICATION", "OWNER_ASSIGNMENT",
            "REMEDIATION", "VERIFICATION", "CLOSED");

    // ── Incident response workflow ───────────────────────────────────────────
    private static final List<String> INCIDENT_WORKFLOW = List.of(
            "DETECTION", "LOGGING", "TRIAGE", "CONTAINMENT",
            "INVESTIGATION", "ERADICATION", "RECOVERY", "CLOSURE", "LESSONS_LEARNED");

    // =========================================================================
    // Dashboard
    // =========================================================================

    @Transactional(readOnly = true)
    public CyberDashboardDto getDashboard(Long orgId) {
        CyberDashboardDto dto = new CyberDashboardDto();

        List<CyberControl> controls = controlRepo.findAllByOrganisationId(orgId);
        dto.setTotalControls(controls.size());
        dto.setCompliantControls((int) controls.stream()
                .filter(c -> "IMPLEMENTED".equalsIgnoreCase(c.getImplementationStatus())).count());
        dto.setNonCompliantControls((int) controls.stream()
                .filter(c -> "NOT_IMPLEMENTED".equalsIgnoreCase(c.getImplementationStatus())).count());
        dto.setPartialControls((int) controls.stream()
                .filter(c -> "PARTIALLY_IMPLEMENTED".equalsIgnoreCase(c.getImplementationStatus())).count());

        List<Vulnerability> vulns = vulnerabilityRepo.findAllByOrganisationId(orgId);
        dto.setOpenVulnerabilities((int) vulns.stream()
                .filter(v -> !"CLOSED".equalsIgnoreCase(v.getWorkflowStatus())).count());
        dto.setCriticalVulnerabilities((int) vulns.stream()
                .filter(v -> "CRITICAL".equalsIgnoreCase(v.getSeverity())).count());
        dto.setOverdueVulnerabilities((int) vulns.stream()
                .filter(v -> Boolean.TRUE.equals(v.getOverdue())).count());

        List<CyberIncident> incidents = incidentRepo.findAllByOrganisationId(orgId);
        dto.setOpenIncidents((int) incidents.stream()
                .filter(i -> !"LESSONS_LEARNED".equalsIgnoreCase(i.getWorkflowStatus())).count());
        dto.setCriticalIncidents((int) incidents.stream()
                .filter(i -> "CRITICAL".equalsIgnoreCase(i.getSeverity())).count());

        List<CyberAsset> assets = assetRepo.findAllByOrganisationId(orgId);
        dto.setTotalAssets(assets.size());
        dto.setCriticalAssets((int) assets.stream()
                .filter(a -> "CRITICAL".equalsIgnoreCase(a.getCriticality())).count());

        List<CyberActionPlan> actionPlans = actionPlanRepo.findAllByOrganisationId(orgId);
        dto.setOpenActionPlans((int) actionPlans.stream()
                .filter(ap -> !"COMPLETED".equalsIgnoreCase(ap.getWorkflowStatus())).count());
        dto.setOverdueActionPlans((int) actionPlans.stream()
                .filter(ap -> ap.getDueDate() != null && ap.getDueDate().isBefore(LocalDate.now())
                        && !"COMPLETED".equalsIgnoreCase(ap.getWorkflowStatus())).count());

        List<CyberEvidence> evidence = evidenceRepo.findAllByOrganisationId(orgId);
        dto.setTotalEvidence(evidence.size());
        dto.setExpiredEvidence((int) evidence.stream()
                .filter(e -> "EXPIRED".equalsIgnoreCase(e.getStatus())).count());
        // Expiring-soon window: evidence with expiryDate today..today+60.
        // Excludes already-EXPIRED rows so the two counts don't double-count.
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(60);
        dto.setExpiringEvidence60d((int) evidence.stream()
                .filter(e -> e.getExpiryDate() != null
                          && !e.getExpiryDate().isBefore(today)
                          && e.getExpiryDate().isBefore(horizon)
                          && !"EXPIRED".equalsIgnoreCase(e.getStatus())).count());

        // Per-framework compliance: % of controls whose status is IMPLEMENTED
        // inside that framework. 0 means no controls assessed yet for the
        // framework (NOT 0% compliant) — frontend tints it differently.
        dto.setEccScore(scoreForFramework(controls, "ECC"));
        dto.setCsccScore(scoreForFramework(controls, "CSCC"));
        dto.setCccScore(scoreForFramework(controls, "CCC"));
        dto.setOtccScore(scoreForFramework(controls, "OTCC"));
        dto.setOverallScore(dto.getTotalControls() == 0
                ? 0
                : Math.round(100f * dto.getCompliantControls() / dto.getTotalControls()));

        Pageable recent = PageRequest.of(0, 5);
        dto.setRecentIncidents(new ArrayList<>(
                incidentRepo.findByOrganisationId(orgId, recent).getContent()));
        dto.setRecentVulnerabilities(new ArrayList<>(
                vulnerabilityRepo.findByOrganisationId(orgId, recent).getContent()));
        dto.setRecentActionPlans(new ArrayList<>(
                actionPlanRepo.findByOrganisationId(orgId, recent).getContent()));

        return dto;
    }

    /** Returns the percentage of controls in this framework marked IMPLEMENTED.
     *  Returns 0 when there are no controls for the framework — caller should
     *  treat 0 as "not started" rather than "0% compliant" since the cycle
     *  may simply not have opened yet. */
    private int scoreForFramework(List<CyberControl> controls, String fwCode) {
        if (fwCode == null || controls == null || controls.isEmpty()) return 0;
        long total = controls.stream()
                .filter(c -> fwCode.equalsIgnoreCase(c.getFrameworkName())
                          || (c.getFrameworkName() != null
                              && c.getFrameworkName().toUpperCase().contains(fwCode.toUpperCase())))
                .count();
        if (total == 0) return 0;
        long compliant = controls.stream()
                .filter(c -> fwCode.equalsIgnoreCase(c.getFrameworkName())
                          || (c.getFrameworkName() != null
                              && c.getFrameworkName().toUpperCase().contains(fwCode.toUpperCase())))
                .filter(c -> "IMPLEMENTED".equalsIgnoreCase(c.getImplementationStatus()))
                .count();
        return Math.round(100f * compliant / total);
    }

    // =========================================================================
    // Frameworks
    // =========================================================================

    @Transactional(readOnly = true)
    public List<CyberFramework> getFrameworks(Long orgId) {
        return frameworkRepo.findAllByOrganisationId(orgId);
    }

    @Transactional
    public CyberFramework saveFramework(CyberFramework f, Long orgId) {
        // Mass-assignment defense — controller accepts the entity directly
        // via @RequestBody. Null id forces save() to INSERT instead of
        // merge() into a potentially cross-tenant row.
        f.setId(null);
        f.setOrganisationId(orgId);
        f.setIsDeleted(false);
        return frameworkRepo.save(f);
    }

    // =========================================================================
    // Controls
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<CyberControl> getControls(Long orgId, Pageable p) {
        return controlRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public List<CyberControl> getAllControls(Long orgId) {
        return controlRepo.findAllByOrganisationId(orgId);
    }

    @Transactional(readOnly = true)
    public CyberControl getControl(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        return controlRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberControl not found: " + id));
    }

    @Transactional
    public CyberControl saveControl(CyberControl c, Long orgId) {
        // Mass-assignment defense — see saveFramework().
        c.setId(null);
        c.setOrganisationId(orgId);
        c.setIsDeleted(false);
        return controlRepo.save(c);
    }

    @Transactional
    public CyberControl updateControl(Long id, CyberControl req) {
        CyberControl existing = getControl(id);
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return controlRepo.save(req);
    }

    @Transactional
    public void deleteControl(Long id) {
        CyberControl c = getControl(id);
        c.setIsDeleted(true);
        controlRepo.save(c);
    }

    // =========================================================================
    // Sub-Controls
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<CyberSubControl> getSubControls(Long orgId, Pageable p) {
        return subControlRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public Page<CyberSubControl> getSubControlsByControl(Long orgId, Long controlId, Pageable p) {
        return subControlRepo.findByOrganisationIdAndControlId(orgId, controlId, p);
    }

    @Transactional
    public CyberSubControl saveSubControl(CyberSubControl s, Long orgId) {
        // Mass-assignment defense — see saveFramework().
        s.setId(null);
        s.setOrganisationId(orgId);
        s.setIsDeleted(false);
        return subControlRepo.save(s);
    }

    @Transactional
    public CyberSubControl updateSubControl(Long id, CyberSubControl req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberSubControl existing = subControlRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberSubControl not found: " + id));
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return subControlRepo.save(req);
    }

    @Transactional
    public void deleteSubControl(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberSubControl s = subControlRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberSubControl not found: " + id));
        s.setIsDeleted(true);
        subControlRepo.save(s);
    }

    // =========================================================================
    // Assessments
    // =========================================================================

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

    // =========================================================================
    // Vulnerabilities
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<Vulnerability> getVulnerabilities(Long orgId, Pageable p) {
        return vulnerabilityRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public Vulnerability getVulnerability(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        return vulnerabilityRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("Vulnerability not found: " + id));
    }

    @Transactional
    public Vulnerability saveVulnerability(Vulnerability v, Long orgId) {
        v.setOrganisationId(orgId);
        v.setIsDeleted(false);
        return vulnerabilityRepo.save(v);
    }

    @Transactional
    public Vulnerability updateVulnerability(Long id, Vulnerability req) {
        Vulnerability existing = getVulnerability(id);
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return vulnerabilityRepo.save(req);
    }

    @Transactional
    public Vulnerability escalate(Long id) {
        Vulnerability v = getVulnerability(id);
        int idx = VULN_WORKFLOW.indexOf(v.getWorkflowStatus());
        if (idx >= 0 && idx < VULN_WORKFLOW.size() - 1) {
            v.setWorkflowStatus(VULN_WORKFLOW.get(idx + 1));
        }
        Vulnerability saved = vulnerabilityRepo.save(v);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "vulnerability", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public void deleteVulnerability(Long id) {
        Vulnerability v = getVulnerability(id);
        v.setIsDeleted(true);
        vulnerabilityRepo.save(v);
    }

    // =========================================================================
    // Exceptions
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<VulnException> getExceptions(Long orgId, Pageable p) {
        return exceptionRepo.findByOrganisationId(orgId, p);
    }

    @Transactional
    public VulnException saveException(VulnException e, Long orgId) {
        e.setOrganisationId(orgId);
        e.setIsDeleted(false);
        return exceptionRepo.save(e);
    }

    @Transactional
    public VulnException approveException(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        VulnException e = exceptionRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("VulnException not found: " + id));
        e.setWorkflowStatus("APPROVED");
        e.setApprovedAt(LocalDate.now());
        VulnException saved = exceptionRepo.save(e);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "vuln_exception", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public VulnException updateException(Long id, VulnException req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        VulnException existing = exceptionRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("VulnException not found: " + id));
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return exceptionRepo.save(req);
    }

    @Transactional
    public void deleteException(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        VulnException e = exceptionRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("VulnException not found: " + id));
        e.setIsDeleted(true);
        exceptionRepo.save(e);
    }

    // =========================================================================
    // Incidents
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<CyberIncident> getIncidents(Long orgId, Pageable p) {
        return incidentRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public CyberIncident getIncident(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        return incidentRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberIncident not found: " + id));
    }

    @Transactional
    public CyberIncident saveIncident(CyberIncident inc, Long orgId) {
        // Mass-assignment defense — see saveFramework().
        inc.setId(null);
        inc.setOrganisationId(orgId);
        inc.setIsDeleted(false);
        return incidentRepo.save(inc);
    }

    @Transactional
    public CyberIncident updateIncident(Long id, CyberIncident req) {
        CyberIncident existing = getIncident(id);
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return incidentRepo.save(req);
    }

    @Transactional
    public CyberIncident startResponse(Long id) {
        CyberIncident inc = getIncident(id);
        int idx = INCIDENT_WORKFLOW.indexOf(inc.getWorkflowStatus());
        if (idx >= 0 && idx < INCIDENT_WORKFLOW.size() - 1) {
            inc.setWorkflowStatus(INCIDENT_WORKFLOW.get(idx + 1));
        }
        CyberIncident saved = incidentRepo.save(inc);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "cyber_incident", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public void deleteIncident(Long id) {
        CyberIncident inc = getIncident(id);
        inc.setIsDeleted(true);
        incidentRepo.save(inc);
    }

    // =========================================================================
    // Assets
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<CyberAsset> getAssets(Long orgId, Pageable p) {
        return assetRepo.findByOrganisationId(orgId, p);
    }

    @Transactional(readOnly = true)
    public List<CyberAsset> getAllAssets(Long orgId) {
        return assetRepo.findAllByOrganisationId(orgId);
    }

    @Transactional
    public CyberAsset saveAsset(CyberAsset a, Long orgId) {
        // Mass-assignment defense — see saveFramework().
        a.setId(null);
        a.setOrganisationId(orgId);
        a.setIsDeleted(false);
        return assetRepo.save(a);
    }

    @Transactional
    public CyberAsset updateAsset(Long id, CyberAsset req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberAsset existing = assetRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberAsset not found: " + id));
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return assetRepo.save(req);
    }

    @Transactional
    public void deleteAsset(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberAsset a = assetRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberAsset not found: " + id));
        a.setIsDeleted(true);
        assetRepo.save(a);
    }

    // =========================================================================
    // Evidence
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<CyberEvidence> getEvidence(Long orgId, Pageable p) {
        return evidenceRepo.findByOrganisationId(orgId, p);
    }

    @Transactional
    public CyberEvidence saveEvidence(CyberEvidence e, Long orgId) {
        e.setOrganisationId(orgId);
        e.setIsDeleted(false);
        return evidenceRepo.save(e);
    }

    @Transactional
    public CyberEvidence updateEvidence(Long id, CyberEvidence req) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberEvidence existing = evidenceRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberEvidence not found: " + id));
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setIsDeleted(false);
        return evidenceRepo.save(req);
    }

    @Transactional
    public void deleteEvidence(Long id) {
        // SEC-CYB-001: scope by tenant — never load another org's row by id.
        CyberEvidence e = evidenceRepo.findByIdAndOrganisationId(id, TenantContext.requireOrganisationId())
                .orElseThrow(() -> new EntityNotFoundException("CyberEvidence not found: " + id));
        e.setIsDeleted(true);
        evidenceRepo.save(e);
    }

    // =========================================================================
    // Action Plans
    // =========================================================================

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
