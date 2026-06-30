package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.automation.event.DomainEvent;
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
}
