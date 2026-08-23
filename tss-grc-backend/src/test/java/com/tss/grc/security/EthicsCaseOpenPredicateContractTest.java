package com.tss.grc.security;

import com.tss.grc.module.ethics.EthicsCaseLifecycle;
import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-290 — three artefacts answered "is this ethics case still open?" and no two of them agreed.
 *
 * <p><b>The defect.</b> {@code ethics.cases} is the misconduct / whistleblowing register (NAZAHA,
 * the Saudi Whistleblower Protection Law). Measured 2026-08-23, <b>identically in dev and the
 * deployed database</b>: 30 live cases, 4 terminal, so <b>26 open</b>.
 *
 * <ul>
 *   <li>{@code EthicsPage} (the client) computed the <em>complement</em> of
 *       {@code {CLOSED, DISMISSED}} and showed <b>26</b>. The only correct reader of the three.</li>
 *   <li>{@code EthicsCaseService.analytics()} summed a five-token <em>inclusion</em> list and
 *       answered <b>10</b>.</li>
 *   <li>{@code BoardPackAggregateService.ethics()} summed a <em>narrower</em> three-token list and
 *       answered <b>10</b> — and that one <b>emits a document</b>: {@code BoardPackService} prints
 *       {@code "Cases: 30 · Open 10 · …"} into the board-pack PDF and {@code BoardPackPptxService}
 *       into the PPTX, in both languages.</li>
 * </ul>
 *
 * <p>🔑 <b>The client was right and the server was wrong, twice.</b> F2-205's question — "how many
 * things read this status, and <em>which of them is the server?</em>" — with the answer inverted:
 * there the server was authoritative and five clients had drifted, here the two server readers were
 * the drifted ones and the figure that leaves the platform was the narrowest of the three. The two
 * server answers coincide today only because {@code REMEDIATION} and {@code ESCALATED} hold no
 * rows, so their disagreement is latent while the understatement is live.
 *
 * <p>🔑 <b>Why an inclusion list is the defect and not merely a style.</b> The column has <b>no
 * CHECK constraint</b> and holds {@code INVESTIGATING} (8 rows) and {@code TRIAGED} (3 rows),
 * near-synonyms of two declared states. An inclusion list silently drops every token nobody
 * remembered; the complement of a declared terminal set counts it as open, so it is <em>chased</em>
 * rather than excused — which for a misconduct caseload is the safe direction (F2-196).
 *
 * <p><b>What this gate holds.</b> That the terminal set is derived rather than typed; that the
 * derivation reads edge-emptiness rather than key-absence (see
 * {@link #theDerivationReadsEdgeEmptinessNotKeyAbsence} — the other reading returns an EMPTY
 * terminal set on this machine, which reads exactly like "every case is open"); that no reader
 * restates the vocabulary in any of the three shapes F2-196 measured; that both server readers ask
 * the repository; that the client's complement is not "aligned" downward by a later slice; and that
 * the rows outside the declared vocabularies are disclosed on the wire, on screen and in both
 * languages.
 *
 * <p><b>Deliberately not asserted:</b> {@code substantiated}. It is a two-token status sum and that
 * is a narrower question, not a restatement (F2-250) — a case in REMEDIATION is still a
 * substantiated one. {@code outcome} was checked as a durable alternative and refuted: it holds the
 * <em>remediation</em> result ({@code POLICY_UPDATE}), not a verdict, on 1 of 30 rows.
 */
class EthicsCaseOpenPredicateContractTest {

    private static final Path LIFECYCLE = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "ethics", "EthicsCaseLifecycle.java");
    private static final Path SERVICE = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "ethics", "service", "EthicsCaseService.java");
    private static final Path REPOSITORY = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "ethics", "repository", "EthicsCaseRepository.java");
    private static final Path BOARD_PACK = Path.of("src", "main", "java", "com", "tss", "grc",
        "module", "reporting", "service", "BoardPackAggregateService.java");
    private static final Path ETHICS_PAGE = Path.of("..", "tss-grc-ui-v2", "src", "pages",
        "ethics", "EthicsPage.tsx");
    private static final Path API_TS = Path.of("..", "tss-grc-ui-v2", "src", "lib", "api.ts");
    private static final Path EN = Path.of("..", "tss-grc-ui-v2", "src", "translations", "en.ts");
    private static final Path AR = Path.of("..", "tss-grc-ui-v2", "src", "translations", "ar.ts");

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stripped(Path p) {
        return GuardExpressions.stripComments(read(p));
    }

    /**
     * The brace-balanced body of the first member whose declaration matches {@code anchor}. Copied
     * from {@code AmlDispositionAttributionContractTest} (F2-289) rather than re-derived, including
     * the assertion that the anchor exists: a body() that silently returns nothing reads exactly
     * like a clean method (F2-244).
     */
    private static String body(String src, String anchor) {
        int at = src.indexOf(anchor);
        assertThat(at).as("anchor %s must exist — a body() that silently returns nothing reads "
            + "exactly like a clean method (F2-244)", anchor).isNotNegative();
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

    // ── 1. the declaration ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the terminal set is derived from the machine, never typed")
    void theTerminalSetIsDerivedNotTyped() {
        Set<String> expected = new TreeSet<>();
        for (String s : EthicsCaseLifecycle.STATES) {
            if (EthicsCaseLifecycle.ALLOWED.getOrDefault(s, Set.of()).isEmpty()) expected.add(s);
        }
        assertThat(EthicsCaseLifecycle.TERMINAL)
            .as("TERMINAL must equal the states of ALLOWED that have no outgoing edges")
            .isEqualTo(expected);
        assertThat(EthicsCaseLifecycle.TERMINAL_LIST).containsExactlyElementsOf(new TreeSet<>(expected));

        // Scoped to the field's own initialiser, never to the spelling of a token: F2-205 recorded
        // a ban written against the literal a slice had just deleted, which a mutation parking the
        // old value on an unused field then survived.
        String lifecycle = stripped(LIFECYCLE);
        Matcher m = Pattern.compile(
            "TERMINAL\\s*=\\s*([^;]*);", Pattern.DOTALL).matcher(lifecycle);
        assertThat(m.find()).as("EthicsCaseLifecycle must assign TERMINAL").isTrue();
        String init = m.group(1);
        assertThat(init)
            .as("TERMINAL's initialiser must be computed, not a literal collection of states")
            .doesNotContain("Set.of(\"")
            .doesNotContain("List.of(\"");
    }

    @Test
    @DisplayName("the derivation reads edge-emptiness, not key-absence — the other reading is empty here")
    void theDerivationReadsEdgeEmptinessNotKeyAbsence() {
        // The negative control for the design choice. DsrCaseLifecycle (F2-196) omits its terminal
        // states from ALLOWED entirely, so it can derive TERMINAL as "mentioned minus keys". This
        // machine gives its terminal states an explicit empty entry, so that same reading returns
        // NOTHING — and an empty terminal set makes every case open and `closed` always zero.
        Set<String> byKeyAbsence = new TreeSet<>(EthicsCaseLifecycle.STATES);
        byKeyAbsence.removeAll(EthicsCaseLifecycle.ALLOWED.keySet());
        assertThat(byKeyAbsence)
            .as("this machine declares every state as a key, so key-absence yields nothing — which "
                + "is precisely why the derivation must read the EDGE SET")
            .isEmpty();
        assertThat(EthicsCaseLifecycle.TERMINAL)
            .as("edge-emptiness must still find the real terminal states")
            .containsExactlyInAnyOrder("CLOSED", "DISMISSED");
    }

    @Test
    @DisplayName("the machine is declared exactly once in the tree")
    void theMachineIsDeclaredOnce() {
        // The map moved out of EthicsCaseService; a second copy is how the three readers drifted.
        assertThat(stripped(SERVICE))
            .as("EthicsCaseService must no longer declare its own machine")
            .doesNotContain("Map<String, Set<String>> TRANSITIONS");
        assertThat(stripped(LIFECYCLE))
            .as("EthicsCaseLifecycle is the one declaration")
            .contains("Map<String, Set<String>> ALLOWED");
    }

    // ── 2. no reader restates the vocabulary ─────────────────────────────────────────────

    @Test
    @DisplayName("open and closed are asked of the repository, in every reader")
    void everyReaderAsksTheRepository() {
        String analytics = body(stripped(SERVICE), "public Map<String, Object> analytics()");
        assertThat(analytics)
            .as("analytics() must ask the repository for open and closed")
            .containsPattern("countOpen\\s*\\(")
            .containsPattern("countClosed\\s*\\(");

        String ethics = body(stripped(BOARD_PACK), "BoardPackAggregate.Ethics ethics(Long org)");
        assertThat(ethics)
            .as("the board-pack aggregate must ask the repository, not sum states of its own")
            .containsPattern("countOpen\\s*\\(");
    }

    @Test
    @DisplayName("no reader restates the open vocabulary — collection literal, or-chain, or per-state sum")
    void noReaderRestatesTheOpenVocabulary() {
        // F2-196 measured THREE ways to restate a vocabulary and its own gate caught only two: a
        // collection literal, a ||-chain, and a per-state arithmetic sum. All three are banned, and
        // the ban is scoped to the OPEN states so `substantiated`'s deliberate two-token sum over
        // SUBSTANTIATED + REMEDIATION stays legal (F2-250).
        List<String> openStates = new ArrayList<>(EthicsCaseLifecycle.STATES);
        openStates.removeAll(EthicsCaseLifecycle.TERMINAL);
        openStates.remove("SUBSTANTIATED");
        openStates.remove("REMEDIATION");
        assertThat(openStates).as("the ban needs states to be about").isNotEmpty();

        for (Path p : List.of(SERVICE, BOARD_PACK)) {
            String src = stripped(p);
            String scope = p.equals(SERVICE)
                ? body(src, "public Map<String, Object> analytics()")
                : body(src, "BoardPackAggregate.Ethics ethics(Long org)");
            for (String s : openStates) {
                assertThat(scope)
                    .as("%s must not name the open state %s — that is an inclusion list restating "
                        + "a vocabulary the machine already declares", p.getFileName(), s)
                    .doesNotContain("\"" + s + "\"");
            }
            assertThat(scope)
                .as("%s must not read a per-state bucket back out to build a figure", p.getFileName())
                .doesNotContain("getOrDefault(\"");
        }

        // And nobody may type the terminal set either — that is the same restatement, inverted.
        for (Path p : List.of(SERVICE, BOARD_PACK, REPOSITORY)) {
            String src = stripped(p);
            for (String shape : List.of("Set.of(\"CLOSED\"", "List.of(\"CLOSED\"",
                                        "Set.of(\"DISMISSED\"", "List.of(\"DISMISSED\"")) {
                assertThat(src).as("%s must not type the terminal set — it is derived",
                    p.getFileName()).doesNotContain(shape);
            }
        }
    }

    @Test
    @DisplayName("the repository's open predicate is the complement, not a second inclusion list")
    void theRepositoryPredicateIsTheComplement() {
        String repo = stripped(REPOSITORY);
        assertThat(body(repo, "default long countOpen(Long organisationId)"))
            .as("countOpen must be a NotIn over the derived terminal set")
            .contains("StatusNotIn")
            .contains("TERMINAL_LIST");
        assertThat(body(repo, "default long countClosed(Long organisationId)"))
            .as("countClosed must be the In over the same derived set")
            .contains("StatusIn")
            .contains("TERMINAL_LIST");
        assertThat(body(repo, "default long countWithUndeclaredStatus(Long organisationId)"))
            .as("the disclosed count must be a NotIn over every declared state")
            .contains("StatusNotIn")
            .contains("STATES");
    }

    // ── 3. the client's correct reader must not be "aligned" downward ────────────────────

    @Test
    @DisplayName("the client register tile stays the complement of the terminal set")
    void theClientTileStaysAComplement() {
        // This is a second copy of the terminal set, deliberately: the client cannot import the
        // machine, and it was the ONE artefact that had the predicate right. It is pinned so that a
        // later slice reconciling the three figures cannot "align" it down to an inclusion list —
        // which is the direction it would be aligned in, since the two server readers were lower.
        String page = read(ETHICS_PAGE);
        assertThat(page)
            .as("EthicsPage's open tile must remain a NEGATED membership test over the terminal set")
            .containsPattern("!\\s*\\[\\s*'CLOSED'\\s*,\\s*'DISMISSED'\\s*\\]\\s*\\.includes");
    }

    @Test
    @DisplayName("the frontend category dropdown mirrors the server taxonomy exactly, both ways")
    void theFrontendCategoryListMirrorsTheServerTaxonomy() {
        String page = read(ETHICS_PAGE);
        Matcher m = Pattern.compile("const CATEGORY_OPTIONS\\s*=\\s*\\[(.*?)\\]", Pattern.DOTALL)
            .matcher(page);
        assertThat(m.find()).as("EthicsPage must declare CATEGORY_OPTIONS").isTrue();
        List<String> fe = new ArrayList<>();
        Matcher tok = Pattern.compile("'([A-Z_]+)'").matcher(m.group(1));
        while (tok.find()) fe.add(tok.group(1));

        assertThat(fe)
            .as("the dropdown and the reporting taxonomy are two copies of one vocabulary, and two "
                + "copies agreeing is not evidence when nobody maintains the agreement (F2-177)")
            .containsExactlyInAnyOrderElementsOf(EthicsCaseLifecycle.CATEGORY_TAXONOMY);
        assertThat(fe).hasSize(EthicsCaseLifecycle.CATEGORY_TAXONOMY.size());
    }

    // ── 4. the rows no bucket can show are disclosed ─────────────────────────────────────

    @Test
    @DisplayName("the undeclared counts are computed, on the wire, and rendered in both languages")
    void theUndeclaredCountsAreDisclosed() {
        String analytics = body(stripped(SERVICE), "public Map<String, Object> analytics()");
        assertThat(analytics)
            .as("analytics() must publish both disclosed counts")
            .contains("\"undeclaredStatus\"")
            .contains("\"undeclaredCategory\"")
            .containsPattern("countWithUndeclaredStatus\\s*\\(")
            .containsPattern("countWithUndeclaredCategory\\s*\\(");

        assertThat(read(API_TS))
            .as("the wire type must declare both, or the client cannot render them")
            .containsPattern("undeclaredStatus\\s*:\\s*number")
            .containsPattern("undeclaredCategory\\s*:\\s*number");

        String page = read(ETHICS_PAGE);
        assertThat(page)
            .as("both counts must reach the panel — assert the call shape, not the token, so an "
                + "import or a comment cannot satisfy it (F2-194)")
            .containsPattern("<Undeclared\\s+n=\\{analytics\\.undeclaredStatus\\}")
            .containsPattern("<Undeclared\\s+n=\\{analytics\\.undeclaredCategory\\}");
        assertThat(page)
            .as("a real zero is a measured zero, so the guard is typeof, never !n (F2-197)")
            .contains("typeof n !== 'number'");
        assertThat(page)
            .as("the disclosure must be announced and carry its hint on title AND aria-label")
            .containsPattern("role=\"status\"[\\s\\S]{0,200}ethicsAnalyticsUndeclaredHint");

        for (Path dict : List.of(EN, AR)) {
            String d = read(dict);
            for (String key : List.of("ethicsAnalyticsUndeclared:", "ethicsAnalyticsUndeclaredHint:")) {
                assertThat(d).as("%s must declare %s — Arabic is the primary language, so a "
                    + "disclosure present in one dictionary only is half a disclosure (house rule 7)",
                    dict.getFileName(), key).contains(key);
            }
        }
    }

    // ── 5. the instrument itself ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("the comment stripper is sound in both directions")
    void theStripperIsSound() {
        // Every scan above reads stripped source, because this codebase documents its retirements
        // by quoting what it removed — the comments added by this very slice name the inclusion
        // lists they replaced, so a raw read would count the epitaph as the corpse (F2-193).
        String probe = "int a = 1; // ban \"INTAKE\" here\n/* and \"TRIAGE\" here */ int b = 2;";
        String out = GuardExpressions.stripComments(probe);
        assertThat(out).doesNotContain("INTAKE").doesNotContain("TRIAGE");
        assertThat(out).contains("int a = 1;").contains("int b = 2;");

        // And it must be load-bearing rather than decorative: the real sources DO carry the banned
        // tokens inside comments, so the legs above would fail on a correct tree without it.
        assertThat(read(SERVICE))
            .as("the fix's own comment quotes the inclusion list it replaced")
            .contains("INTAKE+TRIAGE+INVESTIGATION");
        assertThat(stripped(SERVICE))
            .as("...and stripping must remove it")
            .doesNotContain("INTAKE+TRIAGE+INVESTIGATION");
    }

    @Test
    @DisplayName("the declarations this gate reasons about are the real ones")
    void antiVacuity() {
        assertThat(EthicsCaseLifecycle.ALLOWED).as("the machine must not be empty").hasSize(10);
        assertThat(EthicsCaseLifecycle.STATES)
            .as("every target must also be a declared key on this machine")
            .containsExactlyElementsOf(new TreeSet<>(EthicsCaseLifecycle.ALLOWED.keySet()));
        assertThat(EthicsCaseLifecycle.CATEGORY_TAXONOMY).hasSize(13);
        assertThat(EthicsCaseLifecycle.isOpen("INVESTIGATING"))
            .as("an unrecognised status must count as OPEN — that asymmetry is the whole point")
            .isTrue();
        assertThat(EthicsCaseLifecycle.isOpen("closed"))
            .as("the column is case-insensitive and has no CHECK, so comparison must normalise")
            .isFalse();
    }
}
