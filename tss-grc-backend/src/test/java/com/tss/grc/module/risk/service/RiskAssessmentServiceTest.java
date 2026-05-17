package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskAssessment;
import com.tss.grc.module.risk.repository.RiskAssessmentRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RiskAssessmentService — locks Rules A1 (1..5 scale) and A2 (residual
 * snapshot propagation).
 *
 * <ul>
 *   <li>{@code record()} validates likelihood/impact ∈ [1,5] and stamps
 *       the parent Risk's residual_* columns.</li>
 *   <li>Out-of-range likelihood/impact rejected.</li>
 *   <li>Cross-tenant risk lookup refused.</li>
 *   <li>{@code correct()} mirrors back to parent only when it edits the
 *       most-recent assessment.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long OTHER_ORG = 9L;
    private static final Long RISK_ID = 100L;

    @Mock private RiskAssessmentRepository assessments;
    @Mock private RiskRepository risks;
    @InjectMocks private RiskAssessmentService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Risk risk(Long orgId) {
        Risk r = new Risk();
        r.setId(RISK_ID);
        r.setOrganisationId(orgId);
        r.setTitleEn("Test risk");
        r.setStatus("OPEN");
        r.setInherentLikelihood(4);
        r.setInherentImpact(5);
        return r;
    }

    private static RiskAssessment assessment(int likelihood, int impact) {
        RiskAssessment a = new RiskAssessment();
        a.setRiskId(RISK_ID);
        a.setLikelihood(likelihood);
        a.setImpact(impact);
        return a;
    }

    @Test void record_promotesResidualToParentRisk() {
        Risk r = risk(ORG_ID);
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(assessments.save(any())).thenAnswer(inv -> {
            RiskAssessment a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });
        when(risks.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskAssessment out = service.record(assessment(3, 4));

        assertThat(out.getId()).isEqualTo(1L);
        assertThat(out.getAssessmentDate()).isEqualTo(LocalDate.now());
        assertThat(r.getResidualLikelihood()).isEqualTo(3);
        assertThat(r.getResidualImpact()).isEqualTo(4);
    }

    @Test void record_likelihoodOutOfRangeRejected() {
        assertThatThrownBy(() -> service.record(assessment(6, 3)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("likelihood must be 1..5");
    }

    @Test void record_impactOutOfRangeRejected() {
        assertThatThrownBy(() -> service.record(assessment(3, 0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("impact must be 1..5");
    }

    @Test void record_missingRiskIdRejected() {
        RiskAssessment a = new RiskAssessment();
        a.setLikelihood(3); a.setImpact(3);
        assertThatThrownBy(() -> service.record(a))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("riskId");
    }

    @Test void record_crossTenantRiskRejected() {
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk(OTHER_ORG)));
        assertThatThrownBy(() -> service.record(assessment(3, 3)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("another organisation");
    }

    @Test void correct_latestAssessment_mirrorsBackToRisk() {
        Risk r = risk(ORG_ID);
        RiskAssessment a = assessment(2, 2); a.setId(500L); a.setAssessmentDate(LocalDate.now());
        when(assessments.findById(500L)).thenReturn(Optional.of(a));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(assessments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(risks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assessments.findByRiskIdOrderByAssessmentDateDesc(RISK_ID))
            .thenReturn(List.of(a));

        service.correct(500L, 5, 4, "rolled up after cyber incident");

        assertThat(a.getLikelihood()).isEqualTo(5);
        assertThat(a.getImpact()).isEqualTo(4);
        assertThat(a.getAssessmentNotesEn()).contains("cyber incident");
        assertThat(r.getResidualLikelihood()).isEqualTo(5);
        assertThat(r.getResidualImpact()).isEqualTo(4);
    }

    @Test void correct_olderAssessment_doesNotMirrorBack() {
        Risk r = risk(ORG_ID);
        r.setResidualLikelihood(5); r.setResidualImpact(5);
        RiskAssessment older = assessment(2, 2); older.setId(500L);
        older.setAssessmentDate(LocalDate.now().minusDays(7));
        RiskAssessment newer = assessment(5, 5); newer.setId(501L);
        newer.setAssessmentDate(LocalDate.now());

        when(assessments.findById(500L)).thenReturn(Optional.of(older));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(assessments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assessments.findByRiskIdOrderByAssessmentDateDesc(RISK_ID))
            .thenReturn(List.of(newer, older));

        service.correct(500L, 3, 3, null);

        // older assessment values updated…
        assertThat(older.getLikelihood()).isEqualTo(3);
        assertThat(older.getImpact()).isEqualTo(3);
        // …but the parent Risk residual stays at the newer (5,5) snapshot
        assertThat(r.getResidualLikelihood()).isEqualTo(5);
        assertThat(r.getResidualImpact()).isEqualTo(5);
    }

    @Test void correct_outOfRangeRejected() {
        RiskAssessment a = assessment(2, 2); a.setId(500L);
        when(assessments.findById(500L)).thenReturn(Optional.of(a));
        when(risks.findById(RISK_ID)).thenReturn(Optional.of(risk(ORG_ID)));

        assertThatThrownBy(() -> service.correct(500L, 99, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("likelihood must be 1..5");
    }
}
