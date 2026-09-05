package com.tss.grc.module.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * ERM epic E-J — daily stale-review sweep.
 *
 * <p>OFF in every packaged profile, gated by {@code grc.risk.review-scheduler-enabled}.
 * Declared in {@code application.yml} as a placeholder and named in
 * {@code external.properties.template}, so an operator can turn it on per environment,
 * matching the KRI scheduler convention.
 *
 * <p>⚠ This read "Off by default in dev ... operators flip it on per environment" until
 * F2-462, and both clauses were false: the property was declared in no packaged yml, so it
 * was off in production too, and there was no placeholder and no template entry to flip.
 * Measured while it was unreachable: 24 risks past their review date and unflagged, across
 * three tenants (F2-317).
 *
 * <p>Fires at 06:30 daily (just after the KRI tick)
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
