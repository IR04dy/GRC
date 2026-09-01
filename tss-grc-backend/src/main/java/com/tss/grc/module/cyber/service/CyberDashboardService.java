package com.tss.grc.module.cyber.service;

import com.tss.grc.module.cyber.dto.CyberDashboardDto;
import com.tss.grc.module.cyber.entity.CyberActionPlan;
import com.tss.grc.module.cyber.entity.CyberAsset;
import com.tss.grc.module.cyber.entity.CyberControl;
import com.tss.grc.module.cyber.entity.CyberEvidence;
import com.tss.grc.module.cyber.entity.CyberIncident;
import com.tss.grc.module.cyber.entity.Vulnerability;
import com.tss.grc.module.cyber.repository.CyberActionPlanRepository;
import com.tss.grc.module.cyber.repository.CyberAssetRepository;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import com.tss.grc.module.cyber.repository.CyberEvidenceRepository;
import com.tss.grc.module.cyber.repository.CyberIncidentRepository;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cyber dashboard read-aggregator.
 *
 * <p>Extracted from the former {@code CyberService} god-class (QUAL-CP-001).
 * Genuinely cross-aggregate (it counts across every register), so it reads the
 * repositories directly. Behaviour is unchanged.
 */
@Service
@RequiredArgsConstructor
public class CyberDashboardService {

    private final CyberControlRepository controlRepo;
    private final VulnerabilityRepository vulnerabilityRepo;
    private final CyberIncidentRepository incidentRepo;
    private final CyberAssetRepository assetRepo;
    private final CyberActionPlanRepository actionPlanRepo;
    private final CyberEvidenceRepository evidenceRepo;

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
                              && c.getFrameworkName().toUpperCase(Locale.ROOT).contains(fwCode.toUpperCase(Locale.ROOT))))
                .count();
        if (total == 0) return 0;
        long compliant = controls.stream()
                .filter(c -> fwCode.equalsIgnoreCase(c.getFrameworkName())
                          || (c.getFrameworkName() != null
                              && c.getFrameworkName().toUpperCase(Locale.ROOT).contains(fwCode.toUpperCase(Locale.ROOT))))
                .filter(c -> "IMPLEMENTED".equalsIgnoreCase(c.getImplementationStatus()))
                .count();
        return Math.round(100f * compliant / total);
    }
}
