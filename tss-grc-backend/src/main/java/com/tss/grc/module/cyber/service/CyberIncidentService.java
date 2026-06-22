package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberIncident;
import com.tss.grc.module.cyber.repository.CyberIncidentRepository;
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

    @Autowired(required = false)
    private BroadcastHelper bcast;

    // ── Incident response workflow ───────────────────────────────────────────
    private static final List<String> INCIDENT_WORKFLOW = List.of(
            "DETECTION", "LOGGING", "TRIAGE", "CONTAINMENT",
            "INVESTIGATION", "ERADICATION", "RECOVERY", "CLOSURE", "LESSONS_LEARNED");

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
        // Mass-assignment defense — see CyberFrameworkService.saveFramework().
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
}
