package com.tss.grc.security;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinTable;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-68 — the schema contract, enforced where the work happens and <b>without a database</b>.
 *
 * <h3>The problem this exists to solve</h3>
 *
 * Three findings in one day (F2-49, F2-64, F2-65) came from a single cause: this platform keeps
 * <b>two schema authorities that never reconcile</b>. Development is Python-bootstrapped with
 * Flyway <em>off</em>; production runs Flyway <em>on</em>, baselined at
 * {@code FLYWAY_BASELINE_VERSION = 292} over a restored dev dump. Flyway records every migration
 * numbered at or below the baseline as already applied <em>whether it ever ran or not</em>.
 * Therefore:
 *
 * <p><b>A migration numbered ≤ 292 can never execute in either environment.</b>
 *
 * {@code V172__crisis_comms_expansion.sql} creates {@code bcm.crisis_comms_template_v2} and
 * {@code bcm.crisis_comms_cascade}. Both were absent from <em>both</em> databases while the
 * platform shipped two JPA entities, two services, two controllers and three sidebar-linked pages
 * on top of them. An authorised persona got a 503. Nothing caught it, because the file is
 * well-formed, correctly numbered and unique — which is exactly what
 * {@link com.tss.grc.security.MigrationFilenameRatchetTest} checks.
 *
 * <h3>Why this is a JUnit test and not a database script</h3>
 *
 * {@code Appendix/scripts/table_xref.py} and {@code schema_drift.py} found these by querying both
 * live databases. Neither can run in CI: they need SQL Server and real data. Standing up a SQL
 * Server service container would <b>not</b> fix that — a freshly Flyway-built CI database has no
 * rows, so it cannot reproduce the row-count classification those scanners do, and the half it
 * <em>could</em> reproduce is the schema contract, which needs no database at all. So the work
 * splits by what the evidence actually requires:
 *
 * <ul>
 *   <li><b>Can regress on any commit → gated here, DB-free.</b> Is every mapped table created by
 *       a migration that can still run? That is a property of the source tree.</li>
 *   <li><b>Needs data to observe → stays an audit.</b> Is a table orphaned, seed-only, or shipped
 *       but never exercised? Those are row-count facts. They are <em>findings</em>, not
 *       regressions, and they belong in the scanners plus an owner disposition.</li>
 * </ul>
 *
 * A test that needs infrastructure gets skipped; a test that runs on a bare checkout gets trusted.
 *
 * <h3>What it asserts</h3>
 *
 * <ol>
 *   <li>Every table a JPA mapping names is created by <em>some</em> migration.</li>
 *   <li>No mapped table's only creating migration is at or below the baseline — the F2-64
 *       mechanism precisely.</li>
 *   <li>Anti-vacuity in both directions: the scan must find a realistic number of mappings and
 *       migrations, because a scan that matched nothing would pass for the wrong reason. This
 *       repo has shipped a green-for-the-wrong-reason regex more than once.</li>
 * </ol>
 */
class SchemaContractRatchetTest {

    /**
     * {@code FLYWAY_BASELINE_VERSION} on the deployed server. Anything at or below this is
     * recorded as applied whether it executed or not, so DDL down here is inert on production.
     * If the deployment is ever re-baselined, this constant moves with it — and the failure
     * message says so, because a stale baseline would silently narrow the check.
     */
    private static final int FLYWAY_BASELINE = 292;

    /** Floors, so a scan that finds nothing cannot be mistaken for a clean one. */
    private static final int MIN_MAPPED_TABLES = 300;
    private static final int MIN_MIGRATIONS = 300;
    /**
     * Distinct tables the migration folder creates. Deliberately lower than the mapped count,
     * because 67 mapped tables have no creating migration at all — see
     * {@link #BASELINE_MAPPED_WITHOUT_MIGRATION}.
     */
    private static final int MIN_CREATED_TABLES = 250;

