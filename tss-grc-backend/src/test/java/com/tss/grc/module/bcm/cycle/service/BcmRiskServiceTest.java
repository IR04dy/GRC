package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmRisk;
import com.tss.grc.module.bcm.cycle.repository.BcmRiskRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BcmRiskService — Sprint BCM-1 scoring + lifecycle locks.
 *
 * <ul>
 *   <li>Scale validation: likelihood / impact ∈ [1,5].</li>
 *   <li>Threat category required and validated.</li>
 *   <li>Inherent score = L × I.</li>
 *   <li>Residual score reduced by control_effectiveness:
 *       EFFECTIVE ÷4 · NEEDS_IMPROVEMENT ÷2 · INEFFECTIVE/NOT_TESTED ×1.</li>
 *   <li>Lifecycle DRAFT → ASSESSED → TREATMENT_PLANNED →
 *       ACCEPTED|MITIGATED → CLOSED.</li>
 *   <li>ASSESSED requires likelihood + impact.</li>
 *   <li>TREATMENT_PLANNED transition publishes a domain event for F8.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BcmRiskServiceTest {

    private static final Long ORG_ID = 7L;

    @Mock private BcmRiskRepository risks;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private BcmRiskService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static BcmRisk risk(int likelihood, int impact, String effectiveness) {
        BcmRisk r = new BcmRisk();
        r.setId(1L); r.setOrganisationId(ORG_ID);
        r.setTitleEn("Cloud region outage");
        r.setThreatCategory("CLOUD_OUTAGE");
        r.setStatus("DRAFT");
        r.setLikelihood(likelihood);
        r.setImpact(impact);
        r.setControlEffectiveness(effectiveness);
        return r;
    }

    @Test void scaleOutOfRangeRejected_likelihood() {
        BcmRisk body = risk(1, 3, null); body.setLikelihood(6);
        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("likelihood");
    }

    @Test void scaleOutOfRangeRejected_impact() {
        BcmRisk body = risk(3, 0, null);
        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("impact");
    }

    @Test void invalidThreatCategoryRejected() {
        BcmRisk body = risk(3, 3, null); body.setThreatCategory("METEOR_STRIKE");
        assertThatThrownBy(() -> service.create(body))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("threatCategory");
    }

    @Test void create_computesInherentAndResidualScores() {
        BcmRisk body = risk(4, 5, "EFFECTIVE");  // inherent = 20 ; residual = 20/4 = 5
        when(risks.save(any())).thenAnswer(inv -> {
            BcmRisk r = inv.getArgument(0); r.setId(99L); return r;
        });
        BcmRisk saved = service.create(body);
        assertThat(saved.getInherentRiskScore()).isEqualTo(20);
        assertThat(saved.getResidualRiskScore()).isEqualTo(5);
    }

    @Test void residual_needsImprovementHalves() {
        BcmRisk r = risk(4, 4, "NEEDS_IMPROVEMENT");
        service.recalcScores(r);
        assertThat(r.getInherentRiskScore()).isEqualTo(16);
        assertThat(r.getResidualRiskScore()).isEqualTo(8);
    }

    @Test void residual_notTestedEqualsInherent() {
        BcmRisk r = risk(3, 4, "NOT_TESTED");
        service.recalcScores(r);
        assertThat(r.getResidualRiskScore()).isEqualTo(r.getInherentRiskScore()).isEqualTo(12);
    }

    @Test void transition_draftToAssessedRequiresScores() {
        BcmRisk r = risk(0, 0, null);
        r.setLikelihood(null); r.setImpact(null);
        when(risks.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.transition(1L, "ASSESSED", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("likelihood");
    }

    @Test void transition_draftToAssessedOk() {
        BcmRisk r = risk(3, 3, "NEEDS_IMPROVEMENT");
        when(risks.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(r));
        when(risks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BcmRisk out = service.transition(1L, "ASSESSED", "alice");
        assertThat(out.getStatus()).isEqualTo("ASSESSED");
    }

    @Test void transition_treatmentPlanned_publishesDomainEvent() {
        BcmRisk r = risk(4, 5, "INEFFECTIVE");
        r.setStatus("ASSESSED");
        service.recalcScores(r);
        when(risks.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(r));
        when(risks.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transition(1L, "TREATMENT_PLANNED", "alice");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events, times(1)).publishEvent(ev.capture());
        DomainEvent published = ev.getValue();
        assertThat(published.triggerEvent()).isEqualTo("BCM_RISK_TREATMENT_PLANNED");
        assertThat(published.entityType()).isEqualTo("BcmRisk");
        assertThat(published.payload()).containsEntry("residual_risk_score", 20);
        assertThat(published.payload()).containsEntry("linked_erm", false);
    }

    @Test void transition_illegalJumpRejected() {
        BcmRisk r = risk(3, 3, "EFFECTIVE"); r.setStatus("DRAFT");
        when(risks.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.transition(1L, "CLOSED", null))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test void delete_activeStatusRefused() {
        BcmRisk r = risk(3, 3, "NEEDS_IMPROVEMENT"); r.setStatus("ASSESSED");
        when(risks.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("active");
    }
}
