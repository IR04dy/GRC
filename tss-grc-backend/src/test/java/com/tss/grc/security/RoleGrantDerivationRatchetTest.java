package com.tss.grc.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-143 (grant half) — <b>a grant that is DERIVED from the catalogue is a snapshot, not a rule</b>,
 * and this class stops the platform acquiring a seventh one.
 *
 * <h3>The measurement that produced it</h3>
 *
 * A database built from the migration folder alone ({@code full_chain_proof.py}, all 360 files
 * applied to an empty database) held <b>1,344</b> rows in {@code iam.role_permissions} against dev's
 * <b>1,567</b>. 225 grants missing, 33 roles affected, and the worst of it was {@code ORG_ADMIN} —
 * the primary tenant administrator, the role F2-81's onboarding provisions — <b>30 grants short,
 * with no BCM, COMPLIANCE, EVIDENCE, SCM, SUST, AI or NDI access at all</b>.
 *
 * <p>The cause is ordering, and it is silent. Every grant migration here is shaped
 *
 * <pre>
 *   INSERT INTO iam.role_permissions (role_id, permission_id)
 *   SELECT r.id, p.id
 *     FROM (VALUES …) v
 *     JOIN iam.roles r       ON r.role_name = v.role_name
 *     JOIN iam.permissions p ON p.module = v.module AND p.action = v.action
 *    WHERE NOT EXISTS (SELECT 1 FROM iam.role_permissions x WHERE …);
 * </pre>
 *
 * A set-based INSERT over a JOIN grants nothing when either side is absent, and {@code NOT EXISTS}
 * cannot distinguish <em>already granted</em> from <em>nothing matched</em>. So a grant whose role or
 * permission row is created later in the chain is dropped with no error. On dev the Python bootstrap
 * had created both sides before Flyway ever ran, so the same file granted; a customer built from the
 * folder gets whichever subset happened to be orderable.
 *
 * <p>Proven by replay rather than argued: running the identical folder a <b>second</b> time over the
 * built database adds <b>875 grants across 20 migrations</b>. ⚠ And a second pass is not the fix — it
 * overshoots dev by 686, because it also re-applies everything V333, V335 and V367–V371 deliberately
 * <em>revoked</em>. <b>Re-running an idempotent folder resurrects revocations.</b>
 *
 * <h3>What this class actually enforces, and why it is the narrow half</h3>
 *
 * The 225 are repaired by data, in {@code V372}. What can regress on any commit is the <em>shape</em>,
 * so that is what is gated here, DB-free: of 108 INSERT statements into {@code iam.role_permissions}
 * across 34 files, exactly <b>{@value #DERIVED_STATEMENTS} in {@value #DERIVED_FILES} files</b> pick
 * their {@code (role, permission)} pair by reading {@code iam.role_permissions} itself rather than
 * from a literal list. Those are the snapshots, and they are the migrations the replay showed gaining
 * most: V284 <b>+351</b>, V313 <b>+81</b>, V301 <b>+56</b>.
 *
 * <p>V284's is the archetype and its own comment states the intent — <i>"EXPORT 6th action —
 * catalogue + READ-parity default grants"</i>. It grants {@code MODULE:EXPORT} to every holder of
 * {@code MODULE:READ} <em>at the moment V284 runs</em>. Every READ granted afterwards (V301, V313,
 * V320, V327…V362) therefore has no EXPORT partner, which is why <b>78 of the 225 missing grants are
 * EXPORT and 0 of them are parity-restorable</b>: the matching READ is missing too.
 *
 * <h3>⚠ Why the hazard is documented HERE and not at the three sites</h3>
 *
 * The obvious remedy — add a warning comment at each site — <b>is not available</b>. V284, V301 and
 * V313 are at or below the deployed server's Flyway head (V344), so editing any of them changes its
 * checksum and the next {@code flyway:migrate} fails validation before it runs anything.
 * <b>A migration that has been applied to production is immutable, so it cannot be annotated with its
 * own defect.</b> The commentary has to live in the file that fixes it (V372's header) and in a test
 * that reads them — which is this one.
 *
 * <h3>⚠ A blanket excuse this class asserted and then had to withdraw</h3>
 *
 * Its first version claimed all four derived sites were tolerated <em>because</em> they sit below
 * prod's head, and its own third assertion refused that: <b>V346 is above V344</b>, so immutability is
 * not its excuse. V346 is a different shape and needs a different justification — it <em>transfers</em>
 * a grant from a renamed permission to its replacement, so its population is the holders of the old
 * permission and there cannot be a later-arriving holder of a permission that stops existing. The
 * snapshot hazard is real for parity/expansion and absent for transfer. 🔑 <b>One reason covering four
 * exceptions is usually one reason covering three and a fourth nobody checked</b> — so the tolerated
 * set is now a version→reason map, and the reason is asserted to exist per entry.
 *
 * <h3>Parity is deliberately NOT asserted as an invariant</h3>
 *
 * It would be the obvious follow-on and it would be wrong. ADR-017 D-10a/D-10b, V332, V333 and V335
 * <em>deliberately</em> made EXPORT narrower than READ for {@code REGULATOR_VIEWER} and for the board
 * pack. An invariant "every READ holder holds EXPORT" would refuse three ratified decisions. What is
 * enforceable is that no <em>new</em> migration invents another catalogue-derived grant.
 */
class RoleGrantDerivationRatchetTest {

    /**
     * INSERT statements whose {@code (role, permission)} pair is read out of
     * {@code iam.role_permissions} instead of declared. <b>Never raise this.</b> A new derived grant
     * means a new snapshot that will diverge the moment anything is catalogued after it — declare the
     * pairs as literals instead, the way the other 102 statements do.
     */
    private static final int DERIVED_STATEMENTS = 6;

    /** Distinct migration files holding those statements: V284, V301, V313, V346. */
    private static final int DERIVED_FILES = 4;

    /**
     * The exact files allowed to derive, <b>each with its own reason</b>. A tolerated exception whose
     * justification is inherited from a neighbour is indistinguishable from one nobody examined —
     * V366's lesson, and this map exists because a blanket reason was wrong for one of the four.
     */
    private static final Map<Integer, String> DERIVED_ALLOWED = Map.of(
        284, "PARITY SNAPSHOT, and the archetype of the defect: grants MODULE:EXPORT to every holder "
           + "of MODULE:READ at the moment it runs. Tolerated only because V284 <= prod's Flyway head "
           + "(V344) and is therefore immutable; +351 grants on replay.",
        301, "PARITY SNAPSHOT: CHANGE grants derived from WORKFLOW holders. Immutable (<= V344); "
           + "+56 on replay.",
        313, "PARITY SNAPSHOT: SIGNATURE and AI grants derived from existing holders. Immutable "
           + "(<= V344); +81 on replay.",
        346, "TRANSFER, not a snapshot — and this is the entry that broke the blanket excuse. It moves "
           + "a grant from a RENAMED permission onto its replacement, so its population is the holders "
           + "of a permission that stops existing: there cannot be a later-arriving holder to miss. "
           + "V346 is ABOVE prod's head, so immutability is not its excuse and does not need to be.");

    /**
     * Anti-vacuity floor. This class reads .sql off disk and classifies with a regex; a wrong path or
     * a changed DDL idiom would make every assertion above pass while checking nothing. Measured 108
     * on 2026-08-07 — the floor is deliberately below it so ordinary growth does not trip it, and
     * deliberately above zero so a broken scan does.
     */
    private static final int MIN_GRANT_STATEMENTS = 90;

    private static final Pattern GRANT_INSERT =
        Pattern.compile("INSERT\\s+INTO\\s+iam\\.role_permissions", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_LEVEL_FROM_GRANTS =
        Pattern.compile("\\bFROM\\s+iam\\.role_permissions\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIGRATION_NAME = Pattern.compile("^V(\\d+)__.*\\.sql$");

    private record GrantStatement(int version, String file, String sql, boolean derived) { }

    @Test
    @DisplayName("no migration derives a grant from the grants that already exist, beyond the four that cannot be changed")
    void noNewMigrationDerivesAGrantFromTheCatalogue() throws IOException {
        List<GrantStatement> all = grantStatements();
        List<GrantStatement> derived = all.stream().filter(GrantStatement::derived).toList();
        Set<Integer> versions = new TreeSet<>(derived.stream().map(GrantStatement::version).toList());

        assertThat(derived).as(
            "%d INSERT statements into iam.role_permissions choose their (role, permission) pair by "
            + "reading iam.role_permissions itself. That is a SNAPSHOT of the catalogue at the moment "
            + "the migration runs, not a rule: every permission catalogued afterwards is missed, "
            + "silently, on a fresh install and only there. Declare the pairs as literals — the other "
            + "%d statements in this folder do. Sites: %s",
            derived.size(), all.size() - derived.size(),
            derived.stream().map(GrantStatement::file).distinct().toList())
            .hasSize(DERIVED_STATEMENTS);

        assertThat(versions).as(
            "The derived-grant population moved. Each tolerated file carries its OWN reason in "
            + "DERIVED_ALLOWED; a new one needs a reason written there, not a bumped number. Current "
            + "reasons: %s", DERIVED_ALLOWED)
            .containsExactlyInAnyOrderElementsOf(DERIVED_ALLOWED.keySet());

        assertThat(versions).as("Distinct files, not just statements.").hasSize(DERIVED_FILES);

        // Every tolerated entry must actually SAY which shape it is. A reason that names neither
        // makes the map decorative.
        assertThat(DERIVED_ALLOWED.values()).as(
            "Each toleration must classify itself: a PARITY SNAPSHOT is the defect and is tolerated "
            + "only for immutability, a TRANSFER is sound by construction. An unclassified entry is "
            + "an exception nobody examined.")
            .allSatisfy(reason -> assertThat(reason).containsAnyOf("PARITY SNAPSHOT", "TRANSFER"));
    }

    /**
     * ⚠ These assertions read V372 with its comments <b>stripped</b>, and mutation is why. The first
     * version searched the raw file, and a mutant that replaced the anti-resurrection predicate with
     * {@code OR (1 = 0)} <b>survived</b>: every name it looked for was still present — in the header
     * paragraph explaining the check. 🔑 <b>A source-text assertion cannot distinguish a live
     * predicate from a comment that describes it</b>, and a file that documents its own guard well is
     * exactly the file where that failure is invisible. Same shape as F2-33's wording ratchet and
     * F2-147's sender scan; third instance on this programme.
     */
    @Test
    @DisplayName("V372 repairs the ordering losses and refuses to resurrect the revocations")
    void v372RepairsTheLossesWithoutResurrectingRevocations() throws IOException {
        String raw = bodyOf(372);
        String v372 = stripLineComments(raw);

        assertThat(v372).as("V372 is the migration that repairs F2-143's grant half; it must exist.")
            .isNotEmpty();

        // The two grant groups are kept apart because their AUTHORITY differs: group A is what the
        // folder already declares, group B is 26 SCM grants no migration ever declared. These labels
        // ARE documentation, so they are checked against the raw file on purpose.
        assertThat(raw).as("The two authorities must stay visibly separate to a reader.")
            .contains("Group A", "Group B");

        // The anti-resurrection assertions are the whole reason a grant-restoring migration is safe:
        // a grant sweep is the obvious way to undo V367-V371's self-review fixes by accident. Checked
        // against the STRIPPED file — see this method's javadoc for the mutant that made that
        // necessary.
        // ⚠ Checking for the role NAMES is not enough either, and mutation proved that too: replacing
        // the ETHICS predicate with `OR (1 = 0)` still left "INTERNAL_AUDITOR" in the file, because
        // group A grants it thirteen rows. So the assertion is on the whole PREDICATE, whitespace-
        // normalised so reformatting is allowed and neutering is not.
        String sql = v372.replaceAll("\\s+", " ");
        for (String predicate : List.of(
                "role_name = N'SHARIA_AUDITOR' AND p.module = N'SHARIA' AND p.action = N'APPROVE'",
                "role_name = N'OPEN_BANKING_OFFICER' AND p.module = N'OPENBANKING' "
                    + "AND p.action = N'APPROVE'",
                "role_name = N'INTERNAL_AUDITOR' AND p.module = N'ETHICS' AND p.action = N'READ'")) {
            assertThat(sql).as(
                "V372's SQL must still test this exact pair: %s. Without it the next person to widen "
                + "this file re-opens a self-review path V367-V371 closed and nothing objects — and "
                + "neither a header paragraph describing the check nor the role's name in a VALUES "
                + "list is the check.", predicate)
                .contains(predicate);
        }

        // And the mirror: the governed regulatory channel must still be open. An absence-only check
        // passes on a database where the supervisory authority can furnish nothing at all.
        assertThat(v372).as(
            "V372's SQL must assert BOTH directions for REGULATOR_VIEWER — no raw-register EXPORT, and "
            + "the governed REGULATORY package still present. F2-59 took the eleven; F2-60 put that "
            + "one there on purpose.")
            .contains("REGULATOR_VIEWER")
            .contains("cannot export the governed regulatory package");

        // PLATFORM_ADMIN is deliberately left short, because ModulePermissionEvaluator bypasses. The
        // SQL must PIN that (checks it is 0 or dev's 8, never anything else); the *reason* is prose,
        // so it is checked against the raw file.
        assertThat(v372).as("V372's SQL must pin PLATFORM_ADMIN's grant count so this file cannot "
                            + "quietly top it up.")
            .contains("PLATFORM_ADMIN");
        assertThat(raw).as(
            "V372 must SAY why PLATFORM_ADMIN is not topped up: the evaluator bypasses in code, so a "
            + "grant would make the bypass look like an entitlement.")
            .contains("bypass");

        assertThat(v372).as("Every self-check in this folder THROWs; a warning is not a control.")
            .contains("THROW 51372");
    }

    @Test
    @DisplayName("the scan sees the grant population it claims to")
    void theScanSeesTheGrantPopulationItClaimsTo() throws IOException {
        List<GrantStatement> all = grantStatements();

        assertThat(all).as(
            "Only %d INSERT statements into iam.role_permissions found under %s. This class reads "
            + "the folder off disk and classifies with a regex, so a wrong path or a changed DDL "
            + "idiom would make the ratchet above vacuously green.", all.size(), migrationDir())
            .hasSizeGreaterThanOrEqualTo(MIN_GRANT_STATEMENTS);

        // The classifier must be able to see BOTH kinds, or it is not classifying.
        assertThat(all.stream().filter(GrantStatement::derived).count())
            .as("The derived classifier matched nothing — it is no longer reading this codebase's "
                + "SQL. It works by removing every parenthesised group (which deletes NOT EXISTS "
                + "subqueries and VALUES lists) and then asking whether the statement's own FROM "
                + "names iam.role_permissions.")
            .isGreaterThan(0);
        assertThat(all.stream().filter(s -> !s.derived()).count())
            .as("The classifier called every grant derived, which means the paren-stripping step "
                + "stopped working and NOT EXISTS subqueries are being counted as the source.")
            .isGreaterThan(MIN_GRANT_STATEMENTS - DERIVED_STATEMENTS - 10);
    }

    // ── plumbing ────────────────────────────────────────────────────────────────────────────────

    private static List<GrantStatement> grantStatements() throws IOException {
        List<GrantStatement> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(migrationDir())) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).sorted()
                               .toList()) {
                Matcher name = MIGRATION_NAME.matcher(p.getFileName().toString());
                if (!name.matches()) {
                    continue;
                }
                int version = Integer.parseInt(name.group(1));
                String sql = stripLineComments(Files.readString(p, StandardCharsets.UTF_8));
                Matcher m = GRANT_INSERT.matcher(sql);
                while (m.find()) {
                    String stmt = sql.substring(m.start());
                    int end = stmt.indexOf(';');
                    stmt = end < 0 ? stmt : stmt.substring(0, end);
                    out.add(new GrantStatement(version, p.getFileName().toString(), stmt,
                        TOP_LEVEL_FROM_GRANTS.matcher(stripParenthesised(stmt)).find()));
                }
            }
        }
        return out;
    }

    private static String bodyOf(int version) throws IOException {
        try (Stream<Path> files = Files.list(migrationDir())) {
            Path hit = files.filter(p -> p.getFileName().toString().startsWith("V" + version + "__"))
                            .findFirst().orElse(null);
            return hit == null ? "" : Files.readString(hit, StandardCharsets.UTF_8);
        }
    }

    /** A comment mentioning the table must not be read as SQL touching it. */
    private static String stripLineComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        for (String line : sql.split("\n", -1)) {
            int at = line.indexOf("--");
            out.append(at < 0 ? line : line.substring(0, at)).append('\n');
        }
        return out.toString();
    }

    /**
     * Remove every parenthesised group, innermost first. That deletes {@code NOT EXISTS (…)}
     * subqueries and {@code (VALUES …)} lists, leaving only the statement's own top-level clauses —
     * so {@code FROM iam.role_permissions} in the anti-duplication guard is not mistaken for the
     * source of the pair. Without this step the classifier calls all 108 statements derived, which is
     * how the first version of it read.
     */
    private static String stripParenthesised(String sql) {
        String previous = null;
        String current = sql;
        while (!current.equals(previous)) {
            previous = current;
            current = current.replaceAll("\\([^()]*\\)", " ");
        }
        return current;
    }

    private static Path migrationDir() {
        return Paths.get("src", "main", "resources", "db", "migration", "sqlserver")
            .toAbsolutePath();
    }
}