    /**
     * <b>67, measured 2026-07-31, and it must only ever decrease.</b>
     *
     * <p>That many tables are named by a JPA mapping and created by <em>no migration in this
     * repository</em>. They exist in both databases anyway: the Python bootstrap
     * ({@code setup_ai_lifecycle_db.py}, {@code setup_cpty_db.py}, {@code setup_cyber_db.py}, …)
     * created them in dev, and production inherited them when it was restored from a dev dump on
     * 2026-07-11. Verified individually rather than inferred — {@code ai_governance.ai_lifecycle_event}
     * appears in <b>zero</b> migration files, and {@code compliance.frameworks} is created nowhere
     * (the migrations create {@code cyber.cyber_frameworks}, {@code platform.control_frameworks} and
     * {@code evidence.frameworks}, which are different tables).
     *
     * <p><b>So the migration set is not a complete schema definition.</b> {@code flyway:migrate}
     * against an empty database cannot build this platform: a genuinely new customer deployment
     * would be missing 67 tables that entities map, and every surface over them would answer 503
     * {@code db-object-missing}. That is F2-64's failure mode — except F2-64 was one capability and
     * this is the whole schema. Same root cause: two schema authorities that never reconcile, and
     * the one that fills the gap (the Python bootstrap) ships to nobody.
     *
     * <p>A ratchet rather than a hard zero, because 67 tables cannot be back-filled in one slice and
     * a red build nobody can fix gets disabled. Lower it as bootstrap DDL becomes migrations;
     * <b>never raise it to make a new entity pass</b> — a new table with no migration is a table
     * that will not exist for the next customer.
     *
     * <h4>✅ 0 as of 2026-08-01 — this is now a hard gate, not a ratchet</h4>
     *
     * All 67 landed in one slice, generated from {@code sys.schemas}/{@code sys.columns}/
     * {@code sys.indexes} by {@code Appendix/scripts/schema_from_catalog.py} rather than
     * hand-copied. The reason for generating is V338: it re-issued V12–V14 verbatim and Hibernate
     * rejected the result (<em>Invalid column name 'deleted_by'</em>) because those files were three
     * months stale — <b>re-issuing a stale migration reproduces a stale schema</b>, and doing it 67
     * times by hand is 67 chances to ship that. A catalog read cannot be stale.
     *
     * <p>Proven, not asserted: V341+V342 were applied to a genuinely empty scratch database and the
     * result compared column-for-column with dev — 67/67 tables, 112/112 indexes, 86/86 PK+UNIQUE
     * constraints, <b>1,666/1,666 columns, zero drift</b>, and idempotent on a second pass. The same
     * comparator run against production reports zero drift too.
     *
     * <p><b>The prize, and the reason the ceiling is now 0:</b> a new entity whose table has no
     * runnable DDL fails this build immediately, instead of shipping a platform the next customer
     * cannot be given.
     */
    private static final int BASELINE_MAPPED_WITHOUT_MIGRATION = 0;

    /**
     * <b>11, measured 2026-07-31, and it must only ever decrease.</b>
     *
     * <p>Mapped <em>schemas</em> that no migration creates: {@code cpty, data_officer, dataclass,
     * fraud, openbanking, opres, outsourcing, reginspect, regreporting, rrp, sharia}. Forty schemas
     * have a {@code CREATE SCHEMA}; these eleven exist only because the Python bootstrap made them.
     *
     * <p><b>Why this needs its own ratchet:</b> a second-model review pointed out that
     * {@link #BASELINE_MAPPED_WITHOUT_MIGRATION} could be driven to <em>zero</em> while
     * {@code flyway:migrate} on an empty database still died on the first
     * {@code CREATE TABLE sharia.…}, because the schema it belongs to would not exist. The table
     * ratchet would read 0 and the claim "the migrations can build the platform" would still be
     * false. A gate that can reach green while the property it stands for is broken is not a gate.
     *
     * <h4>✅ 0 as of 2026-08-01, and the review that demanded this ratchet was vindicated twice</h4>
     *
     * Once by prediction — the table ratchet did reach 0 in a single slice, exactly as warned. And
     * once by demonstration: the spine generator's <em>first</em> emission declared only these 11,
     * and the empty-database proof failed on its very first {@code CREATE TABLE} with
     * <em>"The specified schema name 'ai_governance' either does not exist"</em>. {@code ai_governance},
     * {@code compliance} and {@code evidence} are among the 40 schemas that <b>do</b> have a
     * {@code CREATE SCHEMA} — in three <em>earlier</em> migrations — so the new file was silently
     * depending on them. Correct in a full chain, broken out of it. The generator now declares all
     * <b>14</b> schemas its own tables live in, guarded by {@code IF NOT EXISTS}.
     *
     * <p>🔑 A migration that creates a table should declare the schema it needs. Relying on an
     * earlier file is the same ordering fragility that makes V47's column-existence guard pass on an
     * empty database and then fail its {@code ALTER}.
     */
    private static final int BASELINE_MAPPED_SCHEMAS_WITHOUT_MIGRATION = 0;

