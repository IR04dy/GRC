package com.tss.grc.module.privacy_lc;

import com.tss.grc.module.privacy.DsrRequestLifecycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-196 — the DSR lifecycle vocabularies, and the rule that nobody may restate them.
 *
 * <p><b>What went wrong.</b> The platform has three server-side DSR registers on three
 * tables with disjoint status vocabularies and <b>no CHECK constraint on any of the three
 * status columns</b>. Every reader therefore had to know the vocabulary by heart, and seven
 * of them did so independently:
 *
 * <ul>
 *   <li>{@code privacy_lc.dsr_cases} — the declared machine, the owning service's
 *       {@code overdue()} query, {@code PrivacyGapService.dsrSlaSweep()},
 *       {@code ThreeLinesService.dsrOpenCount()}, and the repository's own javadoc.</li>
 *   <li>{@code privacy.data_subject_requests} — three service guards, the stats count,
 *       and {@code PrivacyPage.tsx}.</li>
 * </ul>
 *
 * <p>Two were wrong, and both errors were invisible to a green suite:
 *
 * <ol>
 *   <li>{@code dsrSlaSweep} listed {@code EXTENDED} as closed. It is not: the machine gives
 *       it five outgoing edges, and entering it pushes {@code slaDeadline} out 30 days, so
 *       the deadline already accounts for the Article 4 extension. Measured 2026-08-12 in dev
 *       <em>and</em> the deployed database — the sweep had run on 2026-06-22 and stamped 7 of
 *       12 cases, silently skipping the 2 {@code EXTENDED} ones that were already 4 and 11
 *       days past their extended deadlines. The existing tests passed {@code any()} for that
 *       argument, so they asserted the dedup logic over a wrong selection.</li>
 *   <li>{@code PrivacyPage} and the stats count used <em>inclusion</em> lists of open
 *       statuses, which drop any token nobody remembered — including {@code OPEN}, the one
 *       the intake promotion adapter writes.</li>
 * </ol>
 *
 * <p><b>The rules enforced here.</b> (a) {@code TERMINAL} is derived from the declared
 * machine, never typed; (b) "open" is the <em>complement</em> of terminal, so an
 * unrecognised status is open — the safe direction for a statutory clock; (c) comparison is
 * case-insensitive, because the columns are CI-collated with no CHECK; (d) no other class
 * may restate either vocabulary; (e) the frontend mirror agrees in both directions.
 *
 * <p>Hermetic: reads this classpath and two source trees from disk. No database, no
 * secrets, no running server.
 */
class DsrLifecycleContractTest {

    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path FE_DSR_LIFECYCLE =
        Path.of("..", "tss-grc-ui-v2", "src", "lib", "dsrLifecycle.ts");
    private static final Path FE_PRIVACY_PAGE =
        Path.of("..", "tss-grc-ui-v2", "src", "pages", "privacy", "PrivacyPage.tsx");

    // ── (a) terminal is derived, not typed ────────────────────────────────────────────────

    @Test
    @DisplayName("privacy_lc: TERMINAL is exactly the machine's states with no outgoing edges")
    void terminalIsDerivedFromTheMachineNotTyped() {
        Set<String> mentioned = new TreeSet<>(DsrCaseLifecycle.ALLOWED.keySet());
        DsrCaseLifecycle.ALLOWED.values().forEach(mentioned::addAll);
        Set<String> withOutgoingEdges = DsrCaseLifecycle.ALLOWED.keySet();

        Set<String> derived = new TreeSet<>(mentioned);
        derived.removeAll(withOutgoingEdges);

        assertThat(DsrCaseLifecycle.TERMINAL)
            .as("TERMINAL must be computed from ALLOWED so the two cannot drift")
            .isEqualTo(derived);
        assertThat(DsrCaseLifecycle.TERMINAL)
            .as("stated explicitly so that changing the machine's terminality shows up in a diff")
            .containsExactlyInAnyOrder("DELIVERED", "REJECTED");
        assertThat(DsrCaseLifecycle.TERMINAL_LIST)
            .as("TERMINAL_LIST is the same set, sorted, for repository queries")
            .containsExactlyElementsOf(new TreeSet<>(DsrCaseLifecycle.TERMINAL));
    }

