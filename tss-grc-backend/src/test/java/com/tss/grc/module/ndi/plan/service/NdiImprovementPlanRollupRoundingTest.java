package com.tss.grc.module.ndi.plan.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.audit.service.NdiAuditPublisher;
import com.tss.grc.module.ndi.gap.entity.NdiCorrectiveAction;
import com.tss.grc.module.ndi.gap.repository.NdiCorrectiveActionRepository;
import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlan;
import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlanItem;
import com.tss.grc.module.ndi.plan.repository.NdiImprovementPlanItemRepository;
import com.tss.grc.module.ndi.plan.repository.NdiImprovementPlanRepository;
import com.tss.grc.module.ndi.plan.service.NdiImprovementPlanService.PlanRollup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-382 — {@code NdiImprovementPlanService} averaged CAPA {@code progress_pct} with plain int/int
 * division at two sites: {@code rollup()} (the {@code PlanRollup.progressPct} DTO field returned to
 * callers) and {@code recomputeRollup()} (the stored {@code ndi_improvement_plan.progress_pct}
 * column). Integer division truncates toward zero, so a mean of 50.5 stored 50 — a consistent
 * downward bias of up to ~1pp on a governance completion figure. The sibling
 * {@code NdiGapService#aggregate} computes the same "mean of CAPA progress_pct" correctly as
 * {@code (int) Math.round((double) progSum / n)} (F2-215 two-of-N: the correct idiom sat in the
 * module the whole time). Both plan sites now round half-up to match.
 *
 * <p>Hermetic (Mockito, no DB — the defect is pure arithmetic in the service, so the average is
 * asserted directly rather than the stored bytes; F2-320 / F2-325). The service had no test at all
 * before this (F2-248).
 *
 * <p>Legs 1–2 discriminate (a mean of 50.5 rounds to 51; the pre-fix truncation gives 50). Leg 3 is
 * a regression guard: a mean of 10.33 rounds DOWN to 10, proving the fix is round-half-up and not a
 * blanket increment — it passes on both the pre-fix and fixed trees (round == truncate there).
 */
@ExtendWith(MockitoExtension.class)
class NdiImprovementPlanRollupRoundingTest {

    private static final Long ORG     = 1L;
    private static final Long PLAN_ID = 7L;

    @Mock private NdiImprovementPlanRepository     plans;
    @Mock private NdiImprovementPlanItemRepository items;
    @Mock private NdiCorrectiveActionRepository    capas;
    @Mock private NdiAuditPublisher                audit;
    @InjectMocks private NdiImprovementPlanService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static NdiImprovementPlan plan(String status) {
        NdiImprovementPlan p = new NdiImprovementPlan();
        p.setId(PLAN_ID);
        p.setStatus(status);
        return p;
    }

    private static NdiImprovementPlanItem item(long id, long capaId) {
        NdiImprovementPlanItem r = new NdiImprovementPlanItem();
        r.setId(id);
        r.setCapaId(capaId);
        r.setPriority("HIGH");   // byPriority merges into a TreeMap — must be non-null
        return r;
    }

    private static NdiCorrectiveAction capa(long id, int pct) {
        NdiCorrectiveAction c = new NdiCorrectiveAction();
        c.setId(id);
        c.setProgressPct(pct);
        c.setStatus("IN_PROGRESS");
        return c;
    }

    @Test
    void rollupDtoAveragesProgressRoundedHalfUp() {
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG)).thenReturn(Optional.of(plan("IN_FLIGHT")));
        when(items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(ORG, PLAN_ID))
            .thenReturn(List.of(item(1L, 101L), item(2L, 102L)));
        when(capas.findByIdAndOrganisationId(101L, ORG)).thenReturn(Optional.of(capa(101L, 50)));
        when(capas.findByIdAndOrganisationId(102L, ORG)).thenReturn(Optional.of(capa(102L, 51)));

        PlanRollup r = service.rollup(PLAN_ID);

        // mean(50, 51) = 50.5 → 51. Pre-fix int/int truncation gave 50.
        assertThat(r.progressPct()).isEqualTo(51);
    }

    @Test
    void storedColumnAveragesProgressRoundedHalfUp() {
        NdiImprovementPlan p = plan("IN_FLIGHT");
        when(plans.findByOrganisationIdOrderByPeriodStartDesc(ORG)).thenReturn(List.of(p));
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG)).thenReturn(Optional.of(p));
        when(items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(ORG, PLAN_ID))
            .thenReturn(List.of(item(1L, 101L), item(2L, 102L)));
        when(capas.findByIdAndOrganisationId(101L, ORG)).thenReturn(Optional.of(capa(101L, 50)));
        when(capas.findByIdAndOrganisationId(102L, ORG)).thenReturn(Optional.of(capa(102L, 51)));

        service.recomputeAllForOrg();

        ArgumentCaptor<NdiImprovementPlan> captor = ArgumentCaptor.forClass(NdiImprovementPlan.class);
        verify(plans).save(captor.capture());
        // mean(50, 51) = 50.5 → 51. Pre-fix int/int truncation gave 50.
        assertThat(captor.getValue().getProgressPct()).isEqualTo(51);
    }

    @Test
    void aFractionalMeanBelowHalfStillRoundsDown() {
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG)).thenReturn(Optional.of(plan("IN_FLIGHT")));
        when(items.findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(ORG, PLAN_ID))
            .thenReturn(List.of(item(1L, 101L), item(2L, 102L), item(3L, 103L)));
        when(capas.findByIdAndOrganisationId(101L, ORG)).thenReturn(Optional.of(capa(101L, 10)));
        when(capas.findByIdAndOrganisationId(102L, ORG)).thenReturn(Optional.of(capa(102L, 10)));
        when(capas.findByIdAndOrganisationId(103L, ORG)).thenReturn(Optional.of(capa(103L, 11)));

        PlanRollup r = service.rollup(PLAN_ID);

        // mean(10, 10, 11) = 10.33 → 10 (round-half-up, not a blanket +1). round == truncate here.
        assertThat(r.progressPct()).isEqualTo(10);
    }
}