    /**
     * Mapped tables deliberately not created by any migration in this repo, with the reason.
     * Empty today — and that is the point: an entry here is a documented exception, not a
     * silent pass. If something lands here, the reason must survive a reader who was not present.
     */
    private static final Map<String, String> NOT_MIGRATION_CREATED = Map.of();

    /**
     * The register's exact column order, asserted rather than assumed.
     *
     * <p>The F2-64 gate reads {@code rows_dev} and {@code rows_prod} positionally. If
     * {@code table_xref.py} ever reorders or renames a column, those indices would silently read
     * the wrong fields, no row would ever match {@code ABSENT}/{@code ABSENT}, and the gate would go
     * <b>permanently green</b> while checking nothing. Positional reads need a pinned header.
     */
    private static final List<String> REGISTER_HEADER = List.of(
        "schema", "table", "code", "source", "rows_dev", "rows_prod", "jpa_files", "code_refs",
        "test_refs", "migrations", "mig_inserts", "py_refs", "py_inserts", "reason");

    /** 468 rows today. A register that shrank by a tenth is a broken scan, not a smaller platform. */
    private static final int MIN_REGISTER_ROWS = 400;

    private static final Pattern CREATE_TABLE = Pattern.compile(
        "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?\\[?(\\w+)\\]?\\s*\\.\\s*\\[?(\\w+)\\]?",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern MIGRATION_NAME = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Pattern CREATE_SCHEMA = Pattern.compile(
        "CREATE\\s+SCHEMA\\s+\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);

    // ── the assertions ───────────────────────────────────────────────────────

    @Test
    void mappedTablesWithNoCreateMigrationOnlyEverDecrease() throws IOException {
        Map<String, String> mapped = mappedTables();               // table -> where it is mapped
        Map<String, List<Integer>> created = createdByMigration(); // table -> migration versions

        assertThat(mapped).as(
            "Found %d JPA-mapped tables. Fewer than %d means the scan is broken, not that the "
            + "platform shrank — and a broken scan passes every other assertion in this class.",
            mapped.size(), MIN_MAPPED_TABLES)
            .hasSizeGreaterThanOrEqualTo(MIN_MAPPED_TABLES);

        List<String> orphaned = new ArrayList<>();
        for (var e : mapped.entrySet()) {
            if (!created.containsKey(e.getKey()) && !NOT_MIGRATION_CREATED.containsKey(e.getKey())) {
                orphaned.add("%s  (mapped by %s)".formatted(e.getKey(), e.getValue()));
            }
        }
        orphaned.sort(null);

        assertThat(orphaned.size()).as("""
            %d JPA-mapped tables are created by no migration in this repository (baseline %d).

            If this went UP, an entity was added without DDL that Flyway can run. The table exists \
            on your machine because the Python bootstrap made it; it will not exist for a customer \
            deployed from migrations alone. Put the CREATE in a migration above the Flyway baseline.

            If it went DOWN, lower the baseline in this file — that is the ratchet working.

            %s""".formatted(orphaned.size(), BASELINE_MAPPED_WITHOUT_MIGRATION,
                            String.join("\n            ", orphaned)))
            .isLessThanOrEqualTo(BASELINE_MAPPED_WITHOUT_MIGRATION);
    }

    /**
     * The F2-64 gate, and the one assertion here allowed no exceptions.
     *
     * <p>⚠ <b>An earlier version of this test asserted something false and the failure taught the
     * lesson.</b> It flagged every mapped table whose only {@code CREATE} sits at or below the
     * baseline — and that fired on <em>hundreds</em> of tables which exist perfectly well, because
     * the baseline is precisely the statement "everything up to V292 is already applied". Being
     * created below the baseline is not a defect; being created below the baseline <em>and not
     * actually existing</em> is. The distinction needs evidence about the world, not about the
     * source tree.
     *
     * <p>That evidence is {@code Appendix/TABLE_DISPOSITION_REGISTER.csv}: generated from both live
     * databases by {@code table_xref.py} and <b>committed</b>. So the observation is checked in, and
     * this gate needs the artifact rather than the infrastructure — which is what lets it run in CI
     * with no SQL Server. If the register is missing, the test fails saying so rather than passing
     * quietly, because a check that degrades to green is worse than no check.
     */
    @Test
    void noMappedTableIsUncreatableAndAbsentFromBothDatabases() throws IOException {
        List<String[]> rows = registerRows();

        Set<String> absentFromBoth = new LinkedHashSet<>();
        for (String[] f : rows) {
            if ("ABSENT".equalsIgnoreCase(f[4].trim()) && "ABSENT".equalsIgnoreCase(f[5].trim())) {
                absentFromBoth.add(key(f[0].trim(), f[1].trim()));
            }
        }

        Map<String, String> mapped = mappedTables();
        Map<String, List<Integer>> created = createdByMigration();
        List<String> broken = new ArrayList<>();
        for (String table : absentFromBoth) {
            if (!mapped.containsKey(table)) {
                continue;                     // absent and unmapped is not this test's business
            }
            List<Integer> versions = created.get(table);
            int highest = versions == null ? -1
                : versions.stream().mapToInt(Integer::intValue).max().orElse(-1);
            if (highest <= FLYWAY_BASELINE) {
                broken.add("%s  mapped by %s  ·  created by %s  ·  baseline V%d"
                    .formatted(table, mapped.get(table),
                               versions == null ? "NOTHING" : "V" + versions, FLYWAY_BASELINE));
            }
        }

        assertThat(broken).as("""
            These tables are mapped by JPA, absent from BOTH databases per the committed register, \
            and creatable only by a migration at or below the Flyway baseline (V%d) — which a \
            baselined database skips forever. Every read or write against them answers 503 \
            db-object-missing. This is exactly F2-64: entities, two services, two controllers and \
            three sidebar-linked pages over bcm.crisis_comms_* while V172 (< 292) could never run.

            The remedy is NOT to edit the old migration; that breaks flyway:validate wherever it did \
            run. Re-issue its DDL in a new migration above the baseline, idempotently, the way V336 \
            re-issued V172.

            %s""".formatted(FLYWAY_BASELINE, String.join("\n            ", broken)))
            .isEmpty();
    }

    /**
     * The register is evidence, so its own integrity is part of the contract.
     *
     * <p>⚠ <b>A second-model review found that
     * {@link #noMappedTableIsUncreatableAndAbsentFromBothDatabases} is vacuously green today.</b>
     * Not broken — <em>empty</em>: every {@code ABSENT} row in the committed register is one-sided
     * (three prod-only KRI leftovers), so the loop that looks for absent-from-<em>both</em> iterates
     * zero rows and the assertion holds without examining anything. That is fine as a statement
     * about today, and useless as a gate, because the register only changes when a human runs
     * {@code table_xref.py} against two live databases. A new F2-64 — an entity mapped over a table
     * in neither database — would stay invisible until someone regenerated it.
     *
     * <p>So the gate is completed here, DB-free, by the one thing the source tree <em>can</em>
     * settle: <b>the register must account for every table JPA maps.</b> Add an entity whose table
     * is not in the register and this fails, telling you to regenerate — which is precisely the act
     * that would surface the absent-from-both case. Plus the mechanical checks that stop the
     * positional reads above from rotting: exact header, a row floor, and malformed rows failing
     * instead of being skipped. The old loop skipped short rows with a bare {@code continue}; a
     * truncated register would have quietly reduced the population to nothing.
     */
    @Test
    void theRegisterIsWellFormedAndAccountsForEveryMappedTable() throws IOException {
        List<String[]> rows = registerRows();

        assertThat(rows.size()).as(
            "The committed register has %d data rows. Under %d means a truncated or half-written "
            + "file, and every positional check that reads it would narrow silently.",
            rows.size(), MIN_REGISTER_ROWS)
            .isGreaterThanOrEqualTo(MIN_REGISTER_ROWS);

        Set<String> registered = new LinkedHashSet<>();
        for (String[] f : rows) {
            registered.add(key(f[0].trim(), f[1].trim()));
        }

        List<String> unregistered = new ArrayList<>();
        for (var e : mappedTables().entrySet()) {
            if (!registered.contains(e.getKey())) {
                unregistered.add("%s  (mapped by %s)".formatted(e.getKey(), e.getValue()));
            }
        }
        unregistered.sort(null);

        assertThat(unregistered).as("""
            %d JPA-mapped tables have no row in the committed register, so the register is STALE and \
            the F2-64 gate cannot see them. Regenerate it:

              py TSS-GRC-Platform-Bible/Appendix/scripts/table_xref.py

            That needs both databases. It is the only step in this class that does — and it is the \
            step that turns "a table exists on my machine" into checked-in evidence. Until it runs, \
            an entity mapped over a table that exists nowhere reads as clean.

            %s""".formatted(unregistered.size(), String.join("\n            ", unregistered)))
            .isEmpty();
    }

    /**
     * The companion ratchet to {@link #BASELINE_MAPPED_WITHOUT_MIGRATION}: schemas, not tables.
     * Without it the table ratchet can reach zero while an empty-database build still fails on the
     * first {@code CREATE TABLE} in a schema no migration declares.
     */
    @Test
    void mappedSchemasWithNoCreateSchemaMigrationOnlyEverDecrease() throws IOException {
        Set<String> created = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(migrationDir())) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                Matcher m = CREATE_SCHEMA.matcher(Files.readString(p));
                while (m.find()) {
                    created.add(m.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }

        assertThat(created).as(
            "Parsed CREATE SCHEMA for %d schemas. Near-zero means the pattern stopped matching, "
            + "which would turn this check green while checking nothing.", created.size())
            .hasSizeGreaterThanOrEqualTo(20);

        Set<String> mappedSchemas = new LinkedHashSet<>();
        for (String table : mappedTables().keySet()) {
            mappedSchemas.add(table.substring(0, table.indexOf('.')));
        }
        List<String> uncreated = mappedSchemas.stream().filter(s -> !created.contains(s)).sorted()
            .toList();

        assertThat(uncreated.size()).as("""
            %d schemas hold JPA-mapped tables and are created by no migration (baseline %d):
            %s

            If this went UP, an entity was added in a schema the migrations never declare. Driving \
            the TABLE ratchet to zero does not help: flyway:migrate on an empty database fails on \
            the first CREATE TABLE in a schema that does not exist. Add CREATE SCHEMA above the \
            Flyway baseline in the same migration as the tables.""".formatted(
                uncreated.size(), BASELINE_MAPPED_SCHEMAS_WITHOUT_MIGRATION,
                String.join(", ", uncreated)))
            .isLessThanOrEqualTo(BASELINE_MAPPED_SCHEMAS_WITHOUT_MIGRATION);
    }

    /**
     * Every migration that creates a table must declare the schemas <em>it</em> needs, or be
     * preceded by a file that does — checked in file order, which is the order Flyway runs them.
     *
     * <p>⚠ This test exists because the empty-database proof caught what no other gate could. The
     * spine generator's first emission declared only the 11 schemas no migration covered, leaving
     * {@code ai_governance}, {@code compliance} and {@code evidence} to three earlier files. Both
     * ratchets above read 0 — correctly — while {@code CREATE TABLE} failed on line one of a real
     * build. <b>A table-level gate cannot see a schema-level dependency.</b>
     *
     * <p>Ordering matters here in a way it does not elsewhere in this class, so the check walks the
     * folder numerically and accumulates: a schema is available at file N if any file ≤ N declares
     * it. That reproduces what Flyway does, rather than treating the folder as a set.
     */
    @Test
    void noMigrationCreatesATableInASchemaNoEarlierMigrationHasDeclared() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(migrationDir())) {
            files = s.filter(p -> MIGRATION_NAME.matcher(p.getFileName().toString()).matches())
                .sorted((a, b) -> Integer.compare(version(a), version(b)))
                .toList();
        }
        assertThat(files).as("no migration files parsed — this check would be vacuous")
            .hasSizeGreaterThanOrEqualTo(MIN_MIGRATIONS);

        Set<String> available = new LinkedHashSet<>();
        available.add(DEFAULT_SCHEMA);                 // platform is created by V1's core schemas
        List<String> violations = new ArrayList<>();
        for (Path p : files) {
            String body = Files.readString(p);
            Matcher cs = CREATE_SCHEMA.matcher(body);
            while (cs.find()) {
                available.add(cs.group(1).toLowerCase(Locale.ROOT));
            }
            Matcher ct = CREATE_TABLE.matcher(body);
            while (ct.find()) {
                String schema = ct.group(1).toLowerCase(Locale.ROOT);
                if (!available.contains(schema)) {
                    violations.add("%s creates %s.%s but no migration up to here declares schema [%s]"
                        .formatted(p.getFileName(), schema, ct.group(2).toLowerCase(Locale.ROOT),
                                   schema));
                }
            }
        }

        assertThat(violations).as("""
            A migration creates a table in a schema that nothing before it declares. On an existing \
            database this passes because the schema is already there; on the empty database a new \
            customer gets, it fails on the CREATE TABLE. Add the CREATE SCHEMA (IF NOT EXISTS) to \
            the same file — do not rely on an earlier one.

            %s""".formatted(String.join("\n            ", violations)))
            .isEmpty();
    }

