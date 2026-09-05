package com.tss.grc.module.ccm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import com.tss.grc.module.ccm.repository.ControlTestDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Background tick loop that runs CCM tests on their configured cron.
 *
 * <p>OFF in every packaged profile, gated by {@code grc.ccm.scheduler-enabled}.
 * Declared in {@code application.yml} as a placeholder and named in
 * {@code external.properties.template}, so an operator can turn it on per environment.
 *
 * <p>⚠ This paragraph read "Off by default in dev ... operators flip it on per-env"
 * until F2-462, and both clauses were false: the property was declared in NO packaged yml,
 * so it was off in production too, and there was no placeholder and no template entry for an
 * operator to flip. Enabling it writes to live tenant data, so it stays OFF by default and
 * switching it on is an operator decision taken per environment and recorded.
 *
 * <p>Algorithm per tick (every minute):
 * <ol>
 *   <li>Load enabled, non-deleted test definitions whose {@code last_run_at}
 *       is either {@code null} or older than the smallest realistic cron
 *       window (5 minutes).</li>
 *   <li>For each candidate, parse {@code frequency_cron} and compute the
 *       "should have already run by now" boolean by asking the
 *       {@link CronExpression} for the next-fire-time after the
 *       {@code last_run_at} (or test creation time) and checking whether
 *       that timestamp is now in the past.</li>
 *   <li>Dispatch through {@link CcmService#runDefinitionInternal} —
 *       which records exactly one run row, handles tenant context, and
 *       emits the F8 event.</li>
 * </ol>
 *
 * <p>This is a deliberately simple implementation — fine for the ~10s
 * of tests a single tenant runs. If we ever need 1000s of tests per
 * tenant we'll move to a quartz-backed approach with row-level
 * locking; until then in-process scheduling is the right shape.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CcmSchedulerService {

    private final ControlTestDefinitionRepository defRepo;
    private final CcmService ccmService;

    @Value("${grc.ccm.scheduler-enabled:false}")
    private boolean schedulerEnabled;

    /**
     * Tick every 60 s. Fixed-delay so a slow batch doesn't pile up
     * overlapping runs. Real cron-precision is best-effort — the
     * 1-minute granularity means a test with {@code "0 0 6 * * *"} can
     * fire any time in the 06:00:00..06:00:59 window.
     */
    @Scheduled(fixedDelay = 60_000L)
    public void tick() {
        if (!schedulerEnabled) {
            return;
        }
        // Look back generously — a candidate row is loaded if it has
        // never run, or its last run is more than 5 minutes old. The
        // per-row cron check below decides whether to dispatch.
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        List<ControlTestDefinition> candidates = defRepo.findCandidatesForRun(cutoff);
        if (candidates.isEmpty()) {
            return;
        }
        int dispatched = 0;
        for (ControlTestDefinition d : candidates) {
            if (!isDue(d, LocalDateTime.now())) continue;
            try {
                // Scheduler runs outside any HTTP filter so it must set
                // the tenant context itself for the duration of one
                // dispatch. Cleared in the finally so the next iteration
                // starts clean.
                TenantContext.setCurrentTenant(d.getOrganisationId());
                ccmService.runDefinitionInternal(d);
                dispatched++;
            } catch (RuntimeException ex) {
                log.warn("CCM scheduler dispatch failed for {} (orgId={}): {}",
                        d.getTestCode(), d.getOrganisationId(), ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        if (dispatched > 0) {
            log.info("CCM scheduler dispatched {} test(s) on this tick", dispatched);
        }
    }

    /**
     * True when {@code d.frequency_cron}'s next-fire-time after the
     * last run is in the past relative to {@code now}. Visible for
     * testing.
     */
    static boolean isDue(ControlTestDefinition d, LocalDateTime now) {
        if (d.getFrequencyCron() == null || d.getFrequencyCron().isBlank()) {
            return false;
        }
        CronExpression cron;
        try {
            cron = CronExpression.parse(d.getFrequencyCron());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        LocalDateTime anchor = d.getLastRunAt() == null
                ? (d.getCreatedAt() == null ? now.minusYears(1) : d.getCreatedAt())
                : d.getLastRunAt();
        LocalDateTime next = cron.next(anchor);
        return next != null && !next.isAfter(now);
    }
}
