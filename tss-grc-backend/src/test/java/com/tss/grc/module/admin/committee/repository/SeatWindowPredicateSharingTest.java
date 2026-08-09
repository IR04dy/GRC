package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>F2-178 — the seat-window predicate has exactly one definition, and everything that needs it uses
 * that one.</b>
 *
 * <p><b>Why this test exists.</b> The tree already carried the rule, written for a different pair:
 * {@code ChangeGovernanceHealthService} says <em>"a report describing access must share its predicate with
 * the enforcement point"</em>, because F2-62 cost exactly that — an access report whose predicate had
 * drifted from the gate it described. F2-26's {@code SeatLinkageReport} and F2-177's seat guard became
 * that same pair one table over: both read {@code committee_member_terms}, and they decided "this seat
 * counts" by different rules. The report filtered on {@code renewal_status} alone; the guard also required
 * {@code ended_at IS NULL} and that today fall inside the term window. So a roster could read fully linked
 * while the guard admitted nobody on it, and <b>nothing in the build would have disagreed</b>.
 *
 * <p>⚠ <b>The divergence is 0 rows today and it is dated, not hypothetical.</b> Measured 2026-08-09: no
 * ACTIVE term in dev or production has a closed window or a set {@code ended_at}. But nothing in this
 * platform ever transitions an ACTIVE term to EXPIRED — {@code CommitteeScheduledSweep} only fires
 * 90/60/30-day reminders, and {@code endTerm} is a manual act — so the moment a term passes its
 * {@code term_ends_at} the row keeps reading ACTIVE for ever. The earliest production term ends
 * <b>2027-03-01</b>. A test that only checked today's data would pass until then and then be useless.
 *
 * <p><b>What is pinned, and what deliberately is not.</b> These assertions are textual: they prove the
 * same string reaches both queries and that no third copy of the clause has appeared. They cannot prove
 * the JPQL and {@link CommitteeMemberTerm#isCurrentOn(LocalDate)} agree <em>semantically</em> — that needs
 * a database, so it is proven in the live probe, which compares the SQL aggregate against the Java report
 * for every committee in the dev tenant. Saying so here matters: an invariant that overstates its reach is
 * how F2-159's decoy test survived (it described the code accurately and the code was wrong).
 */
class SeatWindowPredicateSharingTest {

    private static final Path REPO_SOURCE = Path.of(
        "src/main/java/com/tss/grc/module/admin/committee/repository/CommitteeMemberTermRepository.java");

    /** The three conditions the window is, and the only three it may be. */
    private static final List<String> WINDOW_CLAUSES = List.of(
        "t.endedAt IS NULL",
        "t.termStartsAt <= :on",
        "(t.termEndsAt IS NULL OR t.termEndsAt >= :on)");

    // ── 1. one definition ───────────────────────────────────────────────────

    @Test
    void theWindowConstantIsExactlyTheThreeConditionsAndNothingElse() {
        String w = CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW;

        for (String clause : WINDOW_CLAUSES)
            assertThat(w).as("window must contain %s", clause).contains(clause);

        // No renewal_status: that is the caller's half, applied in each query's WHERE. Folding it in would
        // make a constant named "window" silently answer a different question, which is how the report and
        // the guard came to disagree in the first place.
        assertThat(w).doesNotContain("renewalStatus");
        // No hardcoded date function: the clock is a parameter (F2-160).
        assertThat(w).doesNotContain("CURRENT_DATE").doesNotContain("now()");
        // Exactly three ANDs' worth of conditions — a fourth would be a silent widening or narrowing of
        // every consumer at once.
        assertThat(countOccurrences(w, " AND ")).isEqualTo(2);
    }

    @Test
    void theConstantLeadsWithItsOwnSeparatorSoNoCallerCanLoseIt() {
        // Java strips incidental TRAILING whitespace from each line of a text block, so a caller writing
        // `SUM(CASE WHEN """ + CURRENT_SEAT_WINDOW` loses the space it thought it had and emits
        // `WHENt.endedAt`. That fails at CONTEXT STARTUP with a QueryCreationException — the whole
        // application, not one endpoint. Measured: it is how the first draft of the aggregate failed.
        assertThat(CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW).startsWith("\n");
        assertThat(CommitteeMemberTermRepository.CURRENT_SEAT_PREDICATE).startsWith("\n");
    }

    @Test
    void theWhereFragmentIsTheWindowPlusRenewalStatusAndIsBuiltFromTheWindow() {
        String p = CommitteeMemberTermRepository.CURRENT_SEAT_PREDICATE;

        assertThat(p).contains(CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW);
        assertThat(p).contains("t.renewalStatus = 'ACTIVE'");
    }

    // ── 2. every consumer uses that definition ──────────────────────────────

    @Test
    void theSeatGuardsQueryIsBuiltFromTheSharedWindow() throws Exception {
        String q = queryOf("countCurrentSeatsForUser");

        // This is the query CommitteeSeatAuthority calls, i.e. the enforcement point itself.
        assertThat(q).contains(CommitteeMemberTermRepository.CURRENT_SEAT_PREDICATE);
        assertThat(q).contains("t.memberUserId = :userId");
    }

    @Test
    void theCoverageAggregateIsBuiltFromTheSameSharedWindowTwice() throws Exception {
        String q = queryOf("rosterCoverageByCommittee");

        // Twice: once for currentSeats, once for currentLinked. If either arm were re-typed by hand this
        // count would drop and the report could start describing an access rule the guard does not apply.
        assertThat(countOccurrences(q, CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW)).isEqualTo(2);
        assertThat(q).contains("t.renewalStatus = 'ACTIVE'");   // F2-26's half, in the WHERE
        assertThat(q).contains("GROUP BY t.committeeId");
    }

    @Test
    void noQueryInTheRepositoryRetypesTheWindowInsteadOfUsingTheConstant() throws IOException {
        String src = Files.readString(REPO_SOURCE);

        // The clause may appear in the source exactly once: inside the constant's own declaration. Any
        // second literal occurrence is a copy, which is the drift this whole slice is about — and a copy
        // reads as correct on the day it is written, which is why it needs a build failure rather than a
        // review comment.
        for (String clause : WINDOW_CLAUSES)
            assertThat(countOccurrences(src, clause))
                .as("`%s` must be written once, in CURRENT_SEAT_WINDOW — found a second copy", clause)
                .isEqualTo(1);
    }

    /**
     * Every query built from the window filters the tenant, conjunctively.
     *
     * <p>Added after a mutation <b>survived</b>: weakening the aggregate's tenant clause to
     * {@code (t.organisationId = :organisationId OR 1=1)} changed no assertion, because a stray
     * committee id from another tenant simply fails to match any of this tenant's committees and is
     * dropped by the service's join. So on the coverage report the filter is defence rather than
     * correctness — <em>today</em>. On {@link CommitteeMemberTermRepository#countCurrentSeatsForUser}
     * it is neither: that query <b>is</b> the authorisation decision, and losing the clause would seat
     * every tenant's members on every other tenant's committees.
     *
     * <p>The assertion is on the conjunctive <em>form</em> rather than on the mere presence of the
     * parameter, because `= :organisationId` appearing anywhere is satisfied by a clause that has been
     * OR-ed into irrelevance — which is exactly what the surviving mutation did.
     */
    @Test
    void everyQueryBuiltFromTheWindowFiltersTheTenantConjunctively() {
        List<String> unfiltered = new ArrayList<>();
        for (Method m : CommitteeMemberTermRepository.class.getDeclaredMethods()) {
            Query a = m.getAnnotation(Query.class);
            if (a == null || !a.value().contains(CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW))
                continue;
            if (!a.value().contains("WHERE t.organisationId = :organisationId"))
                unfiltered.add(m.getName());
        }
        assertThat(unfiltered).isEmpty();
    }

    @Test
    void everyQueryThatBindsOnAlsoUsesTheSharedWindow() throws Exception {
        List<String> bindsOnWithoutTheWindow = new ArrayList<>();
        for (Method m : CommitteeMemberTermRepository.class.getDeclaredMethods()) {
            Query a = m.getAnnotation(Query.class);
            if (a == null || !a.value().contains(":on")) continue;
            if (!a.value().contains(CommitteeMemberTermRepository.CURRENT_SEAT_WINDOW))
                bindsOnWithoutTheWindow.add(m.getName());
        }
        // `:on` exists in this repository for exactly one purpose. A query that binds a judgement date and
        // then writes its own window is the defect wearing the fix's clothes.
        assertThat(bindsOnWithoutTheWindow).isEmpty();
    }

    // ── 3. the Java twin behaves the way the JPQL reads ─────────────────────

    @Test
    void theEntityMethodAgreesWithTheClauseOnEveryBoundary() {
        LocalDate on = LocalDate.of(2026, 8, 9);

        assertThat(term(on.minusDays(1), on.plusDays(1), null).isCurrentOn(on)).isTrue();
        assertThat(term(on,              on,             null).isCurrentOn(on)).isTrue();  // both inclusive
        assertThat(term(on.minusYears(3), null,          null).isCurrentOn(on)).isTrue();  // indefinite term
        assertThat(term(on.plusDays(1),  null,           null).isCurrentOn(on)).isFalse(); // not started
        assertThat(term(on.minusYears(3), on.minusDays(1), null).isCurrentOn(on)).isFalse(); // expired
        assertThat(term(on.minusDays(1), on.plusDays(1), LocalDateTime.of(2026, 8, 1, 9, 0))
            .isCurrentOn(on)).isFalse();                                                   // ended early
        // NULL start: `t.termStartsAt <= :on` is UNKNOWN in SQL and therefore excludes the row, so Java
        // must exclude it too rather than reading NULL as "started long ago". The column is NOT NULL and 0
        // rows are NULL today, so this branch is unreachable — it is pinned because the alternative is a
        // Java/SQL disagreement that appears the day someone relaxes the column.
        assertThat(term(null, on.plusDays(1), null).isCurrentOn(on)).isFalse();
        assertThat(term(on.minusDays(1), on.plusDays(1), null).isCurrentOn(null)).isFalse();
    }

    @Test
    void theEntityMethodDoesNotConsultRenewalStatus() {
        CommitteeMemberTerm t = term(LocalDate.of(2026, 1, 1), null, null);

        t.setRenewalStatus("RESIGNED");
        // isCurrentOn answers "is the window open", not "is this seat live". Both halves are needed and
        // they are applied by different code; collapsing them here would make the method's name a lie and
        // hide which of the two a future caller actually wanted.
        assertThat(t.isCurrentOn(LocalDate.of(2026, 8, 9))).isTrue();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String queryOf(String methodName) throws NoSuchMethodException {
        for (Method m : CommitteeMemberTermRepository.class.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) continue;
            Query a = m.getAnnotation(Query.class);
            if (a != null) return a.value();
        }
        throw new NoSuchMethodException("no @Query on " + methodName);
    }

    private static CommitteeMemberTerm term(LocalDate start, LocalDate end, LocalDateTime endedAt) {
        CommitteeMemberTerm t = new CommitteeMemberTerm();
        t.setTermStartsAt(start);
        t.setTermEndsAt(end);
        t.setEndedAt(endedAt);
        t.setRenewalStatus("ACTIVE");
        return t;
    }

    private static int countOccurrences(String haystack, String needle) {
        Matcher m = Pattern.compile(Pattern.quote(needle)).matcher(haystack);
        int n = 0;
        while (m.find()) n++;
        return n;
    }
}