    @Test
    @DisplayName("privacy_lc: EXTENDED is open — it has outgoing edges and a pushed-out deadline")
    void extendedIsOpenBecauseTheMachineGivesItOutgoingEdges() {
        assertThat(DsrCaseLifecycle.ALLOWED)
            .as("EXTENDED is a state the case LEAVES; that is why it cannot be terminal")
            .containsKey("EXTENDED");
        assertThat(DsrCaseLifecycle.allowedTargets("EXTENDED")).isNotEmpty();
        assertThat(DsrCaseLifecycle.isTerminal("EXTENDED")).isFalse();
        assertThat(DsrCaseLifecycle.isOpen("EXTENDED")).isTrue();
        assertThat(DsrCaseLifecycle.TERMINAL_LIST)
            .as("the PDPL breach sweep selects on NOT IN this list — EXTENDED must not appear, "
                + "or an extended case can never breach its own extended deadline")
            .doesNotContain("EXTENDED");
    }

    // ── (b) open is the complement ────────────────────────────────────────────────────────

    @Test
    @DisplayName("both registers: open is the complement, so an undeclared status counts as OPEN")
    void openIsTheComplementSoAnUnknownStatusIsOpen() {
        // The load-bearing asymmetry. An inclusion list would answer "not open" here, which is
        // how a forgotten token gets silently excused from a statutory clock.
        for (String invented : List.of("AWAITING_LEGAL", "PAUSED", "", "  ", "ZZZ_NOT_A_STATUS")) {
            assertThat(DsrCaseLifecycle.isOpen(invented))
                .as("privacy_lc: unrecognised status %s must count as open", invented).isTrue();
            assertThat(DsrRequestLifecycle.isOpen(invented))
                .as("privacy: unrecognised status %s must count as open", invented).isTrue();
        }
        assertThat(DsrCaseLifecycle.isOpen(null)).isTrue();
        assertThat(DsrRequestLifecycle.isOpen(null)).isTrue();

        // ...and the complement really is a complement, for every declared token.
        Set<String> allCaseStates = new TreeSet<>(DsrCaseLifecycle.ALLOWED.keySet());
        DsrCaseLifecycle.ALLOWED.values().forEach(allCaseStates::addAll);
        for (String s : allCaseStates) {
            assertThat(DsrCaseLifecycle.isOpen(s)).isNotEqualTo(DsrCaseLifecycle.isTerminal(s));
        }
        for (String s : DsrRequestLifecycle.TERMINAL) {
            assertThat(DsrRequestLifecycle.isOpen(s)).isFalse();
        }
    }

    @Test
    @DisplayName("privacy: the four statuses this register actually holds as finished are terminal")
    void theRequestTerminalSetCoversWhatTheDatabaseHolds() {
        // Measured 2026-08-12, identical in dev and the deployed database: RESOLVED x6 and
        // COMPLETED x1 of 20 rows are finished requests, and FULFILLED — what the service
        // writes and what the page used to test for — appears in NONE of them.
        assertThat(DsrRequestLifecycle.TERMINAL)
            .containsExactlyInAnyOrder("FULFILLED", "REJECTED", "RESOLVED", "COMPLETED");
        for (String open : List.of("NEW", "PENDING", "OPEN", "IN_PROGRESS", "ESCALATED")) {
            assertThat(DsrRequestLifecycle.isOpen(open))
                .as("%s is a live status of this register", open).isTrue();
        }
        assertThat(DsrRequestLifecycle.isOpen("OPEN"))
            .as("OPEN is what DsrPromotionAdapter writes; dropping it hid every DSR filed "
                + "through the platform's own intake front door")
            .isTrue();
    }

    // ── (c) CI collation ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("both registers: comparison is case-insensitive and trims")
    void statusComparisonSurvivesTheCollation() {
        // Latin1_General_100_CI_AS_SC_UTF8 with no CHECK: 'delivered' is storable, and a bare
        // "DELIVERED".equals(...) would be false against it. F2-179 family.
        assertThat(DsrCaseLifecycle.isTerminal("delivered")).isTrue();
        assertThat(DsrCaseLifecycle.isTerminal(" Delivered ")).isTrue();
        assertThat(DsrCaseLifecycle.isOpen("extended")).isTrue();
        assertThat(DsrRequestLifecycle.isTerminal("resolved")).isTrue();
        assertThat(DsrRequestLifecycle.isTerminal("  cOmPlEtEd  ")).isTrue();
        assertThat(DsrRequestLifecycle.isOpen("in_progress")).isTrue();
    }

    // ── (d) nobody restates a vocabulary ─────────────────────────────────────────────────

