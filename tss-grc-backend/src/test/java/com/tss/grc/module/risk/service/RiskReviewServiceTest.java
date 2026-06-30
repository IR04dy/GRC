package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.dto.RiskReviewDtos.ReassessDto;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskReview;
import com.tss.grc.module.risk.repository.RiskRepository;
import com.tss.grc.module.risk.repository.RiskReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskReviewService — ERM epic E-J.
 *
 * Locks the reassessment behaviour (re-score + clock reset + audit row +
 * F8 event) and the stale-review sweep (flag OVERDUE + idempotency).
 */
@ExtendWith(MockitoExtension.class)
class RiskReviewServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long RISK_ID   = 42L;

    @Mock private RiskRepository riskRepository;
    @Mock private RiskReviewRepository reviewRepository;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher events;
    @InjectMocks private RiskReviewService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Risk risk(String status, Long orgId) {
        Risk r = new Risk();
        r.setId(RISK_ID);
        r.setOrganisationId(orgId);
        r.setTitleEn("Sample risk");
        r.setStatus(status);
        r.setResidualLikelihood(2);
        r.setResidualImpact(3);
        r.setResidualScore(6);
        return r;
    }
    private static Risk risk(String status) { return risk(status, ORG_ID); }

    // ─── reassess ────────────────────────────────────────────────────

    @Test
    void reassess_rescore_appliesClampedLI_resetsClock_logsReview_emitsEvent() {
        Risk r = risk("MITIGATING");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.save(any(RiskReview.class))).thenAnswer(inv -> inv.getArgument(0));

        // residualLikelihood 9 must clamp to 5; impact 4 stays
        RiskReview rv = service.reassess(RISK_ID,
                new ReassessDto(9, 4, null, null, "Annual review", null), 7L, "cro");

        // re-scored on the risk (clamped)
        assertThat(r.getResidualLikelihood()).isEqualTo(5);
        assertThat(r.getResidualImpact()).isEqualTo(4);
        // clock reset: default cadence 365, status CURRENT, lastReviewedAt set
        assertThat(r.getReviewStatus()).isEqualTo("CURRENT");
        assertThat(r.getReviewDate()).isEqualTo(LocalDate.now().plusDays(365));
        assertThat(r.getLastReviewedAt()).isNotNull();
        // review row captures before/after + decision RESCORED (both L/I supplied)
        assertThat(rv.getDecision()).isEqualTo("RESCORED");
        assertThat(rv.getResidualLikelihoodBefore()).isEqualTo(2);
        assertThat(rv.getResidualImpactBefore()).isEqualTo(3);
        assertThat(rv.getResidualLikelihoodAfter()).isEqualTo(5);
        assertThat(rv.getReviewedByName()).isEqualTo("cro");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("RISK_REASSESSED");
    }

    @Test
    void reassess_confirmOnly_keepsLI_decisionCONFIRMED() {
        Risk r = risk("MITIGATING");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.save(any(RiskReview.class))).thenAnswer(inv -> inv.getArgument(0));

        RiskReview rv = service.reassess(RISK_ID,
                new ReassessDto(null, null, null, null, "Still valid", null), 7L, "cro");

        assertThat(rv.getDecision()).isEqualTo("CONFIRMED");
        assertThat(r.getResidualLikelihood()).isEqualTo(2);  // unchanged
        assertThat(r.getResidualImpact()).isEqualTo(3);
        assertThat(r.getReviewStatus()).isEqualTo("CURRENT");
    }

    @Test
    void reassess_cadenceOverride_setsNextReviewByNewCadence() {
        Risk r = risk("OPEN");
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(r));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.save(any(RiskReview.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reassess(RISK_ID, new ReassessDto(null, null, 90, "ESCALATE", null, null), 7L, "cro");

        assertThat(r.getReviewCadenceDays()).isEqualTo(90);
        assertThat(r.getReviewDate()).isEqualTo(LocalDate.now().plusDays(90));
    }

    @Test
    void reassess_otherOrg_throwsNotFound() {
        Risk foreign = risk("OPEN", OTHER_ORG);
        when(riskRepository.findById(RISK_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.reassess(RISK_ID, new ReassessDto(null, null, null, null, null, null), 7L, "x"))
            .isInstanceOf(EntityNotFoundException.class);
        verify(riskRepository, never()).save(any());
    }

    // ─── stale sweep ─────────────────────────────────────────────────

    @Test
    void sweep_flipsCurrentToOverdue_emitsEvent_returnsCount() {
        Risk a = risk("OPEN");       a.setId(1L); a.setReviewStatus("CURRENT"); a.setReviewDate(LocalDate.now().minusDays(5));
        Risk b = risk("MITIGATING"); b.setId(2L); b.setReviewStatus("CURRENT"); b.setReviewDate(LocalDate.now().minusDays(1));
        when(riskRepository.findReviewOverdue(eqOrg(), any(LocalDate.class))).thenReturn(List.of(a, b));
        when(riskRepository.save(any(Risk.class))).thenAnswer(inv -> inv.getArgument(0));

        int flagged = service.runOverdueSweepForTenant();

        assertThat(flagged).isEqualTo(2);
        assertThat(a.getReviewStatus()).isEqualTo("OVERDUE");
        assertThat(b.getReviewStatus()).isEqualTo("OVERDUE");
        verify(events, org.mockito.Mockito.times(2)).publishEvent(any(DomainEvent.class));
    }

    @Test
    void sweep_idempotent_skipsAlreadyOverdue() {
        Risk a = risk("OPEN"); a.setId(1L); a.setReviewStatus("OVERDUE"); a.setReviewDate(LocalDate.now().minusDays(5));
        when(riskRepository.findReviewOverdue(eqOrg(), any(LocalDate.class))).thenReturn(List.of(a));

        int flagged = service.runOverdueSweepForTenant();

        assertThat(flagged).isZero();
        verify(riskRepository, never()).save(any());
        verify(events, never()).publishEvent(any(DomainEvent.class));
    }

    private static Long eqOrg() { return org.mockito.ArgumentMatchers.eq(ORG_ID); }
}
