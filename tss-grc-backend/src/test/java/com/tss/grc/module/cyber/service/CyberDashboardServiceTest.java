package com.tss.grc.module.cyber.service;

import com.tss.grc.module.cyber.dto.CyberDashboardDto;
import com.tss.grc.module.cyber.entity.*;
import com.tss.grc.module.cyber.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link CyberDashboardService} — extracted from the former CyberService
 * god-class (QUAL-CP-001). Locks the dashboard counters: open = !terminal
 * status, overdue logic, criticality filters, expired-evidence.
 */
@ExtendWith(MockitoExtension.class)
class CyberDashboardServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private CyberControlRepository controlRepo;
    @Mock private VulnerabilityRepository vulnerabilityRepo;
    @Mock private CyberIncidentRepository incidentRepo;
    @Mock private CyberAssetRepository assetRepo;
    @Mock private CyberActionPlanRepository actionPlanRepo;
    @Mock private CyberEvidenceRepository evidenceRepo;
    @InjectMocks private CyberDashboardService service;

    @Test
    void getDashboard_counts_open_overdue_critical_correctly() {
        // Controls: 3 total, 1 compliant, 1 partial, 1 non-compliant
        CyberControl c1 = new CyberControl(); c1.setImplementationStatus("IMPLEMENTED");
        CyberControl c2 = new CyberControl(); c2.setImplementationStatus("PARTIALLY_IMPLEMENTED");
        CyberControl c3 = new CyberControl(); c3.setImplementationStatus("NOT_IMPLEMENTED");
        when(controlRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of(c1, c2, c3));

        // Vulns: 4 total, 3 open (1 closed), 2 critical, 1 overdue
        Vulnerability v1 = new Vulnerability(); v1.setWorkflowStatus("REMEDIATION"); v1.setSeverity("CRITICAL"); v1.setOverdue(true);
        Vulnerability v2 = new Vulnerability(); v2.setWorkflowStatus("VERIFICATION"); v2.setSeverity("CRITICAL"); v2.setOverdue(false);
        Vulnerability v3 = new Vulnerability(); v3.setWorkflowStatus("CLASSIFICATION"); v3.setSeverity("LOW"); v3.setOverdue(false);
        Vulnerability v4 = new Vulnerability(); v4.setWorkflowStatus("CLOSED"); v4.setSeverity("MEDIUM"); v4.setOverdue(false);
        when(vulnerabilityRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of(v1, v2, v3, v4));

        // Incidents: 3 total, 2 open (1 LESSONS_LEARNED), 1 critical
        CyberIncident i1 = new CyberIncident(); i1.setWorkflowStatus("CONTAINMENT"); i1.setSeverity("CRITICAL");
        CyberIncident i2 = new CyberIncident(); i2.setWorkflowStatus("RECOVERY");    i2.setSeverity("HIGH");
        CyberIncident i3 = new CyberIncident(); i3.setWorkflowStatus("LESSONS_LEARNED"); i3.setSeverity("LOW");
        when(incidentRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of(i1, i2, i3));

        // Assets: 2 total, 1 critical
        CyberAsset a1 = new CyberAsset(); a1.setCriticality("CRITICAL");
        CyberAsset a2 = new CyberAsset(); a2.setCriticality("MEDIUM");
        when(assetRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of(a1, a2));

        // Action plans: 3 total, 2 open, 1 overdue (dueDate yesterday + not COMPLETED)
        CyberActionPlan ap1 = new CyberActionPlan();
        ap1.setWorkflowStatus("IN_PROGRESS"); ap1.setDueDate(LocalDate.now().minusDays(1));
        CyberActionPlan ap2 = new CyberActionPlan();
        ap2.setWorkflowStatus("OPEN"); ap2.setDueDate(LocalDate.now().plusDays(7));
        CyberActionPlan ap3 = new CyberActionPlan();
        ap3.setWorkflowStatus("COMPLETED"); ap3.setDueDate(LocalDate.now().minusDays(30));
        when(actionPlanRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of(ap1, ap2, ap3));

        // Evidence: 2 total, 1 expired
        CyberEvidence e1 = new CyberEvidence(); e1.setStatus("VALID");
        CyberEvidence e2 = new CyberEvidence(); e2.setStatus("EXPIRED");
        when(evidenceRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of(e1, e2));

        // Recent paginated calls — return empty for dashboard tail
        when(incidentRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class)))
            .thenReturn((Page) new PageImpl<>(List.<CyberIncident>of()));
        when(vulnerabilityRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class)))
            .thenReturn((Page) new PageImpl<>(List.<Vulnerability>of()));
        when(actionPlanRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class)))
            .thenReturn((Page) new PageImpl<>(List.<CyberActionPlan>of()));

        CyberDashboardDto dto = service.getDashboard(ORG_ID);

        assertThat(dto.getTotalControls()).isEqualTo(3);
        assertThat(dto.getCompliantControls()).isEqualTo(1);
        assertThat(dto.getPartialControls()).isEqualTo(1);
        assertThat(dto.getNonCompliantControls()).isEqualTo(1);

        assertThat(dto.getOpenVulnerabilities()).isEqualTo(3);     // not CLOSED
        assertThat(dto.getCriticalVulnerabilities()).isEqualTo(2);
        assertThat(dto.getOverdueVulnerabilities()).isEqualTo(1);

        assertThat(dto.getOpenIncidents()).isEqualTo(2);            // not LESSONS_LEARNED
        assertThat(dto.getCriticalIncidents()).isEqualTo(1);

        assertThat(dto.getTotalAssets()).isEqualTo(2);
        assertThat(dto.getCriticalAssets()).isEqualTo(1);

        assertThat(dto.getOpenActionPlans()).isEqualTo(2);          // not COMPLETED
        assertThat(dto.getOverdueActionPlans()).isEqualTo(1);       // ap1 only (ap3 is COMPLETED)

        assertThat(dto.getTotalEvidence()).isEqualTo(2);
        assertThat(dto.getExpiredEvidence()).isEqualTo(1);
    }

    @Test
    void getDashboard_with_empty_org_returns_zeros() {
        when(controlRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of());
        when(vulnerabilityRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of());
        when(incidentRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of());
        when(assetRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of());
        when(actionPlanRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of());
        when(evidenceRepo.findAllByOrganisationId(ORG_ID)).thenReturn(List.of());
        when(incidentRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class)))
            .thenReturn((Page) new PageImpl<>(List.<CyberIncident>of()));
        when(vulnerabilityRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class)))
            .thenReturn((Page) new PageImpl<>(List.<Vulnerability>of()));
        when(actionPlanRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class)))
            .thenReturn((Page) new PageImpl<>(List.<CyberActionPlan>of()));

        CyberDashboardDto dto = service.getDashboard(ORG_ID);
        assertThat(dto.getTotalControls()).isZero();
        assertThat(dto.getOpenIncidents()).isZero();
        assertThat(dto.getOverdueActionPlans()).isZero();
    }
}
