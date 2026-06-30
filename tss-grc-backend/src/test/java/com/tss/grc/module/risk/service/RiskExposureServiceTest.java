package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskControl;
import com.tss.grc.module.risk.repository.RiskControlRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk.service.RiskExposureService.ExposureResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskExposureServiceTest {

    private static final Long ORG = 1L;
    private static final Long RID = 7L;

    @Mock RiskRepository risks;
    @Mock RiskControlRepository riskControls;
    @Mock AuditLogService audit;
    @InjectMocks RiskExposureService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private Risk risk(String status, Integer inhL, Integer inhI, Integer residualScore) {
        Risk r = new Risk();
        r.setId(RID); r.setOrganisationId(ORG); r.setStatus(status);
        r.setInherentLikelihood(inhL); r.setInherentImpact(inhI);
        r.setResidualScore(residualScore);
        return r;
    }
    private RiskControl ctrl(String eff, Double contribPct) {
        RiskControl c = new RiskControl();
        c.setEffectiveness(eff);
        if (contribPct != null) c.setContributionPct(BigDecimal.valueOf(contribPct));
        return c;
    }

    @Test void noControls_residualEqualsInherent_andFlaggedUncontrolled() {
        Risk r = risk("OPEN", 5, 4, 12);   // stale residual 12, no controls
        when(risks.findById(RID)).thenReturn(Optional.of(r));
        when(riskControls.findByOrganisationIdAndRiskIdOrderByIdAsc(ORG, RID)).thenReturn(List.of());
        when(risks.save(any())).thenAnswer(i -> i.getArgument(0));

        ExposureResult out = service.recomputeResidual(RID);

        assertThat(out.applied()).isTrue();
        assertThat(out.uncontrolled()).isTrue();
        assertThat(out.aggregateMitigation()).isEqualTo(0.0);
        assertThat(out.newResidualScore()).isEqualTo(20);     // = inherent (no mitigation)
        assertThat(r.getResidualLikelihood()).isEqualTo(5);
        assertThat(r.getResidualImpact()).isEqualTo(4);
    }

    @Test void effectiveControl_reducesResidual_cappedAt75pct() {
        Risk r = risk("OPEN", 5, 4, 20);
        when(risks.findById(RID)).thenReturn(Optional.of(r));
        when(riskControls.findByOrganisationIdAndRiskIdOrderByIdAsc(ORG, RID))
            .thenReturn(List.of(ctrl("EFFECTIVE", null)));   // weight 1/1, factor 1.0 → sum 1.0, capped 0.75
        when(risks.save(any())).thenAnswer(i -> i.getArgument(0));

        ExposureResult out = service.recomputeResidual(RID);

        assertThat(out.aggregateMitigation()).isEqualTo(0.75);
        assertThat(out.newResidualScore()).isLessThan(20);    // mitigated
        assertThat(out.newResidualScore()).isEqualTo(6);      // round(5·.5)=3 × round(4·.5)=2
        assertThat(out.uncontrolled()).isFalse();
    }

    @Test void weightedByContributionPct() {
        Risk r = risk("OPEN", 5, 4, 20);
        when(risks.findById(RID)).thenReturn(Optional.of(r));
        when(riskControls.findByOrganisationIdAndRiskIdOrderByIdAsc(ORG, RID))
            .thenReturn(List.of(ctrl("NEEDS_IMPROVEMENT", 50.0)));  // 0.5 × 0.50 = 0.25
        when(risks.save(any())).thenAnswer(i -> i.getArgument(0));

        ExposureResult out = service.recomputeResidual(RID);

        assertThat(out.aggregateMitigation()).isEqualTo(0.25);
        assertThat(out.newResidualScore()).isEqualTo(12);     // round(5·√.75)=4 × round(4·√.75)=3
    }

    @Test void notTestedControl_givesNoCredit_butIsControlled() {
        Risk r = risk("OPEN", 5, 4, 20);
        when(risks.findById(RID)).thenReturn(Optional.of(r));
        when(riskControls.findByOrganisationIdAndRiskIdOrderByIdAsc(ORG, RID))
            .thenReturn(List.of(ctrl("NOT_TESTED", null)));
        when(risks.save(any())).thenAnswer(i -> i.getArgument(0));

        ExposureResult out = service.recomputeResidual(RID);

        assertThat(out.aggregateMitigation()).isEqualTo(0.0);
        assertThat(out.newResidualScore()).isEqualTo(20);     // no mitigation until tested
        assertThat(out.uncontrolled()).isFalse();             // a link exists
    }

    @Test void terminalRisk_isFrozen_noRecompute() {
        Risk r = risk("CLOSED", 5, 4, 9);
        r.setResidualLikelihood(3); r.setResidualImpact(3);
        when(risks.findById(RID)).thenReturn(Optional.of(r));

        ExposureResult out = service.recomputeResidual(RID);

        assertThat(out.applied()).isFalse();
        assertThat(r.getResidualLikelihood()).isEqualTo(3);   // untouched
        verify(risks, never()).save(any());
    }

    @Test void nullInherent_cannotDerive_noRecompute() {
        Risk r = risk("OPEN", null, null, null);
        when(risks.findById(RID)).thenReturn(Optional.of(r));

        ExposureResult out = service.recomputeResidual(RID);

        assertThat(out.applied()).isFalse();
        verify(risks, never()).save(any());
    }

    @Test void listUncontrolled_delegatesToTenantScopedQuery() {
        when(risks.findUncontrolled(ORG)).thenReturn(List.of(risk("OPEN", 5, 5, 25)));
        assertThat(service.listUncontrolledRisks()).hasSize(1);
        verify(risks).findUncontrolled(eq(ORG));
    }
}
