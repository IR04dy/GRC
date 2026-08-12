package com.tss.grc.module.privacy;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The ONE declaration of which {@code privacy.data_subject_requests.status} values mean
 * "this request is finished" — PDPL Article 23 (30-day response window).
 *
 * <p><b>Not to be confused with</b> {@code privacy_lc.DsrCaseLifecycle} or
 * {@code data_officer}'s {@code DataSubjectRightsService}. The platform has <b>three</b>
 * DSR registers on three tables with three disjoint status vocabularies (measured
 * 2026-08-12 — 20, 12 and 17 rows, identically in dev and the deployed database, and no
 * CHECK constraint on any of the three columns). This class governs the first only.
 *
 * <p><b>Why it exists (F2-196).</b> Four readers of this one column disagreed:
 *
 * <ul>
 *   <li>{@code DsrPromotionAdapter} writes {@code OPEN} — the intake front door.</li>
 *   <li>{@code PrivacyService}'s start/fulfil/reject guards treated
 *       {@code {FULFILLED, REJECTED}} as terminal.</li>
 *   <li>{@code PrivacyService.stats} counted open as an inclusion list
 *       {@code {PENDING, IN_PROGRESS, OPEN, NEW}} — 10 of 20 rows.</li>
 *   <li>{@code PrivacyPage.tsx} counted open as {@code {PENDING, IN_PROGRESS, NEW}} — 9 of
 *       20, omitting the very status the intake adapter writes.</li>
 * </ul>
 *
 * <p>The stored vocabulary is eight tokens and <b>{@code FULFILLED} is not one of them</b>:
 * it appears in 0 of these 20 rows and in 3 rows of a <em>different</em> table. So the
 * terminal test named a status this register does not hold, while 7 rows that really were
 * finished ({@code RESOLVED} ×6, {@code COMPLETED} ×1) were treated as live — each rendering
 * a red "N days overdue" against the statutory deadline, and each still offering Fulfil /
 * Extend / Reject. The count erred low and the SLA column erred high: a vocabulary mismatch
 * has no direction, it errs whichever way the forgotten token happens to sit.
 *
 * <p><b>The rule.</b> {@link #TERMINAL} is a whitelist of finished states and
 * {@link #isOpen} is its <em>complement</em>, never a second list — so a status nobody
 * anticipated counts as open, which for a statutory clock is the safe direction. The
 * synonym trio {@code FULFILLED} / {@code RESOLVED} / {@code COMPLETED} is recognised
 * rather than collapsed: collapsing them rewrites live rows in two databases, which is a
 * data decision for the owner, not a side effect of a display fix.
 */
public final class DsrRequestLifecycle {

    /**
     * Statuses that mean the request is finished and its PDPL clock has stopped.
     *
     * <p>{@code FULFILLED} and {@code REJECTED} are what {@code PrivacyService} writes.
     * {@code RESOLVED} and {@code COMPLETED} are what this register actually holds (7 of 20
     * rows in both databases) and are unambiguously finished. All four are listed because
     * the column has no CHECK constraint, so all four are reachable and none can be wished
     * away by omitting it here.
     */
    public static final Set<String> TERMINAL = Set.of("FULFILLED", "REJECTED", "RESOLVED", "COMPLETED");

    /** Sorted for a stable, diffable repository query parameter. */
    public static final List<String> TERMINAL_LIST = List.of("COMPLETED", "FULFILLED", "REJECTED", "RESOLVED");

    private DsrRequestLifecycle() {}

    /**
     * Case-insensitively normalise for comparison. The column is
     * {@code Latin1_General_100_CI_AS_SC_UTF8} with no CHECK, so {@code 'resolved'} is
     * storable and a bare {@code "RESOLVED".equals(...)} would be false against it — the
     * F2-179 family. Compare through here.
     */
    public static String normalise(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    /** True when the request is finished — a whitelist. */
    public static boolean isTerminal(String status) {
        return TERMINAL.contains(normalise(status));
    }

    /** True when the request is still live — the complement, so an unknown status is open. */
    public static boolean isOpen(String status) {
        return !isTerminal(status);
    }
}
