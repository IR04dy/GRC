package com.tss.grc.security;

import com.tss.grc.support.GuardExpressions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-284 — every hand-rolled Java ladder that maps a vocabulary token to a rank,
 * declared exactly.
 *
 * <h2>Why this exists</h2>
 *
 * <p>[[F2-230]] built {@code OrderedVocabulary} for precisely this question and its
 * gate, {@code OrderedVocabularySortContractTest}, keys on <b>SQL</b>: derived
 * {@code OrderBy…} query names and {@code CASE … END DESC} ladders inside
 * {@code @Query}. A <b>Java</b> {@code switch (token) { case "CRITICAL" -> 4; … }}
 * is invisible to it by construction — F2-198's shape, where the invariant is real
 * and its population is a mechanism.
 *
 * <p>[[F2-283]] is what that costs: {@code DependencyCascadeService.criticalityRank}
 * knew CRITICAL/HIGH/MEDIUM/LOW and defaulted everything else to 0, so
 * {@code SOVEREIGN} — the platform's own top tier, which
 * {@code BcmBiaAssessmentService} computes and emits {@code BIA_CRITICALITY_SOVEREIGN}
 * for — sorted <em>below LOW</em>. It took writing that service's first unit test to
 * find it. This gate would have failed the day the ladder was written.
 *
 * <h2>Why an exact map rather than a rule</h2>
 *
 * <p>There is no rule to assert. Ladders here legitimately differ in <b>direction</b>
 * ({@code MtpdInheritanceService.severityRank} is documented "lower rank = more
 * urgent" and used ascending, so CRITICAL is 0) and in <b>vocabulary</b>
 * ({@code ThreatIntelCorrelationService.severityRank} correctly has no
 * {@code SOVEREIGN} — that is a BCM national-criticality concept, not a threat-intel
 * severity). Asserting a shared convention would fail on correct code, which is the
 * one failure a gate must never have ([[F2-214]]).
 *
 * <p>So each ladder declares its exact token→rank map. Adding a token, dropping one,
 * or renumbering fails; a new ladder fails until it is declared and therefore read.
 * Nothing here is prose that can rot.
 */
class JavaRankLadderCensusTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** {@code case "A" -> 4;} and {@code case "A", "B" -> 3;} */
    private static final Pattern ARROW = Pattern.compile(
            "case\\s+((?:\"[A-Z0-9_]+\"\\s*,?\\s*)+)->\\s*(-?\\d+)\\s*[;:]?");
    /** {@code case "A": return 4;} / {@code … yield 4;} */
    private static final Pattern CLASSIC = Pattern.compile(
            "case\\s+\"([A-Z0-9_]+)\"\\s*:\\s*(?:return|yield)\\s+(-?\\d+)");
    /** {@code if ("A".equals(s)) return 4;} */
    private static final Pattern IFCHAIN = Pattern.compile(
            "\"([A-Z0-9_]+)\"\\.equals(?:IgnoreCase)?\\([^)]*\\)\\s*\\)?\\s*"
          + "(?:\\{\\s*)?return\\s+(-?\\d+)");
    /**
     * Enclosing method, for the census key.
     *
     * <p>⚠ The first draft matched only an INT-returning signature, so a ladder
     * inside a method returning anything else resolved to {@code "?"} — and every
     * unnamed ladder in one file then collapsed into ONE bucket. That merged
     * {@code WorkflowRequestService}'s two unrelated switches — {@code hopVia}'s
     * candidate order ({@code SUBMITTED=0, RESUBMITTED=1}) and a quorum THRESHOLD
     * ({@code ANY=1, MAJORITY=(total/2)+1}) — into a single phantom vocabulary
     * {@code {ANY, RESUBMITTED, SUBMITTED}} that exists nowhere in the codebase.
     *
     * <p>🔑🔑 The merge did not merely mislabel a ladder — it <b>manufactured</b> one.
     * With methods resolving, {@code hopVia} has TWO tokens and the WF-9 quorum switch
     * has ONE integer-literal arm ({@code MAJORITY} and {@code QUORUM} map to
     * expressions, not literals), so both sit below the three-token threshold and
     * neither is a ladder at all. Fused, they summed PAST it. **A phantom merge is
     * worse than a miss, because a population that grew looks measured.**
     *
     * <p>And {@link #theDerivationFindsAllThreeSyntaxesAndSplitsByMethod}
     * <em>claims</em> to guard exactly this — its fixture simply had three nameable
     * methods, so it could not see the unnameable case. ⚠ The second draft then
     * over-corrected: a signature pattern widened to any return type matches
     * {@code return switch (s) {} as type={@code return}, name={@code switch}, so
     * every ladder keyed as {@code #switch} and all twelve collapsed into one bucket.
     * Widening a pattern and excluding what the widening newly admits are one change,
     * not two. Any return type now matches, keywords are excluded, and an unresolved
     * ladder is keyed by its own offset so two can never merge.
     */
    private static final Pattern METHOD = Pattern.compile(
            "(?:private|public|protected|static|final|synchronized|abstract|\\s)+"
          + "[\\w.<>,\\[\\]?]+\\s+(\\w+)\\s*\\([^;{]*\\)\\s*\\{");

    /**
     * A ladder maps a vocabulary token to a RANK. It does not map one to a duration,
     * a multiplier or a score — and this subtraction is the detector error this gate
     * was built after making.
     *
     * <p>Measured while censusing: {@code TenableInboundService.slaDays} is
     * {@code CRITICAL=0, HIGH=0, MEDIUM=1, LOW=3} and
     * {@code CyberAssessmentService.dueDaysForRisk} is
     * {@code LOW=60, HIGH=14, CRITICAL=7} — both read as "inverted ranks" and both are
     * <b>days</b>. {@code RiskExposureService.effectivenessFactor} is a multiplier.
     * Counting those as ranks produced 11 "inverted pairs", almost all noise.
     * [[F2-268]] recorded the same rule one noun over: <i>a COUNT of days is not a
     * deadline</i>. Here: <b>a duration is not a rank.</b>
     */
    private static final List<String> NOT_A_RANK = List.of(
            "slaDays", "dueDaysForRisk", "effectivenessFactor", "scale");

    /**
     * The census. Key is {@code SimpleName#method}; value is the exact token→rank map.
     *
     * <p>Read, each one, 2026-08-22.
     */
    private static final Map<String, Map<String, Integer>> LADDERS = new TreeMap<>(Map.of(
        // F2-283 added SOVEREIGN. Higher = more severe; the unknown token ranks 0
        // deliberately, so "unclassified sorts after classified" in a tiebreak.
        "DependencyCascadeService#criticalityRank",
        rank("SOVEREIGN", 5, "CRITICAL", 4, "HIGH", 3, "MEDIUM", 2, "LOW", 1),

        // Documented "lower rank = more urgent" and used with comparingInt ASCENDING,
        // so CRITICAL sorts first. Inverted relative to the cascade's and correct:
        // the direction is part of the ladder, not a convention to unify.
        "MtpdInheritanceService#severityRank",
        rank("CRITICAL", 0, "HIGH", 1, "MEDIUM", 2, "LOW", 3),

        // Threat-intel severity. Correctly has NO SOVEREIGN: that is a BCM
        // national-criticality tier, not a threat-intel severity band.
        "ThreatIntelCorrelationService#severityRank",
        rank("CRITICAL", 4, "HIGH", 3, "MEDIUM", 2, "LOW", 1),

        // SAMA large-exposure RAG band. Its own vocabulary; BREACH is the worst.
        "CptyService#bandRank",
        rank("BREACH", 3, "RED", 2, "AMBER", 1),

        // Data-classification order (NCA/SDAIA). TOP_SECRET is the most restrictive.
        "DataclassService#classOrder",
        rank("TOP_SECRET", 4, "CONFIDENTIAL", 3, "INTERNAL", 2, "PUBLIC", 1),

        // Control-coverage staleness: EXPIRED is worse than PARTIAL is worse than GAP
        // being merely absent.
        "ControlMapService#coverageRank",
        rank("EXPIRED", 2, "PARTIAL", 1, "GAP", 0),

        // NDR cycle progress, summed inline into a Data Office KPI. Ordinal rather
        // than a sort key: ACCEPTED is further along than SUBMITTED is than READY.
        "DataOfficerQueryService#dashboard",
        rank("ACCEPTED", 3, "SUBMITTED", 2, "READY", 1)));

    private static Map<String, Integer> rank(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    // ─── derivation ─────────────────────────────────────────────────────────

    private record Ladder(String file, String method, Map<String, Integer> map) {}

    private static List<Ladder> derive() throws IOException {
        List<Ladder> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = GuardExpressions.stripComments(
                        Files.readString(p, StandardCharsets.UTF_8));
                if (!src.contains("case ") && !src.contains(".equals(")) continue;

                Map<String, Map<String, Integer>> byMethod = new LinkedHashMap<>();
                collect(src, ARROW, true, byMethod);
                collect(src, CLASSIC, false, byMethod);
                collect(src, IFCHAIN, false, byMethod);

                String simple = p.getFileName().toString().replace(".java", "");
                byMethod.forEach((meth, map) -> {
                    // >= 3 tokens is a ladder; two is a flag or a boolean fork.
                    if (map.size() >= 3) out.add(new Ladder(simple, meth, map));
                });
            }
        }
        return out;
    }

    private static void collect(String src, Pattern p, boolean multi,
                                Map<String, Map<String, Integer>> byMethod) {
        Matcher m = p.matcher(src);
        while (m.find()) {
            String meth = enclosingMethod(src, m.start());
            int rank = Integer.parseInt(m.group(2));
            Map<String, Integer> target =
                    byMethod.computeIfAbsent(meth, k -> new LinkedHashMap<>());
            if (multi) {
                Matcher t = Pattern.compile("\"([A-Z0-9_]+)\"").matcher(m.group(1));
                while (t.find()) target.put(t.group(1), rank);
            } else {
                target.put(m.group(1), rank);
            }
        }
    }

    /**
     * Control-flow keywords that a widened signature pattern matches as if they were
     * methods. ⚠ {@code return switch (s) {} parses as type={@code return},
     * name={@code switch} — so the second draft keyed EVERY ladder as
     * {@code #switch} and collapsed all twelve into one bucket. Widening a pattern
     * and excluding what the widening newly admits are one change, not two.
     */
    private static final java.util.Set<String> NOT_A_METHOD = java.util.Set.of(
            "switch", "if", "for", "while", "catch", "synchronized", "try", "do",
            "return", "new", "else");

    private static String enclosingMethod(String src, int pos) {
        Matcher m = METHOD.matcher(src.substring(0, pos));
        String last = null;
        while (m.find()) {
            if (!NOT_A_METHOD.contains(m.group(1))) last = m.group(1);
        }
        // Unresolved: key by offset so two unnamed ladders can never merge into one
        // phantom vocabulary. A ladder that reaches this branch is still reported and
        // still has to be declared -- it just cannot be silently absorbed.
        return last != null ? last : "?@" + pos;
    }

    // ─── assertions ─────────────────────────────────────────────────────────

    @Test
    void everyJavaRankLadderIsDeclaredExactly() throws IOException {
        Map<String, Map<String, Integer>> live = new TreeMap<>();
        for (Ladder l : derive()) {
            if (NOT_A_RANK.contains(l.method())) continue;
            live.put(l.file() + "#" + l.method(), new LinkedHashMap<>(l.map()));
        }

        assertThat(live.keySet())
                .as("""
                    F2-284: a hand-rolled Java rank ladder appeared, disappeared or was \
                    renamed. F2-230's gate cannot see these — it keys on SQL ORDER BY and \
                    CASE — and F2-283 is what that costs: criticalityRank ranked SOVEREIGN \
                    below LOW and only the service's first unit test found it. Read the \
                    ladder and declare its exact map, or add it to NOT_A_RANK if it maps a \
                    duration or a multiplier rather than an order.""")
                .containsExactlyInAnyOrderElementsOf(LADDERS.keySet());

        List<String> wrong = new ArrayList<>();
        live.forEach((key, map) -> {
            Map<String, Integer> expected = LADDERS.get(key);
            if (expected != null && !expected.equals(map)) {
                wrong.add(key + " expected " + expected + " but is " + map);
            }
        });
        assertThat(wrong)
                .as("F2-284: a declared ladder changed its tokens or its numbering — "
                    + "which is exactly how SOVEREIGN came to rank below LOW")
                .isEmpty();
    }

    /**
     * The subtraction, pinned in both directions. A ladder that maps a token to a
     * DURATION is not a rank, and counting one produced 11 phantom "inverted pairs"
     * when this census was first measured.
     */
    @Test
    void aDurationOrAMultiplierIsNotARank() throws IOException {
        Map<String, Map<String, Integer>> byKey = new TreeMap<>();
        for (Ladder l : derive()) byKey.put(l.file() + "#" + l.method(), l.map());

        // Every excluded name must still BE a ladder in the tree, or the exclusion is
        // a reason for something that is not in the population (F2-199's direction).
        for (String excluded : NOT_A_RANK) {
            assertThat(byKey.keySet())
                    .as("F2-284: NOT_A_RANK names %s, which the scan no longer finds — "
                        + "delete the entry rather than leaving it to look like coverage",
                        excluded)
                    .anyMatch(k -> k.endsWith("#" + excluded));
        }

        // And each really is a duration/multiplier: its numbers are NOT a dense
        // 0..n-1 or 1..n permutation, which is what a rank looks like.
        List<String> suspicious = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : byKey.entrySet()) {
            String meth = e.getKey().substring(e.getKey().indexOf('#') + 1);
            if (!NOT_A_RANK.contains(meth)) continue;
            List<Integer> vals = e.getValue().values().stream().sorted().toList();
            boolean dense = vals.get(vals.size() - 1) - vals.get(0) == vals.size() - 1
                    && vals.stream().distinct().count() == vals.size();
            if (dense) suspicious.add(e.getKey() + " " + e.getValue());
        }
        assertThat(suspicious)
                .as("F2-284: an entry excluded as a duration has rank-shaped numbers "
                    + "(dense and distinct) — re-read it, it may be an order after all")
                .isEmpty();
    }

    /**
     * Anti-vacuity: the derivation must find the ladders, or every assertion above
     * passes over an empty set. Drives the three literal syntaxes plus the two
     * shapes it must NOT treat as one ladder.
     */
    @Test
    void theDerivationFindsAllThreeSyntaxesAndSplitsByMethod() {
        String src = """
            static int arrowForm(String s) {
                return switch (s) {
                    case "SOVEREIGN" -> 5;
                    case "CRITICAL" -> 4;
                    case "HIGH", "MEDIUM" -> 3;
                    default -> 0;
                };
            }
            static int classicForm(String s) {
                switch (s) {
                    case "A": return 3;
                    case "B": return 2;
                    case "C": return 1;
                }
                return 0;
            }
            static int ifChainForm(String s) {
                if ("X".equals(s)) return 3;
                if ("Y".equals(s)) return 2;
                if ("Z".equals(s)) return 1;
                return 0;
            }
            """;
        Map<String, Map<String, Integer>> byMethod = new LinkedHashMap<>();
        collect(src, ARROW, true, byMethod);
        collect(src, CLASSIC, false, byMethod);
        collect(src, IFCHAIN, false, byMethod);

        assertThat(byMethod.get("arrowForm"))
                .as("F2-284: a multi-token arrow case shares one rank")
                .containsExactlyInAnyOrderEntriesOf(
                    rank("SOVEREIGN", 5, "CRITICAL", 4, "HIGH", 3, "MEDIUM", 3));
        assertThat(byMethod.get("classicForm")).hasSize(3);
        assertThat(byMethod.get("ifChainForm")).hasSize(3);
        assertThat(byMethod.keySet())
                .as("F2-284: ladders are keyed per METHOD, not per file — two ladders in "
                    + "one class must not merge into one phantom vocabulary")
                .containsExactly("arrowForm", "classicForm", "ifChainForm");

        // The `default` arm is not a token and must never enter a map.
        assertThat(byMethod.values()).allSatisfy(m ->
                assertThat(m).doesNotContainKey("default"));
    }

    /**
     * The comment stripper is load-bearing here for a specific reason: this
     * codebase documents a retired ladder by quoting it, and F2-283's own javadoc on
     * {@code criticalityRank} quotes the {@code default -> 0} it replaced. Reading
     * raw source would count the epitaph as the corpse ([[F2-193]]).
     */
    @Test
    void theStripperKeepsCodeAndDropsAQuotedLadder() {
        String src = "// was: case \"CRITICAL\" -> 4; see /* the note\n"
                   + "case \"SOVEREIGN\" -> 5;\n"
                   + "/* case \"LOW\" -> 1; */\n";
        String stripped = GuardExpressions.stripComments(src);

        assertThat(stripped).contains("\"SOVEREIGN\"");
        assertThat(stripped).doesNotContain("was:");
        assertThat(stripped).doesNotContain("\"LOW\"");

        // The two-pass form runs the block pattern from the `/*` INSIDE the line
        // comment and takes the live case with it — a SMALLER population, which reads
        // exactly like a clean tree (F2-198 / F2-211).
        String twoPass = src.replaceAll("/\\*[\\s\\S]*?\\*/", "")
                            .replaceAll("(?<!:)//[^\\n]*", "");
        assertThat(twoPass)
                .as("the two-pass form is what corrupts the source")
                .doesNotContain("\"SOVEREIGN\"");
    }
}
