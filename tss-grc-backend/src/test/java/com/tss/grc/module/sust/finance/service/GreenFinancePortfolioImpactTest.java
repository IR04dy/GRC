package com.tss.grc.module.sust.finance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.finance.dto.GreenFinanceDtos.PortfolioImpactResponse;
import com.tss.grc.module.sust.finance.entity.GreenInstrument;
import com.tss.grc.module.sust.finance.entity.GreenProject;
import com.tss.grc.module.sust.finance.entity.InstrumentAllocation;
import com.tss.grc.module.sust.finance.repository.AllocationReportRepository;
import com.tss.grc.module.sust.finance.repository.GreenInstrumentRepository;
import com.tss.grc.module.sust.finance.repository.GreenProjectRepository;
import com.tss.grc.module.sust.finance.repository.InstrumentAllocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * GF-1 — the portfolio rollup's double-count protection (plan §4.2 last
 * formula): a project financed by two instruments delivers its impact ONCE,
 * split pro-rata; an under-financed project contributes only its funded share.
 */
@ExtendWith(MockitoExtension.class)
class GreenFinancePortfolioImpactTest {

    private static final Long ORG = 1L;

    @Mock private GreenInstrumentRepository instruments;
    @Mock private GreenProjectRepository projects;
    @Mock private InstrumentAllocationRepository allocations;
    @Mock private AllocationReportRepository reports;
    @Mock private AuditLogService audit;
    @InjectMocks private GreenFinanceService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static GreenInstrument instr(Long id, String code) throws Exception {
        GreenInstrument gi = new GreenInstrument();
        setId(gi, id);
        gi.setOrganisationId(ORG);
        gi.setInstrumentCode(code);
        return gi;
    }

    private static GreenProject project(Long id, String code, String cost, String tco2e) throws Exception {
        GreenProject p = new GreenProject();
        setId(p, id);
        p.setOrganisationId(ORG);
        p.setProjectCode(code);
        p.setTotalCostSarMn(new BigDecimal(cost));
        p.setActualTco2eAvoidedYr(new BigDecimal(tco2e));
        return p;
    }

    private static InstrumentAllocation alloc(Long instrumentId, Long projectId, String amount) {
        InstrumentAllocation a = new InstrumentAllocation();
        a.setOrganisationId(ORG);
        a.setInstrumentId(instrumentId);
        a.setProjectId(projectId);
        a.setAmountSarMn(new BigDecimal(amount));
        a.setAllocationDate(LocalDate.of(2026, 1, 1));
        a.setStatus("APPROVED");
        return a;
    }

    /** BaseEntity id has no public setter — reflect it in for the mocks. */
    private static void setId(Object entity, Long id) throws Exception {
        Class<?> c = entity.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, id);
                return;
            } catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException("id");
    }

    @Test
    void sharedProject_impactCountedOnce_andSplitProRata() throws Exception {
        // One 100-SAR-mn project avoiding 500 tCO2e/yr, financed 60/60 by two sukuk
        // (raw share sum 1.2) — the rollup counts 100 %, not 120 %.
        GreenInstrument a = instr(1L, "GS-A"), b = instr(2L, "GS-B");
        GreenProject p = project(10L, "PRJ-1", "100", "500");
        when(instruments.findByOrganisationIdAndIsDeletedFalseOrderByInstrumentCodeAsc(ORG))
            .thenReturn(List.of(a, b));
        when(allocations.findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(ORG, 1L))
            .thenReturn(List.of(alloc(1L, 10L, "60")));
        when(allocations.findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(ORG, 2L))
            .thenReturn(List.of(alloc(2L, 10L, "60")));
        when(projects.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, 10L)).thenReturn(Optional.of(p));

        PortfolioImpactResponse r = service.portfolioImpact();

        assertThat(r.tco2eAvoidedYr()).isEqualByComparingTo("500");        // NOT 600
        assertThat(r.projectsCapped()).isEqualTo(1);
        assertThat(r.byProject()).singleElement().satisfies(pa -> {
            assertThat(pa.rawShareSum()).isEqualByComparingTo("1.2");
            assertThat(pa.countedShare()).isEqualByComparingTo("1");
            assertThat(pa.dedupApplied()).isTrue();
        });
        // 0.6 × (1/1.2) = 0.5 each — the single 100 % split pro-rata.
        assertThat(r.byInstrument()).hasSize(2)
            .allSatisfy(c -> assertThat(c.tco2eAvoidedYr()).isEqualByComparingTo("250"));
    }

    @Test
    void underFinancedProject_contributesOnlyItsFundedShare() throws Exception {
        // 30/30 on a 100-cost project -> 60 % of 500 = 300; no cap involved.
        GreenInstrument a = instr(1L, "GS-A"), b = instr(2L, "GS-B");
        GreenProject p = project(10L, "PRJ-1", "100", "500");
        when(instruments.findByOrganisationIdAndIsDeletedFalseOrderByInstrumentCodeAsc(ORG))
            .thenReturn(List.of(a, b));
        when(allocations.findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(ORG, 1L))
            .thenReturn(List.of(alloc(1L, 10L, "30")));
        when(allocations.findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(ORG, 2L))
            .thenReturn(List.of(alloc(2L, 10L, "30")));
        when(projects.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, 10L)).thenReturn(Optional.of(p));

        PortfolioImpactResponse r = service.portfolioImpact();

        assertThat(r.tco2eAvoidedYr()).isEqualByComparingTo("300");
        assertThat(r.projectsCapped()).isZero();
        assertThat(r.byProject().get(0).dedupApplied()).isFalse();
    }

    @Test
    void proposedAllocations_doNotCount() throws Exception {
        GreenInstrument a = instr(1L, "GS-A");
        GreenProject p = project(10L, "PRJ-1", "100", "500");
        InstrumentAllocation proposed = alloc(1L, 10L, "60");
        proposed.setStatus("PROPOSED");
        when(instruments.findByOrganisationIdAndIsDeletedFalseOrderByInstrumentCodeAsc(ORG))
            .thenReturn(List.of(a));
        when(allocations.findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(ORG, 1L))
            .thenReturn(List.of(proposed, alloc(1L, 10L, "20")));
        when(projects.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, 10L)).thenReturn(Optional.of(p));

        PortfolioImpactResponse r = service.portfolioImpact();

        // Only the APPROVED 20 counts: 0.2 × 500 = 100.
        assertThat(r.tco2eAvoidedYr()).isEqualByComparingTo("100");
    }
}
