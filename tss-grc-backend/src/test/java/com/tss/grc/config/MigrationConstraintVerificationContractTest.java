package com.tss.grc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.tss.grc.support.MigrationVocabularies;

/**
 * F2-461 — no migration may add or leave a constraint that is not verified against the rows
 * already in the table.
 *
 * <p>{@code WITH NOCHECK} leaves {@code is_not_trusted = 1}. Such a constraint is ENABLED and
 * enforces every new INSERT and UPDATE; what it has never done is verify the rows that were
 * already there. And in any listing that checks only a constraint's NAME it is
 * <em>indistinguishable from a verified one</em> — V399's own self-check says so in as many words:
 * "a key that is present and not enforcing is worse than an absent one, because it answers the
 * question a reader was about to ask."
 *
 * <h2>Why this gate rather than the eighteen paragraphs that already say it</h2>
 *
 * Measured over all 388 migrations: <strong>19 mention {@code NOCHECK} and exactly ONE does
 * it</strong> — 11 state the rule in a comment, 7 state it inside a {@code THROW} message
 * literal so an operator reads it at failure time (V364, V365, V375, V392, V393, V394, and
 * V400 itself), and V265 is the only file that uses it. Several of the eighteen also carry a
 * {@code THROW} self-check asserting {@code is_not_trusted = 0} — and every one of those is
 * scoped to <em>the constraints that migration itself creates</em>.
 *
 * <p>⚠ That breakdown is a measurement. An earlier reading of this finding said "sixteen",
 * which was a {@code grep -l} count of files containing the token — F2-273's shape, a copy of
 * a measurement rather than the measurement.
 *
 * <p>🔑 So the rule was stated eighteen times and gated per-migration, which leaves the population
 * that matters — every constraint in the chain — belonging to nobody. F2-198's shape at a
 * migration's own scope, and F2-215's two-of-N at eighteen: a reader who finds the discipline
 * asserted in the file they are editing has no reason to ask whether an older file lacks it.
 *
 * <p>Measured over both live databases before this gate was written: exactly TWO constraints were
 * untrusted, both on {@code cyber.cyber_evidence}, both from V265 — the only {@code WITH NOCHECK}
 * in the whole 386-migration chain. V400 verifies them; this gate stops a seventeenth paragraph
 * being the only thing standing between the next author and a repeat.
 *
 * <h2>Why the scan must strip comments, and why that is not incidental</h2>
 *
 * ⚠⚠ Those eighteen paragraphs contain the literal text this gate forbids, and so does V400's own
 * header, and so does this class's javadoc. A raw text scan flags all of them — F2-193's
 * epitaph-as-corpse, at the scale of the whole convention. The corpus is therefore read through
 * {@link MigrationVocabularies#stripComments}, the platform's one SQL comment stripper (one
 * left-to-right alternation, blanking rather than deleting so line structure survives), and the
 * self-test below asserts <em>both</em> directions: the detector must fire on a real statement and
 * must not fire on the same words inside either comment form.
 *
 * <h2>Why V265 is exempt permanently rather than corrected</h2>
 *
 * An applied migration may never be edited: Flyway stores a checksum, so a changed V265 fails
 * validation on the deployed server and blocks every later migration. The exemption is enforced in
 * BOTH directions — V265 must still contain the shape — so it cannot rot into a claim about a file
 * that no longer has it (F2-199: the direction nobody writes first).
 *
 * <p>DB-free: it reads the migration SQL, exactly as {@code MigrationVocabularies} does, so
 * {@code mvn -o clean package} stays hermetic.
 */
class MigrationConstraintVerificationContractTest {

