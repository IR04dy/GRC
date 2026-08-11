package com.tss.grc.module.aml.sanctions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.aml.sanctions.entity.SanctionsList;
import com.tss.grc.module.aml.sanctions.entity.ScreeningHit;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningHitRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * F2-192 — the AML sanctions SLA sweep, and the seam that was declared and never used.
 *
 * <h3>How this test came to exist</h3>
 * <p>{@code DarkServiceMethodTest.TOLERATED} listed
 * {@code SanctionsScreeningSchedulerService#setClock} as a <b>"DECLARED TEST SEAM … reached by tests
 * only, which is the point"</b>. F2-192's new gate verifies that claim instead of trusting it — by
 * looking for a call site in {@code target/test-classes} — and it failed on its first run:
 * <b>nothing called it, in main sources or in tests.</b> {@code SanctionsHitServiceTest} calls the
 * identically-named setter on the <em>sibling</em> class.
 *
 * <p>🔑 So the entry had been classified as a seam <b>because the method is called setClock</b> — and
 * the comment three lines above it in that same file warns, in its own words, that <i>"a
 * setClock-shaped exemption would let any dark method be hidden by renaming it"</i>. F2-187 wrote the
 * warning and then made the mistake, in the entry directly beneath it. <b>Naming a trap does not
 * exempt you from it, and a warning you wrote yourself is the one you are least likely to re-read.</b>
 *
 * <p>What the failure exposed is not a documentation problem: {@link
 * SanctionsScreeningSchedulerService#runSlaSweep()} is a <b>cross-tenant sweep that flips
 * {@code sla_breach = 1} and fires {@code SANCTIONS_HIT_SLA_BREACHED}</b> on an AML control, its
 * cutoff comes from {@code LocalDateTime.now(clock)}, and it had <b>no test of any kind</b>. The seam
 * was built for tests that were never written. Deleting the unused setter was the other option and it
 * is the wrong one — the sweep needs a fixed clock to be testable at all.
 *
 * <p>⚠ The clock is pinned to a date deliberately <b>not</b> today, per CLAUDE.md's F2-178 rule: a
 * fixed date equal to the real one proves nothing about the injection. {@link
 * #theCutoffComesFromTheInjectedClock()} is the leg that would fail if {@code setClock} stopped being
 * honoured, which is what makes the seam load-bearing rather than decorative.
 */
@ExtendWith(MockitoExtension.class)
class SanctionsSchedulerSweepTest {

    /** Deliberately not today — see the class javadoc. */
    private static final LocalDate PINNED = LocalDate.of(2026, 3, 17);
    private static final LocalDateTime PINNED_AT = PINNED.atTime(9, 30);

    @Mock private SanctionsListRepository listRepo;
    @Mock private ScreeningHitRepository hitRepo;
    @Mock private SanctionsListService listService;
    @Mock private SanctionsHitService hitService;

    private SanctionsScreeningSchedulerService svc;

    @BeforeEach
    void setUp() {
        svc = new SanctionsScreeningSchedulerService(listRepo, hitRepo, listService, hitService);
        svc.setClock(Clock.fixed(PINNED_AT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ScreeningHit hit(long id, long orgId) {
        ScreeningHit h = new ScreeningHit();
        h.setId(id);
        h.setOrganisationId(orgId);
        h.setStatus("PENDING_REVIEW");
        h.setSlaBreach(false);
        return h;
    }

    private static SanctionsList list(long id, Long orgId, String freq, boolean active,
                                      boolean deleted) {
        SanctionsList l = new SanctionsList();
        l.setId(id);
        l.setOrganisationId(orgId);
        l.setListCode("L" + id);
        l.setRefreshFrequency(freq);
        l.setIsActive(active);
        l.setIsDeleted(deleted);
        return l;
    }

    // ── the SLA sweep ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the 24h cutoff is computed from the injected clock, not the wall clock")
    void theCutoffComesFromTheInjectedClock() {
        when(hitRepo.findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
                eq("PENDING_REVIEW"), any())).thenReturn(List.of());

        svc.runSlaSweep();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(hitRepo).findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
            eq("PENDING_REVIEW"), cutoff.capture());
        assertThat(cutoff.getValue())
            .as("cutoff must be the pinned instant minus SLA_REVIEW_WINDOW (%s). If this reads as "
                + "'about now', setClock is no longer honoured and the seam is decorative — which is "
                + "how it sat unused and misdescribed until F2-192.",
                SanctionsHitService.SLA_REVIEW_WINDOW)
            .isEqualTo(PINNED_AT.minus(SanctionsHitService.SLA_REVIEW_WINDOW));
        assertThat(SanctionsHitService.SLA_REVIEW_WINDOW.toHours())
            .as("the window is the thing the cutoff is derived from — pin it so a silent change to "
                + "the review SLA fails here as well as in SanctionsHitService")
            .isEqualTo(24L);
    }

    @Test
    @DisplayName("one call per distinct tenant, with that tenant's context set during the call")
    void sweepsEachTenantOnceUnderItsOwnTenantContext() {
        when(hitRepo.findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
                eq("PENDING_REVIEW"), any()))
            .thenReturn(List.of(hit(1, 7L), hit(2, 7L), hit(3, 9L)));

        List<Long> sawTenant = new ArrayList<>();
        when(hitService.detectSlaBreachesForOrg(anyLong())).thenAnswer(inv -> {
            // The landmine this guards: a scheduler runs off-request, so TenantContext is not set
            // for it. If the sweep asked the service without setting it, every repository query
            // inside would filter on the wrong tenant (or none).
            sawTenant.add(TenantContext.getCurrentTenant());
            return inv.getArgument(0, Long.class) == 7L ? 2 : 1;
        });

        int total = svc.runSlaSweep();

        assertThat(total).as("the sweep returns the sum across tenants").isEqualTo(3);
        verify(hitService).detectSlaBreachesForOrg(7L);
        verify(hitService).detectSlaBreachesForOrg(9L);
        assertThat(sawTenant)
            .as("two hits in org 7 must not mean two sweeps of org 7, and each call must run under "
                + "the tenant it is about")
            .containsExactlyInAnyOrder(7L, 9L);
        assertThat(TenantContext.getCurrentTenant())
            .as("the sweep must not leak a tenant to whatever runs next on this thread")
            .isNull();
    }

    @Test
    @DisplayName("a failing tenant is logged and skipped — the rest of the batch still runs")
    void oneFailingTenantDoesNotAbortTheBatch() {
        when(hitRepo.findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
                eq("PENDING_REVIEW"), any()))
            .thenReturn(List.of(hit(1, 7L), hit(2, 9L)));
        when(hitService.detectSlaBreachesForOrg(7L)).thenThrow(new IllegalStateException("boom"));
        when(hitService.detectSlaBreachesForOrg(9L)).thenReturn(5);

        int total = svc.runSlaSweep();

        assertThat(total)
            .as("skip-and-log, never abort: one tenant's bad data must not stop every other "
                + "tenant's AML SLA breaches from being detected (the F2-191 flipToOverdue rule)")
            .isEqualTo(5);
        assertThat(TenantContext.getCurrentTenant())
            .as("the finally block must clear the tenant even on the failing path")
            .isNull();
    }

    @Test
    @DisplayName("no overdue hits: returns 0 without asking any tenant")
    void emptySweepAsksNobody() {
        when(hitRepo.findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
                eq("PENDING_REVIEW"), any())).thenReturn(List.of());

        assertThat(svc.runSlaSweep()).isZero();
        verify(hitService, never()).detectSlaBreachesForOrg(anyLong());
    }

    // ── the daily refresh ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("only active, undeleted, DAILY lists refresh — and each under its own tenant")
    void refreshFiltersAndScopesPerList() {
        when(listRepo.findAll()).thenReturn(List.of(
            list(1, 4L,  "DAILY",   true,  false),    // yes
            list(2, 4L,  "daily",   true,  false),    // yes — equalsIgnoreCase
            list(3, 5L,  "WEEKLY",  true,  false),    // no  — wrong frequency
            list(4, 5L,  "DAILY",   false, false),    // no  — inactive
            list(5, 5L,  "DAILY",   true,  true),     // no  — soft-deleted
            list(6, null, "DAILY",  true,  false)));  // yes — platform list, org falls back to 1

        List<Long> sawTenant = new ArrayList<>();
        when(listService.refresh(anyLong())).thenAnswer(inv -> {
            sawTenant.add(TenantContext.getCurrentTenant());
            return null;
        });

        assertThat(svc.runDailyRefresh()).isEqualTo(3);
        verify(listService).refresh(1L);
        verify(listService).refresh(2L);
        verify(listService).refresh(6L);
        verify(listService, never()).refresh(3L);
        verify(listService, never()).refresh(4L);
        verify(listService, never()).refresh(5L);
        assertThat(sawTenant)
            .as("a NULL organisation_id is a platform-owned list and the service documents the "
                + "fallback to tenant 1 — assert it rather than leaving the null to surprise a reader")
            .containsExactly(4L, 4L, 1L);
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("a failing list is logged and skipped — later lists still refresh")
    void oneFailingListDoesNotAbortTheRefresh() {
        when(listRepo.findAll()).thenReturn(List.of(
            list(1, 4L, "DAILY", true, false),
            list(2, 4L, "DAILY", true, false)));
        when(listService.refresh(1L)).thenThrow(new IllegalStateException("upstream 503"));

        assertThat(svc.runDailyRefresh())
            .as("the count is lists actually touched, so a failure must not be counted as one")
            .isEqualTo(1);
        verify(listService).refresh(2L);
    }

    // ── the enable gate ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("both ticks are inert until grc.sanctions.scheduler-enabled is set")
    void ticksRespectTheEnableFlag() {
        // Default is false (dev), so an unset flag must mean neither tick does anything. Without
        // this leg, a change that dropped the guard would only be noticed in an environment where
        // the sweep started running on its own.
        svc.refreshTick();
        svc.slaSweepTick();
        verify(listRepo, never()).findAll();
        verify(hitRepo, never()).findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
            any(), any());

        ReflectionTestUtils.setField(svc, "schedulerEnabled", true);
        when(listRepo.findAll()).thenReturn(List.of());
        when(hitRepo.findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
                eq("PENDING_REVIEW"), any())).thenReturn(List.of());

        svc.refreshTick();
        svc.slaSweepTick();
        verify(listRepo).findAll();
        verify(hitRepo).findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
            eq("PENDING_REVIEW"), any());
    }
}
