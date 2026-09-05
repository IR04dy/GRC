package com.tss.grc.config;

import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-467 — <b>a {@code package-info.java} claims the module emits a trigger nothing emits, and an
 * enabled automation rule was wired to the promise.</b>
 *
 * <h2>Why this class exists rather than F2-459's leg</h2>
 *
 * <p>F2-459 built exactly this check and scoped it to the one module it was working in:
 * {@code ShariaLifecycleTest#thePackageInfoEventListNamesOnlyTokensThisModuleEmits} hard-codes
 * {@code module/sharia/package-info.java} and the {@code SHARIA_} prefix. Its reasoning is right
 * and its population was <b>the module the slice happened to be in</b> — F2-198's shape, after a
 * filename, a three-entry allow-list, a module name, an {@code extends} clause, a field-name list
 * inside a fix, a claim shape in a gate and an access modifier.
 *
 * <p>Asked of all 67, <b>five more modules claimed eight tokens the tree does not emit</b>:
 *
 * <pre>
 *   openbanking    OB_API_ABUSE  OB_CERT_EXPIRING  OB_CONSENT_EXPIRED
 *   regreporting   REG_REPORT_FILING_DUE_SOON      REG_REPORT_FILING_OVERDUE
 *   reginspect     REG_DOCUMENT_REQUEST_OVERDUE
 *   dataclass      DATA_REVIEW_DUE
 *   ai/lifecycle   MODEL_DRIFT_THRESHOLD_BREACHED
 * </pre>
 *
 * <p>⚠ <b>Three of the eight are ENABLED live automation rules with a fire count of 0, identical
 * in the dev and the deployed database</b> — AR-248 on {@code OB_API_ABUSE}, AR-249 on
 * {@code OB_CERT_EXPIRING}, AR-289 on {@code DATA_REVIEW_DUE}. F2-436 filed the rules; what
 * nothing recorded is that <b>the documentation is the cause</b>, and it was still asserting. A
 * {@code package-info} is the artefact a developer reads before wiring a rule, so a token
 * documented there reads as available.
 *
 * <h2>The rule that predicts where this drifts</h2>
 *
 * <p>🔑 Every token these modules <i>do</i> emit is a state <b>transition</b> —
 * {@code _REGISTERED}, {@code _TRANSITIONED}, {@code _SUBMITTED}, {@code _GRANTED},
 * {@code _REVOKED}, {@code _CLOSED} — and every token they claimed and did not emit is a
 * <b>derived condition</b>: an expiry, a due date, an overdue clock, a threshold crossing, an
 * abuse pattern. A transition has a natural publisher, the service method that performs it; a
 * derived condition has none unless somebody writes a sweep, and <b>none of the five modules
 * declares a {@code @Scheduled} method at all</b>. So a module publishes what its request path
 * does and documents what its dashboard computes, and the event list drifts precisely at the
 * boundary between the two — always in the direction of promising more.
 *
 * <p>Three of the five did not merely list a token, they <b>described the scheduler that fires
 * it</b> ("fired by F8 scheduler at T-7, T-3, T-1", "(raised by F8 scheduler)", "90 / 30 / 7-day
 * reminders"). That is F2-184 at its strongest: the claim answers the very question a reader would
 * otherwise ask.
 *
 * <h2>What this asserts</h2>
 *
 * <ol>
 *   <li><b>Hard zero, no exemption map</b> — no {@code package-info} under {@code module/} claims
 *       a token no code path emits. Safe by F2-214's test: correcting the sentence is always
 *       available and strictly more informative, so there is no correct code for it to fail on.</li>
 *   <li><b>The disclosure exclusion is load-bearing</b>, driven by F2-459's own corrected file:
 *       {@code module/sharia} now <i>names</i> its three unemitted tokens in a paragraph saying
 *       nothing emits them, so a verb-only rule would read the correction as the claim (F2-193 —
 *       a detector that cannot tell a quotation from an assertion forces every correction to
 *       paraphrase away its own evidence).</li>
 *   <li><b>The composed-prefix subtraction is load-bearing</b> — a token built at run time
 *       ({@code "DATA_FLOW_" + x}) is emitted although no literal exists, and F2-459 measured that
 *       this subtraction moves the platform-wide answer from 13 to 7.</li>
 *   <li><b>Self-test in both directions</b> over synthetic input — on a clean corpus a working
 *       detector and a dead one both report an empty list (F2-235/F2-455).</li>
 *   <li><b>Floors</b> above the largest <em>wrong</em> answer, which here is zero: no files, no
 *       emitted tokens, no claiming files.</li>
 * </ol>
 *
 * <p>⚠ <b>What it deliberately cannot see</b>, said out loud (F2-205). The comparison is
 * <b>tree-wide</b>, so a module documenting a trigger some <i>other</i> module emits passes — the
 * weaker reading, chosen because it cannot fail on a module that legitimately documents a shared
 * platform event. It is also one-directional: a token a module emits and does not document is not
 * a defect this leg reports.
 */
@DisplayName("F2-467 · a documented trigger is a trigger something emits")
class DocumentedTriggerEmissionContractTest {

    private static final Path MAIN = Paths.get("src", "main", "java").toAbsolutePath().normalize();
    private static final Path MODULES = MAIN.resolve(Paths.get("com", "tss", "grc", "module"));

    /** UPPER_SNAKE with at least one underscore — the shape every F8 trigger code takes. */
    private static final Pattern TOKEN = Pattern.compile("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+){1,}\\b");

    /** {@code "PREFIX_" + suffix} — a trigger assembled at run time (F2-459). */
    private static final Pattern COMPOSED =
            Pattern.compile("\"([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*_)\"\\s*\\+");

    /** The verb that turns a mention into a claim of emission. */
    private static final Pattern VERB = Pattern.compile(
            "\\bemit(?:s|ted|ting)?\\b|\\bpublish(?:es|ed)?\\b|\\braise[sd]?\\b|\\bfire[sd]?\\b"
            + "|F8 trigger|F8 event", Pattern.CASE_INSENSITIVE);

    /** …and the polarity that turns a claim back into a disclosure. */
    private static final Pattern NEGATION = Pattern.compile(
            "\\bno\\b|\\bnot\\b|\\bnothing\\b|\\bnever\\b|\\bremoved\\b|\\bcannot\\b"
            + "|\\bunemitted\\b|\\bplanned\\b|\\byet to\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern HEADING = Pattern.compile("<h\\d>([\\s\\S]*?)</h\\d>");

    /** What a package-info presents as emitted, and what it explicitly says is not. */
    record Claims(Set<String> claimed, Set<String> disclosed) {}

    private static Set<String> emittedTokens;
    private static Set<String> composedPrefixes;
    private static int packageInfoFiles;

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  the detector — extracted so the self-test can drive it over synthetic input
    // ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * A section whose <b>heading</b> carries an emission verb claims every token in that section;
     * outside such a section a block claims the tokens it holds if the block itself carries the
     * verb. A negating block is excluded either way.
     *
     * <p>Each clause earned its place by changing an answer. Without the section rule,
     * {@code <h2>F7 audit + F8 events</h2>} sits in its own block while the ten tokens sit in the
     * paragraph beneath it, and {@code DATA_REVIEW_DUE} — an enabled rule at 0 fires — was
     * dropped: <b>under-reporting, the quiet direction</b>. Without the block rule,
     * {@code module/ai/lifecycle}'s bare-prose claim is invisible. Without the negation rule,
     * F2-459's corrected file reads as the defect it fixed.
     */
    static Claims claimsOf(String javadoc) {
        String text = javadoc
                .replaceAll("(?m)^\\s*\\*/?", "")
                .replace("/**", "")
                .replaceAll("\\{@\\w+\\s+([^}]*)\\}", "$1");

        Set<String> claimed = new TreeSet<>();
        Set<String> disclosed = new TreeSet<>();

        // split into [preamble, heading, body, heading, body, ...]
        String[] parts = HEADING.split(text, -1);
        List<String[]> sections = new ArrayList<>();
        sections.add(new String[]{ "", parts[0] });
        Matcher h = HEADING.matcher(text);
        int i = 1;
        while (h.find() && i < parts.length) {
            sections.add(new String[]{ h.group(1), parts[i++] });
        }

        for (String[] section : sections) {
            boolean sectionClaims = VERB.matcher(section[0]).find();
            for (String unit : units(section[1])) {
                Set<String> toks = tokensIn(unit);
                if (toks.isEmpty()) {
                    continue;
                }
                if (NEGATION.matcher(unit).find()) {
                    disclosed.addAll(toks);
                } else if (sectionClaims || VERB.matcher(unit).find()) {
                    claimed.addAll(toks);
                }
            }
        }
        claimed.removeAll(disclosed);
        return new Claims(claimed, disclosed);
    }

    /**
     * The claim units of one section. A {@code <ul>} and the line introducing it are ONE unit,
     * because "Emits F7 audit + F8 trigger codes:" always heads the list rather than sitting in it.
     */
    private static List<String> units(String body) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("([^\\n]*)\\n?\\s*<ul>([\\s\\S]*?)</ul>").matcher(body);
        while (m.find()) {
            out.add(m.group(1) + "\n" + m.group(2));
        }
        for (String chunk : body.split("<ul>[\\s\\S]*?</ul>")) {
            for (String para : chunk.split("\\n\\s*\\n|<p>")) {
                if (!para.isBlank()) {
                    out.add(para);
                }
            }
        }
        return out;
    }

    private static Set<String> tokensIn(String s) {
        Set<String> out = new TreeSet<>();
        Matcher m = TOKEN.matcher(s);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /**
     * Every UPPER_SNAKE literal in real code, plus every run-time-composed prefix.
     *
     * <p>⚠ {@code package-info.java} is excluded from the code side: it is a Java file made
     * entirely of comments, so counting it would make every claim its own evidence — which is
     * exactly why F2-436's census could not see any of this (its shortlist required a trigger to
     * be "present in NO Java file").
     */
    private static synchronized void measure() {
        if (emittedTokens != null) {
            return;
        }
        Set<String> tokens = new TreeSet<>();
        Set<String> prefixes = new TreeSet<>();
        int files = 0;
        try (Stream<Path> walk = Files.walk(MAIN)) {
            for (Path p : walk.filter(x -> x.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("package-info.java")) {
                    if (p.startsWith(MODULES)) {
                        files++;
                    }
                    continue;
                }
                String code = GuardExpressions.stripComments(
                        Files.readString(p, StandardCharsets.UTF_8));
                tokens.addAll(tokensIn(code));
                Matcher m = COMPOSED.matcher(code);
                while (m.find()) {
                    prefixes.add(m.group(1));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        emittedTokens = tokens;
        composedPrefixes = prefixes;
        packageInfoFiles = files;
    }

    private static boolean composed(String token) {
        return composedPrefixes.stream()
                .anyMatch(p -> token.startsWith(p) && !token.equals(p.substring(0, p.length() - 1)));
    }

    private static List<Path> packageInfos() {
        try (Stream<Path> walk = Files.walk(MODULES)) {
            return walk.filter(p -> p.getFileName().toString().equals("package-info.java"))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  1 · the invariant
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("no package-info claims a trigger no code path emits")
    void noPackageInfoClaimsATriggerNothingEmits() {
        measure();
        var offenders = new TreeMap<String, Set<String>>();
        int claimingFiles = 0;
        for (Path p : packageInfos()) {
            Set<String> claimed = claimsOf(read(p)).claimed();
            if (!claimed.isEmpty()) {
                claimingFiles++;
            }
            Set<String> absent = new TreeSet<>();
            for (String t : claimed) {
                if (!emittedTokens.contains(t) && !composed(t)) {
                    absent.add(t);
                }
            }
            if (!absent.isEmpty()) {
                offenders.put(MODULES.relativize(p).toString().replace('\\', '/'), absent);
            }
        }
        assertThat(claimingFiles).as(
                "No package-info reads as making an emission claim at all — the detector has "
                + "stopped matching and the assertion below would then pass over nothing (F2-235). "
                + "A rotted claim rule reports the same empty offender list as a clean tree.")
                .isGreaterThan(15);
        assertThat(offenders).as("""
                A package-info.java is the artefact a developer reads before wiring an automation \
                rule, so a token documented there as emitted reads as available.

                F2-467 found eight such tokens across five modules, and THREE were already ENABLED \
                automation rules with a fire count of 0 in both databases: AR-248 on \
                OB_API_ABUSE, AR-249 on OB_CERT_EXPIRING, AR-289 on DATA_REVIEW_DUE. F2-436 filed \
                the rules; the documentation was the cause and was still asserting.

                Every one was a DERIVED CONDITION - an expiry, a due date, an overdue clock, a \
                threshold crossing - in a module that declares no @Scheduled method at all, while \
                every token those modules do emit is a state TRANSITION. A transition has a \
                natural publisher; a derived condition has none unless somebody writes a sweep.

                If the module emits this now, say so in code and the leg will let the list name \
                it. If it does not, follow module/sharia: move the token out of the emitted list \
                into a paragraph that names it and says nothing publishes it. Do not delete it \
                silently - an absence reads as completeness (F2-197), and a rule already wired to \
                it then has no explanation.""")
                .isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  2 · the two subtractions, both load-bearing, both measured on the real tree
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the disclosure exclusion is load-bearing — F2-459's corrected file proves it")
    void aDisclosureIsNotAClaim() {
        Claims sharia = claimsOf(read(MODULES.resolve(Paths.get("sharia", "package-info.java"))));

        assertThat(sharia.disclosed()).as(
                "F2-459 corrected module/sharia by MOVING three tokens out of the emitted list "
                + "into a paragraph that says nothing emits them. That paragraph sits INSIDE the "
                + "claiming section, so the exclusion has to be per block: a verb-only rule reads "
                + "the correction as the claim it fixed (F2-193).")
                .contains("SHARIA_SCHOLAR_TERM_EXPIRING", "SHARIA_BOARD_MEETING_SHORTFALL",
                        "SHARIA_NC_EVENT_CLOSED");
        assertThat(sharia.claimed()).as(
                "…and the same three must not also read as claimed, or the hard zero above fails "
                + "on a file this programme has already corrected.")
                .doesNotContain("SHARIA_SCHOLAR_TERM_EXPIRING", "SHARIA_BOARD_MEETING_SHORTFALL",
                        "SHARIA_NC_EVENT_CLOSED");
    }

    @Test
    @DisplayName("the composed-prefix subtraction is load-bearing, not decoration")
    void aRuntimeComposedTriggerCountsAsEmitted() {
        measure();
        assertThat(composedPrefixes).as(
                "No composed prefix was harvested — the pattern has stopped matching, and the "
                + "hard zero would then report every runtime-assembled trigger as undocumented "
                + "fiction. F2-459 measured 46 of these.")
                .hasSizeGreaterThan(20);

        var wouldFail = new TreeSet<String>();
        for (Path p : packageInfos()) {
            for (String t : claimsOf(read(p)).claimed()) {
                if (!emittedTokens.contains(t) && composed(t)) {
                    wouldFail.add(t);
                }
            }
        }
        assertThat(wouldFail).as(
                "Without the composed-prefix subtraction the hard zero above would fail on these "
                + "documented tokens, every one of which IS emitted — assembled at run time as "
                + "\"PREFIX_\" + suffix, so no literal exists for a scan to find. F2-459 measured "
                + "that this subtraction moves the platform-wide answer from 13 to 7. If this ever "
                + "empties, the subtraction has become untested rather than unnecessary.")
                .isNotEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  3 · self-test — on a clean corpus a working detector and a dead one agree
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the claim rule fires on a claim and not on a disclosure or a bare mention")
    void theDetectorSeparatesAClaimFromAMention() {
        // (a) a list under a paragraph carrying the verb
        assertThat(claimsOf("""
                /**
                 * <p>Emits F7 audit + F8 trigger codes:
                 * <ul>
                 *   <li>{@code THING_HAPPENED}</li>
                 * </ul>
                 */""").claimed()).containsExactly("THING_HAPPENED");

        // (b) a section HEADING carrying the verb, tokens in the paragraph beneath it -- the
        //     shape that dropped DATA_REVIEW_DUE when the heading was split off
        assertThat(claimsOf("""
                /**
                 * <h2>F7 audit + F8 events</h2>
                 *
                 * <p>2 triggers: {@code ONE_FIRED}, {@code TWO_FIRED}.
                 */""").claimed()).containsExactly("ONE_FIRED", "TWO_FIRED");

        // (c) bare prose, no heading and no list -- module/ai/lifecycle's shape
        assertThat(claimsOf("""
                /**
                 * <h2>Drift gate</h2>
                 *
                 * <p>Observations crossing the threshold emit {@code THRESHOLD_BREACHED}.
                 */""").claimed()).containsExactly("THRESHOLD_BREACHED");

        // (d) a DISCLOSURE inside a claiming section must not read as a claim
        Claims d = claimsOf("""
                /**
                 * <p>Emits F7 audit + F8 trigger codes:
                 * <ul>
                 *   <li>{@code REAL_ONE}</li>
                 * </ul>
                 *
                 * <p>It previously named {@code GHOST_ONE}; nothing emits it.
                 */""");
        assertThat(d.claimed()).containsExactly("REAL_ONE");
        assertThat(d.disclosed()).containsExactly("GHOST_ONE");

        // (e) a token in a block with no verb, in a section with no verb, is not a claim
        assertThat(claimsOf("""
                /**
                 * <h2>Two entities</h2>
                 *
                 * <p>An append-only ledger (ADDED / RETIRED / INCIDENT_REPORTED).
                 */""").claimed()).as(
                "privacy/subprocessor's shape: those are the ledger's own event_type values, "
                + "caller-supplied through the generic event endpoint, not an F8 trigger the "
                + "module publishes. A whole-file scan reported it and was wrong.")
                .isEmpty();

        // (f) a mention of a token elsewhere in a claiming FILE but a non-claiming section
        assertThat(claimsOf("""
                /**
                 * <p>Emits F8 trigger codes:
                 * <ul>
                 *   <li>{@code A_FIRED}</li>
                 * </ul>
                 *
                 * <h2>Vocabulary</h2>
                 *
                 * <p>status is one of DRAFT_STATE / FINAL_STATE.
                 */""").claimed()).containsExactly("A_FIRED");
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  4 · floors — above the largest WRONG answer, which here is zero
    // ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the corpus and the emitted set are measured")
    void bothSidesOfTheComparisonResolvedSomething() {
        measure();
        assertThat(packageInfoFiles).as("package-info.java files under module/").isGreaterThan(50);
        assertThat(packageInfos()).as("the walk that reads them").hasSizeGreaterThan(50);
        assertThat(emittedTokens).as(
                "UPPER_SNAKE literals in real code. A negative assertion over an empty emitted "
                + "set reports every documented token as fiction; over an empty corpus it reports "
                + "none. Both wrong answers are zero, so the floors sit above zero rather than "
                + "the assertion being trusted on its own (F2-215).")
                .hasSizeGreaterThan(1_500);
    }
}
