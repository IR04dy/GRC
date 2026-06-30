package com.tss.grc.module.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * ERM epic E-J — daily stale-review sweep.
 *
 * <p>Off by default in dev — gated by {@code grc.risk.review-scheduler-enabled}
 * (defaults to {@code false}); operators flip it on per environment, matching
 * the KRI scheduler convention. Fires at 06:30 daily (just after the KRI tick)
 * and flags every active risk past its scheduled review date as {@code OVERDUE},
 * emitting an F8 {@code RISK_REVIEW_OVERDUE} event for each newly-flagged risk.
 * The same sweep is reachable on demand via
 * {@code POST /api/v1/risk/reviews/run-overdue-sweep}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskReviewScheduler {

    private final RiskReviewService reviewService;

    @Value("${grc.risk.review-scheduler-enabled:false}")
    private boolean schedulerEnabled;

    @Scheduled(cron = "0 30 6 * * *")
    public void tick() {
        if (!schedulerEnabled) return;
        int flagged = reviewService.runOverdueSweepAllTenants();
        if (flagged > 0) {
            log.info("Risk review sweep flagged {} risk(s) as review-overdue", flagged);
        }
    }
}
