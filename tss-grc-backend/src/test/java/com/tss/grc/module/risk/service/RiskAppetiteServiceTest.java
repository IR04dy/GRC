package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.risk.dto.RiskAppetiteRequest;
import com.tss.grc.module.risk.entity.AppetiteBreachEvent;
import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import com.tss.grc.module.risk.repository.AppetiteBreachEventRepository;
import com.tss.grc.module.risk.repository.RiskAppetiteStatementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** E-H — register→appetite exposure aggregation + F8 breach escalation. */
@ExtendWith(MockitoExtension.class)
class RiskAppetiteServiceTest {

    private static final Long ORG = 1L;

    @Mock RiskAppetiteStatementRepository repo;
    @Mock AppetiteBreachEventRepository breachRepo;
    @Mock RiskRepository risks;
    @Mock ApplicationEventPublisher events;
    @InjectMocks RiskAppetiteService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private RiskAppetiteStatement stmt(Long categoryId, int upper) {
        RiskAppetiteStatement s = new RiskAppetiteStatement();
        s.setId(1L); s.setOrganisationId(ORG); s.setStatus("APPROVED");
        s.setCategoryId(categoryId); s.setTitleEn("Cyber appetite");
        s.setToleranceUpper(BigDecimal.valueOf(upper));
        return s;
    }

    @Test void recompute_setsExposureFromRegister_andEscalatesBreach() {
        RiskAppetiteStatement s = stmt(5L, 8);
        when(repo.findByOrganisationIdAndStatus(ORG, "APPROVED")).thenReturn(List.of(s));
        when(risks.findMaxResidualScoreByCategory(ORG, 5L)).thenReturn(12);   // worst open residual
        when(breachRepo.existsByAppetiteIdAndStatus(1L, "OPEN")).thenReturn(false);  // no open breach yet
        when(breachRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        RiskAppetiteService.ExposureRecompute res = service.recomputeExposureFromRegister();

        assertThat(s.getCurrentExposure()).isEqualByComparingTo("12");   // pulled from register
        assertThat(res.statementsProcessed()).isEqualTo(1);
        assertThat(res.breachesRaised()).isEqualTo(1);                    // 12 > 8 → breach
        verify(repo).save(s);
        verify(events).publishEvent(any(DomainEvent.class));             // F8 escalation fired
    }

    @Test void recompute_withinTolerance_noBreach_noEscalation() {
        RiskAppetiteStatement s = stmt(5L, 8);
        when(repo.findByOrganisationIdAndStatus(ORG, "APPROVED")).thenReturn(List.of(s));
        when(risks.findMaxResidualScoreByCategory(ORG, 5L)).thenReturn(5);   // within band

        RiskAppetiteService.ExposureRecompute res = service.recomputeExposureFromRegister();

        assertThat(res.breachesRaised()).isZero();
        verify(breachRepo, never()).save(any());
        verify(events, never()).publishEvent(any(DomainEvent.class));
    }

    @Test void recompute_isIdempotent_whenOpenBreachAlreadyExists() {
        RiskAppetiteStatement s = stmt(5L, 8);
        when(repo.findByOrganisationIdAndStatus(ORG, "APPROVED")).thenReturn(List.of(s));
        when(risks.findMaxResidualScoreByCategory(ORG, 5L)).thenReturn(12);     // still breached
        when(breachRepo.existsByAppetiteIdAndStatus(1L, "OPEN")).thenReturn(true);  // breach already open

        RiskAppetiteService.ExposureRecompute res = service.recomputeExposureFromRegister();

        assertThat(res.breachesRaised()).isZero();           // no duplicate
        verify(breachRepo, never()).save(any());
        verify(events, never()).publishEvent(any(DomainEvent.class));
    }

    @Test void recompute_skipsStatementsWithoutCategory() {
        when(repo.findByOrganisationIdAndStatus(ORG, "APPROVED")).thenReturn(List.of(stmt(null, 8)));

        RiskAppetiteService.ExposureRecompute res = service.recomputeExposureFromRegister();

        assertThat(res.statementsProcessed()).isZero();
        verify(risks, never()).findMaxResidualScoreByCategory(any(), any());
        verify(repo, never()).save(any());
    }

    // ── F2-400 — update() breach re-evaluation guards on VALUE change (compareTo), not equals ──

    /** Builds a partial-update request that carries only currentExposure. */
    private static RiskAppetiteRequest reqWithExposure(BigDecimal exposure) {
        return new RiskAppetiteRequest(
            null, null, null, null, null, null, null, null, null, null, exposure, null);
    }

    /**
     * F2-400 — the discriminating leg. Stored exposure "7" (scale 0, as
     * recomputeExposureFromRegister writes via BigDecimal.valueOf(int)); a client PUT
     * resends the SAME value as "7.00" (scale 2). Pre-fix ({@code !equals(previous)})
     * BigDecimal.equals compares scale, so "7.00" ≠ "7" → treated as a change →
     * evaluateExposure → EXCEEDED with no OPEN breach → a spurious breach + a spurious
     * APPETITE_BREACHED F8 escalation on an edit that changed nothing. Post-fix
     * (compareTo == 0) the value is unchanged, so no re-evaluation.
     */
    @Test void update_sameExposureAtDifferentScale_doesNotReEvaluateBreach() {
        RiskAppetiteStatement s = stmt(5L, 5);           // toleranceUpper = 5 → 7 is EXCEEDED
        s.setCurrentExposure(new BigDecimal("7"));
        when(repo.findById(1L)).thenReturn(Optional.of(s));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.update(1L, reqWithExposure(new BigDecimal("7.00")));

        verify(breachRepo, never()).save(any());
        verify(events, never()).publishEvent(any(DomainEvent.class));
    }

    /** F2-400 guard (F2-214) — a REAL value increase still evaluates + escalates, so the
     *  fix suppresses only the spurious scale-only re-fire, never a genuine change. */
    @Test void update_realExposureIncrease_stillEscalatesBreach() {
        RiskAppetiteStatement s = stmt(5L, 5);           // upper = 5
        s.setCurrentExposure(new BigDecimal("3"));        // was within tolerance
        when(repo.findById(1L)).thenReturn(Optional.of(s));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.update(1L, reqWithExposure(new BigDecimal("9")));  // real increase → CRITICAL

        verify(events).publishEvent(any(DomainEvent.class));
    }

    /** F2-400 guard (F2-195) — a first-time exposure set (previous null) is a change and
     *  still evaluates; this exercises the null branch of the guard, which a naive fix
     *  (compareTo without the null-check) would NPE on. */
    @Test void update_firstExposureSetFromNull_stillEscalatesBreach() {
        RiskAppetiteStatement s = stmt(5L, 5);           // upper = 5
        s.setCurrentExposure(null);                       // no prior exposure
        when(repo.findById(1L)).thenReturn(Optional.of(s));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.update(1L, reqWithExposure(new BigDecimal("9")));  // first value → CRITICAL

        verify(events).publishEvent(any(DomainEvent.class));
    }
}
