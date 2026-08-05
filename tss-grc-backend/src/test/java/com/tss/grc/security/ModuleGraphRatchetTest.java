package com.tss.grc.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-08b, the half that can be seeded today — and F2-34's reconciliation, as a test.
 *
 * <h3>Why this is not the whole of F2-08b</h3>
 * F2-08b asks for a ratchet over the entire module graph. Its row carries a blocker, and the blocker
 * is real: <b>F2-34 says "the boundary ratchet cannot be seeded until one reading wins"</b>, because
 * {@code MODULE_SEPARABILITY} §2 ("kernel imports do not count") and §6c ("only
 * {@code common}/{@code security}/{@code platform}/{@code spi} are legal") disagree, and the graph's
 * headline numbers swing enormously on which is meant.
 *
 * <h3>⚠ The disagreement is one word, and it is measurable</h3>
 * This tree has <b>two</b> {@code platform} namespaces:
 * <pre>
 *   com.tss.grc.platform            5 files    a top-level package, beside common/config/security
 *   com.tss.grc.module.platform   115 files    a module — licensing, auditlog, automation, files, sso
 * </pre>
 * Every prose statement of the rule says "platform" without saying which, and
 * {@code Appendix/scripts/coupling.py} resolves it <b>both ways in one script</b>: an import of
 * {@code com.tss.grc.platform.*} is tallied as kernel, an import of {@code com.tss.grc.module.platform.*}
 * as a peer. Measured at HEAD by {@code Appendix/scripts/boundary_readings.py}:
 * <pre>
 *   A  narrow kernel (module.platform is a PEER)   975 peer lines · 140 pairs · 13 bidirectional
 *   B  wide kernel   (module.platform IS kernel)   311 peer lines ·  90 pairs ·  4 bidirectional
 * </pre>
 * <b>Reading A reproduces the plan's "13" exactly</b>, and <b>9 of those 13 pairs involve
 * {@code module.platform}</b> — which is the entire discrepancy. The choice legalises or condemns 664
 * import lines, so seeding a ceiling against a guess would pin the wrong graph as tolerated. A ceiling
 * tolerates the population it measures; that is why F2-61(c)'s export ceiling had to be replaced by an
 * invariant, and the same trap applies at 59× the scale here.
 *
 * <h3>⚠ And reading B is already in force, silently, in the only boundary ratchet that exists</h3>
 * {@link ChangeModuleBoundaryTest} declares {@code ALLOWED = Set.of("change_mgmt", "platform")} with
 * the javadoc <em>"platform is the kernel (audit ledger, automation events, licensing, realtime,
 * calendar)"</em>. S5a therefore <b>chose reading B</b>, shipped it, and F2-34 does not record that a
 * choice was already made. Seeding F2-08b under reading A would contradict a passing test.
 *
 * <h3>What this class ratchets: only what is true under BOTH readings</h3>
 * <ol>
 *   <li><b>Business↔business cycles.</b> Four bidirectional pairs involve no kernel candidate at all,
 *       so no reading excuses them. They are genuine module cycles and can be worked now.</li>
 *   <li><b>Kernel→business inversions.</b> An import <em>from</em> a kernel-ish module <em>into</em> a
 *       business module is an inversion whichever way the rule reads — "kernel imports do not count"
 *       is about business depending on kernel, never the reverse. ADR-002's "kernel boots alone" is
 *       unachievable while any of these exist.</li>
 * </ol>
 * Both are floors that may only fall. Neither pre-empts F2-34.
 */
class ModuleGraphRatchetTest {

    private static final Path MODULE_ROOT = Path.of("src/main/java/com/tss/grc/module");

    private static final Pattern MODULE_IMPORT = Pattern.compile(
        "^\\s*import\\s+(?:static\\s+)?com\\.tss\\.grc\\.module\\.([a-z_0-9]+)\\.",
        Pattern.MULTILINE);

    /**
     * Modules that are kernel-ish under <em>either</em> reading — the ones whose job is to serve every
     * other module. An import from one of these into a business module is the inversion.
     *
     * <p>{@code platform} is the disputed one, and it belongs here regardless: under reading B it is
     * literally the kernel, and under reading A it is still the module every other module depends on
     * (113 lines from {@code bcm} alone). Its direction of dependency is the question, not its role.
     */
    private static final Set<String> KERNEL_ISH = Set.of("platform", "integrations", "dashboard");

    /**
     * <b>4, measured 2026-08-06. Only ever decreases.</b>
     *
     * <p>Bidirectional pairs in which <em>neither</em> side is kernel-ish, so no reading of §2 or §6c
     * excuses them: {@code bcm↔tprm} (6/1 lines), {@code compliance↔policy} (5/2),
     * {@code privacy↔tprm} (1/1), {@code scm↔tprm} (2/2). Three of the four involve {@code tprm}.
     *
     * <p>These are the cycles F2-08b can act on today. Breaking one means routing a call through a
     * port the importing module owns — S5a's pattern, already proven once on {@code change_mgmt}.
     */
    private static final int BASELINE_BUSINESS_CYCLES = 4;

    /** Named, so a swap — one cycle broken while another appears — cannot pass on the count alone. */
    private static final Set<String> BASELINE_CYCLE_PAIRS = Set.of(
        "bcm<->tprm", "compliance<->policy", "privacy<->tprm", "scm<->tprm");

