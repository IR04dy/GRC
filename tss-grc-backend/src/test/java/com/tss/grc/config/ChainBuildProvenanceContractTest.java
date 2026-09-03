package com.tss.grc.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-444 — a builder/reader pair coupled by a database name, and the reader could not ask which
 * head it was looking at.
 *
 * <h3>What went wrong</h3>
 *
 * {@code Appendix/scripts} holds three instruments for one question:
 * {@code chain_proof.py} and {@code full_chain_proof.py} <b>build</b> a scratch database by applying
 * V1 → head in Flyway's order; {@code schema_convergence.py} <b>reads</b> that database and diffs it
 * against the Python-bootstrapped dev schema (F2-09). They were coupled by nothing but the database
 * name — and <b>both builders DROP the database on a successful run</b>, so the only database the
 * reader can ever find is one left behind by an aborted run or by {@code --keep}.
 *
 * <p>Measured 2026-09-03: the reader was run and reported, against a scratch database built
 * <b>twenty-seven days earlier at V372</b>, <b>28 CHECK constraints, 7 foreign keys and 18
 * columns</b> as missing from a fresh install — naming V375/V376/V377 and V387/V388/V389, the very
 * migrations this programme's own recent slices had written. Rebuilt at V398 the honest answer is
 * <b>0 columns and 0 in the defect bucket</b>. Every one of the 28 was merely newer than the build.
 *
 * <h3>🔑 The reader's own second line said so, and one partition asserted the opposite</h3>
 *
 * It printed <em>"could not determine the chain's stopping point"</em>, and its TABLE and COLUMN
 * partitions each printed an explicit caveat — <em>"⚠ NO STOP GIVEN, so this cannot be read as a
 * defect"</em>. Its CONSTRAINT partition instead asserted a conclusion:
 *
 * <blockquote>The migration ran and the constraint is not there.</blockquote>
 *
 * That is only true if the build REACHED the migration, which the reader cannot know. <b>One script,
 * three partitions, two honest</b> — and the one that asserted is the one that produced the phantom
 * findings. F2-184's shape (a heading that answers the question a reader was about to ask) at the
 * level of a report, and F2-215's two-of-N with the correct treatment visible twice over.
 *
 * <h3>What this asserts</h3>
 *
 * <ol>
 *   <li>Both builders call {@code chain_marker.stamp} — so a build records the head it REACHED,
 *       which on an aborted run is the last version applied cleanly.</li>
 *   <li>The reader calls {@code chain_marker.read} and prints a stale banner — so the stop is a
 *       <b>measurement</b> rather than a flag an operator has to remember. ⚠ Reading a recorded head
 *       is not <em>inferring</em> one: the reader's refusal to guess the stop from the
 *       highest-numbered object present was always correct and is unchanged.</li>
 *   <li>No partition asserts <em>"The migration ran"</em> outside a branch guarded by a known stop.
 *       <b>This is the load-bearing leg</b>: the coupling can be restored and the prose reverted
 *       independently, and it was the prose that turned a stale build into 28 findings.</li>
 * </ol>
 *
 * <p>Hermetic: reads four files off disk. These scripts have no test coverage of any kind otherwise,
 * which is not the same as somebody having decided they were safe (F2-248) — and this is the same
 * cross-tree instrument shape {@code ConnectorBackingContractTest} (F2-193) and
 * {@code SelfReportingControlReaderContractTest} (F2-437) already use.
 */
class ChainBuildProvenanceContractTest {

    private static final Path SCRIPTS =
        Path.of("..", "TSS-GRC-Platform-Bible", "Appendix", "scripts");

    private static final String MARKER = "chain_marker.py";
    private static final List<String> BUILDERS = List.of("chain_proof.py", "full_chain_proof.py");
    private static final String READER = "schema_convergence.py";

    /**
     * Prose that asserts the build reached a migration. Each must sit inside a branch that knows the
     * stop. Held as text on purpose: the claim is about what a human reads in the report, and no
     * structural property of the code can express it (F2-205's shape — a claim about text lives
     * where text lives).
     */
    private static final List<String> ASSERTS_THE_MIGRATION_RAN = List.of(
        "The migration ran and the constraint is not there");

