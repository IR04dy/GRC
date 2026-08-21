package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
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
 * <p><b>F2-265 — and this gate now publishes its own measurement.</b> Four documents used to
 * restate these counts in prose. A count in prose is a <b>copy</b> of a measurement, and the
 * doc-map cell that carried them says <i>"re-derive it, never edit it by hand"</i> — an
 * instruction with no enforcement, which drifted through <b>four</b> re-derivations, each of
 * which added a warning rather than an enforcement. {@link #REPORT} is the authority now, and
 * {@link #theDocMapCellPointsAtTheGateRatherThanRestatingIt()} refuses a restatement.
 *
 * <p><b>Why a ratchet and not zero.</b> Demanding zero would block every unrelated change on
 * documentation debt accumulated over months, and the corrected rule in the doc-map is narrower
 * than that: <i>a spec is owed before a finding is WORKED</i>. So the count may only fall.
 * ⚠ <b>This paragraph used to open <i>"31 specs are owed today"</i> while
 * {@link #BASELINE_SPECS_OWED} three paragraphs below read 29</b> — the constant's own javadoc
 * narrates the walk down to 29 and this sentence was not part of it. F2-265: house rule 3's
 * named hazard (<i>read the constant, never a prose summary of it</i>) inside the file that owns
 * the constant, so the number is stated in exactly one place now. The register outlives the plan and is what an external auditor is handed, which is why
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

    /**
     * Where the measurement lives, so no document has to restate it.
     *
     * <p>The convention is this tree's own — {@code target/permission-usage.txt},
     * {@code target/mass-assignment-risk.txt}, {@code target/server-stamped-fields.txt}. It exists
     * because F2-265 measured what a copied count does: the plan's doc-map cell reported
     * <b>94 referenced / 57 specs / 40 owed</b> against a measured <b>264 / 235 / 29</b>, and the
     * list of owed ids it published — the list a reader consults to obey the cell's own rule
     * <i>"write one before scheduling any of them"</i> — <b>omitted 22 of the 29 and named one
     * that was already paid</b>.
     */
    private static final Path REPORT = Path.of("target", "findings-register-coverage.txt");

    /**
     * The doc-map cell that owns the rule, anchored on its own rule sentence.
     *
     * <p>Deliberately the <i>rule</i> rather than a heading — a cell that stops saying this has
     * stopped being the cell this gate is about.
     *
     * <p>⚠⚠ <b>Resolved against {@link #unquoted(String)} text, and the omission broke this gate
     * on its own plan row.</b> I quote-stripped {@link #COVERAGE_COUNT} and not the anchor, so the
     * moment F2-265's own plan entry quoted this rule sentence two lines matched and the uniqueness
     * assertion failed. <b>A detector that cannot tell a quotation from a claim is defeated by this
     * register's own correction convention</b> (F2-193) — and it applies to every part of the
     * detector, not just the part whose claim is about counts.
     */
    private static final String DOC_MAP_ANCHOR = "never edit it by hand";

    /**
     * A coverage count restated in prose.
     *
     * <p>Scoped to the shape of the claim rather than to digits (F2-192): the cell legitimately
     * carries id ranges, dates and narrative numbers, and a blanket ban would fail on correct
     * prose — the one failure a gate must never have (F2-214). Pinned in both directions by
     * {@link #theRestatementDetectorIsPinnedInBothDirections()}.
     *
     * <p>⚠ {@code referenc(?:es|ed)} covers both stems on purpose. The pre-fix cell carried
     * {@code referenced **74+**} as history, and a pattern keyed on the present tense alone would
     * have missed it — passing for a reason unrelated to its claim.
     */
    private static final Pattern COVERAGE_COUNT = Pattern.compile(
            "referenc(?:es|ed)\\s+\\*\\*\\d+\\+?\\*\\*"
            + "|carries\\s+\\*\\*\\d+\\*\\*"
            + "|\\*\\*\\d+\\s+(?:are\\s+)?owed\\*\\*"
            + "|leaving\\s+\\*\\*\\d+\\*\\*");

    /** A plan row's ID cell — exactly a bolded finding id, so a mention cannot match. */
    private static final Pattern ID_CELL = Pattern.compile("^\\*\\*F2-(\\d{2,3})[a-z]?\\*\\*$");

    /**
     * Verdict markers, as Unicode escapes rather than raw characters (F2-235: an escape is the
     * only form that survives every tool between an author and a file).
     */
    private static final List<String> CLOSED_MARKERS =
            List.of("\u2705", "\u2714", "\u274c");

    private static final List<String> OPEN_MARKERS =
            List.of("\u2b1c", "\ud83d\udd34");

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

    /** Strip double-quoted spans, so a correction that quotes what it replaces is not a claim. */
    private static String unquoted(String text) {
        return text.replaceAll("\"[^\"]*\"", "\"\"");
    }

    /** Split a markdown row on pipes, ignoring pipes inside inline code spans. */
    private static List<String> rowCells(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inCode = false;
        for (char c : line.toCharArray()) {
            if (c == '`') {
                inCode = !inCode;
            }
            if (c == '|' && !inCode) {
                cells.add(buf.toString().trim());
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        cells.add(buf.toString().trim());
        return cells;
    }

    /**
     * A HINT at whether an owed finding is closed, read off its own plan row.
     *
     * <p><b>Nothing is gated on this and it is not a verdict.</b> F2-265's first attempt classified
     * by scanning for a marker anywhere on the line, and reported F2-68 and F2-70 as partial when
     * both read {@code DONE} — the lines here run past 6,000 characters and carry many markers, so
     * the lookup could not fail loudly and produced a name instead (F2-252). It is published
     * because the owed count alone cannot tell retrospective documentation from live work, and it
     * is a hint because a plan-status parser is not something this gate can keep correct — making
     * it load-bearing would build the authoritative-looking-but-unverified artefact F2-265 is
     * about.
     */
    private static String closureHint(String plan, int id) {
        for (String line : plan.split("\n")) {
            if (!line.startsWith("|")) {
                continue;
            }
            List<String> cells = rowCells(line);
            if (cells.size() < 4) {
                continue;
            }
            Matcher m = ID_CELL.matcher(cells.get(1));
            if (!m.matches() || Integer.parseInt(m.group(1)) != id) {
                continue;
            }
            String verdict = "";
            for (int i = cells.size() - 1; i >= 2; i--) {
                if (!cells.get(i).isEmpty()) {
                    verdict = cells.get(i);
                    break;
                }
            }
            for (String marker : CLOSED_MARKERS) {
                if (verdict.startsWith(marker)) {
                    return "hint:closed";
                }
            }
            for (String marker : OPEN_MARKERS) {
                if (verdict.startsWith(marker)) {
                    return "hint:OPEN";
                }
            }
            return "hint:no-status-marker (read the row)";
        }
        return "hint:no-own-row (named only inside another finding's cell)";
    }

    // ────────────────────────────────────────────────────────────────────────────────────────

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

    @Test
    @DisplayName("the measurement is published, so no document has to restate it")
    void publishesTheMeasurementSoNoDocumentRestatesIt() throws IOException {
        assumeDocs();
        String plan = read(PLAN);
        Set<Integer> referenced = ids(REFERENCED, plan);
        Set<Integer> specced = ids(SPECCED, read(REGISTER));
        List<Integer> owed = referenced.stream().filter(id -> !specced.contains(id)).toList();
        List<Integer> orphans = specced.stream().filter(id -> !referenced.contains(id)).toList();

        // ⚠ Delete first. F2-265's mutation harness replaced the write below with a
        // branch that never fires, and this leg PASSED on the PREVIOUS run's file, which
        // still carried the right numbers -- the stale-artefact-answers-an-older-question
        // shape, one artefact over from the stale surefire report this repo records.
        Files.deleteIfExists(REPORT);

        List<String> out = new ArrayList<>();
        out.add("# Findings-register coverage - GENERATED by FindingsRegisterCoverageTest.");
        out.add("# Do not hand-edit, and do not copy these numbers into prose. F2-265: a count in");
        out.add("# prose is a copy of a measurement, and the plan cell that carried these said");
        out.add("# \"re-derive it, never edit it by hand\" through four drifts, each of which added a");
        out.add("# warning rather than an enforcement.");
        out.add("");
        out.add("plan_references_distinct_ids=" + referenced.size());
        out.add("register_specs=" + specced.size());
        out.add("specs_owed=" + owed.size() + "  (ceiling " + BASELINE_SPECS_OWED
                + ", decrease-only)");
        out.add("orphan_specs=" + orphans.size());
        out.add("");
        out.add("# Specs owed. Column 2 is a HINT read off the finding's own plan row; NOTHING is");
        out.add("# gated on it and it is not a verdict - see closureHint().");
        for (int id : owed) {
            out.add(String.format("F2-%-4d %s", id, closureHint(plan, id)));
        }
        String body = String.join(System.lineSeparator(), out);
        Files.writeString(REPORT, body, StandardCharsets.UTF_8);

        assertThat(REPORT).as("the report must exist for the doc-map cell to point at").exists();
        assertThat(read(REPORT))
                .as("the report on disk must be the one this run produced, not a leftover "
                    + "from an earlier one that happens to carry the same numbers")
                .isEqualTo(body)
                .contains("specs_owed=" + owed.size())
                .contains("register_specs=" + specced.size());
    }

    @Test
    @DisplayName("the doc-map cell points at the measurement instead of restating it")
    void theDocMapCellPointsAtTheGateRatherThanRestatingIt() {
        assumeDocs();
        List<String> found = read(PLAN).lines()
                .filter(l -> unquoted(l).contains(DOC_MAP_ANCHOR))
                .toList();
        assertThat(found)
                .as("expected exactly one plan cell carrying the rule '%s'. Zero means the anchor "
                    + "moved and every assertion below is vacuous; more than one means the rule is "
                    + "stated twice, which is the defect this gate is about.", DOC_MAP_ANCHOR)
                .hasSize(1);
        String cell = found.get(0);

        assertThat(cell)
                .as("the doc-map cell must name the gate that owns this measurement. Deleting the "
                    + "counts without naming their source leaves a reader nowhere to look, which "
                    + "is how the counts got written into prose in the first place.")
                .contains("FindingsRegisterCoverageTest");
        assertThat(cell)
                .as("the doc-map cell must name the published artefact, so 're-derive it' is an "
                    + "instruction a reader can follow rather than one they have to invent.")
                .contains(REPORT.toString().replace('\\', '/'));

        List<String> restated = COVERAGE_COUNT.matcher(unquoted(cell)).results()
                .map(MatchResult::group).toList();
        assertThat(restated)
                .as("the doc-map cell restates %d coverage count(s) the gate owns: %s. Every one is "
                    + "a copy of a measurement that moves on almost every commit - the pre-fix cell "
                    + "read 94/57/40 against a measured 264/235/29. State the rule and point at %s; "
                    + "if you are recording what a figure USED to be, put it in double quotes, "
                    + "which is this register's own correction convention.",
                        restated.size(), restated, REPORT)
                .isEmpty();
    }

    @Test
    @DisplayName("the restatement detector fires on the text it retired, and not on a quotation")
    void theRestatementDetectorIsPinnedInBothDirections() {
        String retired = "Current, and every figure here is script output: plan references **94** "
                + "distinct ids, the register carries **57** specs, **40 are owed**.";
        assertThat(COVERAGE_COUNT.matcher(unquoted(retired)).results().count())
                .as("the detector must fire on the literal pre-fix sentence; a gate that passes on "
                    + "a green tree is indistinguishable from one whose pattern stopped matching")
                .isEqualTo(3L);

        String history = "The plan then referenced **74+** distinct ids, not 62.";
        assertThat(COVERAGE_COUNT.matcher(unquoted(history)).results().count())
                .as("both stems: the pre-fix cell carried this as unquoted history, and a "
                    + "present-tense-only pattern would have missed it")
                .isEqualTo(1L);

        String quoted = "CORRECTED: this cell used to read *\"plan references **94** distinct ids, "
                + "the register carries **57** specs, **40 are owed**\"* - every figure stale.";
        assertThat(COVERAGE_COUNT.matcher(unquoted(quoted)).results().count())
                .as("a correction QUOTES the sentence it replaces (F2-193), so a detector that "
                    + "cannot tell a quotation from a claim would force every correction to "
                    + "paraphrase away its own evidence")
                .isZero();

        String clean = "Re-derive with FindingsRegisterCoverageTest, which writes "
                + "target/findings-register-coverage.txt. Round 3 covers 12 findings.";
        assertThat(unquoted("a row quoting *\"" + DOC_MAP_ANCHOR + "\"* is not the cell"))
                .as("the anchor must be resolved against quote-stripped text: F2-265's own plan\n"
                    + "entry quotes this rule sentence, and without stripping, two lines match and\n"
                    + "the uniqueness assertion fails on a correct tree")
                .doesNotContain(DOC_MAP_ANCHOR);
        assertThat(COVERAGE_COUNT.matcher(unquoted(clean)).results().count())
                .as("must not fire on prose carrying no coverage count - a gate that fails on "
                    + "correct code trains the next reader to edit the assertion (F2-214)")
                .isZero();
    }
}
