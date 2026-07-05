package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberIncident;
import com.tss.grc.module.cyber.repository.CyberIncidentRepository;
import com.tss.grc.module.incident.service.IncidentService;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cyber incident register + 9-step response workflow.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberIncidentService {

    private final CyberIncidentRepository incidentRepo;

    /**
     * CY-B (C-CRIT-3): the canonical incident register. Every SOC-workspace incident
     * registers a linked {@code cyber.incidents} row through this service so it reaches
     * the dashboard KPI + PDPL breach bridge. One-way dependency (cyber → incident);
     * no cycle.
     */
    private final IncidentService registerService;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    // ── Incident response workflow ───────────────────────────────────────────
    private static final List<String> INCIDENT_WORKFLOW = List.of(
            "DETECTION", "LOGGING", "TRIAGE", "CONTAINMENT",
            "INVESTIGATION", "ERADICATION", "RECOVERY", "CLOSURE", "LESSONS_LEARNED");

    /**
     * Map the SOC 9-step workflow (and the looser vocabulary present in seed data)
     * onto the register's strict status set (OPEN|INVESTIGATING|RESOLVED|CLOSED),
     * so the dashboard open-count reflects where the workspace incident actually is.
     */
    private static String workflowToStatus(String wf) {
        if (wf == null) return "OPEN";
        return switch (wf.trim().toUpperCase()) {
            case "CLOSURE", "RESOLVED"            -> "RESOLVED";
            case "LESSONS_LEARNED", "CLOSED", "DONE" -> "CLOSED";
            case "CONTAINMENT", "INVESTIGATION", "INVESTIGATING", "ANALYSIS",
                 "ERADICATION", "RECOVERY", "IN_PROGRESS", "IN_REVIEW" -> "INVESTIGATING";
            default -> "OPEN";   // DETECTION, LOGGING, TRIAGE, NEW, OPEN, …
        };
    }

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
        // CY-H — title_en is NOT NULL; reject a blank/empty body with 400 not a DB 500.
        inc.setTitleEn(CyberInputValidation.requireText(inc.getTitleEn(), "titleEn"));
        // Mass-assignment defense — see CyberFrameworkService.saveFramework().
        inc.setId(null);
        inc.setOrganisationId(orgId);
        inc.setIsDeleted(false);
        inc.setCanonicalIncidentId(null);            // set below, after the mirror exists
        CyberIncident saved = incidentRepo.save(inc);

        // CY-B: register a canonical cyber.incidents row so this workspace incident
        // reaches the dashboard "open incidents" KPI + trend and is PDPL-linkable.
        String desc = "Registered from /cyber SOC workspace incident #" + saved.getId()
                + (saved.getIncidentType() != null ? " · type=" + saved.getIncidentType() : "")
                + (saved.getImpact() != null ? " · impact=" + saved.getImpact() : "");
        Long canonicalId = registerService.registerFromWorkspace(
                orgId, saved.getId(),
                saved.getTitleEn(), saved.getTitleAr(), saved.getSeverity(),
                saved.getIncidentType(),                 // sanitised to CK_incidents_category in the register
                workflowToStatus(saved.getWorkflowStatus()),
                saved.getDetectedAt() != null ? saved.getDetectedAt().toLocalDate() : null,
                desc);
        saved.setCanonicalIncidentId(canonicalId);
        return incidentRepo.save(saved);
    }

    @Transactional
    public CyberIncident updateIncident(Long id, CyberIncident req) {
        CyberIncident existing = getIncident(id);
        req.setId(existing.getId());
        req.setOrganisationId(existing.getOrganisationId());
        req.setExternalId(existing.getExternalId());
        req.setCreatedAt(existing.getCreatedAt());
        req.setCanonicalIncidentId(existing.getCanonicalIncidentId());   // CY-B: keep the register link
        req.setIsDeleted(false);
        CyberIncident saved = incidentRepo.save(req);
        // CY-B: reflect any workflow/status change onto the canonical register row.
        registerService.syncStatusFromWorkspace(saved.getOrganisationId(), saved.getId(),
                workflowToStatus(saved.getWorkflowStatus()));
        return saved;
    }

    @Transactional
    public CyberIncident startResponse(Long id) {
        CyberIncident inc = getIncident(id);
        int idx = INCIDENT_WORKFLOW.indexOf(inc.getWorkflowStatus());
        if (idx >= 0 && idx < INCIDENT_WORKFLOW.size() - 1) {
            inc.setWorkflowStatus(INCIDENT_WORKFLOW.get(idx + 1));
        }
        CyberIncident saved = incidentRepo.save(inc);
        // CY-B: advance the canonical register row's status with the workflow.
        registerService.syncStatusFromWorkspace(saved.getOrganisationId(), saved.getId(),
                workflowToStatus(saved.getWorkflowStatus()));
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "cyber_incident", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public void deleteIncident(Long id) {
        CyberIncident inc = getIncident(id);
        inc.setIsDeleted(true);
        incidentRepo.save(inc);
        // CY-B: soft-delete the canonical mirror too, so the dashboard count stays honest.
        registerService.softDeleteFromWorkspace(inc.getOrganisationId(), inc.getId());
    }
}
