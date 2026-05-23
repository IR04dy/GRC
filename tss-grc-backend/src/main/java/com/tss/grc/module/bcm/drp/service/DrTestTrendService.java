package com.tss.grc.module.bcm.drp.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.drp.entity.DisasterRecoveryPlan;
import com.tss.grc.module.bcm.drp.repository.DisasterRecoveryPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Disaster-Recovery test trending — Sprint-9 Slice 9-B.
 *
 * <p>Slice 9-B in {@code BCM_GRC_GAP_ANALYSIS_AND_PLAN.md} was scoped as
 * "RTO/RPO trending across exercises + incidents". The existing schema
 * captures DR test {@code outcome} (PASSED / FAILED / PARTIAL) and the
 * {@code last_tested_at} timestamp on {@link DisasterRecoveryPlan}, but
 * the per-test measured RTO/RPO is not yet on the entity. This service
 * delivers the v1 the same data unlocks today: a quarterly DR test
 * outcome trend that the Board pack expects ("our DR-test pass rate
 * over the last 4 quarters").
 *
 * <p>A future Slice 9-B+ can add per-test {@code actualRtoHours} +
 * {@code actualRpoHours} columns and layer the actual-vs-target series
 * on top of this aggregator — the response shape is forward-compatible.
 *
 * <p>Pure read aggregator. No schema changes. Tenant-scoped via
 * {@link TenantContext}.
 */
@Service
@RequiredArgsConstructor
public class DrTestTrendService {

    /** Number of quarters to report (rolling). */
    public static final int DEFAULT_QUARTERS = 8;
    public static final int MAX_QUARTERS = 20;

    private final DisasterRecoveryPlanRepository drps;

    @Transactional(readOnly = true)
    public TrendReport trend(Integer quarters) {
        Long org = TenantContext.getCurrentTenant();

        int q = quarters == null || quarters <= 0
            ? DEFAULT_QUARTERS
            : Math.min(quarters, MAX_QUARTERS);

        List<DisasterRecoveryPlan> all = drps.findAllByOrganisationId(org);

        // Build quarter buckets ending today, walking backwards.
        LocalDate today = LocalDate.now();
        List<QuarterBucket> rolling = new ArrayList<>(q);
        for (int i = q - 1; i >= 0; i--) {
            LocalDate end   = startOfQuarter(today.minusMonths(i * 3L)).plusMonths(3).minusDays(1);
            LocalDate start = startOfQuarter(today.minusMonths(i * 3L));
            rolling.add(new QuarterBucket(quarterLabel(start), start, end));
        }

        // Tally per quarter
        List<QuarterStats> series = new ArrayList<>(q);
        for (QuarterBucket b : rolling) {
            int passed = 0, failed = 0, partial = 0, na = 0;
            for (DisasterRecoveryPlan d : all) {
                LocalDateTime tested = d.getLastTestedAt();
                if (tested == null) continue;
                LocalDate testedDate = tested.toLocalDate();
                if (testedDate.isBefore(b.start) || testedDate.isAfter(b.end)) continue;
                String outcome = d.getLastTestedOutcome();
                if (outcome == null) { na++; continue; }
                switch (outcome.toUpperCase(Locale.ROOT)) {
                    case "PASSED"  -> passed++;
                    case "FAILED"  -> failed++;
                    case "PARTIAL" -> partial++;
                    default        -> na++;
                }
            }
            int tested = passed + failed + partial + na;
            double passRate = tested == 0 ? 0.0 : (double) passed / tested;
            series.add(new QuarterStats(
                b.label, b.start, b.end,
                tested, passed, failed, partial, na,
                round2(passRate)
            ));
        }

        // Snapshot — DRPs tested in last 12 months + never-tested count
        LocalDate cutoff = today.minusMonths(12);
        int total = all.size();
        int neverTested = 0;
        int testedRecently = 0;
        int passedRecently = 0;
        int overdue = 0;
        for (DisasterRecoveryPlan d : all) {
            if (d.getLastTestedAt() == null) {
                neverTested++;
            } else if (d.getLastTestedAt().toLocalDate().isBefore(cutoff)) {
                overdue++;
            } else {
                testedRecently++;
                if ("PASSED".equalsIgnoreCase(d.getLastTestedOutcome())) {
                    passedRecently++;
                }
            }
        }
        double recentPassRate = testedRecently == 0 ? 0.0 : (double) passedRecently / testedRecently;

        return new TrendReport(
            today,
            series,
            new Snapshot(
                total, neverTested, overdue, testedRecently,
                passedRecently, round2(recentPassRate)
            )
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private static LocalDate startOfQuarter(LocalDate d) {
        int month = ((d.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(d.getYear(), month, 1);
    }

    private static String quarterLabel(LocalDate startOfQuarter) {
        int q = ((startOfQuarter.getMonthValue() - 1) / 3) + 1;
        return startOfQuarter.getYear() + "-Q" + q;
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Private bucket used during accumulation; not exposed in API. */
    private record QuarterBucket(String label, LocalDate start, LocalDate end) {}

    // ─── DTO types ───────────────────────────────────────────────────

    public record TrendReport(
        LocalDate evaluatedOn,
        List<QuarterStats> series,
        Snapshot snapshot
    ) {}

    public record QuarterStats(
        String quarter,                  // "2026-Q1"
        LocalDate quarterStart,
        LocalDate quarterEnd,
        int totalTested,
        int passed,
        int failed,
        int partial,
        int na,
        double passRate                  // 0..1 — rounded to 2dp
    ) {}

    public record Snapshot(
        int totalDrps,
        int neverTested,
        int testedOverdue,               // last_tested_at older than 12 months
        int testedRecently,              // last_tested_at within 12 months
        int passedRecently,
        double recentPassRate            // 0..1 — rounded to 2dp
    ) {}
}
