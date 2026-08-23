package com.tss.grc.security;

import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.module.ethics.service.AmlAlertService;
import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-289 — a consequence attached to TWO of a switch's THREE decision branches, where the comment
 * explaining it was byte-identical in the two that had it.
 *
 * <p><b>The defect.</b> {@code AmlAlertService#transition} recorded who decided and when.
 * {@code CLEARED} and {@code STR_FILED} each stamped {@code decidedByName} and {@code decidedAt};
 * {@code ESCALATED} — which records a {@code TRUE_POSITIVE} disposition, i.e. the analyst's
 * conclusion that a monitored transaction is genuinely suspicious, and the referral an MLRO acts on
 * under SAMA AML/CFT and FATF R.20 — stamped neither. {@code ESCALATED → CLOSED} is a declared edge
 * and the {@code CLOSED} branch keeps decision metadata as-is, so an alert could be escalated as a
 * confirmed true positive and closed with <b>no decider recorded anywhere, permanently</b>.
 *
 * <p>🔑🔑 <b>Repetition reads as a rule.</b> The five-line comment explaining the attribution was
 * copied verbatim into the two branches that had it, and the gap sat between them — so a reader
 * scrolling the switch met the mechanism twice and its absence once. Worse, the duplicated comment's
 * own worked example was <i>"the STR filed with the Saudi FIU"</i>, one of the two branches it
 * appeared in. F2-215's two-of-N, sharpened: <b>a comment duplicated across the branches that have a
 * mechanism is stronger evidence of coverage than one stated once.</b>
 *
 * <p>🔑 And F2-223 is the slice that added the attribution — to two of the three branches. F2-287's
 * headline: the slice that establishes a rule is the worst-placed slice to apply it to its own
 * leftovers, because the branch it did not touch is the one its new comment makes look finished.
 *
 * <p><b>Latent, and this says so.</b> 0 rows are {@code ESCALATED} and both rows carrying a
 * disposition also carry a decider, identical in dev and prod. It is reachable from the platform's
 * own screen today — the Escalate control is offered on every {@code INVESTIGATING} alert and three
 * real alerts are in that state — so this is a latent-hazard closure, not a defect discovery
 * (F2-283's rule about saying which one you have).
 *
 * <p><b>Hermetic.</b> Reads the two sources from disk the way {@code ConnectorBackingContractTest}
 * and {@code ScreeningEscalationContractTest} already do, so {@code mvn -o clean package} still
 * needs no database.
 *
 * <p>⚠ <b>The branch population is DERIVED, never a list of three names.</b> The decision outcomes
 * are {@code TRANSITIONS.get("INVESTIGATING")} — the states an alert under investigation can be
 * moved to. Add a fourth outcome edge and this gate demands it record a decider; type the three
 * names instead and it would certify exactly the itinerary its author happened to walk, which is the
 * mistake F2-221 had to correct in F2-220's filing.
 */
class AmlDispositionAttributionContractTest {

    private static final Path SERVICE = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "ethics", "service", "AmlAlertService.java");
    private static final Path ETHICS_PAGE = Path.of("..", "tss-grc-ui-v2", "src", "pages",
        "ethics", "EthicsPage.tsx");

    /** The one writer of the decision tuple. */
    private static final String WRITER = "recordDisposition";

    /** The four columns that are one attribution of one act and must move together. */
    private static final List<String> TUPLE = List.of(
        "setDisposition(", "setDispositionNotes(", "setDecidedByName(", "setDecidedAt(");

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String service() {
        return GuardExpressions.stripComments(read(SERVICE));
    }

    /**
     * The brace-balanced body of the first member whose declaration matches {@code anchor}.
     *
     * <p>String and character literals are skipped so a brace inside a message cannot end the walk.
     */
    private static String body(String src, String anchor) {
        int at = src.indexOf(anchor);
        assertThat(at).as("anchor %s must exist in the source — a body() that silently returns "
            + "nothing reads exactly like a clean method (F2-244)", anchor).isNotNegative();
        int open = src.indexOf('{', at);
        assertThat(open).as("anchor %s must be followed by a block", anchor).isNotNegative();
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                char quote = c;
                for (i++; i < src.length(); i++) {
                    if (src.charAt(i) == '\\') i++;
                    else if (src.charAt(i) == quote) break;
                }
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(open, i + 1);
        }
        throw new AssertionError("unbalanced braces after " + anchor);
    }

    /**
     * The body of {@code EthicsPage}'s AML transition handler.
     *
     * <p>Sliced by signature rather than by a brace walk: a {@code .tsx} file is full of
     * apostrophes in prose and of template literals, so the literal-skipping walk that is safe over
     * Java would swallow arbitrary spans here. The next declaration at the same indentation is a
     * cheaper boundary and cannot silently over-read — and it is asserted, because a slice that
     * quietly returns the wrong span reads exactly like a clean handler (F2-244).
     */
    private static String amlTransitionHandler() {
        String page = read(ETHICS_PAGE);
        int at = page.indexOf("async function transition(a: AmlAlertDto");
        assertThat(at).as("EthicsPage must still declare the AML transition handler — if it was "
            + "renamed, this gate is measuring nothing").isNotNegative();
        int end = page.indexOf("\n  /**", at);
        int fn = page.indexOf("\n  async function ", at + 1);
        if (fn >= 0 && (end < 0 || fn < end)) end = fn;
        String handler = end < 0 ? page.substring(at) : page.substring(at, end);
        assertThat(handler)
            .as("the slice must contain the call it exists to describe, or the boundary above ran "
              + "past or short of the handler")
            .contains("amlAlertApi.transition(");
        assertThat(handler)
            .as("and it must NOT reach the screening-hit draft further down the file, whose "
              + "vocabulary belongs to the sibling register (F2-288)")
            .doesNotContain("setHitDraft(");
        return handler;
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> transitions() throws Exception {
        var f = AmlAlertService.class.getDeclaredField("TRANSITIONS");
        f.setAccessible(true);
        return (Map<String, Set<String>>) f.get(null);
    }

    private static ClosedVocabulary vocabulary(String name) throws Exception {
        var f = AmlAlertService.class.getDeclaredField(name);
        f.setAccessible(true);
        Object v = f.get(null);
        assertThat(v).as("AmlAlertService.%s must exist and be non-null", name).isNotNull();
        return (ClosedVocabulary) v;
    }

    // ── the assertions ────────────────────────────────────────────────────────

    @Test
    @DisplayName("the decision tuple has exactly one writer")
    void theDecisionTupleHasExactlyOneWriter() {
        String src = service();
        for (String setter : TUPLE) {
            assertThat(count(src, setter))
                .as("%s must be written in exactly one place. The pre-fix tree wrote three of these "
                  + "four at three sites inside one switch, which is how two branches ended up "
                  + "stamping the decider and the third did not. A tuple with one writer cannot be "
                  + "partially applied by a future branch (F2-245: the claim that prevents drift is "
                  + "about WHERE the writes live).", setter)
                .isEqualTo(1);
        }
        assertThat(body(src, "private void " + WRITER))
            .as("all four columns are written by the one writer, or the count above is satisfied by "
              + "a setter that moved somewhere else entirely")
            .contains(TUPLE.get(0)).contains(TUPLE.get(1))
            .contains(TUPLE.get(2)).contains(TUPLE.get(3));
    }

    @Test
    @DisplayName("every decision outcome the machine allows records a decider")
    void everyDecisionOutcomeRecordsADecider() throws Exception {
        String src = service();
        String machine = body(src, "public AmlAlert transition(");
        Set<String> outcomes = new TreeSet<>(transitions().get("INVESTIGATING"));

        assertThat(outcomes)
            .as("anti-vacuity: the decision outcomes are derived from the machine, so an empty set "
              + "would make every assertion below pass over nothing")
            .hasSizeGreaterThanOrEqualTo(3);

        List<String> unattributed = new ArrayList<>();
        for (String target : outcomes) {
            String label = "case \"" + target + "\"";
            int at = machine.indexOf(label);
            assertThat(at)
                .as("the transition switch must handle %s, which the machine declares as an outcome "
                  + "of INVESTIGATING. A missing case means the disposition is never recorded for "
                  + "that outcome at all — the same absence as the ESCALATED branch this gate "
                  + "exists for, one level quieter.", target)
                .isNotNegative();
            // the branch runs from its label to the next `case ` / `default` at the same level
            int end = machine.indexOf("case \"", at + label.length());
            int def = machine.indexOf("default ", at + label.length());
            if (end < 0 || (def >= 0 && def < end)) end = def;
            String branch = end < 0 ? machine.substring(at) : machine.substring(at, end);
            if (!branch.contains(WRITER + "(")) unattributed.add(target);
        }

        assertThat(unattributed)
            .as("Every outcome of an investigation is a decision, and a decision with no recorded "
              + "decider is not a decision (KycService says so in those words on the sibling "
              + "register). ESCALATED was the missing one: it recorded a TRUE_POSITIVE disposition "
              + "and stamped neither decidedByName nor decidedAt, and because ESCALATED -> CLOSED "
              + "keeps decision metadata as-is, the alert closed that way permanently.")
            .isEmpty();
    }

    @Test
    @DisplayName("the attribution is the authenticated principal, and it refuses rather than blanks")
    void theAttributionRefusesRatherThanBlanks() {
        String writer = body(service(), "private void " + WRITER);
        assertThat(writer)
            .as("F2-236 — for the attribution half of a transition this is the accessor to use. A "
              + "service that stamps the clock from its own reading has already declared that IT is "
              + "the recorder of the act, so a blank name beside a real timestamp reads as an "
              + "attribution and is worse than a record that could not be written.")
            .contains("CurrentActor.requireUsername(");
        assertThat(writer)
            .as("CurrentActor.username() returns null for a non-GrcUserDetails principal, so using "
              + "it here would store an empty decider on an FIU-facing record")
            .doesNotContain("CurrentActor.username()");
    }

    @Test
    @DisplayName("the clock and the name move together, so a second decision cannot split them")
    void theClockAndTheNameMoveTogether() {
        String writer = body(service(), "private void " + WRITER);
        assertThat(writer)
            .as("STR_FILED used to preserve an existing decidedAt while overwriting decidedByName "
              + "unconditionally — F2-223's split waiting for a second decision (the name from "
              + "decision 2 beside the clock from decision 1). ESCALATED -> STR_FILED is the only "
              + "two-disposition path the machine allows, and it is now reachable with both halves "
              + "populated, so the conditional would start mattering the day this gate was relaxed.")
            .doesNotContain("getDecidedAt() == null")
            .doesNotContain("getDecidedAt()!=null")
            .doesNotContain("getDecidedAt() != null");
    }

    @Test
    @DisplayName("no caller value reaches disposition without passing the vocabulary")
    void noCallerValueReachesDispositionUnchecked() throws Exception {
        String src = service();
        assertThat(body(src, "private void " + WRITER))
            .as("the pre-fix tree wrote body.get(\"disposition\").toString() straight onto the "
              + "column at three sites, over a table with ZERO CHECK constraints of any kind")
            .contains("DISPOSITION.require(");
        // Scoped to the SHAPE of the claim (F2-192): a raw caller value assigned to the column,
        // not the mere presence of the key — the frontend legitimately mentions `disposition`.
        Matcher m = Pattern.compile(
            "setDisposition\\s*\\(\\s*(?![^)]*DISPOSITION\\s*\\.)[^)]*body\\s*\\.\\s*get")
            .matcher(src);
        List<String> raw = new ArrayList<>();
        while (m.find()) raw.add(m.group());
        assertThat(raw).as("a caller-supplied disposition must go through the vocabulary").isEmpty();

        ClosedVocabulary v = vocabulary("DISPOSITION");
        assertThat(v.tokens()).containsExactlyInAnyOrder("TRUE_POSITIVE", "FALSE_POSITIVE");
        assertThat(v.constraint()).isEqualTo("CK_aml_alerts_disposition");
    }

    @Test
    @DisplayName("both branch defaults are members of the vocabulary they are stored under")
    void bothBranchDefaultsAreMembers() throws Exception {
        String machine = body(service(), "public AmlAlert transition(");
        ClosedVocabulary v = vocabulary("DISPOSITION");
        Matcher m = Pattern.compile(WRITER + "\\s*\\([^)]*?\"([A-Z_]+)\"\\s*\\)").matcher(machine);
        Set<String> defaults = new LinkedHashSet<>();
        while (m.find()) defaults.add(m.group(1));
        assertThat(defaults)
            .as("anti-vacuity: each branch passes its fallback disposition as a literal, so an "
              + "empty set means the extraction stopped matching and the check below is over nothing")
            .isNotEmpty();
        for (String d : defaults) {
            assertThat(v.tokens())
                .as("%s is a fallback the service stores WITHOUT going through require(), so a "
                  + "vocabulary that does not admit it is a 547 on a legitimate transition — the "
                  + "mirror of V393's own §4 self-check, asserted here where a reader will see it",
                    d)
                .contains(d);
        }
    }

    @Test
    @DisplayName("the analytics buckets are derived from the machine, never a second typed list")
    void theAnalyticsBucketsAreDerived() throws Exception {
        String analytics = body(service(), "public Map<String, Object> analytics(");
        assertThat(analytics)
            .as("these two loops were a third and fourth copy of vocabularies this file declares. "
              + "An inclusion list over a column nothing constrains is F2-196's defect: a token "
              + "nobody remembered is silently absent from the KPI rather than chased.")
            .contains("TRANSITIONS.keySet()")
            .contains("SEVERITY.tokens()");
        // ⚠ Scoped to the SHAPE of the claim, and this leg's first draft was not: it banned the
        // literal "INVESTIGATING" anywhere in analytics() and FAILED ON CORRECT CODE, because
        // `openAlerts` is `byStatus.getOrDefault("OPEN") + getOrDefault("INVESTIGATING")`. That is
        // a deliberately NARROWER question than the machine's non-terminal complement — "awaiting
        // disposition", not "not closed" — and F2-250 already recorded it as such on this very
        // service. A gate that fails on working code trains the next reader to edit the assertion
        // rather than the code (F2-214). The claim is that the BUCKETING enumerates no second
        // vocabulary; two named states in a KPI that means something else is not that.
        assertThat(analytics)
            .as("a typed inclusion list is exactly what was removed from the two bucketing loops")
            .doesNotContain("List.of(");
    }

    @Test
    @DisplayName("severity is closed and alert_type is deliberately left open")
    void severityIsClosedAndAlertTypeIsDeliberatelyOpen() throws Exception {
        String editable = body(service(), "private void applyEditable(");
        assertThat(editable)
            .as("analytics() buckets by an inclusion list of exactly SEVERITY's four tokens, so a "
              + "value outside them makes the alert invisible to the KPI strip (F2-197's shape)")
            .contains("SEVERITY.require(");
        assertThat(vocabulary("SEVERITY").tokens())
            .containsExactlyInAnyOrder("LOW", "MEDIUM", "HIGH", "CRITICAL");

        // The other direction, asserted as a DECISION rather than left as an absence (F2-183's
        // rule about consumer.complaint.subcategory). 10 distinct alert_type values are stored and
        // no vocabulary for it is declared anywhere in either codebase, so constraining it would
        // mean inventing one. If a vocabulary is ever declared, this leg is where it gets noticed.
        assertThat(editable)
            .as("alert_type is deliberately unconstrained: no reader's inclusion list, no dropdown "
              + "and no CHECK declares a vocabulary for it, so a require() here would be an "
              + "invented rule rather than an enforced one. Closing it means declaring the "
              + "vocabulary FIRST, with the owner.")
            .contains("t.setAlertType(s.getAlertType())");
        assertThat(service()).doesNotContain("CK_aml_alerts_alert_type");
    }

    @Test
    @DisplayName("the frontend sends only dispositions the register admits")
    void theFrontendSendsOnlyDeclaredDispositions() throws Exception {
        // The F2-193 pair: the frontend half cannot tell a real vocabulary member from a plausible
        // string, and the backend half cannot see what a screen actually sends. Both are needed.
        //
        // ⚠⚠ Scoped to the AML transition handler, and this leg's first draft was FILE-scoped and
        // failed on correct code. `EthicsPage` also drives the SCREENING-HIT register from the same
        // component, and that one's vocabulary is NEEDS_REVIEW / POTENTIAL_MATCH / TRUE_MATCH /
        // FALSE_POSITIVE — F2-288's two-registers-one-word finding, in one file. A file-scoped scan
        // therefore compares one register's literals against the other register's CHECK and reports
        // a defect that is not there. Third time in two slices that a scan wider than its claim
        // invented a finding (F2-192).
        String handler = amlTransitionHandler();
        Matcher m = Pattern.compile("disposition:\\s*'([A-Z_]+)'").matcher(handler);
        Set<String> sent = new LinkedHashSet<>();
        while (m.find()) sent.add(m.group(1));
        assertThat(sent)
            .as("anti-vacuity: EthicsPage's AML transition handler sends a disposition literal per "
              + "branch, so an empty set means this regex stopped matching and reads as a clean tree")
            .hasSizeGreaterThanOrEqualTo(2);
        assertThat(vocabulary("DISPOSITION").tokens())
            .as("F2-180's rule in the direction that costs a screen: 19 of 22 incident categories a "
              + "page offered were refused by its column's CHECK. Every token this screen sends "
              + "must be one V393 admits, or the register's own Clear/Escalate/File-STR controls "
              + "answer 547.")
            .containsAll(sent);
    }

    @Test
    @DisplayName("the comment stripper is sound in both directions")
    void theStripperIsSound() {
        String raw = read(SERVICE);
        String stripped = GuardExpressions.stripComments(raw);
        assertThat(raw)
            .as("positive control: the pre-fix shape is quoted in this file's own javadoc and in the "
              + "service's, so a scan over RAW source reads the epitaph as the corpse (F2-193)")
            .contains("decidedByName");
        assertThat(stripped)
            .as("the stripper must not eat the code: the one writer and its call sites survive")
            .contains("private void " + WRITER)
            .contains("CurrentActor.requireUsername(");
        assertThat(count(stripped, "F2-223"))
            .as("every F2-223 reference in this service is inside a comment, so a working stripper "
              + "removes them all. A non-zero count here means the stripper is a no-op and every "
              + "count-based assertion above is reading prose as code.")
            .isZero();
    }
}
