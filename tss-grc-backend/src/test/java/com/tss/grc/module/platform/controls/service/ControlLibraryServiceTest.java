package com.tss.grc.module.platform.controls.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.controls.entity.Control;
import com.tss.grc.module.platform.controls.entity.ControlAssessment;
import com.tss.grc.module.platform.controls.entity.ControlMapping;
import com.tss.grc.module.platform.controls.entity.ControlObjective;
import com.tss.grc.module.platform.controls.entity.RiskControlLink;
import com.tss.grc.module.platform.controls.repository.ControlAssessmentRepository;
import com.tss.grc.module.platform.controls.repository.ControlMappingRepository;
import com.tss.grc.module.platform.controls.repository.ControlObjectiveRepository;
import com.tss.grc.module.platform.controls.repository.ControlRepository;
import com.tss.grc.module.platform.controls.repository.RiskControlLinkRepository;
import com.tss.grc.module.platform.regulatory.entity.AuthorityDocumentCitation;
import com.tss.grc.module.platform.regulatory.repository.AuthorityDocumentCitationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ControlLibraryService — F2 rule lock.
 *
 * <ul>
 *   <li>Control creation requires code, titleEn, valid domain; defaults applied.</li>
 *   <li>Duplicate (controlCode, version) rejected.</li>
 *   <li>Control lifecycle: DRAFT → ACTIVE → RETIRED only; backwards refused.</li>
 *   <li>Delete of ACTIVE control refused.</li>
 *   <li>Mapping requires non-blank rationale + valid coverage; duplicate (control,
 *       citation) refused.</li>
 *   <li>Risk-Control link requires riskId + controlId; duplicate refused.</li>
 *   <li>Assessment refused on non-ACTIVE control.</li>
 *   <li>Assessment workflow: DRAFT → UNDER_REVIEW → APPROVED|REJECTED → DRAFT (from REJECTED).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ControlLibraryServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private ControlObjectiveRepository objectives;
    @Mock private ControlRepository controls;
    @Mock private ControlMappingRepository mappings;
    @Mock private RiskControlLinkRepository riskControlLinks;
    @Mock private ControlAssessmentRepository assessments;
    @Mock private AuthorityDocumentCitationRepository citations;
    @InjectMocks private ControlLibraryService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Control control(String code, String status) {
        Control c = new Control();
        c.setId(1L);
        c.setOrganisationId(ORG_ID);
        c.setControlCode(code);
        c.setVersion("1.0");
        c.setDomain("CYBER");
        c.setTitleEn("Demo control");
        c.setControlType("PREVENTIVE");
        c.setAutomationLevel("MANUAL");
        c.setStatus(status);
        return c;
    }

    // ─── Controls ─────────────────────────────────────────────────

    @Test void create_missingCodeRejected() {
        Control body = control("X", "ACTIVE"); body.setControlCode(null);
        assertThatThrownBy(() -> service.createControl(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("controlCode");
    }

    @Test void create_invalidDomainRejected() {
        Control body = control("C-X-1", "ACTIVE"); body.setDomain("WHATEVER");
        assertThatThrownBy(() -> service.createControl(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("domain");
    }

    @Test void create_duplicateCodeVersionRejected() {
        Control body = control("C-X-1", "ACTIVE");
        when(controls.findByOrganisationIdAndControlCodeAndVersion(ORG_ID, "C-X-1", "1.0"))
            .thenReturn(Optional.of(body));
        assertThatThrownBy(() -> service.createControl(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
    }

    @Test void create_defaultsApplied() {
        Control body = new Control();
        body.setControlCode("C-NEW"); body.setTitleEn("x"); body.setDomain("CYBER");
        when(controls.findByOrganisationIdAndControlCodeAndVersion(any(), any(), any())).thenReturn(Optional.empty());
        when(controls.save(any())).thenAnswer(inv -> { Control c = inv.getArgument(0); c.setId(99L); return c; });

        Control saved = service.createControl(body);
        assertThat(saved.getVersion()).isEqualTo("1.0");
        assertThat(saved.getControlType()).isEqualTo("PREVENTIVE");
        assertThat(saved.getAutomationLevel()).isEqualTo("MANUAL");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    @Test void transition_draftToActiveOk() {
        Control c = control("C-1", "DRAFT");
        when(controls.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        when(controls.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Control out = service.transitionControl(1L, "ACTIVE");
        assertThat(out.getStatus()).isEqualTo("ACTIVE");
    }

    @Test void transition_retiredIsTerminal() {
        Control c = control("C-1", "RETIRED");
        when(controls.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.transitionControl(1L, "ACTIVE"))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void delete_activeRefused() {
        Control c = control("C-1", "ACTIVE");
        when(controls.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.deleteControl(1L))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("ACTIVE");
    }

    // ─── Mappings ─────────────────────────────────────────────────

    @Test void createMapping_requiresRationale() {
        when(controls.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(control("C-1", "ACTIVE")));
        when(citations.findByIdAndOrganisationId(10L, ORG_ID))
            .thenReturn(Optional.of(new AuthorityDocumentCitation()));
        ControlMapping m = new ControlMapping();
        m.setControlId(1L); m.setCitationId(10L); m.setCoverage("FULL");
        // no rationaleEn
        assertThatThrownBy(() -> service.createMapping(m))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rationaleEn");
    }

    @Test void createMapping_duplicateRejected() {
        when(controls.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(control("C-1", "ACTIVE")));
        when(citations.findByIdAndOrganisationId(10L, ORG_ID))
            .thenReturn(Optional.of(new AuthorityDocumentCitation()));
        when(mappings.findByControlIdAndCitationId(1L, 10L)).thenReturn(Optional.of(new ControlMapping()));
        ControlMapping m = new ControlMapping();
        m.setControlId(1L); m.setCitationId(10L); m.setCoverage("FULL"); m.setRationaleEn("x");
        assertThatThrownBy(() -> service.createMapping(m))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
    }

    @Test void createMapping_invalidCoverageRejected() {
        when(controls.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(control("C-1", "ACTIVE")));
        when(citations.findByIdAndOrganisationId(10L, ORG_ID))
            .thenReturn(Optional.of(new AuthorityDocumentCitation()));
        ControlMapping m = new ControlMapping();
        m.setControlId(1L); m.setCitationId(10L); m.setCoverage("MAYBE"); m.setRationaleEn("x");
        assertThatThrownBy(() -> service.createMapping(m))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("coverage");
    }

    // ─── Risk ↔ Control links ─────────────────────────────────────

    @Test void linkRisk_duplicateRejected() {
        when(controls.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(control("C-1", "ACTIVE")));
        when(riskControlLinks.findByRiskIdAndControlId(99L, 1L)).thenReturn(Optional.of(new RiskControlLink()));
        RiskControlLink l = new RiskControlLink();
        l.setRiskId(99L); l.setControlId(1L);
        assertThatThrownBy(() -> service.linkRiskToControl(l))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
    }

    @Test void linkRisk_defaultsToMitigates() {
        when(controls.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(control("C-1", "ACTIVE")));
        when(riskControlLinks.findByRiskIdAndControlId(any(), any())).thenReturn(Optional.empty());
        when(riskControlLinks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RiskControlLink l = new RiskControlLink();
        l.setRiskId(99L); l.setControlId(1L);
        l.setLinkType(null);
        RiskControlLink saved = service.linkRiskToControl(l);
        assertThat(saved.getLinkType()).isEqualTo("MITIGATES");
    }

    // ─── Assessments ──────────────────────────────────────────────

    @Test void createAssessment_refusedOnRetired() {
        Control c = control("C-1", "RETIRED");
        when(controls.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(c));
        ControlAssessment a = new ControlAssessment();
        a.setControlId(1L);
        assertThatThrownBy(() -> service.createAssessment(a))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("RETIRED");
    }

    @Test void assessmentWorkflow_walks() {
        ControlAssessment a = new ControlAssessment();
        a.setId(7L); a.setOrganisationId(ORG_ID); a.setControlId(1L); a.setWorkflowStatus("DRAFT");
        when(assessments.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(a));
        when(assessments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transitionAssessment(7L, "UNDER_REVIEW");
        assertThat(a.getWorkflowStatus()).isEqualTo("UNDER_REVIEW");

        service.transitionAssessment(7L, "REJECTED");
        assertThat(a.getWorkflowStatus()).isEqualTo("REJECTED");

        // REJECTED can recycle to DRAFT
        service.transitionAssessment(7L, "DRAFT");
        assertThat(a.getWorkflowStatus()).isEqualTo("DRAFT");
    }

    @Test void objectiveCreate_invalidDomainRejected() {
        ControlObjective o = new ControlObjective();
        o.setObjectiveCode("OBJ-X"); o.setTitleEn("x"); o.setDomain("INVALID");
        assertThatThrownBy(() -> service.createObjective(o))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("domain");
    }
}
