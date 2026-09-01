package com.tss.grc.module.policy;

import com.tss.grc.module.policy.entity.PolicyDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-419 — <b>the intake adapter picked the newest policy version lexically, in a module that had
 * already been swept for exactly that.</b>
 *
 * <p>A policy version is a caller-supplied dotted string, so a lexical order is wrong in the
 * ordinary case: {@code "1.9"} beats {@code "1.10"} and {@code "9.0"} beats {@code "10.0"} as text.
 * PM-H2 found that and converted the three latest-version picks in {@code PolicyManagementService}
 * to a numeric comparison — its comments say so — and F2-401 then hardened that comparison into a
 * proper total order after measuring that the old per-pair mix was intransitive.
 *
 * <p>Neither reached {@code PolicyExceptionPromotionAdapter}, which resolved the policy an intake
 * submission's exception hangs off with {@code Comparator.comparing(p -> nullSafe(p.getVersion()))}
 * — a lexical sort key, behind a helper named for null-safety that also lower-cased. So the
 * exception was filed against a <b>superseded</b> version of the policy it excepts, on a path whose
 * own error message says <em>"An exception must hang off the policy it excepts."</em>
 *
 * <p>🔑 Four sites in this module pick a latest policy version; three used the numeric comparator
 * and one did not — F2-205/F2-282's lone-outlier tell, and F2-215's two-of-N with the mechanism
 * visibly present three times over. The other five consumers of
 * {@code …OrderByPolicyCodeAscVersionDesc} were read and do <b>not</b> depend on the order (they
 * iterate and count, or re-sort), so the finder's lexical {@code VersionDesc} — which no derived
 * query could order numerically — is not itself the defect.
 *
 * <p>Hermetic: source reads plus direct calls on a pure comparator. No database, no Spring context.
 */
@DisplayName("F2-419 — one answer to which policy version is newer")
class PolicyVersionOrderContractTest {

    private static final Path MAIN = Path.of("src/main/java/com/tss/grc/module/policy");
    private static final Path SERVICE = MAIN.resolve("service/PolicyManagementService.java");
    private static final Path ADAPTER = MAIN.resolve("intake/PolicyExceptionPromotionAdapter.java");

    /**
     * A lexical sort key over a version getter, which is the defect's exact shape. Matches
     * {@code Comparator.comparing(x -> …getVersion())} and a bare
     * {@code a.getVersion().compareTo(b.getVersion())}, and is deliberately blind to
     * {@code PolicyVersions.compare} / {@code PolicyVersions.LATEST}.
     */
    private static final Pattern LEXICAL_VERSION_ORDER = Pattern.compile(
        "Comparator\\s*\\.\\s*comparing\\s*\\([^)]{0,120}getVersion\\s*\\(\\s*\\)"
        + "|getVersion\\s*\\(\\s*\\)\\s*\\.\\s*compareTo\\s*\\(");

    // ── leg 1: the invariant ─────────────────────────────────────────────────────────

    /**
     * A hard zero with <b>no exemption map</b>, and that is safe rather than strict: {@link
     * PolicyVersions#LATEST} is always available and always correct, so there is no correct code
     * for this to fail on (F2-214) and therefore nothing to exempt.
     */
    @Test
    @DisplayName("no site in module/policy orders policy versions lexically")
    void noSiteOrdersPolicyVersionsLexically() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (Path p : javaUnder(MAIN)) {
            scanned++;
            String code = stripComments(Files.readString(p));
            if (LEXICAL_VERSION_ORDER.matcher(code).find()) {
                offenders.add(p.getFileName().toString());
            }
        }
        assertThat(offenders)
            .as("a lexical order over a dotted version string puts \"1.9\" above \"1.10\" and "
              + "\"9.0\" above \"10.0\". Use PolicyVersions.LATEST (or PolicyVersions.compare) — "
              + "it is the module's one answer and it is a total order")
            .isEmpty();