    /** Tokens unique enough to the privacy_lc register that seeing two together is a restatement. */
    private static final Set<String> CASE_TOKENS =
        Set.of("INTAKE", "VERIFYING_IDENTITY", "COLLECTING", "REDACTING", "DELIVERED", "EXTENDED");

    /** Same for privacy.data_subject_requests. */
    private static final Set<String> REQUEST_TOKENS =
        Set.of("FULFILLED", "RESOLVED", "COMPLETED", "PENDING", "ESCALATED", "IN_PROGRESS");

    private static final Pattern COLLECTION_LITERAL =
        Pattern.compile("(?:List|Set)\\.of\\(([^()]*)\\)", Pattern.DOTALL);
    private static final Pattern EQUALS_CHAIN =
        Pattern.compile("\"([A-Z_]+)\"\\s*\\.equals\\(");

    @Test
    @DisplayName("no class outside the two lifecycle classes restates a DSR status vocabulary")
    void noOtherClassHandRollsADsrStatusVocabulary() {
        // Scoped deliberately: only files that touch the register's own entity, and only
        // collection literals or ||-chains holding TWO OR MORE of its tokens. A single token
        // is not a vocabulary — DsrCaseService legitimately tests one status at a time to
        // stamp timestamps, and PrivacyService counts ESCALATED alone as the at-risk bucket.
        // Scoping to the shape of the claim rather than to a setter name is the F2-191 lesson.
        Map<String, List<String>> offenders = new LinkedHashMap<>();
        int caseFiles = 0;
        int requestFiles = 0;

        for (Path f : javaSources()) {
            String name = f.getFileName().toString();
            String src = stripComments(read(f));
            boolean touchesCases    = src.contains("DsrCase");
            boolean touchesRequests = src.contains("DataSubjectRequest");
            if (touchesCases) caseFiles++;
            if (touchesRequests) requestFiles++;

            if (touchesCases && !name.equals("DsrCaseLifecycle.java")) {
                collect(offenders, f, src, CASE_TOKENS);
            }
            if (touchesRequests && !name.equals("DsrRequestLifecycle.java")) {
                collect(offenders, f, src, REQUEST_TOKENS);
            }
        }

        assertThat(offenders)
            .as("""
                A DSR status vocabulary is restated outside its lifecycle class. That is how \
                F2-196 happened: five artefacts each held their own answer to "is this case \
                still open?" and two were wrong, one of them the PDPL breach sweep. Call \
                DsrCaseLifecycle / DsrRequestLifecycle instead of listing statuses.""")
            .isEmpty();

        // Pin the narrowing by measurement, not by a named example: if the entity-name scoping
        // above silently stopped matching, these counts collapse and the emptiness above would
        // be vacuous. F2-192's surviving-mutation lesson.
        assertThat(caseFiles).as("main sources referencing DsrCase").isBetween(4, 12);
        assertThat(requestFiles).as("main sources referencing DataSubjectRequest").isBetween(4, 12);
    }

    /** Accessors that identify a statement as being about one of the two registers. */
    private static final Set<String> REGISTER_ACCESSORS =
        Set.of("dsrRepo", "dsrCases", "dsrRepository", "DsrCase", "DataSubjectRequest");

    private static void collect(Map<String, List<String>> offenders, Path f, String src, Set<String> vocab) {
        List<String> hits = new ArrayList<>();

        Matcher lit = COLLECTION_LITERAL.matcher(src);
        while (lit.find()) {
            List<String> found = vocab.stream().filter(t -> lit.group(1).contains('"' + t + '"')).sorted().toList();
            if (found.size() >= 2) hits.add("collection literal " + found);
        }
        for (String stmt : src.split(";")) {
            // A ||-chain of .equals — the exact shape the three PrivacyService guards used.
            Matcher eq = EQUALS_CHAIN.matcher(stmt);
            Set<String> chained = new TreeSet<>();
            while (eq.find()) {
                if (vocab.contains(eq.group(1))) chained.add(eq.group(1));
            }
            if (chained.size() >= 2) hits.add("equals-chain " + chained);

            // A SUM OF PER-STATUS COUNT CALLS. This third shape was missing on the first
            // draft of this test, and a mutation restoring `stats`' four-way
            // countByOrganisationIdAndStatus(...) sum SURVIVED — because it is neither a
            // collection literal nor an equals-chain. The two shapes I had covered were
            // exactly the two I had just fixed: F2-192's finding ("a gate verifies the claim
            // shape it was written for") reappearing inside the gate written to answer it.
            // Scoped by a register accessor in the same statement so that the neighbouring
            // breach-status sums in PrivacyService are not swept up.
            if (REGISTER_ACCESSORS.stream().anyMatch(stmt::contains)) {
                Set<String> literals = new TreeSet<>();
                Matcher tok = Pattern.compile("\"([A-Z_]+)\"").matcher(stmt);
                while (tok.find()) {
                    if (vocab.contains(tok.group(1))) literals.add(tok.group(1));
                }
                if (literals.size() >= 2) hits.add("per-status statement " + literals);
            }
        }
        if (!hits.isEmpty()) {
            offenders.put(f.getFileName().toString(), hits);
        }
    }

