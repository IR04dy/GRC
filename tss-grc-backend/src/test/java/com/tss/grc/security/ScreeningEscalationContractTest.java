package com.tss.grc.security;

import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.module.ethics.service.KycService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>F2-288 — the escalation that makes a screening match matter must live on ONE path that both
 * write routes reach, and the vocabularies it dispatches on must be closed.</b>
 *
 * <h3>The defect</h3>
 * {@code KycService} reacted to a confirmed sanctions/PEP match by flagging its subject —
 * {@code isSanctioned} + {@code riskRating = PROHIBITED} for the four sanctions lists,
 * {@code isPep} + {@code eddRequired} + a rating raise for {@code PEP_GLOBAL}. That reaction lived
 * inline in {@code recordHit} <b>only</b>. {@code disposition()} — the route a reviewer uses to
 * <em>adjudicate</em> a hit — wrote the decision and applied none of the consequences, so
 * confirming a UN or OFAC match left the subject unflagged and confirming a PEP match left
 * {@code eddRequired} false, which is the flag FATF R.12 and SAMA's AML/CFT rules turn on.
 *
 * <p>🔑 <b>The comment in {@code recordHit} is why nobody asked.</b> It documents the reaction
 * mechanism at length, on the one path that had it, and warns against refusing the caller's
 * disposition because <em>"the service reacts to it below"</em>. All true, and all about
 * {@code recordHit}. F2-215's two-of-N: a reader who finds a mechanism explained does not then ask
 * which sibling route is missing it. That comment also listed three of the vocabulary's four tokens,
 * which is what made it a fourth uncounted copy.
 *
 * <h3>Why the vocabulary half is in the same gate and not a separate one</h3>
 * {@code ethics.screening_hits} had <b>zero</b> CHECK constraints, so {@code disposition} was
 * narrowed by nothing at all and the collation is case-INSENSITIVE — a caller sending
 * {@code true_match} had it stored verbatim and the {@code "TRUE_MATCH".equals(...)} comparison
 * silently skipped the escalation (F2-179). Unifying the escalation without closing the vocabulary
 * would leave one shared predicate that can still be defeated by casing, so the two are one fix.
 *
 * <h3>⚠ The frontend is a party to this contract</h3>
 * The two hit vocabularies were written down <b>in full</b> in exactly one place — the dropdowns in
 * {@code EthicsPage.tsx} — so this test reads that file the way {@code ConnectorBackingContractTest}
 * does, and compares token for token <b>in both directions</b>. A dropdown offering a token the
 * column now refuses is F2-180's defect (19 of 22 incident categories a screen offered were refused
 * by its column's CHECK); a column admitting a token no operator can pick is the mirror, and reads
 * as coverage.
 */
class ScreeningEscalationContractTest {

    private static final Path SERVICE = Path.of(
        "src/main/java/com/tss/grc/module/ethics/service/KycService.java");
    private static final Path PAGE = Path.of(
        "../tss-grc-ui-v2/src/pages/ethics/EthicsPage.tsx");

    /**
     * One left-to-right alternation, never two sequential passes: at a {@code //} an inner
     * {@code /*} can never open a block, and at a block a URL cannot truncate it (F2-198). Newlines
     * are preserved so an offset still refers to a line (F2-269).
     */
    private static final Pattern COMMENT =
        Pattern.compile("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/");

