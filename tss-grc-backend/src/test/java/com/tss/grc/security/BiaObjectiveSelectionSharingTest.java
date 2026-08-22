package com.tss.grc.security;

import com.tss.grc.module.bcm.entity.BiaRecord;
import com.tss.grc.module.bcm.service.BiaObjectives;
import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-282 — one predicate decides which BIA states a process's recovery objectives.
 *
 * <h2>The finding</h2>
 *
 * <p>A process can carry several BIA records; measured 2026-08-22, two of the ten
 * live processes do, identically in dev and prod. All three readers of
 * {@code BiaRecordRepository#findByProcessIdIn} picked one per process with a
 * hand-rolled loop, and the three loops did not agree — two orderings and three
 * null behaviours for one question:
 *
 * <ul>
 *   <li>{@code DependencyCascadeService} — lowest MTPD, a null row could seed;</li>
 *   <li>{@code BcmBoardPackService} — lowest MTPD, {@code continue}d past nulls, so
 *       a process with no stated MTPD vanished from the board pack;</li>
 *   <li>{@code MtpdInheritanceService} — <b>latest by {@code assessmentDate}</b>,
 *       filtering nulls only after selecting, so a blank MTPD on the most recent
 *       record cost the process its objective entirely.</li>
 * </ul>
 *
 * <p>RTO ≤ MTPD by definition, so whichever reader a control consults decides
 * whether a continuity plan is compliant — and the vendor-inheritance control asks
 * precisely that of a supplier's attested recovery time. They agree on every live
 * row today, which is F2-177's shape: what made it harmless was three independent
 * implementations coinciding, with nobody maintaining that.
 *
 * <h2>What this gate holds, and what it deliberately does not</h2>
 *
 * <p>It holds that the selection lives in exactly one place and that all three
 * callers ask it. It does <b>not</b> assert anything about {@code status}: 4 of 8
 * processes resolve to an unapproved BIA and for {@code Core Banking Operations}
 * the winner is an {@code IN_PROGRESS} row stating 1h over an {@code APPROVED} one
 * stating 72h. Preferring the approved row would make these controls report
 * <em>less</em> disruption on a customer's data and nothing here can tell which
 * figure is that organisation's real tolerance, so the status is surfaced on the
 * wire and the choice is filed (F2-197 — disclose, never manufacture an all-clear).
 */
class BiaObjectiveSelectionSharingTest {

    /** The three callers that must ask the shared predicate. */
    private static final List<String> CALLERS = List.of(
        "module/bcm/cascade/DependencyCascadeService.java",
        "module/bcm/cycle/service/BcmBoardPackService.java",
        "module/bcm/mtpd_inheritance/service/MtpdInheritanceService.java");

    private static final Path MAIN = Path.of("src", "main", "java", "com", "tss", "grc");
    private static final String SELECTOR = "module/bcm/service/BiaObjectives.java";

    /**
     * A hand-rolled selection: {@code x.getMtpdHours() < y.getMtpdHours()} — one
     * record's objective compared against ANOTHER'S. That is the defect; reading
     * {@code getMtpdHours()} to render it, to threshold a reach time against it, or
     * to filter nulls is legitimate and common.
     *
     * <p>⚠ The first draft was two loose alternatives, one of them
     * {@code (?:<|>)\s*\w+\.getMtpdHours\(\)} — and a Java lambda arrow contains a
     * {@code >}, so {@code .filter(b -> b.getMtpdHours() != null)} matched and the
     * gate failed on the correct code this slice had just written. A gate that
     * fails on correct code trains the next reader to edit the assertion rather
     * than the code (F2-214). Requiring the getter on BOTH sides is the pattern's
     * actual claim.
     */
    private static final Pattern HAND_ROLLED = Pattern.compile(
            "getMtpdHours\\(\\)\\s*(?:<=|>=|<|>)\\s*\\w+\\.getMtpdHours\\(\\)");

    /** The outlier's ordering: choosing a BIA by its assessment date. */
    private static final Pattern BY_DATE = Pattern.compile(
            "getAssessmentDate\\(\\)[\\s\\S]{0,120}?\\bisAfter\\b|"
          + "\\bisAfter\\b[\\s\\S]{0,120}?getAssessmentDate\\(\\)");

    private static Path resolve(String rel) {
        Path p = MAIN;
        for (String part : rel.split("/")) p = p.resolve(part);
        return p;
    }

    private static String code(String rel) throws IOException {
        return GuardExpressions.stripComments(
                Files.readString(resolve(rel), StandardCharsets.UTF_8));
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    // ─── the predicate is written once ──────────────────────────────────────

    @Test
    void theSelectionIsWrittenOnce() throws IOException {
        String sel = code(SELECTOR);

        assertThat(sel)
                .as("F2-282: the predicate is reachable by name")
                .containsPattern("authoritativeByProcess\\s*\\(")
                .as("F2-282: and the choice between two records is a named method")
                .containsPattern("boolean\\s+preferred\\s*\\(");

        // Exactly one ordering comparison, inside `preferred`. Asserting the BODY
        // rather than the file is what stops a second rule being added elsewhere in
        // the class and still counting as "written once".
        String body = sel.substring(sel.indexOf("boolean preferred("));
        body = body.substring(0, body.indexOf("\n    }"));
        assertThat(count(Pattern.compile("(?<![-<>=!])(?:<=|>=|<|>)(?!=)"), body))
                .as("F2-282: one comparison decides it — %s", body)
                .isEqualTo(1);
    }

    /**
     * ⚠ The predicate is a STATIC FUNCTION and not a {@code default} method on
     * {@code BiaRecordRepository}, and that is load-bearing rather than stylistic.
     *
     * <p>The first draft put it on the repository. It compiled and passed its own
     * tests, and it broke two existing {@code MtpdInheritanceServiceTest} cases —
     * because <b>a Mockito mock does not execute default methods</b>. The mocked
     * repository answered nothing, the stubbed {@code findByProcessIdIn} went
     * unused, and the resolved MTPD silently became empty. A decision placed on a
     * mockable interface is a decision every test that mocks that interface
     * silently loses (F2-237's family). This leg keeps it off the interface.
     */
    @Test
    void theRepositoryDeclaresNoDecision() throws IOException {
        String repo = code("module/bcm/repository/BiaRecordRepository.java");
        assertThat(repo)
                .as("F2-282: a `default` method here is invisible to every test that "
                    + "mocks this repository — keep the decision a pure function")
                .doesNotContain("default ")
                .doesNotContain("authoritativeByProcess");
        assertThat(count(HAND_ROLLED, repo)).isZero();
    }

    @Test
    void noCallerHandRollsTheSelection() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String rel : CALLERS) {
            String src = code(rel);
            int hand = count(HAND_ROLLED, src);
            int byDate = count(BY_DATE, src);
            if (hand > 0) offenders.add(rel + " — " + hand + " local MTPD comparison(s)");
            if (byDate > 0) offenders.add(rel + " — selects a BIA by assessmentDate");
        }
        assertThat(offenders)
                .as("""
                    F2-282: a caller is choosing between BIA records for itself again. \
                    Two orderings and three null behaviours for one question is what this \
                    slice removed, and RTO <= MTPD by definition, so the reader that picks \
                    differently decides whether a continuity plan is compliant. Ask \
                    BiaObjectives.authoritativeByProcess.""")
                .isEmpty();
    }

    @Test
    void everyCallerAsksTheSharedPredicate() throws IOException {
        List<String> silent = new ArrayList<>();
        for (String rel : CALLERS) {
            if (!Pattern.compile("BiaObjectives\\.authoritativeByProcess\\s*\\(")
                    .matcher(code(rel)).find()) {
                silent.add(rel);
            }
        }
        assertThat(silent)
                .as("F2-282: all three readers must go through the one predicate — "
                    + "a caller that stops asking has re-created the divergence")
                .isEmpty();
        assertThat(CALLERS).hasSize(3);
    }

    /**
     * Anti-vacuity. The ban above is a "collect the offenders, assert empty" leg,
     * which on a clean tree passes whether the detector works or not (F2-219), so
     * this drives it against the literal shapes all three callers had — and against
     * the shapes it must NOT flag.
     */
    @Test
    void theDetectorFiresOnTheShapesThatWereThere() {
        String cascade = """
            if (cur == null
                || (b.getMtpdHours() != null
                    && (cur.getMtpdHours() == null
                        || b.getMtpdHours() < cur.getMtpdHours()))) {""";
        String boardPack =
            "if (cur == null || (cur.getMtpdHours() != null "
          + "&& b.getMtpdHours() < cur.getMtpdHours())) {";
        String inheritance = """
            LocalDate da = a.getAssessmentDate() != null ? a.getAssessmentDate() : LocalDate.MIN;
            LocalDate db = b.getAssessmentDate() != null ? b.getAssessmentDate() : LocalDate.MIN;
            return db.isAfter(da) ? b : a;""";

        assertThat(count(HAND_ROLLED, cascade)).as("cascade's loop").isGreaterThan(0);
        assertThat(count(HAND_ROLLED, boardPack)).as("board pack's loop").isGreaterThan(0);
        assertThat(count(BY_DATE, inheritance)).as("the outlier's ordering").isGreaterThan(0);

        // Must NOT fire. The two lambda shapes are what broke the first draft: a
        // Java arrow contains a '>'.
        assertThat(count(HAND_ROLLED, "Integer mtpd = b == null ? null : b.getMtpdHours();")).isZero();
        assertThat(count(HAND_ROLLED, "boolean breached = threshold != null && reachHour >= threshold;")).isZero();
        assertThat(count(HAND_ROLLED, ".filter(b -> b.getMtpdHours() != null)")).isZero();
        assertThat(count(HAND_ROLLED, ".sorted(Comparator.comparingInt(BiaRecord::getMtpdHours))")).isZero();
        assertThat(count(BY_DATE, "b.getAssessmentDate()")).isZero();
    }

    // ─── the rule itself ───────────────────────────────────────────────────

    /**
     * The null behaviour the three copies disagreed about, asserted directly and in
     * both arrival orders — so the outcome cannot depend on what the query happened
     * to return first, which is F2-186's ordering defect in a recovery objective.
     */
    @Test
    void aRowStatingAnMtpdBeatsOneThatDoesNot() {
        BiaRecord blank = bia(1L, null, LocalDate.of(2026, 6, 1));
        BiaRecord stated = bia(1L, 24, LocalDate.of(2026, 1, 1));

        assertThat(pick(List.of(blank, stated))).isSameAs(stated);
        assertThat(pick(List.of(stated, blank)))
                .as("F2-282: order-independent, or the answer depends on the query plan")
                .isSameAs(stated);
    }

    @Test
    void theLowestStatedMtpdWinsRegardlessOfDate() {
        // The live shape: an older APPROVED record stating 72h and a newer
        // IN_PROGRESS one stating 1h. The date loses; the tighter tolerance wins.
        BiaRecord older = bia(1L, 72, LocalDate.of(2026, 1, 15));
        BiaRecord newer = bia(1L, 1, LocalDate.of(2026, 4, 15));

        assertThat(pick(List.of(older, newer)).getMtpdHours()).isEqualTo(1);
        assertThat(pick(List.of(newer, older)).getMtpdHours()).isEqualTo(1);
    }

    @Test
    void processesAreKeptApart() {
        Map<Long, BiaRecord> out = BiaObjectives.authoritativeByProcess(List.of(
            bia(1L, 8, LocalDate.of(2026, 1, 1)),
            bia(2L, 336, LocalDate.of(2026, 1, 1))));

        assertThat(out).hasSize(2);
        assertThat(out.get(1L).getMtpdHours()).isEqualTo(8);
        assertThat(out.get(2L).getMtpdHours()).isEqualTo(336);
    }

    @Test
    void aRowWithNoProcessOrNoRowsAtAllIsSkippedRatherThanThrowing() {
        assertThat(BiaObjectives.authoritativeByProcess(null)).isEmpty();
        assertThat(BiaObjectives.authoritativeByProcess(List.of())).isEmpty();

        List<BiaRecord> withNulls = new ArrayList<>();
        withNulls.add(null);
        withNulls.add(bia(null, 4, LocalDate.of(2026, 1, 1)));
        withNulls.add(bia(7L, 12, LocalDate.of(2026, 1, 1)));
        Map<Long, BiaRecord> out = BiaObjectives.authoritativeByProcess(withNulls);

        assertThat(out).hasSize(1);
        assertThat(out.get(7L).getMtpdHours())
                .as("F2-282: an unlinked row must not become some process's objective")
                .isEqualTo(12);
    }

    // ─── fixtures ──────────────────────────────────────────────────────────

    private static BiaRecord bia(Long processId, Integer mtpd, LocalDate assessed) {
        BiaRecord b = new BiaRecord();
        b.setProcessId(processId);
        b.setMtpdHours(mtpd);
        b.setAssessmentDate(assessed);
        return b;
    }

    private static BiaRecord pick(List<BiaRecord> rows) {
        return BiaObjectives.authoritativeByProcess(rows).get(rows.get(0).getProcessId());
    }
}
