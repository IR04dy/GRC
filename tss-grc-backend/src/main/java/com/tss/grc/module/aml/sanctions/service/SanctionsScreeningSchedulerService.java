package com.tss.grc.module.aml.sanctions.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.aml.sanctions.entity.SanctionsList;
import com.tss.grc.module.aml.sanctions.entity.ScreeningHit;
import com.tss.grc.module.aml.sanctions.repository.SanctionsListRepository;
import com.tss.grc.module.aml.sanctions.repository.ScreeningHitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sprint 13 — Sanctions scheduler: daily list refresh hook + SLA-breach sweep.
 *
 * <p>OFF in every packaged profile, gated by {@code grc.sanctions.scheduler-enabled}.
 * Declared in {@code application.yml} as a placeholder and named in
 * {@code external.properties.template}, so an operator can turn it on per environment.
 *
 * <p>⚠ This paragraph read "Off by default in dev ... operators flip it on per-env"
 * until F2-462, and both clauses were false: the property was declared in NO packaged yml,
 * so it was off in production too, and there was no placeholder and no template entry for an
 * operator to flip. Enabling it writes to live tenant data, so it stays OFF by default and
 * switching it on is an operator decision taken per environment and recorded.
 *
 * <p>Two scheduled ticks:
 * <ul>
 *   <li>{@code 0 0 4 * * *} (daily 04:00) — refresh every active list whose
 *       {@code refreshFrequency} is DAILY. Calls
 *       {@link SanctionsListService#refresh(Long)} which stamps
 *       {@code lastRefreshedAt} and emits {@code SANCTIONS_LIST_REFRESHED}.</li>
 *   <li>Every 15 minutes — SLA-breach sweep for every tenant that has
 *       at least one PENDING_REVIEW hit. Delegates to
 *       {@link SanctionsHitService#detectSlaBreachesForOrg(Long)}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionsScreeningSchedulerService {

    private final SanctionsListRepository listRepo;
    private final ScreeningHitRepository hitRepo;
    private final SanctionsListService listService;
    private final SanctionsHitService hitService;

    @Value("${grc.sanctions.scheduler-enabled:false}")
    private boolean schedulerEnabled;

    private Clock clock = Clock.system(ZoneId.of("UTC"));

    /** Test-only seam — drive both the refresh + SLA sweep against a fixed clock. */
    public void setClock(Clock clock) { this.clock = clock; }

    /** Daily list refresh — 04:00 server-local. */
    @Scheduled(cron = "0 0 4 * * *")
    public void refreshTick() {
        if (!schedulerEnabled) return;
        runDailyRefresh();
    }

    /** SLA sweep — every 15 minutes. */
    @Scheduled(cron = "0 */15 * * * *")
    public void slaSweepTick() {
        if (!schedulerEnabled) return;
        runSlaSweep();
    }

    /**
     * Refresh every {@code is_active=true, refresh_frequency='DAILY'} list.
     * Returns the count of lists touched. Visible for tests + operator
     * "refresh now" buttons.
     */
    int runDailyRefresh() {
        List<SanctionsList> active = listRepo.findAll().stream()
            .filter(l -> Boolean.TRUE.equals(l.getIsActive()) && Boolean.FALSE.equals(l.getIsDeleted()))
            .filter(l -> "DAILY".equalsIgnoreCase(l.getRefreshFrequency()))
            .toList();
        int touched = 0;
        for (SanctionsList l : active) {
            // Set the tenant context to the list's org (or to platform-NULL=1 as a fallback)
            try {
                TenantContext.setCurrentTenant(l.getOrganisationId() == null ? 1L : l.getOrganisationId());
                listService.refresh(l.getId());
                touched++;
            } catch (RuntimeException ex) {
                log.warn("Sanctions list refresh failed for list={} ({}): {}",
                    l.getId(), l.getListCode(), ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        if (touched > 0) {
            log.info("Sanctions scheduler refreshed {} list(s)", touched);
        }
        return touched;
    }

    /**
     * Cross-tenant SLA sweep — for each tenant with an overdue PENDING_REVIEW
     * hit, flip {@code sla_breach=1} and fire {@code SANCTIONS_HIT_SLA_BREACHED}.
     * Returns the total number of hits newly marked breached.
     */
    int runSlaSweep() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(SanctionsHitService.SLA_REVIEW_WINDOW);
        List<ScreeningHit> overdue = hitRepo
            .findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
                "PENDING_REVIEW", cutoff);
        if (overdue.isEmpty()) return 0;
        Set<Long> tenants = new HashSet<>();
        for (ScreeningHit h : overdue) tenants.add(h.getOrganisationId());

        int total = 0;
        for (Long orgId : tenants) {
            try {
                TenantContext.setCurrentTenant(orgId);
                total += hitService.detectSlaBreachesForOrg(orgId);
            } catch (RuntimeException ex) {
                log.warn("Sanctions SLA sweep failed for org={}: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        if (total > 0) {
            log.info("Sanctions SLA sweep marked {} hit(s) breached across {} tenant(s)", total, tenants.size());
        }
        return total;
    }

}