    /**
     * The three ways a migration can leave a constraint unverified.
     *
     * <p>{@code WITH NOCHECK} on an ADD skips validation of the existing rows; {@code NOCHECK
     * CONSTRAINT} disables one outright; and {@code CHECK_CONSTRAINTS} left off in a bulk-load
     * option list loads rows past every constraint on the table. All three leave the object in the
     * catalogue looking live.
     */
    /**
     * A T-SQL string literal, with the doubled-quote escape. Blanked before the scan, because
     * a token inside one is DATA and never code.
     *
     * <p>⚠⚠ This gate's first run flagged SEVEN migrations and every hit was inside a
     * {@code THROW} message literal -- six existing files plus V400's own three. So the files
     * it accused were precisely the ones that state the rule most carefully, by putting it
     * where an operator reads it at failure time: <em>"Fix the data, do not add WITH
     * NOCHECK."</em> 🔑 A gate that fails on the most disciplined files in the corpus is the
     * sharpest form of failing on correct code (F2-214), and stripping comments alone was not
     * enough to avoid it. F2-454 recorded this pattern for a colon inside a literal, and
     * F2-460's gate used it one slice ago for the same reason.
     *
     * <p>⚠⚠ And it may NOT be a regex here. Borrowing F2-454's
     * {@code '(?:[^']|'')*'} threw {@link StackOverflowError} over a 300-line migration: an
     * alternation inside {@code *} recurses once per character consumed on the JVM. F2-418
     * recorded that exact pattern and F2-446 recorded it again -- there is no safe regex for
     * one over whole-file input. 🔑 F2-454 uses it safely because it applies it to individual
     * {@code @Query} values, which are short: the difference is the INPUT SIZE rather than the
     * pattern, which is exactly why borrowing it looked sound.
     */
    static String blankSqlLiterals(String sql) {
        char[] out = sql.toCharArray();
        int i = 0;
        while (i < out.length) {
            if (out[i] != '\'') {
                i++;
                continue;
            }
            out[i++] = ' ';
            while (i < out.length) {
                if (out[i] == '\'') {
                    // A quote ends the literal. The doubled-quote escape needs no branch of
                    // its own: breaking to the outer loop leaves it looking at the second
                    // quote, which it consumes and re-enters here -- two characters either
                    // way, ending inside the literal either way. A branch that handled the
                    // escape explicitly stood here and its mutation could not be killed,
                    // because there is no input on which the two answers differ. It is gone:
                    // a branch that cannot change an answer tells a reader that something
                    // subtle is being handled (F2-206).
                    out[i++] = ' ';
                    break;
                }
                // Newlines are preserved so line structure survives, as the comment stripper
                // does -- a blanked span must not change where a line ends.
                if (out[i] != '\n') out[i] = ' ';
                i++;
            }
        }
        return new String(out);
    }

    private static final Map<String, Pattern> DISABLING = new LinkedHashMap<>();

    static {
        DISABLING.put("WITH NOCHECK", Pattern.compile("WITH\\s+NOCHECK", Pattern.CASE_INSENSITIVE));
        DISABLING.put("NOCHECK CONSTRAINT",
            Pattern.compile("NOCHECK\\s+CONSTRAINT", Pattern.CASE_INSENSITIVE));
        DISABLING.put("CHECK_CONSTRAINTS off",
            Pattern.compile("CHECK_CONSTRAINTS\\s*(?:=\\s*)?OFF", Pattern.CASE_INSENSITIVE));
    }

    /**
     * The one grandfathered file, with the reason it can never be corrected.
     *
     * <p>Enforced in both directions by {@link #theGrandfatheredMigrationStillHasTheShape()}.
     */
    private static final Map<String, String> GRANDFATHERED = Map.of(
        "V265__cyber_evidence_fk_status_constraints.sql",
        """
        The only WITH NOCHECK in the chain, and it may never be edited: Flyway stores a checksum, \
        so a changed V265 fails validation on the deployed server and blocks every later \
        migration. Its own comment explains itself — "existing rows that predate CY-H are \
        grandfathered" — and that is no longer true: measured in both live databases, all 12 \
        evidence rows carry an in-vocabulary status with canonical casing and no dangling \
        control_id. V400 re-verifies both constraints in place with WITH CHECK CHECK CONSTRAINT, \
        which validates the existing rows without dropping, adding or widening anything.""");