        // The floor comes AFTER the claim, deliberately. F2-417 put one first and the control then
        // short-circuited on the floor, so its output read as "the detector changed" rather than
        // as "here are the offenders" — it sent a reader to the instrument instead of the code.
        // And the number is MEASURED, not guessed: module/policy holds 16 java files today.
        assertThat(scanned)
            .as("anti-vacuity: the walk must reach the module. A rotted path resolves to nothing, "
              + "and an empty population is indistinguishable from a clean one (F2-235)")
            .isGreaterThan(8);
    }

    /** The detector, over synthetic input, both ways — a scan that cannot fire reads like a clean tree. */
    @Test
    @DisplayName("the lexical-order detector fires on the pre-fix shape and not on the primitive")
    void theLexicalOrderDetectorFiresOnThePreFixShapeAndNotOnTheFixedOne() {
        assertThat(LEXICAL_VERSION_ORDER.matcher(
                ".max(Comparator.comparing(p -> nullSafe(p.getVersion())))").find())
            .as("the literal shape this slice removed")
            .isTrue();
        assertThat(LEXICAL_VERSION_ORDER.matcher(
                "if (a.getVersion().compareTo(b.getVersion()) > 0) return a;").find())
            .as("and the other way somebody would hand-roll it")
            .isTrue();
        assertThat(LEXICAL_VERSION_ORDER.matcher(".max(PolicyVersions.LATEST)").find())
            .as("the fixed shape must NOT match, or the gate fails on correct code")
            .isFalse();
        assertThat(LEXICAL_VERSION_ORDER.matcher(
                "return PolicyVersions.compare(a.getVersion(), b.getVersion());").find())
            .as("nor may routing through the primitive read as a lexical order")
            .isFalse();
    }

    // ── leg 2: one copy of the algorithm ────────────────────────────────────────────

    /**
     * {@code compareVersions} keeps its signature — F2-401's transitivity and numeric-ordering
     * tests call it directly and pass unchanged, which is the corroboration that the move was
     * faithful — but it must <b>delegate</b>. A second copy of the algorithm is the thing that
     * drifted here in the first place (F2-177), so the gate refuses one.
     */
    @Test
    @DisplayName("PolicyManagementService.compareVersions delegates rather than keeping a copy")
    void theServiceDelegatesToThePrimitiveRatherThanKeepingACopy() throws IOException {
        String service = stripComments(Files.readString(SERVICE));

        assertThat(service)
            .as("the service must route to the module's one answer")
            .contains("PolicyVersions.compare(");
        assertThat(service)
            .as("and must not hold a second segment-splitting implementation — two copies of a "
              + "comparison are exactly what produced this finding")
            .doesNotContain("a.split(\"\\\\.\")")
            .doesNotContain("tryParseInt");
        assertThat(service)
            .as("the newVersion latest-pick reads as what it means")
            .contains("PolicyVersions.LATEST");

        String adapter = stripComments(Files.readString(ADAPTER));
        assertThat(adapter)
            .as("the adapter picks the newest version through the primitive")
            .contains("PolicyVersions.LATEST");
        assertThat(adapter)
            .as("and the helper whose only use was the broken sort key is gone — a helper named "
              + "nullSafe that also lower-cased is worse than none, because the name answers the "
              + "question a reader was about to ask (F2-184)")
            .doesNotContain("nullSafe");
    }

    // ── leg 3: the primitive's contract ─────────────────────────────────────────────

    @Test
    @DisplayName("the ordinary cases a lexical sort gets wrong")
    void theOrdinaryCasesALexicalSortGetsWrong() {
        assertThat(PolicyVersions.compare("1.10", "1.9"))
            .as("a policy on its tenth minor revision — the reachable case, and the one that "
              + "needs no two-digit major version at all")
            .isPositive();
        assertThat(PolicyVersions.compare("10.0", "9.0"))
            .as("PM-H2's own worked example")
            .isPositive();
        assertThat("1.10".compareTo("1.9"))
            .as("the control: lexically it is the other way round, which is the whole finding")
            .isNegative();
    }

    @Test
    @DisplayName("it is a total order, including across non-numeric segments")
    void itIsATotalOrderIncludingAcrossNonNumericSegments() {
        // F2-401's triple: an intransitive comparator makes Stream.max return a non-maximal
        // element with no exception to notice.
        assertThat(PolicyVersions.compare("2", "10")).isNegative();
        assertThat(PolicyVersions.compare("10", "1x")).isNegative();
        assertThat(PolicyVersions.compare("2", "1x")).isNegative();
        assertThat(PolicyVersions.compare("1x", "2")).isPositive();
        assertThat(PolicyVersions.compare("1.0", "1.0")).isZero();
        assertThat(PolicyVersions.compare("2", "2.0"))
            .as("a missing trailing segment counts as 0")
            .isZero();
    }

    @Test
    @DisplayName("LATEST picks the newest document and a null version never wins")
    void latestPicksTheNewestDocumentAndANullVersionNeverWins() {
        PolicyDocument v1_9  = doc(11L, "1.9");
        PolicyDocument v1_10 = doc(12L, "1.10");
        PolicyDocument none  = doc(13L, null);

        assertThat(Stream.of(v1_9, v1_10, none).max(PolicyVersions.LATEST))
            .as("1.10 is newer than 1.9, and a document with no version is not 'what is in force'")
            .hasValue(v1_10);
        assertThat(Stream.of(none, v1_9).max(PolicyVersions.LATEST))
            .as("a null version sorts lowest rather than throwing — the comparator is used on "
              + "caller-supplied data")
            .hasValue(v1_9);
        assertThat(Stream.of(none).max(PolicyVersions.LATEST))
            .as("and a lone null-versioned document is still returned")
            .hasValue(none);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────

    private static PolicyDocument doc(Long id, String version) {
        PolicyDocument p = new PolicyDocument();
        p.setId(id);
        p.setPolicyCode("POL-INFO-001");
        p.setVersion(version);
        return p;
    }

    private static List<Path> javaUnder(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /**
     * Blank out comments with a single left-to-right character scan, preserving newlines and every
     * string literal.
     *
     * <p>⚠ Not a regex: {@code /*[\s\S]*?*}{@code /} recurses per character on the JVM and throws
     * {@link StackOverflowError} over whole-file input (F2-416), and {@code
     * PolicyManagementService} is over a thousand lines. Stripping is load-bearing here for a
     * second reason: this slice's own comments <em>quote</em> the lexical shape they removed, so a
     * scan that reads the epitaph as the corpse fails on a correct tree (F2-193).
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (src.startsWith("//", i)) {
                while (i < src.length() && src.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (src.startsWith("/*", i)) {
                int end = src.indexOf("*/", i + 2);
                int stop = (end < 0) ? src.length() : end + 2;
                while (i < stop) {
                    out.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
            } else if (c == '"' || c == '\'') {
                char q = c;
                out.append(c);
                i++;
                while (i < src.length()) {
                    char d = src.charAt(i);
                    out.append(d);
                    i++;
                    if (d == '\\' && i < src.length()) {
                        out.append(src.charAt(i));
                        i++;
                    } else if (d == q) {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
