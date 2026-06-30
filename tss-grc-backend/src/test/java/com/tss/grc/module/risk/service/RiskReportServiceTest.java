package com.tss.grc.module.risk.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.risk.dto.RiskReportDtos.AppetiteBreachRow;
import com.tss.grc.module.risk.dto.RiskReportDtos.AuditPackage;
import com.tss.grc.module.risk.dto.RiskReportDtos.RegisterStats;
import com.tss.grc.module.risk.entity.AppetiteBreachEvent;
import com.tss.grc.module.risk.entity.Risk;
import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import com.tss.grc.module.risk.repository.AppetiteBreachEventRepository;
import com.tss.grc.module.risk.repository.RiskAcceptanceRepository;
import com.tss.grc.module.risk.repository.RiskAppetiteStatementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RiskReportService — ERM epic E-K (reporting + audit evidence package).
 */
@ExtendWith(MockitoExtension.class)
class RiskReportServiceTest {

    private static final Long ORG = 1L;

    @Mock RiskRepository risks;
    @Mock RiskAppetiteStatementRepository appetites;
    @Mock AppetiteBreachEventRepository breaches;
    @Mock RiskAcceptanceRepository acceptances;
    @Mock AuditLogService audit;
    @InjectMocks RiskReportService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Risk risk(long id, int residual, String level) {
        Risk r = new Risk(); r.setId(id); r.setOrganisationId(ORG);
        r.setTitleEn("R" + id); r.setStatus("OPEN"); r.setRiskLevel(level); r.setResidualScore(residual);
        return r;
    }

    private static RiskAppetiteStatement appetite(long id) {
        RiskAppetiteStatement s = new RiskAppetiteStatement();
        s.setId(id); s.setOrganisationId(ORG); s.setTitleEn("Credit appetite");
        s.setCategoryId(3L); s.setToleranceUpper(new BigDecimal("8")); s.setCurrentExposure(new BigDecimal("20"));
        return s;
    }

    private static AppetiteBreachEvent breach(long id, long appetiteId, String status) {
        AppetiteBreachEvent e = new AppetiteBreachEvent();
        e.setId(id); e.setAppetiteId(appetiteId); e.setBreachSeverity("CRITICAL");
        e.setObservedValue(new BigDecimal("20")); e.setStatus(status); e.setBreachedAt(LocalDateTime.now());
        return e;
    }

    @Test void topRisks_mapsAndCapsLimit() {
        when(risks.findTopActiveByResidual(eq(ORG), any(Pageable.class)))
            .thenReturn(List.of(risk(5, 20, "CRITICAL"), risk(6, 12, "HIGH")));

        var out = service.topRisks(10);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).riskCode()).isEqualTo("RISK-0005");
        assertThat(out.get(0).residualScore()).isEqualTo(20);
        assertThat(out.get(1).riskLevel()).isEqualTo("HIGH");
    }

    @Test void appetiteBreaches_joinsStatementContext() {
        when(appetites.findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(appetite(3L))));
        when(breaches.findByAppetiteIdOrderByBreachedAtDesc(3L))
            .thenReturn(List.of(breach(1L, 3L, "OPEN"), breach(2L, 3L, "ACKNOWLEDGED")));

        List<AppetiteBreachRow> all = service.appetiteBreaches(false);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).appetiteTitleEn()).isEqualTo("Credit appetite");
        assertThat(all.get(0).toleranceUpper()).isEqualByComparingTo("8");

        List<AppetiteBreachRow> open = service.appetiteBreaches(true);
        assertThat(open).hasSize(1);
        assertThat(open.get(0).status()).isEqualTo("OPEN");
    }

    @Test void registerStats_aggregatesGroupedCountsAndPosture() {
        when(risks.countGroupedByLevel(ORG)).thenReturn(List.of(
            new Object[]{"CRITICAL", 1L}, new Object[]{"HIGH", 2L}, new Object[]{"LOW", 5L}));
        when(risks.countGroupedByStatus(ORG)).thenReturn(List.of(
            new Object[]{"OPEN", 6L}, new Object[]{"CLOSED", 2L}));
        when(risks.countByOrganisationId(ORG)).thenReturn(8L);
        when(risks.findReviewOverdue(eq(ORG), any(LocalDate.class))).thenReturn(List.of(risk(1, 4, "LOW")));
        when(acceptances.countByOrganisationIdAndStatus(ORG, "APPROVED")).thenReturn(2L);
        when(appetites.findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(appetite(3L))));
        when(breaches.findByAppetiteIdOrderByBreachedAtDesc(3L)).thenReturn(List.of(breach(1L, 3L, "OPEN")));

        RegisterStats s = service.registerStats();

        assertThat(s.totalRisks()).isEqualTo(8);
        assertThat(s.byLevel()).containsEntry("CRITICAL", 1L).containsEntry("LOW", 5L);
        assertThat(s.byStatus()).containsEntry("OPEN", 6L);
        assertThat(s.reviewOverdue()).isEqualTo(1);
        assertThat(s.activeAcceptances()).isEqualTo(2);
        assertThat(s.openAppetiteBreaches()).isEqualTo(1);
    }

    @Test void auditPackage_assembles_andAuditsTheExport() {
        when(risks.findTopActiveByResidual(eq(ORG), any(Pageable.class))).thenReturn(List.of(risk(5, 20, "CRITICAL")));
        when(risks.countGroupedByLevel(ORG)).thenReturn(List.<Object[]>of(new Object[]{"CRITICAL", 1L}));
        when(risks.countGroupedByStatus(ORG)).thenReturn(List.<Object[]>of(new Object[]{"OPEN", 1L}));
        when(risks.countByOrganisationId(ORG)).thenReturn(1L);
        when(risks.findReviewOverdue(eq(ORG), any(LocalDate.class))).thenReturn(List.of());
        when(acceptances.countByOrganisationIdAndStatus(ORG, "APPROVED")).thenReturn(0L);
        when(appetites.findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(appetite(3L))));
        when(breaches.findByAppetiteIdOrderByBreachedAtDesc(3L)).thenReturn(List.of(breach(1L, 3L, "OPEN")));

        AuditPackage pkg = service.auditPackage();

        assertThat(pkg.organisationId()).isEqualTo(ORG);
        assertThat(pkg.generatedAt()).isNotNull();
        assertThat(pkg.topRisks()).hasSize(1);
        assertThat(pkg.openAppetiteBreaches()).hasSize(1);
        assertThat(pkg.stats().totalRisks()).isEqualTo(1);
        verify(audit).log(eq("RISK_AUDIT_PACKAGE_EXPORTED"), eq("Risk"), any(), any());
    }
}
