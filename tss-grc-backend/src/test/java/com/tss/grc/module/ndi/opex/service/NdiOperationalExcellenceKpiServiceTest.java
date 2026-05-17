package com.tss.grc.module.ndi.opex.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.opex.entity.NdiOperationalExcellenceKpi;
import com.tss.grc.module.ndi.opex.repository.NdiOperationalExcellenceKpiRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Locks Rule 13 — direction-aware OpEx KPI status computation.
 *
 * <p>"Higher is better" KPIs (target ≥ baseline) and "lower is better"
 * KPIs (target &lt; baseline) must score against the right end of the
 * scale. Service runs the math on every create / update / update-actual
 * so a row never persists with a stale status.
 */
@ExtendWith(MockitoExtension.class)
class NdiOperationalExcellenceKpiServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock  private NdiOperationalExcellenceKpiRepository repo;
    @InjectMocks private NdiOperationalExcellenceKpiService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static NdiOperationalExcellenceKpi kpi(BigDecimal baseline, BigDecimal target, BigDecimal actual) {
        NdiOperationalExcellenceKpi k = new NdiOperationalExcellenceKpi();
        k.setCycleId(1L);
        k.setCategory("Data Quality");
        k.setIndicatorNameEn("Demo KPI");
        k.setBaseline(baseline);
        k.setTarget(target);
        k.setActual(actual);
        return k;
    }

    @BeforeEach
    void mockRepoSave() {
        when(repo.save(any(NdiOperationalExcellenceKpi.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Higher-is-better (catalog coverage 45→90) ──────────────────

    @Test void higherIsBetter_atOrAboveTarget_achieved() {
        NdiOperationalExcellenceKpi k = service.create(
            kpi(new BigDecimal("45"), new BigDecimal("90"), new BigDecimal("92")));
        assertThat(k.getStatus()).isEqualTo("ACHIEVED");
    }

    @Test void higherIsBetter_progressAt90Percent_onTrack() {
        // progress = (actual-baseline)/(target-baseline)
        //          = (86-45)/(90-45) = 41/45 = 0.911 → ON_TRACK band (≥ 0.90)
        NdiOperationalExcellenceKpi k = service.create(
            kpi(new BigDecimal("45"), new BigDecimal("90"), new BigDecimal("86")));
        assertThat(k.getStatus()).isEqualTo("ON_TRACK");
    }

    @Test void higherIsBetter_progressAt70Percent_atRisk() {
        // (78-45)/(90-45) = 33/45 = 0.733 → AT_RISK band (≥ 0.70, < 0.90)
        NdiOperationalExcellenceKpi k = service.create(
            kpi(new BigDecimal("45"), new BigDecimal("90"), new BigDecimal("78")));
        assertThat(k.getStatus()).isEqualTo("AT_RISK");
    }

    @Test void higherIsBetter_progressBelow70Percent_behindPlan() {
        // (60-45)/(90-45) = 15/45 = 0.333 → BEHIND_PLAN
        NdiOperationalExcellenceKpi k = service.create(
            kpi(new BigDecimal("45"), new BigDecimal("90"), new BigDecimal("60")));
        assertThat(k.getStatus()).isEqualTo("BEHIND_PLAN");
    }

    // ── Lower-is-better (mean-time-to-resolve 10→2 hours) ──────────

    @Test void lowerIsBetter_atOrBelowTarget_achieved() {
        NdiOperationalExcellenceKpi k = service.create(
            kpi(new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("2")));
        assertThat(k.getStatus()).isEqualTo("ACHIEVED");
    }

    @Test void lowerIsBetter_aboveTarget_progressesAgainstTheRange() {
        // baseline=10 target=2 actual=3
        // For lower-is-better: closer to target = better.
        // The service uses progressFraction = (actual-baseline)/(target-baseline)
        //   = (3-10)/(2-10) = -7/-8 = 0.875 → AT_RISK band
        NdiOperationalExcellenceKpi k = service.create(
            kpi(new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("3")));
        assertThat(k.getStatus()).isEqualTo("AT_RISK");
    }

    // ── Edge cases ────────────────────────────────────────────────

    @Test void actualNull_dataSourceNull_dataMissing() {
        NdiOperationalExcellenceKpi k = kpi(new BigDecimal("0"), new BigDecimal("100"), null);
        k.setDataSource(null);
        service.create(k);
        assertThat(k.getStatus()).isEqualTo("DATA_MISSING");
    }

    @Test void actualNull_dataSourceSet_notStarted() {
        NdiOperationalExcellenceKpi k = kpi(new BigDecimal("0"), new BigDecimal("100"), null);
        k.setDataSource("data-warehouse.kpi_view");
        service.create(k);
        assertThat(k.getStatus()).isEqualTo("NOT_STARTED");
    }
}