    /**
     * No migration may hard-code the database it runs against — F2-76.
     *
     * <p>Five files carried {@code USE TSS_GRC;} (V3, V4, V5, V7, V10). Flyway is already connected
     * to the target database, so it was redundant here and wrong anywhere else. The benign failure
     * mode is an error on a customer database with another name. <b>The dangerous one is success:</b>
     * if some unrelated {@code TSS_GRC} exists on the same instance, the statement switches to it,
     * that file's DDL lands in the wrong database, and Flyway records the migration as applied.
     *
     * <p>All five sat below the V292 baseline, so neither environment ever executed them — the same
     * reason F2-75 hid in V3 for the file's whole life. A hard zero, because there is no legitimate
     * use for it.
     *
     * <p>⚠ The first version of this check matched any line <em>starting with</em> {@code "USE "}
     * and reported {@code V191__contextual_help.sql} — whose seeded help text contains the English
     * sentence <em>"Use the Bow-tie view…"</em> inside an INSERT. This repository's standing lesson
     * is that a regex matching too little invents defects; that was the mirror image, and a gate
     * with a false positive gets suppressed just as fast as one that is always red. The pattern now
     * anchors the <b>whole line</b>: {@code USE}, one identifier, optional semicolon, end.
     */
    private static final Pattern USE_STATEMENT = Pattern.compile(
        "^\\s*USE\\s+\\[?\\w+]?\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);

    @Test
    void noMigrationHardCodesItsDatabaseName() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.list(migrationDir())) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                for (String line : Files.readAllLines(p)) {
                    if (USE_STATEMENT.matcher(line).matches()) {
                        offenders.add(p.getFileName() + ": " + line.strip());
                    }
                }
            }
        }

        // Anti-vacuity: the pattern must still match the thing it was written for. A pattern
        // tightened until it matches nothing is the other way this gate could go quietly green.
        assertThat(USE_STATEMENT.matcher("USE TSS_GRC;").matches())
            .as("the pattern must still recognise the statement this test exists to ban").isTrue();
        assertThat(USE_STATEMENT.matcher("  use [Other_Db]").matches()).isTrue();
        assertThat(USE_STATEMENT.matcher(
            "  'Use the **Bow-tie** view (`/risk/bowtie`) for top-tier risks.',").matches())
            .as("and must not fire on English prose inside seeded help text").isFalse();
        assertThat(offenders).as("""
            A migration hard-codes its database with USE. Flyway already connects to the target, so \
            remove the line. On a customer database with a different name this either fails, or \
            silently applies the file's DDL to whatever other database bears the hard-coded name \
            while Flyway records success.

            %s""".formatted(String.join("\n            ", offenders)))
            .isEmpty();
    }

    private static int version(Path p) {
        Matcher m = MIGRATION_NAME.matcher(p.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    @Test
    void theMigrationFolderItselfIsScannable() throws IOException {
        Map<String, List<Integer>> created = createdByMigration();
        long files;
        try (Stream<Path> s = Files.list(migrationDir())) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".sql")).count();
        }

        assertThat(files).as(
            "Only %d migration files found under %s. This class reads the folder off disk, so a "
            + "wrong path would make every assertion above vacuously true.", files, migrationDir())
            .isGreaterThanOrEqualTo(MIN_MIGRATIONS);

        assertThat(created).as(
            "Parsed CREATE TABLE for %d distinct tables out of %d migration files. Near-zero means "
            + "the CREATE_TABLE pattern stopped matching this codebase's DDL style, which would "
            + "turn both checks above green while checking nothing.", created.size(), files)
            .hasSizeGreaterThanOrEqualTo(MIN_MAPPED_TABLES);
    }

    // ── evidence gathering ───────────────────────────────────────────────────

    /**
     * Every {@code schema.table} named by a JPA mapping, by reflection over the compiled
     * annotations rather than by grepping source. That choice is not stylistic: a source regex
     * has under-counted a security surface in this repository repeatedly — F2-27 missed 8
     * constant-valued guards, F2-30 missed 54 field pairs, and the entity-file count has been
     * reported as 418, 417 and 413 by three different greps because {@code @EntityListeners},
     * {@code @EntityGraph} and Javadoc prose all look like entities to a pattern.
     */
    private static Map<String, String> mappedTables() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Map<String, String> out = new TreeMap<>();

        for (var bd : scanner.findCandidateComponents("com.tss.grc")) {
            Class<?> c;
            try {
                c = Class.forName(bd.getBeanClassName());
            } catch (Throwable t) {
                continue;
            }
            Table t = c.getAnnotation(Table.class);
            if (t != null) {
                put(out, t.schema(), t.name(), c.getSimpleName());
            }
            for (SecondaryTable st : c.getAnnotationsByType(SecondaryTable.class)) {
                put(out, st.schema(), st.name(), c.getSimpleName() + " @SecondaryTable");
            }
            for (Field f : c.getDeclaredFields()) {
                JoinTable jt = f.getAnnotation(JoinTable.class);
                if (jt != null) {
                    put(out, jt.schema(), jt.name(), c.getSimpleName() + "." + f.getName());
                }
                CollectionTable ct = f.getAnnotation(CollectionTable.class);
                if (ct != null) {
                    put(out, ct.schema(), ct.name(), c.getSimpleName() + "." + f.getName());
                }
            }
        }
        return out;
    }

    /**
     * {@code spring.jpa.properties.hibernate.default_schema} in {@code application.yml}. A
     * {@code @Table} that omits {@code schema=} resolves to this at runtime.
     */
    private static final String DEFAULT_SCHEMA = "platform";

    private static void put(Map<String, String> out, String schema, String name, String where) {
        // ⚠ The first version of this method skipped mappings with no explicit schema, with a
        // comment calling that "honest" because the schema resolves at runtime. It was not
        // honest — application.yml states the default outright, so the mapping IS knowable, and
        // skipping it made this class blind to exactly the defect it was written to catch.
        // Four entities declare only name=: platform.file_metadata, persistence_row,
        // sso_provider_config and nafath_transaction. All four were absent from every schema of
        // both databases, behind live controllers (/api/v1/files, /api/v1/persistence) and two
        // shipped FE adapters, with DDL at V12/V13/V14 — below the baseline, so unrunnable.
        // A second-model review found them; this gate had already declared itself green.
        // Resolving the default costs one line and removes the blind spot.
        if (name == null || name.isBlank()) {
            return;
        }
        String resolved = (schema == null || schema.isBlank()) ? DEFAULT_SCHEMA : schema;
        out.putIfAbsent(key(resolved, name), where);
    }

    /** Table -> the migration versions whose DDL creates it. */
    private static Map<String, List<Integer>> createdByMigration() throws IOException {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(migrationDir())) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
                Matcher nm = MIGRATION_NAME.matcher(p.getFileName().toString());
                if (!nm.matches()) {
                    continue;   // MigrationFilenameRatchetTest owns malformed names
                }
                int version = Integer.parseInt(nm.group(1));
                Matcher m = CREATE_TABLE.matcher(Files.readString(p));
                Set<String> seen = new LinkedHashSet<>();
                while (m.find()) {
                    seen.add(key(m.group(1), m.group(2)));
                }
                for (String table : seen) {
                    out.computeIfAbsent(table, k -> new ArrayList<>()).add(version);
                }
            }
        }
        return out;
    }

    /**
     * The committed register's data rows, header verified and quoting honoured.
     *
     * <p>The {@code reason} column contains commas, so a naive {@code split(",")} only worked by
     * luck — the first six fields happen to be comma-free. Relying on that is how a positional read
     * turns into a silent no-op after one generator change, so the header is pinned and the split
     * respects quotes.
     */
    private static List<String[]> registerRows() throws IOException {
        Path register = Paths.get("..", "TSS-GRC-Platform-Bible", "Appendix",
                                  "TABLE_DISPOSITION_REGISTER.csv").toAbsolutePath().normalize();
        assertThat(Files.exists(register)).as(
            "The committed table register is missing at %s. Regenerate it with "
            + "`py TSS-GRC-Platform-Bible/Appendix/scripts/table_xref.py`. This test refuses to pass "
            + "without it rather than degrade into a no-op.", register)
            .isTrue();

        List<String> lines = Files.readAllLines(register);
        assertThat(lines).as("The register at %s is empty.", register).isNotEmpty();

        String head = lines.get(0).startsWith("﻿") ? lines.get(0).substring(1) : lines.get(0);
        assertThat(splitCsv(head)).as(
            "The register's columns changed. The F2-64 gate reads rows_dev/rows_prod by position, "
            + "so a reordered or renamed column would make it read the wrong fields and pass "
            + "forever. Update REGISTER_HEADER deliberately, together with the indices below it.")
            .containsExactlyElementsOf(REGISTER_HEADER);

        List<String[]> rows = new ArrayList<>();
        List<String> malformed = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            List<String> f = splitCsv(line);
            if (f.size() != REGISTER_HEADER.size()) {
                malformed.add("line %d: %d fields, expected %d — %.80s"
                    .formatted(i + 1, f.size(), REGISTER_HEADER.size(), line));
                continue;
            }
            rows.add(f.toArray(new String[0]));
        }

        // The previous version skipped short rows with a bare `continue`, so a truncated register
        // reduced the checked population toward zero without saying a word.
        assertThat(malformed).as(
            "Malformed register rows — a check that silently skips them is a check that shrinks "
            + "its own population:\n  %s", String.join("\n  ", malformed))
            .isEmpty();

        return rows;
    }

    /** Minimal RFC-4180 field split: honours double quotes and doubled escapes. */
    private static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static Path migrationDir() {
        return Paths.get("src", "main", "resources", "db", "migration", "sqlserver")
            .toAbsolutePath();
    }

    private static String key(String schema, String table) {
        return (schema + "." + table).toLowerCase(Locale.ROOT);
    }
}
