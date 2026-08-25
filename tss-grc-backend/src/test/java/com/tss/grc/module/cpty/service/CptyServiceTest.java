package com.tss.grc.module.cpty.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cpty.dto.CptyDtos.DashboardResponse;
import com.tss.grc.module.cpty.entity.Counterparty;
import com.tss.grc.module.cpty.repository.CounterpartyRepository;
import com.tss.grc.module.cpty.repository.CptyEventRepository;
import com.tss.grc.module.cpty.repository.CreditRatingRepository;
import com.tss.grc.module.cpty.repository.LargeExposureLimitRepository;
import com.tss.grc.module.cpty.repository.RatingActionRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-352 — {@code CptyService.dashboard()} must aggregate exposure/sector/grade over ALL
 * counterparties, not a capped page.
 *
 * <p>The dashboard's {@code totalCounterparties} / status / limit-band figures are exact
 * {@code countBy…} queries, but {@code totalExposureSarM}, {@code bySector} and
 * {@code byInternalGrade} were summed/grouped over {@code findByOrganisationIdOrderByReferenceAsc(
 * org(), PageRequest.of(0, 50))} — the first 50 counterparties by reference. On a tenant with more
 * than 50 counterparties the SAMA total-exposure and concentration figures were computed over that
 * subset only, understating exposure (the flattering direction) beside an all-time count
 * (F2-197 / F2-345: a capped list is fine to render, wrong to aggregate).
 *
 * <p>Hermetic: the fix is which set the aggregate reads, so the load-bearing assertion is the
 * SOURCING — the uncapped finder is used and the capped {@code Pageable} overload is not.
 * Reverting to the capped body makes these legs fail (the capped overload returns a null
 * {@code Page} that the reverted body dereferences, and the uncapped stub goes unused under strict
 * stubbing) — F2-274, the test is coupled to the uncapped contract.
 */
@ExtendWith(MockitoExtension.class)
class CptyServiceTest {

    private static final Long ORG = 7L;

    @Mock private CounterpartyRepository cptyRepo;
    @Mock private CreditRatingRepository ratingRepo;
    @Mock private RatingActionRepository actionRepo;
    @Mock private LargeExposureLimitRepository limitRepo;
    @Mock private CptyEventRepository eventRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private CptyService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Counterparty cp(long id, String sector, String grade, String exposureSarM) {
        Counterparty c = new Counterparty();
        c.setId(id);
        c.setOrganisationId(ORG);
        c.setReference("CP-" + String.format("%05d", id));
        c.setSector(sector);
        c.setCurrentInternalGrade(grade);
        c.setTotalExposureSarM(exposureSarM == null ? null : new BigDecimal(exposureSarM));
        return c;
    }

    @Test
    void dashboardAggregatesOverAllCounterparties_notACappedPage() {
        when(cptyRepo.findByOrganisationIdOrderByReferenceAsc(ORG)).thenReturn(List.of(
            cp(1, "CORPORATE_LARGE", "AA", "10.00"),
            cp(2, "CORPORATE_LARGE", "A",  "20.00"),
            cp(3, "SOVEREIGN",       null, "30.00"),   // null grade — skipped from byInternalGrade
            cp(4, "RETAIL",          "B",  null)));     // null exposure — ignored from the sum

        DashboardResponse dto = service.dashboard();

        // Total exposure sums ALL rows (60 = 10 + 20 + 30; c4's null is ignored), not a 50-capped
        // subset. This is the SAMA large-exposure concentration figure.
        assertThat(dto.getTotalExposureSarM()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(dto.getBySector())
            .containsEntry("CORPORATE_LARGE", 2L)
            .containsEntry("SOVEREIGN", 1L)
            .containsEntry("RETAIL", 1L);
        assertThat(dto.getByInternalGrade()).containsOnlyKeys("AA", "A", "B");  // c3 null skipped

        // Sourcing (the defect): aggregates come from the uncapped finder; the capped PageRequest
        // overload is never used for them.
        verify(cptyRepo).findByOrganisationIdOrderByReferenceAsc(ORG);
        verify(cptyRepo, never())
            .findByOrganisationIdOrderByReferenceAsc(eq(ORG), any(Pageable.class));
    }

    @Test
    void dashboardKeepsAllTimeCountsFromCountByQueries() {
        when(cptyRepo.findByOrganisationIdOrderByReferenceAsc(ORG)).thenReturn(List.of());
        when(cptyRepo.countByOrganisationId(ORG)).thenReturn(200L);
        when(cptyRepo.countByOrganisationIdAndStatus(ORG, "ACTIVE")).thenReturn(150L);
        when(limitRepo.countByOrganisationIdAndBand(ORG, "BREACH")).thenReturn(3L);

        DashboardResponse dto = service.dashboard();

        // The headline counts remain exact all-time countBy queries — unchanged by the fix.
        assertThat(dto.getTotalCounterparties()).isEqualTo(200L);
        assertThat(dto.getActiveCount()).isEqualTo(150L);
        assertThat(dto.getLimitsBreach()).isEqualTo(3L);
        // With no counterparties the exposure aggregate is zero (not null).
        assertThat(dto.getTotalExposureSarM()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
