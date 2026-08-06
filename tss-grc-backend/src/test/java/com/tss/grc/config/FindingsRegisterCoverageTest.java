package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-79 — the findings register must not silently fall behind the plan.
 *
 * <p><b>The defect this replaces.</b> The doc-map cell asserted that {@code F2-54}…{@code F2-63} had
 * <i>"ZERO entries in the register (grep: 0 hits each)"</i>. The grep was for {@code ### F2-} and the
 * register writes {@code ## F2-… · Title} — one hash out. So it reported zero for <b>every</b> id in
 * the file, including the 54 that were present, and the conclusion drawn from it was wrong in the
 * direction that looks like diligence.
 *
 * <p><b>Why that is a class and not a typo.</b> A query against the wrong shape returns a
 * clean-looking <b>zero</b> rather than an error. F2-72's comparator dissolved its own definition and
 * read green; F2-01's prose count-gate produced 69 false positives; this programme's own scans have
 * done it twice more since. <b>A measurement that cannot fail is indistinguishable from a measurement
 * that passes</b>, so this test asserts the *instrument* as well as the number:
 * {@link #theHeadingPatternActuallyMatchesTheRegister()} exists purely so a pattern that stops
 * matching fails loudly instead of reporting a tidy zero owed.
 *
 * <p><b>Mutation-proven, and the result is the whole argument.</b> Breaking both scans at once
 * ({@code ## F2-} → {@code ### F2-}, {@code F2-} → {@code FX-}) gives <b>4 run, 2 failures</b> — and
 * the two that failed are the two instrument checks. {@link #specsOwedOnlyEverDecreases()} read
 * <b>0 owed</b> and {@link #theRegisterDoesNotInventFindings()} read <b>0 orphans</b>: with nothing
 * matching on either side, both set differences are empty, so <b>both ratchets were perfectly green
 * while both scans were dead.</b> That is not a hypothetical about this gate — it is what the line
 * this test replaces actually did. A ratchet cannot detect its own blindness, because the direction
 * a broken scan fails in is always the reassuring one.
 *
 * <p><b>Why a ratchet and not zero.</b> 31 specs are owed today. Demanding zero would block every
 * unrelated change on documentation debt accumulated over months, and the corrected rule in the
 * doc-map is narrower than that: <i>a spec is owed before a finding is WORKED</i>. So the count may
 * only fall. The register outlives the plan and is what an external auditor is handed, which is why
 * it is worth a gate at all.
 */
class FindingsRegisterCoverageTest {

    private static final Path PLAN = Path.of("..", "UNIFIED_IMPLEMENTATION_PLAN.md");
    private static final Path REGISTER =
            Path.of("..", "TSS-GRC-Platform-Bible", "Registers", "FINDINGS_REGISTER_V2.md");

    /**
     * Any <i>mention</i> of an id in the plan — deliberately not a plan <i>row</i>.
     *
     * <p>A row-shaped pattern was measured and rejected: {@code ^\| \*\*F2-nnn\*\* \|} matches
     * <b>78</b> of the 140 ids the plan names, because the plan tracks findings in more than one
     * shape. {@code F2-118} and {@code F2-112} start no table cell at all — they live inside
     * {@code F2-03}'s row and in the sequencing line. Asserting the row shape would therefore report
     * <b>62 orphans</b>, every one of them fine, which is this programme's standing lesson that <b>a
     * regex that matches too little invents defects</b>.
     *
     * <p>The cost of the looser pattern is stated rather than hidden: this check can see a register
     * spec for a finding the plan has <b>never heard of</b>, and cannot see one the plan mentions in
     * passing without tracking. That is the failure worth catching, and the measurement above is why
     * a tighter pattern is not available. It also sharpens the finding itself — <b>the plan is a work
     * programme and the register is the flat per-finding record</b>, which is exactly why the register
     * outliving the plan is the thing that matters.
     */
    private static final Pattern REFERENCED = Pattern.compile("F2-(\\d{2,3})");

    /**
     * A register spec — {@code ## F2-nnn · Title} at the start of a line.
     *
     * <p>⚠ This is the exact pattern the original check got wrong. It is asserted separately below,
     * because if it ever stops matching, every other assertion here becomes vacuously true in the
     * comfortable direction.
     */
    private static final Pattern SPECCED = Pattern.compile("^## F2-(\\d{2,3})", Pattern.MULTILINE);

    /**
     * Specs owed as measured on 2026-08-06: the plan references 137 distinct ids and the register
     * carries 107, leaving 33; 31 after that slice wrote the specs for F2-78 and F2-79.
     *
     * <p><b>30 later the same day, and the way it got there is the point.</b> The F2-38 slice filed two
     * new findings (F2-141, F2-142), which pushed the owed count back to 33 and <b>failed this
     * ratchet</b> — the gate catching its own author adding to the debt it measures. Paying all three
     * specs in the same slice took it to 30. <b>Filing two findings and still ending lower is what
     * pay-as-you-go looks like from the outside.</b> May only decrease.
     *
     * <p><b>29 on the same day again, and the mechanism repeated exactly.</b> The F2-142 slice filed
     * {@code F2-147} — the server-side crisis senders reporting success for messages no gateway sent —
     * which pushed the count to 31 and failed this ratchet a second time, in the same session, on the
     * same author. Paying the specs for {@code F2-144} (closed end-to-end that morning) and
     * {@code F2-147} (filed minutes earlier) took it to 29. The alternative on offer was raising the
     * number by one and moving on, which is the whole reason the ceiling exists.
     */
    private static final int BASELINE_SPECS_OWED = 29;

    /** Floors, so a collapsed scan cannot read as a clean one (F2-69). */
    private static final int MIN_REFERENCED = 120;
    private static final int MIN_SPECCED = 100;

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p.toAbsolutePath(), e);
        }
    }

    private static Set<Integer> ids(Pattern pattern, String text) {
        Set<Integer> out = new TreeSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            out.add(Integer.parseInt(m.group(1)));
        }
        return out;
    }

    private static void assumeDocs() {
        Assumptions.assumeTrue(Files.isRegularFile(PLAN) && Files.isRegularFile(REGISTER),
                "plan / register not reachable from " + PLAN.toAbsolutePath());
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the register heading pattern still matches — or every count below is vacuous")
    void theHeadingPatternActuallyMatchesTheRegister() {
        assumeDocs();
        String reg = read(REGISTER);

        int specs = ids(SPECCED, reg).size();
        assertThat(specs)
                .as("the '## F2-nnn' pattern matched %d headings in a register that should hold "
                    + "100+. This is F2-79's own defect reproduced: the original check looked for "
                    + "'### F2-' (one hash too many) and therefore reported ZERO specs for a file "
                    + "that had 54 — a query against the wrong shape returns a clean-looking zero, "
                    + "not an error. If the register's heading level changed, fix this pattern; do "
                    + "not let it report that nothing is owed.", specs)
                .isGreaterThanOrEqualTo(MIN_SPECCED);

        assertThat(reg)
                .as("no '## F2-' heading found at all — the register format has changed")
                .containsPattern("(?m)^## F2-\\d");
    }

    @Test
    @DisplayName("the plan scan still finds the findings it references")
    void thePlanScanIsNotVacuous() {
        assumeDocs();
        int referenced = ids(REFERENCED, read(PLAN)).size();
        assertThat(referenced)
                .as("the plan referenced only %d distinct F2-* ids; it should reference 120+. The "
                    + "scan broke, and a low number here understates the debt.", referenced)
                .isGreaterThanOrEqualTo(MIN_REFERENCED);
    }

    @Test
    @DisplayName("specs owed may only decrease — the register outlives the plan")
    void specsOwedOnlyEverDecreases() {
        assumeDocs();
        Set<Integer> referenced = ids(REFERENCED, read(PLAN));
        Set<Integer> specced = ids(SPECCED, read(REGISTER));

        List<String> owed = referenced.stream()
                .filter(id -> !specced.contains(id))
                .map(id -> "F2-" + id)
                .toList();

        assertThat(owed.size())
                .as("%d findings are referenced by the plan with no spec in the register; %d was the "
                    + "measured baseline. The register is what an external auditor is handed, and it "
                    + "outlives the plan — a finding whose only record is a plan cell disappears when "
                    + "the plan is superseded. The rule is narrow on purpose: a spec is owed before a "
                    + "finding is WORKED, not before any unrelated change lands. Owed: %s",
                        owed.size(), BASELINE_SPECS_OWED, owed)
                .isLessThanOrEqualTo(BASELINE_SPECS_OWED);
    }

    @Test
    @DisplayName("every register spec is a finding the plan actually references")
    void theRegisterDoesNotInventFindings() {
        assumeDocs();
        Set<Integer> referenced = ids(REFERENCED, read(PLAN));
        Set<Integer> specced = ids(SPECCED, read(REGISTER));

        List<String> orphans = specced.stream()
                .filter(id -> !referenced.contains(id))
                .map(id -> "F2-" + id)
                .toList();

        // Three existed when this was written — F2-47, F2-113 and F2-124, specced from slices that
        // never told the plan. Closing F2-79 named all three in the plan, so the measurement is now
        // zero and the ceiling sits there. ⚠ Worth being straight about how it got to zero: the plan
        // now *mentions* them in the sentence recording that they were orphans, which satisfies this
        // check. That is the honest limit of a mention-based scan (see REFERENCED), and it is still
        // worth holding at zero — the failure it catches is a register spec for a finding the plan has
        // never heard of, which is how a finding ends up recorded in one document and invisible in
        // the other.
        assertThat(orphans.size())
                .as("%d register specs name a finding the plan never mentions: %s. Either the plan "
                    + "owes a row or the spec belongs to a closed programme — both are worth knowing, "
                    + "because a finding recorded in only one of the two documents is a finding that "
                    + "one audience cannot see.", orphans.size(), orphans)
                .isZero();
    }
}