    private static String stripComments(String src) {
        Matcher m = COMMENT.matcher(src);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                m.group().replaceAll("[^\\n]", " ")));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String read(Path p) throws IOException {
        assertThat(Files.exists(p)).as("%s must exist for this gate to mean anything", p).isTrue();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** Brace-balanced body of a method DECLARATION, never a call site (F2-244). */
    private static String bodyOf(String src, String method) {
        Matcher m = Pattern.compile(
            "(?m)^[ \\t]*(?:public|protected|private)[^\\n=;()]*\\b"
            + Pattern.quote(method) + "\\s*\\(").matcher(src);
        assertThat(m.find()).as("no declaration of %s in KycService — this gate is keyed on "
                + "(class, method) pairs precisely so a rename fails loudly", method).isTrue();
        int open = src.indexOf('{', m.end());
        assertThat(open).as("%s has no body", method).isGreaterThan(-1);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            if (src.charAt(i) == '{') depth++;
            else if (src.charAt(i) == '}' && --depth == 0) return src.substring(open, i + 1);
        }
        throw new AssertionError("unbalanced braces walking " + method);
    }

    private static int countOf(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }


    /**
     * The vocabularies are package-private on purpose — nothing outside {@code module/ethics} has
     * business consulting them — so this gate reads them reflectively rather than asking production
     * code to widen its API for a test's benefit. Reflection is also this tree's rule for measuring
     * a security surface (F2-187): it cannot be fooled by a rename or a relocation the way a source
     * regex can, and it throws rather than skipping, so a field that disappears fails loudly.
     */
    private static Object constant(String name) {
        try {
            java.lang.reflect.Field f = KycService.class.getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(null);
            assertThat(v).as("KycService.%s must exist and be non-null", name).isNotNull();
            return v;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                "KycService." + name + " is gone. This gate is keyed on it because it is what the "
                + "escalation dispatches on; renaming it must fail here rather than silently "
                + "shrinking what is checked.", e);
        }
    }

    private static ClosedVocabulary vocab(String name) {
        return (ClosedVocabulary) constant(name);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> stringSet(String name) {
        return (Set<String>) constant(name);
    }

    private static String string(String name) {
        return (String) constant(name);
    }

    // ─── the escalation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("the confirmed-match consequences have exactly one writer")
    void theEscalationHasExactlyOneWriter() throws IOException {
        String src = stripComments(read(SERVICE));
        String escalation = bodyOf(src, "applyConfirmedMatch");

        // ⚠ A bare count of these setters FAILS ON CORRECT CODE, and the first draft of this leg
        // did. `apply()` legitimately copies all four from the request — the subject form lets an
        // operator flag a PEP or a sanctioned party by hand, which is a different question from
        // reacting to a hit — and `completeEdd` legitimately clears `eddRequired`. A gate that
        // fails on working code trains the next reader to edit the assertion (F2-214), so this is
        // scoped to the SHAPE of the claim: the two hit-writing routes must not write a
        // consequence themselves, they must go through the one shared method (F2-192).
        String recordHit = bodyOf(src, "recordHit");
        String disposition = bodyOf(src, "disposition");
        for (String setter : List.of("setIsSanctioned(", "setIsPep(", "setEddRequired(",
                                     "setRiskRating(")) {
            assertThat(escalation)
                .as("`%s` is a consequence of a CONFIRMED match, so applyConfirmedMatch must be "
                  + "the place it happens", setter)
                .contains(setter);
            assertThat(recordHit)
                .as("`%s` must not be written inline in recordHit. Two copies is exactly how this "
                  + "defect formed: recordHit had the escalation, `disposition` did not, and the "
                  + "two agreed on everything a reader checks and differed on the only thing that "
                  + "matters (F2-190/F2-215).", setter)
                .doesNotContain(setter);
            assertThat(disposition)
                .as("`%s` must not be written inline in disposition() either — the fix is one "
                  + "shared predicate, not a second copy on the route that was missing it", setter)
                .doesNotContain(setter);
        }
        // The two escalation literals are escalation-only, so these counts CAN be pinned at one and
        // that is what stops the shared method being copied back out.
        assertThat(escalation).contains("setRiskRating(\"PROHIBITED\")");
        assertThat(escalation).contains("setRiskRating(\"HIGH\")");
        assertThat(countOf(src, "setRiskRating(\"PROHIBITED\")")).isEqualTo(1);
        assertThat(countOf(src, "setIsSanctioned(true)")).isEqualTo(1);
        assertThat(countOf(src, "setIsPep(true)")).isEqualTo(1);
    }

    @Test
    @DisplayName("both write routes reach the escalation")
    void bothWriteRoutesReachTheEscalation() throws IOException {
        String src = stripComments(read(SERVICE));
        for (String route : List.of("recordHit", "disposition")) {
            assertThat(bodyOf(src, route))
                .as("`%s` writes this register's disposition, so it must apply the consequences of "
                  + "a confirmed match. Before F2-288 only recordHit did, and `disposition` is the "
                  + "REVIEWER's route — the one where a confirmed sanctions match is most likely to "
                  + "be recorded.", route)
                .containsPattern("applyConfirmedMatch\\s*\\(");
        }
    }

    @Test
    @DisplayName("nothing compares a disposition against a bare literal any more")
    void theConfirmedComparisonIsVocabularyAware() throws IOException {
        String src = stripComments(read(SERVICE));
        // Scoped to the SHAPE of the claim, not to the token: TRUE_MATCH legitimately appears in
        // the vocabulary declaration and in the CONFIRMED constant (F2-192, F2-205).
        assertThat(src)
            .as("a case-SENSITIVE String.equals against a case-INSENSITIVE column is F2-179's "
              + "landmine, and here it meant `true_match` was stored verbatim and the sanctions "
              + "escalation never fired. Compare through ClosedVocabulary#is, which normalises.")
            .doesNotContainPattern("\"(?:TRUE_MATCH|FALSE_POSITIVE|NEEDS_REVIEW|POTENTIAL_MATCH)\"\\s*\\.\\s*equals")
            .doesNotContainPattern("\"(?:LOW|STANDARD|HIGH|PROHIBITED)\"\\s*\\.\\s*equals\\s*\\(\\s*\\w+\\.getRiskRating");
    }

    @Test
    @DisplayName("every column the escalation dispatches on is closed by a vocabulary")
    void theDispatchColumnsAreClosed() throws IOException {
        String src = stripComments(read(SERVICE));
        assertThat(bodyOf(src, "recordHit"))
            .as("both closed columns must be normalised on the create path, BEFORE the escalation "
              + "reads them — a raw value would slip past ClosedVocabulary#is exactly as it "
              + "slipped past the old equals()")
            .containsPattern("HIT_DISPOSITION\\s*\\.\\s*orDefault\\s*\\(")
            .containsPattern("HIT_LIST_SOURCE\\s*\\.\\s*require\\s*\\(");
        assertThat(bodyOf(src, "disposition"))
            .containsPattern("HIT_DISPOSITION\\s*\\.\\s*require\\s*\\(");

        int normalise = src.indexOf("HIT_LIST_SOURCE.require(");
        int escalate = src.indexOf("applyConfirmedMatch(", src.indexOf("recordHit"));
        assertThat(normalise).isGreaterThan(-1);
        assertThat(escalate).as("the escalation must run AFTER normalisation on the create path")
            .isGreaterThan(normalise);
    }

    // ─── the cross-codebase half ────────────────────────────────────────────

    private static Set<String> tsTokens(String page, String constant) {
        Matcher decl = Pattern.compile(
            Pattern.quote(constant) + "[^=]*=\\s*\\[([\\s\\S]*?)\\n\\];").matcher(page);
        assertThat(decl.find())
            .as("`%s` must still be declared in EthicsPage.tsx as an array literal — this gate is "
              + "the only thing comparing the operator's dropdown with the column's CHECK, and a "
              + "declaration it cannot parse reports an EMPTY vocabulary, which reads exactly like "
              + "agreement (F2-209's inventory lesson)", constant)
            .isTrue();
        Set<String> out = new LinkedHashSet<>();
        Matcher v = Pattern.compile("value:\\s*'([A-Z_]+)'").matcher(decl.group(1));
        while (v.find()) out.add(v.group(1));
        assertThat(out).as("%s parsed to no tokens", constant).isNotEmpty();
        return out;
    }

    @Test
    @DisplayName("the operator's dropdowns and the closed vocabularies agree, both ways")
    void theFrontendDropdownsAgreeWithTheVocabularies() throws IOException {
        String page = read(PAGE);
        List<String> problems = new ArrayList<>();

        record Pair(String constant, ClosedVocabulary vocab) { }
        for (Pair p : List.of(
                new Pair("HIT_DISPOSITIONS", vocab("HIT_DISPOSITION")),
                new Pair("HIT_LIST_SOURCES", vocab("HIT_LIST_SOURCE")))) {
            Set<String> ts = tsTokens(page, p.constant());
            for (String t : ts) {
                if (!p.vocab().tokens().contains(t)) {
                    problems.add(p.constant() + " offers '" + t + "', which "
                        + p.vocab().constraint() + " now REFUSES — the operator would pick it and "
                        + "the save would fail as a constraint violation (F2-180)");
                }
            }
            for (String t : p.vocab().tokens()) {
                if (!ts.contains(t)) {
                    problems.add(p.vocab().constraint() + " admits '" + t + "' but "
                        + p.constant() + " does not offer it — a token no operator can pick, which "
                        + "reads as coverage");
                }
            }
        }
        assertThat(problems).as("the dropdown IS the vocabulary for this register: it is the only "
            + "place either was ever written down in full").isEmpty();
    }

    @Test
    @DisplayName("the risk-rating options and tone map cover every member")
    void theRiskRatingSurfacesCoverEveryMember() throws IOException {
        String page = read(PAGE);
        Matcher opts = Pattern.compile("RISK_RATING_OPTIONS[^=]*=\\s*\\[([\\s\\S]*?)\\n\\];")
            .matcher(page);
        assertThat(opts.find()).as("RISK_RATING_OPTIONS must still be a parseable array").isTrue();
        Set<String> offered = new LinkedHashSet<>();
        Matcher v = Pattern.compile("value:\\s*'([A-Z_]+)'").matcher(opts.group(1));
        while (v.find()) offered.add(v.group(1));

        assertThat(offered)
            .as("the form's ratings and CK_kyc_subjects_risk_rating must be the same set: the PEP "
              + "branch of applyConfirmedMatch READS this column to decide whether to raise the "
              + "rating, so a value the form can produce and the vocabulary does not know is a "
              + "confirmed PEP match that silently leaves the rating alone")
            .containsExactlyInAnyOrderElementsOf(vocab("RISK_RATING").tokens());

        Matcher tone = Pattern.compile("RISK_RATING_TONE[^=]*=\\s*\\{([\\s\\S]*?)\\n\\};")
            .matcher(page);
        assertThat(tone.find()).isTrue();
        for (String t : vocab("RISK_RATING").tokens()) {
            assertThat(tone.group(1))
                .as("'%s' has no tone, so the badge falls to `neutral` and a PROHIBITED subject "
                  + "could render as unremarkable (F2-197)", t)
                .containsPattern("\\b" + t + "\\s*:");
        }
    }

    @Test
    @DisplayName("the disposition badge names only real members")
    void theDispositionBadgeNamesOnlyRealMembers() throws IOException {
        String page = read(PAGE);
        Matcher badge = Pattern.compile("h\\.disposition\\s*===\\s*'([A-Z_]+)'").matcher(page);
        int seen = 0;
        while (badge.find()) {
            seen++;
            assertThat(vocab("HIT_DISPOSITION").tokens())
                .as("the hit badge compares against '%s', which is not a member of %s — a token "
                  + "the column can never hold, so that branch is dead and whatever it was meant "
                  + "to colour renders as the fallback instead (F2-197)",
                    badge.group(1), vocab("HIT_DISPOSITION").constraint())
                .contains(badge.group(1));
        }
        assertThat(seen).as("anti-vacuity: the badge's tone expression must still be findable, or "
            + "this leg passes over a page it never read").isGreaterThanOrEqualTo(2);
    }

    // ─── the instrument ─────────────────────────────────────────────────────

    @Test
    @DisplayName("the comment stripper is sound in both directions")
    void theStripperIsSound() {
        String hostile = "// see /* not a block\nint a = 1;\n/* real\nblock */ int b = 2;\n"
                       + "String u = \"https://x/y\"; // tail\n";
        String stripped = stripComments(hostile);
        assertThat(stripped)
            .as("a `/*` inside a line comment must not open a block — written as two sequential "
              + "passes this deletes everything to the next closing delimiter, and a corrupted "
              + "source yields a SMALLER population, which reads exactly like a clean tree (F2-198)")
            .contains("int a = 1;")
            .contains("int b = 2;")
            .contains("https://x/y")
            .doesNotContain("not a block")
            .doesNotContain("real");
        assertThat(stripped.lines().count())
            .as("newlines must survive, or every reported offset refers to the wrong line (F2-269)")
            .isEqualTo(hostile.lines().count());

        // and the two-pass form -- the actual defect, never a reordered alternation, which is an
        // equivalent mutant because // and /* can never match at the same position (F2-211).
        String twoPass = hostile.replaceAll("/\\*[\\s\\S]*?\\*/", "").replaceAll("(?<!:)//[^\\n]*", "");
        assertThat(twoPass)
            .as("the two-pass form must demonstrably lose code, or this self-test proves nothing")
            .doesNotContain("int a = 1;");
    }

    @Test
    @DisplayName("the vocabularies are non-trivial and name real constraints")
    void theVocabulariesAreReal() {
        assertThat(vocab("HIT_DISPOSITION").tokens()).hasSize(4);
        assertThat(vocab("HIT_LIST_SOURCE").tokens()).hasSize(8);
        assertThat(vocab("RISK_RATING").tokens()).hasSize(4);
        assertThat(vocab("HIT_DISPOSITION").tokens()).contains(string("CONFIRMED"));
        assertThat(vocab("HIT_LIST_SOURCE").tokens())
            .as("every list the escalation dispatches on must be a member, or the branch is "
              + "unreachable and nothing says so")
            .containsAll(stringSet("SANCTIONS_LISTS"))
            .contains(string("PEP_LIST"));
        assertThat(stringSet("SANCTIONS_LISTS")).doesNotContain(string("PEP_LIST"));
    }
}
