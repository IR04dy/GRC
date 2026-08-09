package com.tss.grc.module.intake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-175 — {@code intake.intake_records} has exactly one writer, and peers see only the {@code spi}.
 *
 * <h3>Why this is an invariant and not a ratchet</h3>
 *
 * <p>A ceiling tolerates the population it measures. That is the right instrument when a sweep has to
 * happen in stages — and the wrong one here, because the population is <b>one</b> and the whole finding
 * is that a second member of it went unnoticed from the day it was written. CLAUDE.md records the same
 * distinction from F2-61(c): once a sweep lands, leaving the ceiling in place makes a tolerated writer
 * indistinguishable from a reviewed one. So these are total assertions with named exceptions and no
 * counts to raise.
 *
 * <h3>What each assertion is actually preventing</h3>
 *
 * <p>Not "somebody adds a writer on purpose" — that is rare and reviewable. It is the shape F2-175 came
 * in: a module needs to open an intake, the entity and the repository are both on the classpath, and
 * writing eleven setters is visibly easier than finding out what the routing catalogue is for. The
 * result compiles, works, and diverges in five places at once. There is now nothing to reach for: the
 * repository is invisible outside the module, and inside the module there is one place a record is born.
 *
 * <p>⚠ Note what could <em>not</em> have caught this, because the register filed F2-175 believing
 * otherwise: {@code ModuleGraphRatchetTest}. Its {@code KERNEL_ISH} set is {@code platform},
 * {@code integrations} and {@code dashboard}, so an {@code ai/governance → intake} edge is neither a
 * kernel inversion nor half of a business cycle — and under ADR-018 it is SKU → Base, the <em>legal</em>
 * direction. The violation was never one of dependency direction. It was encapsulation, and nothing in
 * this tree measured that until this class.
 */
class IntakeSingleWriterInvariantTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path INTAKE = MAIN.resolve("com/tss/grc/module/intake");

    /**
     * The packages a peer module may import from {@code module/intake}.
     *
     * <p>{@code spi} is the seam in both directions — {@link
     * com.tss.grc.module.intake.spi.RegisterPromotionPort} outbound (13 adapters), {@link
     * com.tss.grc.module.intake.spi.SystemIntakePort} inbound. Nothing else is anybody's business:
     * an outside module holding the entity can construct one, and holding the repository can save it.
     */
    private static final List<String> PEER_VISIBLE = List.of(
        "com.tss.grc.module.intake.spi."
    );

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException("unreadable: " + p, e);
        }
    }

    /**
     * Strip block and line comments. This class asserts on <em>code</em>, and this tree documents
     * retired mechanisms in prose at length — including, in {@code MlModelDriftService}, the exact
     * strings {@code new IntakeRecord()} and {@code IntakeRecordRepository}. A scan that reads its own
     * explanation as a violation is F2-172's dead-button guard, which passed while being false because
     * the javadoc explaining the fix contained the token it searched for.
     */
    private static String code(Path p) {
        return read(p)
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("(?m)^\\s*//.*$", "");
    }

    // ─── One construction site ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("`new IntakeRecord()` appears exactly once in main sources, in IntakeService")
    void exactlyOneConstructionSite() throws IOException {
        Map<String, Integer> sites = new TreeMap<>();
        for (Path p : javaFiles(MAIN)) {
            String src = code(p);
            int n = 0, i = 0;
            while ((i = src.indexOf("new IntakeRecord(", i)) >= 0) { n++; i++; }
            if (n > 0) sites.put(MAIN.relativize(p).toString().replace('\\', '/'), n);
        }

        assertThat(sites).as(
            "⚠ An IntakeRecord must be born in exactly one place. Every property F2-172 installed — "
            + "the catalogued reference-code prefix, the catalogued SLA, the catalogued assignee, the "
            + "TRIAGING status, and the two history events that are the record's whole audit trail — "
            + "lives at that site. A second constructor does not inherit any of them, and the one "
            + "F2-175 found got four of the five wrong plus a hand-rolled JSON escaper that made the "
            + "record unpromotable. If you need to file a submission, call IntakeService.submit or "
            + "SystemIntakePort.fileSystemSubmission. Found: %s", sites)
            .containsExactly(
                Map.entry("com/tss/grc/module/intake/service/IntakeService.java", 1));
    }

    // ─── One repository caller ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IntakeRecordRepository is used by exactly one main-source class")
    void exactlyOneRepositoryCaller() throws IOException {
        List<String> users = new ArrayList<>();
        for (Path p : javaFiles(MAIN)) {
            if (p.getFileName().toString().equals("IntakeRecordRepository.java")) continue;
            if (code(p).contains("IntakeRecordRepository")) {
                users.add(MAIN.relativize(p).toString().replace('\\', '/'));
            }
        }

        assertThat(users).as(
            "⚠ Holding the repository is what let another module write this table directly. "
            + "MlModelDriftService did, from 2026 until F2-175, and consulted none of intake's rules. "
            + "Found: %s", users)
            .containsExactly("com/tss/grc/module/intake/service/IntakeService.java");
    }

    // ─── Peers see the spi and nothing else ──────────────────────────────────────────────────────

    @Test
    @DisplayName("no module outside intake imports anything of it but the spi")
    void peersImportOnlyTheSpi() throws IOException {
        Map<String, List<String>> offenders = new TreeMap<>();
        for (Path p : javaFiles(MAIN)) {
            if (p.normalize().startsWith(INTAKE.normalize())) continue;      // intake may use itself
            List<String> bad = code(p).lines()
                .map(String::strip)
                .filter(l -> l.startsWith("import com.tss.grc.module.intake"))
                .map(l -> l.substring("import ".length()).replace(";", ""))
                .filter(imp -> PEER_VISIBLE.stream().noneMatch(imp::startsWith))
                .toList();
            if (!bad.isEmpty()) offenders.put(MAIN.relativize(p).toString().replace('\\', '/'), bad);
        }

        assertThat(offenders).as(
            "⚠ module/intake is Base under ADR-018 and its internals are not a peer's to touch. The "
            + "seam is the spi package, in both directions: RegisterPromotionPort outbound (a target "
            + "register creating a promoted record), SystemIntakePort inbound (a mechanism filing a "
            + "submission). Importing the entity lets you construct a record; importing the "
            + "repository lets you save one. Both are how F2-175 happened. Found: %s", offenders)
            .isEmpty();
    }

    // ─── Anti-vacuity ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the scan reaches real sources and the spi seam is genuinely used in both directions")
    void theScanIsNotVacuous() throws IOException {
        List<Path> all = javaFiles(MAIN);
        // 1,869 main sources measured 2026-08-10. A floor, not a count: the three invariants above are
        // all "this set is empty" or "this set is exactly one", and every one of them passes vacuously
        // if the walk finds nothing — which is what a wrong relative path produces, silently.
        assertThat(all).as("the main source tree must be where this test thinks it is")
            .hasSizeGreaterThan(1_500);

        // If PEER_VISIBLE were the reason peersImportOnlyTheSpi passes, it would pass over an empty
        // population too. Both halves of the seam must actually be imported from outside the module —
        // 13 promotion adapters, and at least one system-intake caller.
        int outbound = 0, inbound = 0;
        for (Path p : all) {
            if (p.normalize().startsWith(INTAKE.normalize())) continue;
            String src = code(p);
            if (src.contains("import com.tss.grc.module.intake.spi.RegisterPromotionPort;")) outbound++;
            if (src.contains("import com.tss.grc.module.intake.spi.SystemIntakePort;")) inbound++;
        }
        assertThat(outbound).as("promotion adapters outside module/intake").isGreaterThanOrEqualTo(13);
        assertThat(inbound).as(
            "⚠ At least one peer must file system intake through the port, or this whole class is "
            + "asserting a property of a seam nobody uses — and the next CRITICAL drift goes back to "
            + "building the entity by hand because that is the visible option.")
            .isGreaterThanOrEqualTo(1);

        // And the comment stripper must be doing something: MlModelDriftService's javadoc names both
        // retired tokens on purpose, so a scan that read raw text would fail the two tests above.
        Path drift = MAIN.resolve("com/tss/grc/module/ai/governance/service/MlModelDriftService.java");
        assertThat(read(drift)).as("the retired mechanism is documented there on purpose")
            .contains("IntakeRecordRepository");
        assertThat(code(drift)).as(
            "…and the stripper must remove it, or these invariants would be measuring prose")
            .doesNotContain("IntakeRecordRepository");
    }
}