    private static String read(String name) throws IOException {
        Path p = SCRIPTS.resolve(name);
        assertThat(Files.exists(p)).as("%s must exist at %s", name, p.toAbsolutePath()).isTrue();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    // ─────────────────────────── the detector ───────────────────────────

    /**
     * Every line holding one of the assertive phrases whose <b>enclosing</b> {@code if} does not
     * mention a known stop.
     *
     * <p>⚠ The enclosing conditional is found by walking up to the first line with strictly smaller
     * indentation that starts with {@code if} — never a fixed character window, because F2-424
     * measured that a javadoc of more than 300 characters silently pushes an annotation out of one
     * and the population then reads as clean.
     */
    static List<String> unguardedAssertions(String source, List<String> phrases) {
        List<String> out = new ArrayList<>();
        String[] lines = source.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            boolean hit = false;
            for (String phrase : phrases) {
                if (lines[i].contains(phrase)) { hit = true; break; }
            }
            if (!hit) continue;
            int indent = indentOf(lines[i]);
            String guard = null;
            for (int j = i - 1; j >= 0; j--) {
                if (lines[j].isBlank()) continue;
                int ind = indentOf(lines[j]);
                if (ind >= indent) continue;
                String t = lines[j].strip();
                if (t.startsWith("if ") || t.startsWith("elif ")) { guard = t; }
                break;
            }
            if (guard == null || !(guard.contains("stop is not None") || guard.contains("stop:"))) {
                out.add("line " + (i + 1) + ": guarded by " + (guard == null ? "NOTHING" : guard));
            }
        }
        return out;
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) n++;
        return n;
    }

    // ─────────────────────────── the legs ───────────────────────────

    @Test
    void everyChainBuilderStampsTheHeadItReached() throws IOException {
        String marker = read(MARKER);
        assertThat(marker)
            .as("the shared marker must declare stamp/read/describe — one declaration, three readers")
            .contains("def stamp(", "def read(", "def describe(", "def folder_head(");

        for (String builder : BUILDERS) {
            String src = read(builder);
            assertThat(src)
                .as("""
                    %s builds a scratch database and DROPS it on success, so the only database the
                    reader can ever find is one a --keep or an aborted run left behind. It must
                    record the head it reached, or the reader is guessing.""", builder)
                .contains("chain_marker.stamp(");
            assertThat(src)
                .as("%s must import the shared marker rather than declaring a second one (F2-190)",
                    builder)
                .contains("import chain_marker");
        }
    }

    @Test
    void theReaderDerivesTheHeadFromTheMarkerAndSaysWhenTheBuildIsStale() throws IOException {
        String src = read(READER);
        assertThat(src)
            .as("""
                The reader must READ the recorded head rather than requiring --stop. ⚠ It must not
                INFER one: guessing the stop from the highest-numbered object present makes the
                caveat depend on the same migrations it qualifies, and that reasoning is preserved
                verbatim in stop_point()'s docstring.""")
            .contains("chain_marker.read(", "chain_marker.describe(");
        assertThat(read(MARKER))
            .as("and the stale banner has to name both heads, or 'stale' is not actionable")
            .contains("STALE BUILD", "folder is %d version(s) ahead");
    }

    @Test
    void noPartitionAssertsThatTheMigrationRanWithoutKnowingTheStop() throws IOException {
        assertThat(unguardedAssertions(read(READER), ASSERTS_THE_MIGRATION_RAN))
            .as("""
                A partition may not assert *"The migration ran and the constraint is not there"*
                unless the build's head is known — that sentence, printed against a
                twenty-seven-day-old build, is what turned 28 newer constraints into 28 phantom
                findings. The TABLE and COLUMN partitions have always printed the caveat instead;
                this is the one that did not.

                Fix the prose, not this assertion.""")
            .isEmpty();
        assertThat(read(READER))
            .as("and the caveat must still be there for the case where no stop is known")
            .contains("NO STOP KNOWN");
    }

    @Test
    void theDetectorFiresOnTheShapeItClaimsToCatch() {
        String guarded = String.join("\n",
            "        if skipped and stop is not None:",
            "            print(\"The migration ran and the constraint is not there. blah\")");
        String bare = String.join("\n",
            "        if skipped:",
            "            print(\"The migration ran and the constraint is not there. blah\")");
        String none = "        print(\"The migration ran and the constraint is not there.\")";
        String elsewhere = String.join("\n",
            "        if stop:",
            "            print(\"The migration ran and the constraint is not there.\")");

        assertThat(unguardedAssertions(guarded, ASSERTS_THE_MIGRATION_RAN))
            .as("a stop-guarded assertion is legitimate and must NOT be flagged — a gate that"
                + " fails on correct code trains the next reader to edit the assertion (F2-214)")
            .isEmpty();
        assertThat(unguardedAssertions(elsewhere, ASSERTS_THE_MIGRATION_RAN))
            .as("the truthy form of the same guard is equally legitimate").isEmpty();
        assertThat(unguardedAssertions(bare, ASSERTS_THE_MIGRATION_RAN))
            .as("an assertion guarded by something OTHER than the stop must be flagged")
            .hasSize(1);
        assertThat(unguardedAssertions(none, ASSERTS_THE_MIGRATION_RAN))
            .as("""
                An unconditional assertion must be flagged. This is the literal pre-fix shape, and
                the leg above passes on a clean corpus whether the detector works or not — an
                empty list is what a working detector and a dead one both produce (F2-235).""")
            .hasSize(1);
    }

    @Test
    void theScriptsWereActuallyRead() throws IOException {
        // Derived floors, never arithmetic: F2-438 asserted a guessed bound and failed on a correct
        // tree, and F2-443 did it again one slice later.
        assertThat(read(MARKER).length()).as("chain_marker.py").isGreaterThan(2_000);
        for (String b : BUILDERS) {
            assertThat(read(b).length()).as(b).isGreaterThan(3_000);
        }
        String reader = read(READER);
        assertThat(reader.length()).as(READER).isGreaterThan(15_000);
        assertThat(reader.split("NO STOP", -1).length - 1)
            .as("""
                The reader has three partitions (tables, columns, constraints) and each must carry
                the caveat for the no-stop case. Two of three carried it before F2-444; a count of
                three is what stops one of them losing it again.""")
            .isGreaterThanOrEqualTo(3);
    }
}
