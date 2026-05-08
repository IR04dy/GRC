package com.tss.grc.module.cyber.service;

import com.tss.grc.module.cyber.dto.CyberDashboardDto;
import com.tss.grc.module.cyber.entity.*;
import com.tss.grc.module.cyber.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CyberService — Sprint 4 §4.1 test backlog.
 *
 * Locks the two cyber-specific state machines:
 *
 *   Vulnerability:
 *     REGISTERED → CLASSIFICATION → OWNER_ASSIGNMENT → REMEDIATION → VERIFICATION → CLOSED
 *
 *   Incident:
 *     DETECTION → LOGGING → TRIAGE → CONTAINMENT → INVESTIGATION → ERADICATION
 *               → RECOVERY → CLOSURE → LESSONS_LEARNED
 *
 * Plus dashboard counters (open = !terminal status, overdue logic, criticality
 * filters) and the gap → CAPA conversion that links the new action plan back
 * to its source assessment.
 */
@ExtendWith(MockitoExtension.class)
class CyberServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long ID     = 100L;

    @Mock private CyberFrameworkRepository frameworkRepo;
    @Mock private CyberAssetRepository assetRepo;
    @Mock private CyberControlRepository controlRepo;
    @Mock private CyberSubControlRepository subControlRepo;
    @Mock private ControlAssessmentRepository assessmentRepo;
    @Mock private VulnerabilityRepository vulnerabilityRepo;
    @Mock private VulnExceptionRepository exceptionRepo;
    @Mock private CyberIncidentRepository incidentRepo;
    @Mock private CyberEvidenceRepository evidenceRepo;
    @Mock private CyberActionPlanRepository actionPlanRepo;
    @InjectMocks private CyberService service;

    // ─── Vulnerability escalation ────────────────────────────────────

    @Test
    void escalate_walks_full_VULN_workflow() {
        String[] chain = { "REGISTERED", "CLASSIFICATION", "OWNER_ASSIGNMENT",
                           "REMEDIATION", "VERIFICATION", "CLOSED" };
        for (int i = 0; i < chain.length - 1; i++) {
            Vulnerability v = new Vulnerability();
            v.setId(ID);
            v.setOrganisationId(ORG_ID);
            v.setWorkflowStatus(chain[i]);
            when(vulnerabilityRepo.findById(ID)).thenReturn(Optional.of(v));
            when(vulnerabilityRepo.save(any(Vulnerability.class))).thenAnswer(inv -> inv.getArgument(0));

            Vulnerability after = service.escalate(ID);
            assertThat(after.getWorkflowStatus()).isEqualTo(chain[i + 1]);
        }
    }

    @Test
    void escalate_at_CLOSED_is_no_op() {
        // Terminal state → service silently keeps it (no exception, no advance)
        Vulnerability v = new Vulnerability();
        v.setId(ID);
        v.setWorkflowStatus("CLOSED");
        when(vulnerabilityRepo.findById(ID)).thenReturn(Optional.of(v));
        when(vulnerabilityRepo.save(any(Vulnerability.class))).thenAnswer(inv -> inv.getArgument(0));

        Vulnerability after = service.escalate(ID);
        assertThat(after.getWorkflowStatus()).isEqualTo("CLOSED");
    }

    @Test
    void escalate_unknown_status_is_no_op() {
        // Defensive: unknown/legacy status (idx == -1) leaves the field untouched
        Vulnerability v = new Vulnerability();
        v.setId(ID);
        v.setWorkflowStatus("LEGACY_VALUE");
        when(vulnerabilityRepo.findById(ID)).thenReturn(Optional.of(v));
        when(vulnerabilityRepo.save(any(Vulnerability.class))).thenAnswer(inv -> inv.getArgument(0));

        Vulnerability after = service.escalate(ID);
        assertThat(after.getWorkflowStatus()).isEqualTo("LEGACY_VALUE");
    }

    @Test
    void escalate_unknown_id_throws_EntityNotFound() {
        when(vulnerabilityRepo.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.escalate(ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Vulnerability not found");
    }

    // ─── Incident response (9-step) ──────────────────────────────────

    @Test
    void startResponse_walks_full_INCIDENT_workflow() {
        String[] chain = { "DETECTION", "LOGGING", "TRIAGE", "CONTAINMENT",
                           "INVESTIGATION", "ERADICATION", "RECOVERY", "CLOSURE", "LESSONS_LEARNED" };
        for (int i = 0; i < chain.length - 1; i++) {
            CyberIncident inc = new CyberIncident();
            inc.setId(ID);
            inc.setWorkflowStatus(chain[i]);
            when(incidentRepo.findById(ID)).thenReturn(Optional.of(inc));
            when(incidentRepo.save(any(CyberIncident.class))).thenAnswer(inv -> inv.getArgument(0));

            CyberIncident after = service.startResponse(ID);
            assertThat(after.getWorkflowStatus()).isEqualTo(chain[i + 1]);
        }
    }

    @Test
    void startResponse_at_LESSONS_LEARNED_is_no_op() {
        CyberIncident inc = new CyberIncident();
        inc.setId(ID);
        inc.setWorkflowStatus("LESSONS_LEARNED");
        when(incidentRepo.findById(ID)).thenReturn(Optional.of(inc));
        when(incidentRepo.save(any(CyberIncident.class))).thenAnswer(inv -> inv.getArgument(0));

        CyberIncident after = service.startResponse(ID);
        assertThat(after.getWorkflowStatus()).isEqualTo("LESSONS_LEARNED");
    }

    // ─── Gap → CAPA conversion ───────────────────────────────────────

    @Test
    void convertGapToCapa_creates_action_plan_and_back_links_assessment() {
        ControlAssessment a = new ControlAssessment();
        a.setId(55L);
        a.setControlId(7L);
        a.setExternalId("ASSESS-EXT-XYZ");
        a.setGapDescription("MFA missing on jump host");
        a.setStatus("APPROVED");
        when(assessmentRepo.findById(55L)).thenReturn(Optional.of(a));
        when(actionPlanRepo.save(any(CyberActionPlan.class))).thenAnswer(inv -> {
            CyberActionPlan saved = inv.getArgument(0);
            saved.setId(900L);
            return saved;
        });
        when(assessmentRepo.save(any(ControlAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        CyberActionPlan capa = service.convertGapToCapa(55L, ORG_ID);

        ArgumentCaptor<CyberActionPlan> capaCaptor = ArgumentCaptor.forClass(CyberActionPlan.class);
        verify(actionPlanRepo).save(capaCaptor.capture());
        CyberActionPlan saved = capaCaptor.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getSourceType()).isEqualTo("ASSESSMENT");
        assertThat(saved.getSourceId()).isEqualTo(55L);
        assertThat(saved.getSourceRef()).isEqualTo("ASSESS-EXT-XYZ");
        assertThat(saved.getLinkedControlId()).isEqualTo(7L);
        assertThat(saved.getDescriptionEn()).contains("MFA missing");
        assertThat(saved.getWorkflowStatus()).isEqualTo("OPEN");

        // Back-link: CAPA id must be stamped on assessment.capaId
        assertThat(a.getCapaId()).isEqualTo(900L);
        assertThat(capa.getId()).isEqualTo(900L);
    }

    // ─── Approve assessment / exception ──────────────────────────────

    @Test
    void approveAssessment_sets_status_APPROVED() {
        ControlAssessment a = new ControlAssessment();
        a.setId(ID);
        a.setStatus("DRAFT");
        when(assessmentRepo.findById(ID)).thenReturn(Optional.of(a));
        when(assessmentRepo.save(any(ControlAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        ControlAssessment after = service.approveAssessment(ID);
        assertThat(after.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approveException_sets_workflow_APPROVED_and_stamps_approvedAt() {
        VulnException ex = new VulnException();
        ex.setId(ID);
        ex.setWorkflowStatus("REQUESTED");
        when(exceptionRepo.findById(ID)).thenReturn(Optional.of(ex));
        when(exceptionRepo.save(any(VulnException.class))).thenAnswer(inv -> inv.getArgument(0));

        VulnException after = service.approveException(ID);
        assertThat(after.getWorkflowStatus()).isEqualTo("APPROVED");
        assertThat(after.getApprovedAt()).isEqualTo(LocalDate.now());
    }

    // ─── Action plan completion ──────────────────────────────────────

    @Test
    void completeActionPlan_sets_COMPLETED_progress_100_and_completedAt() {
        CyberActionPlan ap = new CyberActionPlan();
        ap.setId(ID);
        ap.setWorkflowStatus("IN_PROGRESS");
        ap.setProgressPct(45);
        when(actionPlanRepo.findById(ID)).thenReturn(Optional.of(ap));
        when(actionPlanRepo.save(any(CyberActionPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        CyberActionPlan after = service.completeActionPlan(ID);
        assertThat(after.getWorkflowStatus()).isEqualTo("COMPLETED");
        assertThat(after.getProgressPct()).isEqualTo(100);
        assertThat(after.getCompletedAt()).isEqualTo(LocalDate.now());
    }

    // ─── Soft delete patterns ────────────────────────────────────────

    @Test
    void deleteVulnerability_soft_deletes_via_save() {
        Vulnerability v = new Vulnerability();
        v.setId(ID);
        when(vulnerabilityRepo.findById(ID)).thenReturn(Optional.of(v));
        when(vulnerabilityRepo.save(any(Vulnerability.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteVulnerability(ID);

        assertThat(v.getIsDeleted()).isTrue();
        verify(vulnerabilityRepo).save(v);
        verify(vulnerabilityRepo, never()).deleteById(any());
    }

    @Test
    void deleteIncident_soft_deletes_via_save() {
        CyberIncident inc = new CyberIncident();
        inc.setId(ID);
        when(incidentRepo.findById(ID)).thenReturn(Optional.of(inc));
        when(incidentRepo.save(any(CyberIncident.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteIncident(ID);
        assertThat(inc.getIsDeleted()).isTrue();
    }

    // ─── Dashboard counters ──────────────────────────────────────────

    @Test
    void getDashboard_counts_open_overdue_critical_correctly() {
        // Controls: 3 total, 1 compliant, 1 partial, 1 non-compliant
        CyberControl c1 = new CyberControl(); c1.setImplementationStatus("COMPLIANT");
        CyberControl c2 = new CyberControl(); c2.setImplementationStatus("PARTIAL");
        CyberControl c3 = new CyberControl(); c3.setImplementationStatus("NON_COMPLIANT");
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
