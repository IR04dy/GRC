package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.dto.*;
import com.tss.grc.module.compliance.entity.*;
import com.tss.grc.module.compliance.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ComplianceService — Sprint 4 §4.1 test backlog.
 *
 * Locks the three-entity surface (Control / Assessment / Finding):
 *   - Default values applied on create (status=PLANNED, maturityLevel=1, status=OPEN)
 *   - Tenant scope on every read/write
 *   - Soft-delete preserves rows (sets isDeleted=true via save)
 *   - Compliance rate falls back to control-status ratio when no assessments exist
 */
@ExtendWith(MockitoExtension.class)
class ComplianceServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long ID        = 7L;

    @Mock private ControlRepository controlRepo;
    @Mock private ComplianceAssessmentRepository assessmentRepo;
    @Mock private AuditFindingRepository findingRepo;
    @InjectMocks private ComplianceService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ─── Controls ────────────────────────────────────────────────────

    @Test
    void createControl_defaults_status_PLANNED_and_maturity_1() {
        ControlRequest req = new ControlRequest(
            "AC-2", "Account Mgmt", "إدارة الحسابات", "NCA_ECC",
            "AC", null, null, "ciso@tss", "Manage accounts"
        );
        when(controlRepo.save(any(Control.class))).thenAnswer(inv -> {
            Control c = inv.getArgument(0);
            c.setId(ID);
            return c;
        });

        service.createControl(req);

        ArgumentCaptor<Control> captor = ArgumentCaptor.forClass(Control.class);
        verify(controlRepo).save(captor.capture());
        Control saved = captor.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getStatus()).isEqualTo("PLANNED");
        assertThat(saved.getMaturityLevel()).isEqualTo(1);
    }

    @Test
    void createControl_explicit_status_overrides_default() {
        ControlRequest req = new ControlRequest(
            "AC-2", "Account Mgmt", null, "NCA_ECC", "AC",
            3, "IMPLEMENTED", "ciso@tss", "desc"
        );
        when(controlRepo.save(any(Control.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createControl(req);

        ArgumentCaptor<Control> captor = ArgumentCaptor.forClass(Control.class);
        verify(controlRepo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("IMPLEMENTED");
        assertThat(captor.getValue().getMaturityLevel()).isEqualTo(3);
    }

    @Test
    void getControl_other_tenant_throws_EntityNotFound() {
        Control c = new Control();
        c.setId(ID);
        c.setOrganisationId(OTHER_ORG);
        when(controlRepo.findById(ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.getControl(ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Control not found");
    }

    @Test
    void deleteControl_soft_deletes_via_save() {
        Control c = new Control();
        c.setId(ID);
        c.setOrganisationId(ORG_ID);
        when(controlRepo.findById(ID)).thenReturn(Optional.of(c));
        when(controlRepo.save(any(Control.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteControl(ID);

        assertThat(c.getIsDeleted()).isTrue();
        verify(controlRepo).save(c);
        verify(controlRepo, never()).deleteById(any());
    }

    // ─── Assessments ─────────────────────────────────────────────────

    @Test
    void createAssessment_defaults_status_PLANNED() {
        AssessmentRequest req = new AssessmentRequest(
            "NCA_ECC", "2026-Q1", "External Audit", 85.0, null,
            java.time.LocalDate.now(), "notes"
        );
        when(assessmentRepo.save(any(ComplianceAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createAssessment(req);

        ArgumentCaptor<ComplianceAssessment> captor = ArgumentCaptor.forClass(ComplianceAssessment.class);
        verify(assessmentRepo).save(captor.capture());
        ComplianceAssessment saved = captor.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getStatus()).isEqualTo("PLANNED");
    }

    @Test
    void updateAssessment_other_tenant_throws_EntityNotFound() {
        AssessmentRequest req = new AssessmentRequest(
            "NCA_ECC", "2026-Q1", null, 50.0, "ACTIVE",
            null, null
        );
        ComplianceAssessment a = new ComplianceAssessment();
        a.setId(ID);
        a.setOrganisationId(OTHER_ORG);
        when(assessmentRepo.findById(ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.updateAssessment(ID, req))
            .isInstanceOf(EntityNotFoundException.class);

        verify(assessmentRepo, never()).save(any());
    }

    // ─── Findings ────────────────────────────────────────────────────

    @Test
    void createFinding_defaults_status_OPEN() {
        FindingRequest req = new FindingRequest(
            "MFA missing on jump host", null, "HIGH",
            "ciso@tss", "Enforce TOTP", java.time.LocalDate.now(), null,
            null, null
        );
        when(findingRepo.save(any(AuditFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createFinding(req);

        ArgumentCaptor<AuditFinding> captor = ArgumentCaptor.forClass(AuditFinding.class);
        verify(findingRepo).save(captor.capture());
        AuditFinding saved = captor.getValue();
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(saved.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void deleteFinding_other_tenant_throws_EntityNotFound() {
        AuditFinding f = new AuditFinding();
        f.setId(ID);
        f.setOrganisationId(OTHER_ORG);
        when(findingRepo.findById(ID)).thenReturn(Optional.of(f));

        assertThatThrownBy(() -> service.deleteFinding(ID))
            .isInstanceOf(EntityNotFoundException.class);

        verify(findingRepo, never()).save(any());
    }

    @Test
    void deleteFinding_soft_deletes_via_save() {
        AuditFinding f = new AuditFinding();
        f.setId(ID);
        f.setOrganisationId(ORG_ID);
        when(findingRepo.findById(ID)).thenReturn(Optional.of(f));
        when(findingRepo.save(any(AuditFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteFinding(ID);

        assertThat(f.getIsDeleted()).isTrue();
        verify(findingRepo).save(f);
    }

    // ─── Stats ───────────────────────────────────────────────────────

    @Test
    void getStats_uses_avgScore_when_assessments_exist() {
        when(controlRepo.countByOrganisationId(ORG_ID)).thenReturn(50L);
        when(controlRepo.countByOrganisationIdAndStatus(ORG_ID, "IMPLEMENTED")).thenReturn(20L);
        when(controlRepo.countByOrganisationIdAndStatus(ORG_ID, "PARTIAL")).thenReturn(15L);
        when(controlRepo.countByOrganisationIdAndStatus(ORG_ID, "PLANNED")).thenReturn(15L);
        when(findingRepo.countByOrganisationIdAndStatus(ORG_ID, "OPEN")).thenReturn(8L);
        when(findingRepo.countByOrganisationIdAndSeverity(ORG_ID, "CRITICAL")).thenReturn(2L);
        when(assessmentRepo.avgScoreByOrg(ORG_ID)).thenReturn(72.5);
        when(assessmentRepo.avgScoreByFramework(ORG_ID))
            .thenReturn(List.<Object[]>of(new Object[]{ "NCA_ECC", 80.0 }));

        ComplianceStats stats = service.getStats();

        assertThat(stats.totalControls()).isEqualTo(50);
        assertThat(stats.implementedControls()).isEqualTo(20);
        assertThat(stats.overallComplianceRate()).isCloseTo(72.5, within(0.0001));
        assertThat(stats.openFindings()).isEqualTo(8);
        // criticalFindings is counted by SEVERITY=CRITICAL, not status (QUAL-RC-002)
        assertThat(stats.criticalFindings()).isEqualTo(2);
        assertThat(stats.frameworkScores()).hasSize(1);
        Map<String, Object> entry = stats.frameworkScores().get(0);
        assertThat(entry.get("framework")).isEqualTo("NCA_ECC");
    }

    @Test
    void getStats_falls_back_to_implemented_ratio_when_no_assessments() {
        // No avgScore: rate = (implemented / total) * 100
        when(controlRepo.countByOrganisationId(ORG_ID)).thenReturn(40L);
        when(controlRepo.countByOrganisationIdAndStatus(ORG_ID, "IMPLEMENTED")).thenReturn(10L);
        when(controlRepo.countByOrganisationIdAndStatus(ORG_ID, "PARTIAL")).thenReturn(0L);
        when(controlRepo.countByOrganisationIdAndStatus(ORG_ID, "PLANNED")).thenReturn(30L);
        when(findingRepo.countByOrganisationIdAndStatus(ORG_ID, "OPEN")).thenReturn(0L);
        when(findingRepo.countByOrganisationIdAndSeverity(ORG_ID, "CRITICAL")).thenReturn(0L);
        when(assessmentRepo.avgScoreByOrg(ORG_ID)).thenReturn(null);
        when(assessmentRepo.avgScoreByFramework(ORG_ID)).thenReturn(List.<Object[]>of());

        ComplianceStats stats = service.getStats();
        assertThat(stats.overallComplianceRate()).isCloseTo(25.0, within(0.0001)); // 10/40
    }

    @Test
    void getStats_zero_division_safe_when_no_controls() {
        when(controlRepo.countByOrganisationId(ORG_ID)).thenReturn(0L);
        when(controlRepo.countByOrganisationIdAndStatus(ORG_ID, "IMPLEMENTED")).thenReturn(0L);
        when(controlRepo.countByOrganisationIdAndStatus(ORG_ID, "PARTIAL")).thenReturn(0L);
        when(controlRepo.countByOrganisationIdAndStatus(ORG_ID, "PLANNED")).thenReturn(0L);
        when(findingRepo.countByOrganisationIdAndStatus(ORG_ID, "OPEN")).thenReturn(0L);
        when(findingRepo.countByOrganisationIdAndSeverity(ORG_ID, "CRITICAL")).thenReturn(0L);
        when(assessmentRepo.avgScoreByOrg(ORG_ID)).thenReturn(null);
        when(assessmentRepo.avgScoreByFramework(ORG_ID)).thenReturn(List.<Object[]>of());

        ComplianceStats stats = service.getStats();
        assertThat(stats.overallComplianceRate()).isEqualTo(0.0);  // not NaN, not Infinity
    }
}
