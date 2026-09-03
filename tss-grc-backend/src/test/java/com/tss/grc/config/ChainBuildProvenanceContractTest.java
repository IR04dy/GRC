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
    private static final String READER = "schema_convergence.py";
    private static final String CENSUS = "mojibake_census.py";


    /**
     * Each builder, and <b>how many paths in it can leave a database behind</b>.
     *
     * <p>⚠⚠ Counted, not merely present, and the control harness is why: {@code chain_proof.py}
     * stamps on TWO paths — the completed chain, and the {@code --keep} failure path that leaves a
     * PARTIAL build for the reader to find — so reverting either one leaves a
     * {@code contains("chain_marker.stamp(")} satisfied by the other. That mutation
     * <b>SURVIVED</b>, and the mutated code was genuinely wrong: a completed chain would no longer
     * record its head. F2-194's substring-satisfied-by-something-else, in a leg written after
     * quoting it.
     *
     * <p>🔑 The failure path is the one that matters most, because a database left behind by an
     * ABORTED run is exactly the artefact the reader will find.
     */
    private static final List<String[]> BUILDERS = List.of(
        new String[] { "chain_proof.py", "2" },
        new String[] { "full_chain_proof.py", "1" });

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

        for (String[] entry : BUILDERS) {
            String builder = entry[0];
            int expected = Integer.parseInt(entry[1]);
            String src = read(builder);
            assertThat(src.split("chain_marker\\.stamp\\(", -1).length - 1)
                .as("""
                    %s builds a scratch database and DROPS it on success, so the only database the
                    reader can ever find is one a --keep or an aborted run left behind. EVERY path
                    that can leave one must record the head it reached, or the reader is guessing —
                    and a bare `contains` is satisfied by whichever path survives, which is how the
                    control mutation on this leg first SURVIVED.""", builder)
                .isEqualTo(expected);
            assertThat(src)
                .as("%s must import the shared marker rather than declaring a second one (F2-190)",
                    builder)
                .contains("import chain_marker");
        }
        assertThat(read("chain_proof.py"))
            .as("""
                and the aborted path must stamp complete=False — a partial build recorded as
                complete is worse than one recorded as unknown, because the reader would then
                attribute every later object to a defect with confidence.""")
            .contains("complete=False");
    }

    @Test
    void theReaderDerivesTheHeadFromTheMarkerAndSaysWhenTheBuildIsStale() throws IOException {
        String src = read(READER);
        assertThat(src.split("chain_marker\\.read\\(", -1).length - 1)
            .as("""
                TWO call sites, and they answer different questions: stop_point() reads the marker
                to DERIVE the stop, and main() reads it to PRINT the provenance. Counted rather
                than merely present because reverting either one left a `contains` satisfied by the
                other, and that mutation SURVIVED — the mutated code being genuinely wrong, since
                the report would then claim the provenance is unknown while still using the head.

                ⚠ It must not INFER the head: guessing it from the highest-numbered object present
                makes the caveat depend on the same migrations it qualifies, and that reasoning is
                preserved verbatim in stop_point()'s docstring.""")
            .isEqualTo(2);
        assertThat(src)
            .as("and the provenance line must be built by the shared describe(), never re-worded"
                + " locally — one declaration, three readers (F2-190)")
            .contains("chain_marker.describe(");
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
    void theConvergenceReaderComparesIndexesAndDefaultsByShapeRatherThanByName() throws IOException {
        String src = read(READER);
        assertThat(src)
            .as("""
                F2-09's row named the INDEX axis as still open and EXCLUDED the DEFAULT axis with a
                stated reason \u2014 "1,910 of dev's 2,259 are system-named, so a name comparison is
                noise". F2-445 added both, keyed on SHAPE.""")
            .contains("def report_indexes(", "def report_defaults(",
                      "def index_shapes(", "def default_shapes(");

        // \u26a0\u26a0 The load-bearing property: SQL Server auto-names a primary key with a
        //    per-database random suffix, so a NAME-keyed diff reports the same PK on the same table
        //    as BOTH missing and extra. Measured: name-keyed 393 missing / 400 extra, shape-keyed
        //    14 / 21 \u2014 96% of a name-keyed answer is noise. The shape must therefore carry the
        //    key columns, the uniqueness and the filter, and the NAME must not be part of it.
        String shapes = body(src, "def index_shapes(");
        assertThat(shapes)
            .as("the index shape must carry what the index ENFORCES, not what it is called")
            .contains("bool(uq)", "bool(pk)", "cols", "filt");
        assertThat(shapes)
            .as("""
                and the name must be a VALUE of the map, never part of its key \u2014 the whole
                point of the axis. `out.setdefault(key, []).append(name)` keeps the names for
                reporting while keying on the shape.""")
            .contains("out.setdefault(key, []).append(name)");

        assertThat(body(src, "def default_shapes("))
            .as("a DEFAULT's identity is (table, column, definition); its name is system-generated"
                + " for 1,910 of dev's 2,259 and carries no information")
            .doesNotContain("d.name");

        assertThat(src)
            .as("""
                and the reader must say what it means when one column appears on BOTH sides: the
                definition DIFFERS rather than being absent. That is how F2-445 found
                bcm.bcp_binder_export.watermark_text, where the difference was ENCODING.""")
            .contains("BOTH missing and extra");
    }

    @Test
    void theMojibakeCensusTestsPerRunAndNamesWhatMustNotBeRepaired() throws IOException {
        String src = read(CENSUS);

        // \u26a0\u26a0 PER RUN, not per value. A value holding correct Arabic AND mojibake cannot
        //    encode to cp1252 at all, so a whole-value round-trip reports it CLEAN \u2014 the quiet
        //    direction. My own first sweep of this corpus reported 3 corrupted cells where the
        //    per-run test finds 16, and read rule 174's four corrupted columns as one.
        assertThat(src)
            .as("the detector must be a ROUND-TRIP, never a blacklist of suspicious sequences \u2014"
                + " a blacklist flags legitimate text and misses novel corruption")
            .contains("encode(\"cp1252\"", "decode(\"utf-8\"");
        assertThat(body(src, "def bad_runs("))
            .as("""
                and it must walk maximal non-ASCII RUNS rather than testing the whole value. This
                is the one property the 2026-06-08 repair's successor cannot afford to lose: a
                mixed value reads as clean under a whole-value test.""")
            .contains("for ch in", "cur.append(ch)");

        assertThat(src)
            .as("""
                platform.audit_log is hash-chained (F2-31), so its corrupted messages may NOT be
                repaired \u2014 repairing a sealed row breaks the chain (F2-434). The census must
                report them and say so, because an operator needs to know the corruption reached
                the ledger even though nothing may touch it.""")
            .contains("UNREPAIRABLE", "platform.audit_log.message", "hash-chained");
        assertThat(src)
            .as("""
                and it must name the PRODUCER columns \u2014 a corrupted template regenerates
                corrupted rows, so repairing its output first is repairing the symptom. Rule 174's
                action_json produced three corrupted findings AFTER the 2026-06-08 repair.""")
            .contains("PRODUCER_COLUMNS", "platform.automation_rules.action_json");
        assertThat(src)
            .as("read-only: every write would land in a customer database (house rule 6)")
            .doesNotContain("UPDATE ", "DELETE ");
    }

    /**
     * One Python function's body: from its {@code def} line to the next line at column 0 that is
     * not blank. Indentation-delimited, never a fixed character window (F2-424).
     */
    private static String body(String src, String def) {
        int i = src.indexOf(def);
        assertThat(i).as("%s must exist", def).isGreaterThanOrEqualTo(0);
        String[] lines = src.substring(i).split("\r?\n", -1);
        StringBuilder out = new StringBuilder(lines[0]).append('\n');
        for (int j = 1; j < lines.length; j++) {
            if (!lines[j].isBlank() && !Character.isWhitespace(lines[j].charAt(0))) break;
            out.append(lines[j]).append('\n');
        }
        return out.toString();
    }

    @Test
    void theScriptsWereActuallyRead() throws IOException {
        // Derived floors, never arithmetic: F2-438 asserted a guessed bound and failed on a correct
        // tree, and F2-443 did it again one slice later.
        assertThat(read(MARKER).length()).as("chain_marker.py").isGreaterThan(2_000);
        for (String[] b : BUILDERS) {
            assertThat(read(b[0]).length()).as(b[0]).isGreaterThan(3_000);
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