    /**
     * Extracted so {@link #theDetectorFiresOnTheShapesItClaims()} can drive it over synthetic
     * input: on a clean corpus the hard zero below asserts an empty list, and an empty list is
     * what a correct detector and a dead one both produce (F2-235).
     */
    static List<String> disablingShapesIn(String sql) {
        String code = blankSqlLiterals(MigrationVocabularies.stripComments(sql));
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : DISABLING.entrySet()) {
            Matcher m = e.getValue().matcher(code);
            int n = 0;
            while (m.find()) n++;
            if (n > 0) hits.add(e.getKey() + " x" + n);
        }
        return hits;
    }

    @Test
    void noMigrationLeavesAConstraintUnverified() {
        List<String> offenders = new ArrayList<>();
        for (Path f : MigrationVocabularies.migrationFiles()) {
            String name = f.getFileName().toString();
            List<String> hits = disablingShapesIn(MigrationVocabularies.read(f));
            if (hits.isEmpty() || GRANDFATHERED.containsKey(name)) continue;
            offenders.add(name + "   " + String.join(", ", hits));
        }
        assertThat(offenders)
            .as("""
                A constraint added `WITH NOCHECK`, or disabled with `NOCHECK CONSTRAINT`, is
                present in every listing and enforcing nothing about the rows already stored — and
                it is indistinguishable from a verified one unless somebody thinks to read
                `sys.check_constraints.is_not_trusted`.

                The chain states this rule in eighteen separate migrations and gates it in several,
                always over the constraints that migration itself creates — so the population that
                matters belonged to nobody, and V265's two sat untrusted in both live databases
                until F2-461.

                If the existing rows genuinely violate the constraint, fix the DATA: add the
                constraint in a later migration after a repair, or narrow it. A pre-flight that
                NAMES the offending rows is the house pattern (V399, V400) — a bare "the ALTER
                TABLE statement conflicted" sends the next reader nowhere and a failing migration
                blocks every later one.""")
            .isEmpty();
    }

    @Test
    void theGrandfatheredMigrationStillHasTheShape() {
        for (Map.Entry<String, String> e : GRANDFATHERED.entrySet()) {
            Path f = MigrationVocabularies.migrationFiles().stream()
                .filter(p -> p.getFileName().toString().equals(e.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "The grandfathered migration " + e.getKey() + " is not in the corpus. Either "
                    + "it was renamed — which is a Flyway checksum failure on the deployed server "
                    + "— or this exemption is now a claim about a file that does not exist."));

            assertThat(disablingShapesIn(MigrationVocabularies.read(f)))
                .as("""
                    %s is exempt, so it must still HAVE the shape. An exemption for something that
                    is no longer there reads exactly like an exemption for something that is
                    (F2-199), and it would silently permit the next author to add a WITH NOCHECK to
                    this file believing it was already allowed.

                    Reason on record:
                    %s""".formatted(e.getKey(), e.getValue()))
                .isNotEmpty();

            assertThat(e.getValue().length())
                .as("the exemption's reason must say why, not merely that it is allowed (F2-188)")
                .isGreaterThan(200);
        }
    }

    @Test
    void theCorpusIsMeasured() {
        List<Path> files = MigrationVocabularies.migrationFiles();

        // An anti-vacuity floor, because a hard zero over a population the instrument cannot see
        // is the most reassuring possible artefact (F2-205). The number sits above the largest
        // WRONG answer rather than above zero (F2-215): a walk that resolves nothing gives 0, and
        // a filter that stops matching `.sql` gives 0 too.
        assertThat(files)
            .as("migration files found. If this collapses, the hard zero above is passing over "
                + "nothing rather than over a clean corpus.")
            .hasSizeGreaterThan(300);

        // V400 is what makes the hard zero true today, so its presence is part of the claim: the
        // gate stops a repeat and that file is what fixed the instance.
        assertThat(files.stream().map(p -> p.getFileName().toString()))
            .as("V400 verifies V265's two constraints in place; without it this gate would be a "
                + "rule with a live counter-example in the database it describes.")
            .anyMatch(n -> n.startsWith("V400__"));
    }

    @Test
    void theDetectorFiresOnTheShapesItClaims() {
        // MUST flag: each of the three shapes, and the literal statement V265 uses.
        assertThat(disablingShapesIn(
            "ALTER TABLE cyber.cyber_evidence WITH NOCHECK\n"
            + "    ADD CONSTRAINT ck_x CHECK (status IN ('A'));"))
            .as("the literal shape V265 uses")
            .containsExactly("WITH NOCHECK x1");

        assertThat(disablingShapesIn("ALTER TABLE t NOCHECK CONSTRAINT ALL;"))
            .containsExactly("NOCHECK CONSTRAINT x1");

        assertThat(disablingShapesIn("BULK INSERT t FROM 'f' WITH (CHECK_CONSTRAINTS = OFF);"))
            .containsExactly("CHECK_CONSTRAINTS off x1");

        // Case and whitespace are not the contract.
        assertThat(disablingShapesIn("alter table t with   nocheck add constraint c check (1=1);"))
            .containsExactly("WITH NOCHECK x1");

        // MUST NOT flag: the same words in either comment form. This is the load-bearing half —
        // eighteen migrations, V400's header and this class's own javadoc all contain them, so a
        // raw scan flags the whole convention (F2-193).
        assertThat(disablingShapesIn("-- No WITH NOCHECK anywhere; SQL Server verifies the rows.\n"
            + "ALTER TABLE t ADD CONSTRAINT c CHECK (x IN ('A'));"))
            .as("a line comment stating the rule is not a violation of it")
            .isEmpty();

        assertThat(disablingShapesIn("/* WITH NOCHECK would leave the constraint unverified,\n"
            + "   so this file does not use it. */\n"
            + "ALTER TABLE t ADD CONSTRAINT c CHECK (x IN ('A'));"))
            .as("a block comment stating the rule is not a violation of it")
            .isEmpty();

        // And the mirror of that: stripping a comment must not blank the statement beside it.
        assertThat(disablingShapesIn("/* explaining the rule */ ALTER TABLE t WITH NOCHECK "
            + "ADD CONSTRAINT c CHECK (1=1);"))
            .as("blanking a comment must not blank the statement beside it")
            .containsExactly("WITH NOCHECK x1");

        assertThat(disablingShapesIn("ALTER TABLE t ADD CONSTRAINT c CHECK (x IN ('A'));"))
            .as("an ordinary ADD CONSTRAINT is not a violation")
            .isEmpty();

        // MUST NOT flag: the rule quoted inside a THROW message. This is what the gate's first
        // run got wrong, and it accused the six migrations that state the rule most carefully
        // plus V400 itself -- the text below is V364's, verbatim.
        assertThat(disablingShapesIn(
            "THROW 51364, N'in the table were never verified against it. "
            + "Fix the data, do not add WITH NOCHECK.', 1;"))
            .as("a THROW message stating the rule is not a violation of it")
            .isEmpty();

        // The doubled-quote escape, in BOTH directions. 111 of the 388 migrations contain a
        // doubled single quote, so this is reachable from the corpus rather than theoretical.
        //
        // ⚠ The case that stood here could not tell a correct scanner from one that ignores
        // the escape: it put the forbidden phrase inside the literal AND a real statement after
        // it, so the correct scanner found the real one and a broken scanner found the leaked
        // one -- both exactly one hit, from different text. A count is what made it look
        // complete. Each case below is arranged so the two scanners give DIFFERENT answers.
        assertThat(disablingShapesIn(
            "THROW 1, N'the owner''s rows are already verified', 1;\n"
            + "ALTER TABLE t WITH NOCHECK ADD CONSTRAINT c CHECK (1=1);"))
            .as("""
                A doubled quote must not end the literal: ignoring the escape re-enters a
                literal at the closing quote and blanks everything after it, so the real
                WITH NOCHECK on the next line disappears. That is the quiet direction -- an
                under-report reads exactly like a clean corpus.""")
            .containsExactly("WITH NOCHECK x1");

        assertThat(disablingShapesIn(
            "THROW 1, N'don''t add WITH NOCHECK; fix the data instead', 1;"))
            .as("""
                And the mirror: ignoring the escape leaks the rest of the literal out as code,
                so the phrase the message is warning against reads as a use of it. That is the
                loud direction, and it accuses the file that states the rule most carefully.""")
            .isEmpty();
    }

    @Test
    void theRuleTheChainStatesInProseIsNowEnforced() {
        // The finding is that eighteen migrations SAY this and none of them checked the corpus.
        // Counted from the comments, which is the one place the claim lives — so if a future
        // author deletes the prose, this leg says the convention was documented and stops
        // pretending the number is current.
        int stating = 0;
        int mentioning = 0;
        for (Path f : MigrationVocabularies.migrationFiles()) {
            String sql = MigrationVocabularies.read(f);
            if (!sql.toUpperCase().contains("NOCHECK")) continue;
            mentioning++;
            // Both blankings, because the rule is stated in BOTH places: 11 files put it in a
            // comment and 7 in a THROW message so an operator reads it when a migration fails.
            String code = blankSqlLiterals(MigrationVocabularies.stripComments(sql));
            if (!code.toUpperCase().contains("NOCHECK")) stating++;
        }
        assertThat(stating)
            .as("""
                Migrations that mention NOCHECK without doing it — 11 in a comment and 7 in a
                THROW message, measured 18 of the 19 that mention it at all when F2-461 was
                written. The point of the number is that every one of them gated the rule over
                its OWN constraints only, so the corpus-wide question was nobody's until this
                class asked it, and V265's two sat untrusted in both live databases.

                Also an anti-vacuity check on BOTH blankings: if the comment stripper or the
                literal scanner stopped working, these files would read as code containing
                NOCHECK, this count would collapse, and the hard zero above would start failing
                on the most disciplined files in the corpus.""")
            .isGreaterThan(14);

        assertThat(mentioning)
            .as("files mentioning NOCHECK at all: 19 measured. `stating` is 18 of them and "
                + "V265 is the one that does it.")
            .isEqualTo(stating + 1);
    }
}
