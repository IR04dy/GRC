package com.tss.grc.module.bcm.service;

import com.tss.grc.module.bcm.entity.BiaRecord;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The one answer to "which BIA states this process's recovery objectives?".
 *
 * <h2>Why this exists (F2-282)</h2>
 *
 * <p>A process can carry several BIA records — measured 2026-08-22, two of the ten
 * live processes do, in dev and prod alike. All three readers of
 * {@code BiaRecordRepository#findByProcessIdIn} were picking one per process with
 * a hand-rolled loop, and the three loops did not agree:
 *
 * <ul>
 *   <li>{@code DependencyCascadeService} — lowest MTPD; a null-MTPD row could seed
 *       the map and was then replaced by any non-null one.</li>
 *   <li>{@code BcmBoardPackService} — lowest MTPD, but it {@code continue}d past a
 *       null MTPD, so a process whose only BIA left MTPD blank was <em>absent from
 *       the board pack</em> rather than present with no objective.</li>
 *   <li>{@code MtpdInheritanceService} — <b>latest by {@code assessmentDate}</b>,
 *       filtering nulls only <em>after</em> selecting, so when the most recent BIA
 *       had no MTPD the process ended up with <b>no MTPD at all</b> even though an
 *       older record stated one, and the vendor gap became unmeasurable.</li>
 * </ul>
 *
 * <p>Two orderings and three null behaviours for one question. They agree on every
 * live row today, which is exactly F2-177's shape: what made it harmless was three
 * independent implementations happening to coincide, and nobody was maintaining
 * that. RTO ≤ MTPD by definition, so the reader that picks differently decides
 * whether a continuity plan is compliant — and the vendor-inheritance control asks
 * precisely that of a supplier's attested recovery time.
 *
 * <h2>The rule, and why this one</h2>
 *
 * <p><b>Lowest stated MTPD wins</b>, and a row that states one always beats a row
 * that does not. Chosen by measurement rather than taste: two of the three callers
 * already did this, it is the <em>conservative</em> direction (a tighter tolerance
 * over-reports disruption rather than under-reporting it), and adopting it changes
 * nothing on either live database — verified, the rules coincide on all 8 processes
 * that have a BIA. The "latest by date" outlier is the one that moves.
 *
 * <h2>Why a pure function and not a repository default method</h2>
 *
 * <p>⚠ The first draft put this on {@code BiaRecordRepository} as a
 * {@code default} method. It compiled, it passed its own tests — and it broke two
 * existing {@code MtpdInheritanceServiceTest} cases, because <b>a Mockito mock does
 * not execute default methods</b>: the mocked repository returned nothing, the
 * stubbed {@code findByProcessIdIn} went unused, and the resolved MTPD silently
 * became empty. That is the trap, not the test's fault — a decision placed on a
 * mockable interface is a decision every test that mocks that interface silently
 * loses (F2-237's family: a mocked collaborator answers, and then the test is
 * asserting the mock).
 *
 * <p>A static function over a supplied list has no such hole: the callers keep
 * their existing query, the existing stubs keep working, and this rule is
 * unit-testable with no repository at all.
 *
 * <h2>What this deliberately does not do</h2>
 *
 * <p>⚠ It does <b>not</b> consult {@code status}, and that is a filed owner
 * decision rather than an omission. Measured: <b>4 of 8 processes resolve to an
 * unapproved BIA</b>, and for {@code Core Banking Operations} (CRITICAL) the winner
 * is an {@code IN_PROGRESS} record stating MTPD <b>1h</b> over an {@code APPROVED}
 * one stating <b>72h</b>. Preferring the approved row would make these controls
 * report <em>less</em> disruption on a customer's data, and nothing here can tell
 * which figure is that organisation's real tolerance — so the status is surfaced to
 * the caller instead (F2-197: disclose, never manufacture an all-clear).
 */
public final class BiaObjectives {

    private BiaObjectives() {}

    /**
     * Groups the given BIA records by process and keeps the authoritative one.
     *
     * @param records BIA rows the caller has already scoped to its tenant
     * @return process id → the BIA whose objectives that process should be judged by
     */
    public static Map<Long, BiaRecord> authoritativeByProcess(Collection<BiaRecord> records) {
        Map<Long, BiaRecord> best = new HashMap<>();
        if (records == null) return best;
        for (BiaRecord b : records) {
            if (b == null || b.getProcessId() == null) continue;
            BiaRecord cur = best.get(b.getProcessId());
            if (cur == null || preferred(b, cur)) best.put(b.getProcessId(), b);
        }
        return best;
    }

    /**
     * True when {@code candidate} should replace {@code incumbent}.
     *
     * <p>A row that states an MTPD always beats one that does not — that is the
     * null behaviour the three copies disagreed about, and stating it once is the
     * point. Between two rows that both state one, the lower wins. The result is
     * independent of arrival order, so it cannot depend on what the query happened
     * to return first (F2-186's ordering defect, in a recovery objective).
     */
    private static boolean preferred(BiaRecord candidate, BiaRecord incumbent) {
        Integer c = candidate.getMtpdHours();
        Integer i = incumbent.getMtpdHours();
        if (c == null) return false;
        if (i == null) return true;
        return c < i;
    }
}