    /**
     * <b>55, measured 2026-08-06. Only ever decreases.</b>
     *
     * <p>Import lines from a kernel-ish module into a business module:
     * {@code platform}→business 38 (risk 14, iam 6, evidence 5, privacy 3, then audit_lc/capa/ndi/bcm/
     * privacy_lc 2 each), {@code integrations}→business 8 (socops 6, cyber 2),
     * {@code dashboard}→business 9 (risk 4, compliance 2, iam/incident/workflow 1 each).
     *
     * <p>⚠ <b>F2-34 recorded 40 of these at {@code ff087c7c}; there are now 55.</b> The inversion has
     * grown by 15 while the item sat blocked on a decision, which is the argument for ratcheting it
     * <em>now</em>: this number needs no reading to be wrong.
     *
     * <p>⚠ An import from {@code integrations} <em>into</em> {@code platform} is NOT counted — that is
     * business depending on kernel, which is legal under both readings. A first version of the
     * measuring script summed both directions and reported 68; a total that mixes directions measures
     * nothing.
     */
    private static final int BASELINE_KERNEL_INVERSIONS = 55;

    // ── the assertions ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Business-to-business cycles only ever decrease, and are named so a swap cannot hide")
    void businessCyclesOnlyEverDecrease() {
        Map<String, Integer> edges = edges();
        Set<String> cycles = new TreeSet<>();
        for (String e : edges.keySet()) {
            String[] p = e.split("->");
            if (KERNEL_ISH.contains(p[0]) || KERNEL_ISH.contains(p[1])) {
                continue;                                   // a reading might excuse it; not our business
            }
            if (edges.containsKey(p[1] + "->" + p[0])) {
                cycles.add(p[0].compareTo(p[1]) < 0 ? p[0] + "<->" + p[1] : p[1] + "<->" + p[0]);
            }
        }

        assertThat(cycles).as(
            "⚠ Neither side of these is kernel-ish, so NO reading of MODULE_SEPARABILITY §2 or §6c "
            + "excuses them — they are genuine module cycles and F2-08b can act on them without "
            + "waiting for F2-34. Named rather than counted so that breaking one while introducing "
            + "another cannot pass. Found: %s", cycles)
            .containsExactlyInAnyOrderElementsOf(BASELINE_CYCLE_PAIRS);
        assertThat(cycles).as("the count must only ever fall from %d", BASELINE_BUSINESS_CYCLES)
            .hasSizeLessThanOrEqualTo(BASELINE_BUSINESS_CYCLES);
    }

    @Test
    @DisplayName("Kernel-to-business inversions only ever decrease — no reading makes these legal")
    void kernelInversionsOnlyEverDecrease() {
        Map<String, Integer> edges = edges();
        int lines = 0;
        Map<String, Integer> detail = new TreeMap<>();
        for (Map.Entry<String, Integer> e : edges.entrySet()) {
            String[] p = e.getKey().split("->");
            if (KERNEL_ISH.contains(p[0]) && !KERNEL_ISH.contains(p[1])) {
                lines += e.getValue();
                detail.put(e.getKey(), e.getValue());
            }
        }

        assertThat(lines).as(
            "⚠ An import FROM a kernel-ish module INTO a business module is an inversion under every "
            + "reading — '§2: kernel imports do not count' is about business depending on kernel, never "
            + "the reverse — and ADR-002's \"kernel boots alone\" is unachievable while any exist. "
            + "F2-34 recorded 40 at ff087c7c; this must only ever fall from %d. Never raise it to "
            + "admit a new one.%n  %s", BASELINE_KERNEL_INVERSIONS, detail)
            .isLessThanOrEqualTo(BASELINE_KERNEL_INVERSIONS);
    }

    @Test
    @DisplayName("The scan cannot silently find nothing, and it agrees with the reconciliation script")
    void theScanIsNotVacuous() {
        Map<String, Integer> edges = edges();

        assertThat(edges).as("a near-empty graph would make both ratchets above vacuously green")
            .hasSizeGreaterThan(80);
        assertThat(edges.values().stream().mapToInt(Integer::intValue).sum()).as(
            "peer import lines under reading A — boundary_readings.py measured 975 at HEAD; this "
            + "counts the same population, so a large divergence means one of the two is wrong")
            .isGreaterThan(700);

        // The disputed reading-dependent totals are deliberately NOT ratcheted here. Recorded so a
        // reader can see what is being withheld and why, rather than assuming it was forgotten.
        long bidiAll = edges.keySet().stream()
            .filter(e -> edges.containsKey(e.split("->")[1] + "->" + e.split("->")[0]))
            .count() / 2;
        assertThat(bidiAll).as(
            "13 bidirectional pairs under reading A, 4 under reading B. NOT ratcheted: seeding this "
            + "number would pin whichever reading I guessed as the tolerated architecture, and "
            + "F2-34 is an owner decision. See boundary_readings.py for the priced comparison.")
            .isGreaterThanOrEqualTo(BASELINE_BUSINESS_CYCLES);
    }

    // ── evidence gathering ───────────────────────────────────────────────────

    /** {@code "src->dst"} → import lines. Comments stripped: a javadoc naming a peer is not a dependency. */
    private static Map<String, Integer> edges() {
        assertThat(Files.isDirectory(MODULE_ROOT)).as("module root %s must exist", MODULE_ROOT).isTrue();
        Set<String> modules = new TreeSet<>();
        try (Stream<Path> s = Files.list(MODULE_ROOT)) {
            s.filter(Files::isDirectory).forEach(p -> modules.add(p.getFileName().toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<String, Integer> edges = new TreeMap<>();
        for (String m : modules) {
            for (Path f : javaFiles(MODULE_ROOT.resolve(m))) {
                Matcher mt = MODULE_IMPORT.matcher(code(read(f)));
                while (mt.find()) {
                    String other = mt.group(1);
                    if (!other.equals(m) && modules.contains(other)) {
                        edges.merge(m + "->" + other, 1, Integer::sum);
                    }
                }
            }
        }
        return edges;
    }

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return new ArrayList<>(walk.filter(p -> p.toString().endsWith(".java")).sorted().toList());
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

    private static String code(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//[^\\n]*", " ");
    }
}
