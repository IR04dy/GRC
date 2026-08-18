package com.tss.grc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-235 — no source file may contain a RAW control byte, because a corpus an instrument cannot
 * read is indistinguishable from a corpus with nothing in it.
 *
 * <p>🔑 <b>Every measurement in this programme is a scan over these two source trees</b> — the
 * de-mocking gates, the provenance gates, the vocabulary censuses, and the sweeps that locate a
 * defect family before a gate is written for it. ripgrep classifies a file containing a NUL byte as
 * <b>binary</b>: a content-mode search prints {@code "binary file matches"} and <b>no lines</b>, and
 * a search whose pattern does not match prints nothing at all. So the file contributes nothing to
 * any sweep, and <b>the absence reads exactly like a clean file</b> — the under-reporting direction
 * this register keeps recording (F2-198's comment stripper, F2-208's heredoc {@code \\b}, F2-229's
 * schema-less key, F2-217's lower-case-only shape).
 *
 * <p><b>Both instances found were behaviour-preserving, which is precisely why they survived.</b>
 * A raw 0x00 inside a Java string literal is legal (JLS: a StringCharacter is any InputCharacter
 * but {@code "} and {@code \}), and in a JS regex literal {@code [\\s<NUL>-<0x1F>]} denotes exactly
 * the range {@code [\\s\\x00-\\x1f]} does. Nothing failed, nothing behaved oddly, and a 4,073-test
 * green build had no way to notice:
 *
 * <ul>
 *   <li>{@code SplitAttributionContractTest} — 3 NULs, from F2-234's own escape collapse while
 *       writing the map key {@code cls + "\0" + path}. The gate that measures split attribution was
 *       itself unreadable by the tool used to measure everything else.</li>
 *   <li>{@code KbArticlePage.tsx} — a NUL <i>and</i> a 0x1F, present since {@code 957b679e}, the
 *       commit whose message reads <i>"3 real bug fixes: … KB stored-XSS (#120)"</i>. So the file
 *       holding the platform's URL-scheme allowlist — the guard against {@code javascript:} in
 *       markdown links rendered through {@code dangerouslySetInnerHTML} — has been invisible to
 *       ripgrep <b>since the day that guard was written</b>. Any security sweep of the frontend
 *       silently skipped the one file most worth reading.</li>
 *   <li>{@code IntakeSystemSubmissionTest} — a raw 0x01 that was <i>deliberate</i> (F2-175's
 *       C0-control-character payload fixture) and therefore not a defect, but written as a raw byte
 *       the test's whole subject was invisible to a reader: the source showed {@code \"v2\" model}
 *       with an unexplained gap. Rewritten as the octal escape {@code \\001} — identical at
 *       runtime, legible in the diff.</li>
 * </ul>
 *
 * <p>This is a <b>hard zero with no exemption map</b>, and that is safe rather than strict: Java and
 * TypeScript both offer an escape for every control character, so a raw one is never the only way to
 * express an intent. A gate that fails on correct code is worse than no gate (F2-214) — here there
 * is no correct code it can fail on. The deliberate case above is the proof: it kept its behaviour
 * and gained a reader.
 */
class ScannableSourceContractTest {

    /** The trees every instrument in this repo scans. Each is asserted separately, below. */
    private static final List<Path> ROOTS = List.of(
        Path.of("src", "main"),
        Path.of("src", "test"),
        Path.of("..", "tss-grc-ui-v2", "src"));

    /**
     * Anti-vacuity floors, per root rather than in total. F2-215's rule: <b>a floor has to sit above
     * the largest WRONG answer, not above zero.</b> The wrong answers here are a root that does not
     * resolve (0 files) and a scan that reaches only one tree — so a single total of "at least 1,000"
     * would pass while two thirds of the corpus went unread. Measured 2026-08-18:
     * 2,286 / 446 / 825 = 3,557.
     */
    private static final List<Integer> FLOORS = List.of(2_000, 400, 700);

    private static final Set<Integer> LEGAL_CONTROL_BYTES = Set.of(0x09, 0x0A, 0x0D); // TAB, LF, CR

    private static final Set<String> SCANNED_SUFFIXES = Set.of(
        ".java", ".ts", ".tsx", ".sql", ".yml", ".yaml", ".properties", ".json", ".xml", ".md");

    /** Where the three F2-235 instances were, relative to the backend module dir. */
    private static final List<Path> THE_THREE_INSTANCES = List.of(
        Path.of("src", "test", "java", "com", "tss", "grc", "security",
                "SplitAttributionContractTest.java"),
        Path.of("src", "test", "java", "com", "tss", "grc", "module", "intake", "service",
                "IntakeSystemSubmissionTest.java"),
        Path.of("..", "tss-grc-ui-v2", "src", "pages", "knowledge-base", "KbArticlePage.tsx"));

    // ---------------------------------------------------------------- the detector

    /** Byte offsets of every control byte that is not TAB, LF or CR. */
    static List<int[]> rawControlBytes(byte[] data) {
        List<int[]> hits = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;
            if (b < 0x20 && !LEGAL_CONTROL_BYTES.contains(b)) hits.add(new int[] {i, b});
        }
        return hits;
    }

    private static List<Path> sourceFilesUnder(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                       .filter(p -> {
                           String n = p.getFileName().toString();
                           int dot = n.lastIndexOf('.');
                           return dot > 0 && SCANNED_SUFFIXES.contains(n.substring(dot));
                       })
                       .sorted()
                       .toList();
        }
    }

    // ---------------------------------------------------------------- the invariant

    @Test
    @DisplayName("no source file in either tree contains a raw control byte")
    void noSourceFileContainsARawControlByte() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (Path root : ROOTS) {
            for (Path p : sourceFilesUnder(root)) {
                scanned++;
                byte[] data = Files.readAllBytes(p);
                List<int[]> hits = rawControlBytes(data);
                if (hits.isEmpty()) continue;

                int[] first = hits.get(0);
                int line = 1;
                for (int i = 0; i < first[0]; i++) if ((data[i] & 0xFF) == 0x0A) line++;
                boolean blinding = hits.stream().anyMatch(h -> h[1] == 0);
                offenders.add(String.format(
                    "%s:%d — %d raw control byte(s), first 0x%02x at offset %d%s",
                    p.toString().replace('\\', '/'), line, hits.size(), first[1], first[0],
                    blinding ? "  [NUL — ripgrep reads this file as BINARY, so a content-mode "
                             + "sweep sees NO lines from it]" : ""));
            }
        }

        assertThat(scanned)
            .as("the walk found almost nothing, which is what a broken root looks like")
            .isGreaterThan(FLOORS.stream().mapToInt(Integer::intValue).sum());

        assertThat(offenders)
            .as("""
                A raw control byte in source is a collapsed escape, and where it is a NUL the file \
                becomes invisible to every ripgrep-based instrument in this repo — a content-mode \
                search returns no lines, and a non-matching search is indistinguishable from a \
                clean file. Write the escape (\\\\0, \\\\001, \\\\x00, \\\\u0001) instead: the \
                runtime value is identical and the intent survives into the diff. F2-235 found \
                three, one of them in the file holding the KB URL-scheme XSS allowlist, unreadable \
                since the commit that added it.""")
            .isEmpty();
    }

    @Test
    @DisplayName("each root is scanned — a floor per tree, not one total")
    void everyRootContributesItsMeasuredShareOfTheCorpus() throws IOException {
        for (int i = 0; i < ROOTS.size(); i++) {
            Path root = ROOTS.get(i);
            int found = sourceFilesUnder(root).size();
            assertThat(found)
                .as("%s resolved to %d scanned files — a root that stops resolving reports a "
                    + "SMALLER population, which reads exactly like a tree with nothing wrong "
                    + "in it", root, found)
                .isGreaterThanOrEqualTo(FLOORS.get(i));
        }
    }

    /**
     * F2-195's rule: the corpus assertion above passes both when the tree is clean and when the
     * detector has rotted, so the detector is exercised against values that plainly contain the
     * thing it looks for. Without this leg, deleting the body of {@link #rawControlBytes} leaves
     * every other assertion in this class green.
     */
    @Test
    @DisplayName("the detector fires on a raw NUL and on a raw 0x1F, and not on tab/CR/LF")
    void theDetectorFiresOnAValueThatPlainlyContainsOne() {
        assertThat(rawControlBytes("ok\0ok".getBytes()))
            .as("a NUL is the byte that blinds ripgrep — if this is empty the invariant is vacuous")
            .isNotEmpty();
        assertThat(rawControlBytes("ok\037ok".getBytes()))
            .as("the KB sanitiser's range END collapsed too, so 0x1F must be caught as well")
            .isNotEmpty();
        assertThat(rawControlBytes("tab\there\r\nand a line".getBytes()))
            .as("tab, CR and LF are how source files are written — firing on them would make this "
                + "gate fail on every file in the tree")
            .isEmpty();
    }

    /**
     * A count can be satisfied by the wrong files. This names the three the finding was found in, so
     * a walk that resolves the backend but not the frontend fails <b>by name</b> rather than by
     * drifting under a floor.
     */
    @Test
    @DisplayName("the scan reaches the three files F2-235 was found in")
    void theScanReachesTheThreeFilesTheFindingWasFoundIn() throws IOException {
        Set<Path> scanned = new LinkedHashSet<>();
        for (Path root : ROOTS) scanned.addAll(sourceFilesUnder(root));

        for (Path instance : THE_THREE_INSTANCES) {
            assertThat(Files.exists(instance))
                .as("%s has moved — repoint this list, do not delete the entry: it is what proves "
                    + "the walk reaches both source trees", instance)
                .isTrue();
            assertThat(scanned.stream().anyMatch(p -> {
                try {
                    return Files.isSameFile(p, instance);
                } catch (IOException e) {
                    return false;
                }
            }))
                .as("%s is not in the scanned population, so the invariant above says nothing "
                    + "about the place this defect was actually found", instance)
                .isTrue();
        }
    }
}