    // ── (e) the frontend mirror ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("the frontend's terminal set mirrors the backend's, in both directions")
    void theFrontendMirrorsTheRequestTerminalSet() {
        String fe = stripComments(read(FE_DSR_LIFECYCLE));
        int at = fe.indexOf("DSR_REQUEST_TERMINAL");
        assertThat(at).as("dsrLifecycle.ts no longer declares DSR_REQUEST_TERMINAL").isGreaterThan(0);

        // Anchor on the assignment. A bare `\[([^\]]*)\]` matches the TYPE ANNOTATION
        // `readonly string[]` first and yields an empty group — which reads exactly like
        // "the frontend declares no terminal statuses". Sixth time in this programme that a
        // pattern was satisfied by something other than the thing it claimed to match.
        Matcher arr = Pattern.compile("=\\s*\\[([^\\]]*)\\]", Pattern.DOTALL).matcher(fe.substring(at));
        assertThat(arr.find()).as("DSR_REQUEST_TERMINAL is not assigned an array literal").isTrue();

        Set<String> feTokens = new TreeSet<>();
        Matcher tok = Pattern.compile("'([A-Z_]+)'").matcher(arr.group(1));
        while (tok.find()) feTokens.add(tok.group(1));

        assertThat(feTokens)
            .as("""
                The frontend and the server must agree on which DSR statuses are finished. \
                They did not: the page tested for FULFILLED, a token this register holds in \
                0 of 20 rows, so 7 completed requests each rendered a red statutory-breach \
                countdown. Enforced in both directions — a token added on either side must \
                be added on the other in the same commit.""")
            .containsExactlyInAnyOrderElementsOf(new TreeSet<>(DsrRequestLifecycle.TERMINAL));
    }

    @Test
    @DisplayName("PrivacyPage asks the shared predicate rather than comparing statuses inline")
    void thePrivacyPageCallsTheSharedPredicate() {
        // Comments stripped FIRST — otherwise the "was `status === 'FULFILLED'`" note that
        // records what this fix replaced reads as a fresh inline comparison, and the negative
        // assertion below fires on the correction itself. That happened on this test's first
        // run; F2-193 recorded the same trap and the same remedy on the Java side, three
        // methods further down this very file.
        String page = stripComments(read(FE_PRIVACY_PAGE));

        // Assert the CALL SHAPE, not the imported name: an import satisfies a substring check
        // while the call site still compares inline. F2-194 recorded that mutation surviving.
        assertThat(page).as("the SLA column and row actions must call isDsrTerminal(...)")
            .containsPattern("isDsrTerminal\\s*\\(");
        assertThat(page).as("the PDPL Article 23 open count must call isDsrOpen(...)")
            .containsPattern("isDsrOpen\\s*\\(");

        for (String token : REQUEST_TOKENS) {
            assertThat(page)
                .as("PrivacyPage compares status to '%s' inline again — route it through "
                    + "dsrLifecycle.ts so the FE and the server cannot diverge", token)
                .doesNotContainPattern("status\\s*===\\s*'" + token + "'");
        }
    }

    // ── plumbing ─────────────────────────────────────────────────────────────────────────

    private static List<Path> javaSources() {
        try (Stream<Path> s = Files.walk(MAIN_JAVA)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + p.toAbsolutePath(), e);
        }
    }

    /**
     * Strip block and line comments. Without this the javadoc on {@code DsrCaseRepository} —
     * which now quotes the wrong list precisely so the mistake stays legible — would read as a
     * fresh restatement. F2-193: a detector that cannot tell a quotation from a claim forces
     * every correction to paraphrase away its own evidence.
     */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\n]*", " ");
    }
}
