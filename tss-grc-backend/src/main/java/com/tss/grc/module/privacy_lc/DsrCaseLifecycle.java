package com.tss.grc.module.privacy_lc;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The ONE declaration of the {@code privacy_lc.dsr_cases} lifecycle — PDPL Article 4
 * (30-day fulfilment window, one 30-day extension, 60 days maximum).
 *
 * <p><b>Why this class exists (F2-196).</b> Four classes read
 * {@code privacy_lc.dsr_cases.status} and three of them hand-rolled their own answer to
 * "is this case still open?":
 *
 * <ul>
 *   <li>{@code DsrCaseService.ALLOWED} — the declared state machine (moved here).</li>
 *   <li>{@code DsrCaseService.overdue()} — passed {@code {DELIVERED, REJECTED}}. Correct.</li>
 *   <li>{@code PrivacyGapService.dsrSlaSweep()} — passed {@code {DELIVERED, REJECTED, EXTENDED}}.
 *       <b>Wrong.</b> {@code EXTENDED} is not terminal: the machine below gives it five
 *       outgoing edges, and {@code DsrCaseService.transition} pushes {@code slaDeadline}
 *       out by 30 days when entering it — so the deadline <em>already</em> accounts for
 *       the extension and a case past its extended deadline is unambiguously in breach.
 *       Excluding it meant the extended deadline could never be enforced at all.</li>
 *   <li>{@code ThreeLinesService.dsrOpenCount()} — an <em>inclusion</em> list of five
 *       open states. Complete when written, but an inclusion list over an open vocabulary
 *       silently drops any state nobody remembered to add.</li>
 * </ul>
 *
 * <p>Measured 2026-08-12, identically in dev and the deployed database: the sweep had run
 * (2026-06-22) and stamped {@code sla_breached_at} on 7 of 12 cases. The 2 {@code EXTENDED}
 * cases were already 4 and 11 days past their extended deadlines at that moment and were
 * omitted — no {@code PDPL_DSR_SLA_BREACH} event, no audit row, {@code sla_breach_notified}
 * still false. So this was a live omission on a statutory control, not a latent one.
 *
 * <p><b>The rule this class enforces.</b> {@link #TERMINAL} is <em>derived</em> from
 * {@link #ALLOWED} — a state is terminal exactly when it has no outgoing edges — so the
 * two cannot drift, and {@link #isOpen} is the <em>complement</em> of terminal rather than
 * a second list. A status nobody has heard of therefore counts as OPEN, which is the safe
 * direction for a statutory clock: a new state is reported and chased, never silently
 * excused. That asymmetry is the whole point; see {@code DsrLifecycleContractTest}.
 */
public final class DsrCaseLifecycle {

    /**
     * The declared PDPL Article 4 machine. Terminal states deliberately have NO entry:
     * that absence is what {@link #TERMINAL} reads, so adding a state here without an
     * entry declares it terminal and adding one with an entry declares it open.
     *
     * <pre>
     *   INTAKE              → VERIFYING_IDENTITY | REJECTED | EXTENDED
     *   VERIFYING_IDENTITY  → COLLECTING         | REJECTED | EXTENDED
     *   COLLECTING          → REDACTING          | REJECTED | EXTENDED
     *   REDACTING           → DELIVERED          | EXTENDED
     *   EXTENDED            → VERIFYING_IDENTITY | COLLECTING | REDACTING | DELIVERED | REJECTED
     * </pre>
     */
    public static final Map<String, Set<String>> ALLOWED = Map.of(
        "INTAKE",             Set.of("VERIFYING_IDENTITY", "REJECTED", "EXTENDED"),
        "VERIFYING_IDENTITY", Set.of("COLLECTING", "REJECTED", "EXTENDED"),
        "COLLECTING",         Set.of("REDACTING", "REJECTED", "EXTENDED"),
        "REDACTING",          Set.of("DELIVERED", "EXTENDED"),
        "EXTENDED",           Set.of("VERIFYING_IDENTITY", "COLLECTING", "REDACTING", "DELIVERED", "REJECTED")
    );

    /**
     * States the case can never leave — computed, never typed. Every state the machine
     * mentions, minus every state that has outgoing edges. Today: DELIVERED, REJECTED.
     */
    public static final Set<String> TERMINAL;

    /** Sorted so a repository query's parameter order is stable and diffable. */
    public static final List<String> TERMINAL_LIST;

    static {
        Set<String> mentioned = new TreeSet<>(ALLOWED.keySet());
        ALLOWED.values().forEach(mentioned::addAll);
        mentioned.removeAll(ALLOWED.keySet());
        TERMINAL = Set.copyOf(mentioned);
        TERMINAL_LIST = List.copyOf(new TreeSet<>(mentioned));
    }

    /** The status a newly filed case starts in. */
    public static final String INITIAL = "INTAKE";

    private DsrCaseLifecycle() {}

    /**
     * Case-insensitively normalise a stored status for comparison. The column is
     * {@code Latin1_General_100_CI_AS_SC_UTF8} and there is no CHECK constraint, so
     * {@code 'extended'} is storable and {@code "EXTENDED".equals(...)} would be false
     * against it — the F2-179 family. Compare through here, never with a bare equals.
     */
    public static String normalise(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    /** True when the case has reached a state it can never leave. */
    public static boolean isTerminal(String status) {
        return TERMINAL.contains(normalise(status));
    }

    /**
     * True when the case is still live and its PDPL clock still runs — the complement of
     * {@link #isTerminal}, so an unrecognised status is OPEN.
     */
    public static boolean isOpen(String status) {
        return !isTerminal(status);
    }

    /** Legal targets from {@code from}; empty when {@code from} is terminal or unknown. */
    public static Set<String> allowedTargets(String from) {
        return ALLOWED.getOrDefault(normalise(from), Set.of());
    }
}
