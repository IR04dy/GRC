package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.ControlAssessment;
import com.tss.grc.module.cyber.entity.CyberActionPlan;
import com.tss.grc.module.cyber.repository.ControlAssessmentRepository;
import com.tss.grc.module.cyber.repository.CyberActionPlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CyberAssessmentService} — extracted from the former CyberService
 * god-class (QUAL-CP-001). Locks gap → CAPA conversion (which links the new
 * action plan back to its source assessment) and assessment approval.
 */
@ExtendWith(MockitoExtension.class)
class CyberAssessmentServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long ID     = 100L;

    @Mock private ControlAssessmentRepository assessmentRepo;
    @Mock private CyberActionPlanRepository actionPlanRepo;
    @InjectMocks private CyberAssessmentService service;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    @Test
    void convertGapToCapa_creates_action_plan_and_back_links_assessment() {
        ControlAssessment a = new ControlAssessment();
        a.setId(55L);
        a.setControlId(7L);
        a.setExternalId("ASSESS-EXT-XYZ");
        a.setGapDescription("MFA missing on jump host");
        a.setStatus("APPROVED");
        when(assessmentRepo.findByIdAndOrganisationId(55L, ORG_ID)).thenReturn(Optional.of(a));
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

    @Test
    void approveAssessment_sets_status_APPROVED() {
        ControlAssessment a = new ControlAssessment();
        a.setId(ID);
        a.setStatus("DRAFT");
        when(assessmentRepo.findByIdAndOrganisationId(ID, ORG_ID)).thenReturn(Optional.of(a));
        when(assessmentRepo.save(any(ControlAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        ControlAssessment after = service.approveAssessment(ID);
        assertThat(after.getStatus()).isEqualTo("APPROVED");
    }
}
