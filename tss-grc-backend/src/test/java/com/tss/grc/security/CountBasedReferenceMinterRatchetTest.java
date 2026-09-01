package com.tss.grc.security;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>F2-356 — a server-minted reference ordinal is seeded from a MAX, never from a live COUNT.</b>
 *
 * <h3>Why this is a gate and not a sixth fix</h3>
 * A reference minter that seeds its ordinal from {@code count(...) + 1} re-mints an ordinal already
 * in use whenever a row is missing from the counted set — deterministically past a query window,
 * after a soft-delete gap (the derived {@code countBy…} respects {@code @Where(is_deleted = 0)}), or
 * under a concurrent create — and a filtered unique index then turns that into a 500 (or, with no
 * unique index, a silent duplicate reference). It was fixed <b>one register per slice, five slices
 * running</b>: F2-337 {@code OBI-} (open-banking incidents), F2-351 {@code RUN-} (BCM exercise runs),
 * F2-353 {@code CP-} (SAMA counterparties), F2-354 {@code OSI-} (on-site inspections) and F2-355
 * {@code DR-} (inspection document requests). Each was found by grepping for the count-seed shape,
 * which was five-for-five.
 *
 * <p>🔑 <b>Five survivors after five fixes is what a shape looks like when nothing counts it</b>
 * (F2-208 / F2-266). The reusable move is the one this class makes: sweep the population and assert
 * the shape, so the sixth instance fails the build instead of waiting for the sixth slice. The
 * platform's own correct idiom is {@code ComplaintService#generateNextComplaintNo} — seed from the
 * MAX ordinal (a MAX survives a gap; a used ordinal is never re-issued), then a skip-loop backstop.
 *
 * <h3>What is asserted, and its two deliberate blind spots (F2-205 — say what the instrument cannot see)</h3>
 * The detector flags a method that both (a) computes a count-seed ({@code count…() + 1},
 * {@code .size() + 1} or {@code .count() + 1}) and (b) builds a reference in the SAME method body —
 * a zero-padded {@code String.format("…%0Nd…")} or a {@code set…Reference}/{@code set…Code} call.
 * That is the shape all five fixes had, and it is asserted as an exact enumerated set.
 *
 * <ol>
 *   <li><b>Split-method minters are not seen.</b> {@code RrpService#nextOrdinal} computes
 *       {@code .count() + 1L} and RETURNS a long that its caller formats — the count-seed and the
 *       reference build are in different methods, so no single-method scan matches it. It is
 *       one-per-fiscal-year (a re-mint needs a soft-delete gap in a 1–2 row set), so it is filed as
 *       low-risk rather than reshaped. Recorded here so a reader does not read the zero as total.</li>
 *   <li><b>Nothing here says a match is a defect.</b> The two survivors carry bounded skip-loops and
 *       their authors accepted the residual race for their cadence; they are counted, with a reason
 *       each, because a count seed re-mints past a skip-loop bound and is the shape to convert if the
 *       cadence ever rises — not because they 500 today.</li>
 * </ol>
 */
class CountBasedReferenceMinterRatchetTest {

    private static final Path MAIN = Path.of("src/main/java");

    /** A {@code count(...) + 1} / {@code .size() + 1} / {@code .count() + 1} ordinal seed. */
    private static final Pattern COUNT_SEED = Pattern.compile(
        "(?:\\bcount\\w*\\s*\\([^;{}]*\\)|\\.\\s*size\\s*\\(\\s*\\)|\\.\\s*count\\s*\\(\\s*\\))\\s*\\+\\s*1L?");

    /**
     * A reference build: a zero-padded {@code String.format} ordinal, or a
     * reference/code setter.
     *
     * <p>⚠ F2-416 — the optional {@code Locale.X,} group is load-bearing. That slice put
     * {@code Locale.ROOT} on every identifier-building {@code String.format} (an ar-SA JVM
     * otherwise mints Arabic-Indic digits, and {@code TRY_CAST} of one is NULL, which resets
     * every MAX seed to 0). Inserting an argument between the paren and the literal blinded
     * this detector completely: it found ZERO minters where it knows two exist, so the
     * forward leg passed <b>vacuously over an empty list</b> and only
     * {@link #everyKnownSurvivorIsStillDetected} caught it. 🔑 A detector keyed on the
     * spelling of a call is broken by anything that adds an argument to that call, and it
     * fails in the quiet direction — which is the whole argument for the reverse leg.
     */
    private static final Pattern REFERENCE_BUILD = Pattern.compile(
        "String\\.format\\s*\\(\\s*(?:(?:java\\.util\\.)?Locale\\.\\w+\\s*,\\s*)?\"[^\"]*%0\\d*d|\\.\\s*set\\w*(?:Reference|Code)\\s*\\(");

    private static final Pattern METHOD_HEADER = Pattern.compile(
        "(?:public|protected|private)\\s+(?:static\\s+)?[\\w<>,\\[\\].?\\s]+?\\s+(\\w+)\\s*\\([^;{]*\\)\\s*\\{");

    /**
     * The count-seed reference minters that remain, each read and each mitigated or low-risk. A new
     * one must be added here with a reason (or, better, reshaped to a MAX seed); a reshaped one must
     * lose its entry in the same commit (the reverse leg enforces it). F2-188: a named map proves a
     * reason exists, never that it is true — both were read, not assumed.
     */
    private static final Map<String, String> KNOWN_COUNT_SEED_MINTERS = new LinkedHashMap<>() {{
        put("CrisisAarService#nextReportCode",
            "Seeds AAR-<year>-NNN from countByOrganisationIdAndReportCodeStartingWith + 1, then a "
            + "bounded 5-attempt skip-loop backstopped by the V124 unique index. The author accepted "
            + "the residual race for the one-AAR-per-major-event cadence; a soft-delete gap larger "
            + "than the loop bound would still 500, so it is the shape to convert if the cadence rises.");
        put("MtpdInheritanceService#generateAssessmentCode",
            "Seeds MTPD-INH-<year>-<q>-NN from countByOrganisationIdAndCycleYearAndIsDeletedFalse + 1, "
            + "then a skip-loop guard against rapid double-fire. Same mitigation and same residual: a "
            + "soft-delete gap past the loop bound re-mints a live ordinal.");
    }};

    @Test
    @DisplayName("no NEW reference minter seeds its ordinal from a live count (MAX-seed the sequence)")
    void everyCountSeedMinterIsAKnownMitigatedSurvivor() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path f : javaSources()) {
            if (!f.getFileName().toString().endsWith("Service.java")) { continue; }
            String cls = f.getFileName().toString().replace(".java", "");
            found.addAll(scan(stripComments(Files.readString(f, StandardCharsets.UTF_8)), cls));
        }
        List<String> sorted = found.stream().distinct().sorted().toList();

        assertThat(sorted).as("""
            Reference minters seeding an ordinal from a live count + 1 (rather than MAX + 1): %s

            A count seed re-mints an in-use ordinal past a query window, after a soft-delete gap
            (the derived countBy respects @Where(is_deleted = 0)), or under a concurrent create — a
            500 on a filtered unique index, or a silent duplicate without one. Five registers were
            fixed one per slice (F2-337/351/353/354/355); mirror ComplaintService#generateNextComplaintNo
            (MAX seed + skip-loop) rather than adding a sixth here.

            If a NEW entry appeared, reshape it to a MAX seed. If you reshaped a known one, delete its
            KNOWN_COUNT_SEED_MINTERS entry in the same commit.
            %s""".formatted(sorted, String.join("\n            ", sorted)))
            .containsExactlyInAnyOrderElementsOf(KNOWN_COUNT_SEED_MINTERS.keySet());
    }

    /** Reverse leg: every enumerated survivor is still one the scan finds, so the map cannot rot. */
    @Test
    void everyKnownSurvivorIsStillDetected() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path f : javaSources()) {
            if (!f.getFileName().toString().endsWith("Service.java")) { continue; }
            String cls = f.getFileName().toString().replace(".java", "");
            found.addAll(scan(stripComments(Files.readString(f, StandardCharsets.UTF_8)), cls));
        }
        assertThat(found).as(
            "A KNOWN_COUNT_SEED_MINTERS entry no longer matches the detector — it was reshaped or "
            + "renamed; delete its entry in the same commit.")
            .containsAll(KNOWN_COUNT_SEED_MINTERS.keySet());
    }

    /**
     * The detector must fire on the count-seed shape and NOT on a MAX seed or an array size — a
     * corpus assertion passes both when the tree is clean and when the regex has rotted (F2-195), so
     * this drives the extracted {@link #scan} over synthetic input in both directions.
     */
    @Test
    void theDetectorFiresOnCountSeedAndNotOnMaxSeedOrArraySize() {
        String countSeedMinter = """
            class X {
              private String nextCode(long org) {
                long n = repo.countByOrganisationId(org) + 1;
                return String.format("AC-%05d", n);
              }
            }""";
        String maxSeedMinter = """
            class X {
              private String nextCode(long org) {
                int n = repo.findMaxSeq(org) + 1;
                return String.format("AC-%05d", n);
              }
            }""";
        String arraySizePlusOne = """
            class X {
              private String[][] table(java.util.List<?> rows) {
                String[][] out = new String[rows.size() + 1][4];
                return out;
              }
            }""";
        String countButNoReference = """
            class X {
              private long howMany(long org) {
                return repo.countByOrganisationId(org) + 1;
              }
            }""";
        // F2-416 — the same minter with an explicit Locale. The sweep that added
        // Locale.ROOT platform-wide blinded this detector, and it failed in the quiet
        // direction (zero found, forward leg vacuous). Both shapes are asserted now, so
        // the detector cannot silently depend on either one.
        String countSeedMinterWithLocale = """
            class X {
              private String nextCode(long org) {
                long n = repo.countByOrganisationId(org) + 1;
                return String.format(java.util.Locale.ROOT, "AC-%05d", n);
              }
            }""";

        assertThat(scan(stripComments(countSeedMinter), "X")).containsExactly("X#nextCode");
        assertThat(scan(stripComments(countSeedMinterWithLocale), "X"))
            .as("F2-416: an explicit Locale must not hide a count-seed minter")
            .containsExactly("X#nextCode");
        assertThat(scan(stripComments(maxSeedMinter), "X")).isEmpty();
        assertThat(scan(stripComments(arraySizePlusOne), "X")).isEmpty();
        assertThat(scan(stripComments(countButNoReference), "X")).isEmpty();
    }

    // ── detector ────────────────────────────────────────────────────────────

    /** Methods whose body both computes a count-seed and builds a reference. */
    static List<String> scan(String source, String className) {
        List<String> hits = new ArrayList<>();
        Matcher m = METHOD_HEADER.matcher(source);
        while (m.find()) {
            int brace = source.indexOf('{', m.end() - 1);
            if (brace < 0) { continue; }
            String body = balancedBody(source, brace);
            if (COUNT_SEED.matcher(body).find() && REFERENCE_BUILD.matcher(body).find()) {
                hits.add(className + "#" + m.group(1));
            }
        }
        return hits;
    }

    /** The brace-balanced body starting at {@code open} (which must index a '{'). */
    private static String balancedBody(String s, int open) {
        int depth = 1, j = open + 1;
        while (j < s.length() && depth > 0) {
            char c = s.charAt(j);
            if (c == '{') { depth++; }
            else if (c == '}') { depth--; }
            j++;
        }
        return s.substring(open, j);
    }

    /**
     * Strip {@code //} and block comments in ONE left-to-right alternation, preserving newlines
     * inside block comments so brace balance is unaffected (F2-198: two sequential passes corrupt a
     * {@code //} line whose text contains {@code /*}).
     */
    private static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') { i++; }
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                    if (s.charAt(i) == '\n') { out.append('\n'); }
                    i++;
                }
                i += 2;
            } else if (c == '"') {
                out.append(c); i++;
                while (i < n && s.charAt(i) != '"') {
                    if (s.charAt(i) == '\\' && i + 1 < n) { out.append(s.charAt(i)); i++; }
                    out.append(s.charAt(i)); i++;
                }
                if (i < n) { out.append(s.charAt(i)); i++; }
            } else {
                out.append(c); i++;
            }
        }
        return out.toString();
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (UncheckedIOException e) {
            throw new IOException(e);
        }
    }
}
