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
import java.util.TreeSet;
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
     * F2-141 — the register codes that mean <b>no JPA mapping and no Java reference at all</b>.
     *
     * <p>{@code table_xref.py} assigns these three when a table is named by no {@code @Table},
     * {@code @JoinTable} or {@code @CollectionTable} and by no {@code schema.table} string anywhere
     * in Java. They differ only in how the rows got there: {@code ORPHAN_CONFIRMED} has none,
     * {@code SEED_ONLY}'s only writer is a Python seed script, {@code LEGACY} holds rows that arrived
     * from a migration or by hand. For the purpose below the distinction does not matter — <b>the
     * application cannot read any of them</b>.
     */
    private static final Set<String> NO_CODE_CODES =
        Set.of("ORPHAN_CONFIRMED", "SEED_ONLY", "LEGACY");

    /**
     * <b>36, measured 2026-08-06, and it must only ever decrease.</b>
     *
     * <p>Tables in both databases that the application cannot read: 10 {@code ORPHAN_CONFIRMED},
     * 16 {@code SEED_ONLY}, 10 {@code LEGACY}, holding <b>381 rows</b> in dev and the same 381 in
     * production, which inherited them when it was restored from a dev dump.
     *
     * <h4>Why the number is 36 and F2-141 said 5</h4>
     *
     * F2-141 found its five through a <em>bilingual column</em> scan, so its population is an
     * artifact of its instrument: it could only see a dead table that happens to carry an
     * {@code *_ar} column beside a {@code NOT NULL} {@code *_en}. Same class as the finding itself —
     * <b>an instrument's population is narrower than its subject</b> — one level up.
     *
     * <h4>Why this one is a ratchet while the two gates below are hard zeros</h4>
     *
     * These 36 are <b>historical debt awaiting an owner-authorised {@code DROP}</b> (house rule 6:
     * destructive DB operations need explicit OK), so a red build here would be unfixable by
     * engineering alone. The forward-looking property — <em>no new dead table can be shipped</em> —
     * is asserted at zero by {@link #noMigrationAboveTheBaselineCreatesATableNothingMaps} and
     * {@link #noMigrationAboveTheBaselineWritesToATableTheApplicationCannotRead}. Keep that split:
     * <b>never widen this ratchet to admit a new dead table.</b> A ceiling tolerates the population
     * it measures, which is why F2-144(b) replaced the authorisation ceiling with a pinned set —
     * here the register already names all 36, and re-listing them in Java would create the second
     * authority RC-A exists to prevent.
     */
    private static final int BASELINE_NO_CODE_TABLES = 36;

    /**
     * F2-301 — the five tables whose <b>name</b> shadows a table this platform maps: the schema's
     * own name prefixed onto a table that schema already has, so {@code bcm.bcm_exercises} sits
     * beside the live {@code bcm.exercises}. Shadow on the left, the register really read on the
     * right.
     *
     * <h4>Why a name is worth a gate of its own</h4>
     *
     * The {@link #BASELINE_NO_CODE_TABLES} ratchet above already counts all five — their
     * <em>disposition</em> has been recorded in {@code TABLE_DISPOSITION.md} since F2-141. What
     * nothing recorded is the <b>hazard</b>: a table that does not exist makes a query fail, and a
     * table with a confusable name makes it <b>succeed with the wrong rows</b>. Every one of the
     * five returns a different count from its twin — 14/13, 10/4, 18/41, 20/6, 14/12 (dev, and
     * identical in production) — so a lookup by name yields a plausible number rather than an error.
     *
     * <p>🔑 <b>Measured cost, not a hypothetical:</b> F2-300 measured {@code bcm.bcm_exercises} by
     * name, got 14 rows and three CHECK constraints back, and drew <b>four wrong conclusions</b>
     * about a register whose entity maps {@code bcm.exercises}. The orphan sorts FIRST in a name
     * search. {@code compliance.compliance_findings} is the one to fear next: 18 rows against the
     * live {@code compliance.findings}' 41, so measuring the shadow under-reports an audit-findings
     * register by more than half — wrong in the flattering direction.
     *
     * <h4>The shape alone is not the defect, which is why this is a named set</h4>
     *
     * <b>54 mapped tables use the same schema-prefix shape with no bare twin at all</b>
     * ({@code bcm.bcm_plans}, {@code cyber.cyber_controls}, {@code evidence.evidence_items} …), so
     * it is house style. What makes a pair dangerous is the shape <em>plus</em> exactly one side
     * being unmapped. Of the six such pairs in the register, five are these, one has <b>both</b>
     * sides mapped ({@code cyber.cyber_incidents} 26 rows beside {@code cyber.incidents} 48 — two
     * entities in two modules over one concept, F2-196's shape, filed as an owner decision), and
     * <b>none</b> runs the other way round with the prefixed side live.
     *
     * <p>Asserted <b>exactly</b> rather than as a ceiling: a new shadow must fail, and a resolved
     * one must be deleted from here in the same commit, because a ceiling would let a new shadow in
     * behind a dropped one and make a tolerated pair indistinguishable from a reviewed one
     * (house rule 3).
     */
    private static final Map<String, String> NAME_SHADOWS = Map.of(
        "bcm.bcm_exercises",                 "bcm.exercises",
        "compliance.compliance_assessments", "compliance.assessments",
        "compliance.compliance_findings",    "compliance.findings",
        "workflow.workflow_definitions",     "workflow.definitions",
        "workflow.workflow_instances",       "workflow.instances");

    /**
     * Anti-vacuity for {@link #theShadowRuleIgnoresTheHouseStylePrefix}: the schema-prefix shape is
     * used legitimately 54 times. If this scan ever sees a handful, the prefix test broke and the
     * "it is only a defect when one side is unmapped" narrowing would be asserted over nothing.
     */
    private static final int MIN_LEGITIMATE_PREFIXED_TABLES = 40;

    /**
     * Anti-vacuity for the two hard gates: 88 tables are created by migrations above the baseline
     * today. If this scan ever sees a handful, the {@code CREATE TABLE} parse or the version filter
     * broke, and both gates would report a clean zero over an empty population — the direction a
     * broken scan always fails in.
     */
    private static final int MIN_TABLES_CREATED_ABOVE_BASELINE = 50;

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
    private static final Pattern INSERT_INTO = Pattern.compile(
        "INSERT\\s+INTO\\s+\\[?(\\w+)\\]?\\s*\\.\\s*\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_TABLE = Pattern.compile(
        "ALTER\\s+TABLE\\s+\\[?(\\w+)\\]?\\s*\\.\\s*\\[?(\\w+)\\]?", Pattern.CASE_INSENSITIVE);

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
     * F2-141's asked-for <b>schema-side companion scan</b>, and the forward half of the answer to
     * "should these tables exist?".
     *
     * <p>Every gate in this class until now asked about <em>mapped</em> tables. That is the blind
     * spot F2-140 and F2-141 both name: <b>a control that asks about mapped things cannot see
     * unmapped ones, and its green reading is not evidence of absence.</b> Thirty-six tables carry
     * 381 rows the application cannot read, and nothing in the build had an opinion about a
     * thirty-seventh.
     *
     * <p>This asserts the one thing the source tree can settle without a database and without an
     * owner decision: <b>a migration above the Flyway baseline may not create a table that no JPA
     * mapping names.</b> The baseline matters because DDL at or below V292 is inert on a baselined
     * deployment — the 33 dead tables that <em>are</em> in migrations all sit at V1–V123, authored
     * before the modular monolith existed. So this gate says nothing about them and everything about
     * what ships next.
     *
     * <p>Measured 2026-08-06: <b>88 tables created above the baseline, 88 mapped, zero violations.</b>
     * A hard zero rather than a ratchet, because a new migration creating dead schema is never
     * correct and there is no population to work down.
     *
     * <p>⚠ <b>Its sibling {@link #everyTableCreatedAboveTheBaselineIsInTheCommittedRegister} was
     * carved out of this method after a mutation.</b> Both lived here, and injecting one scratch
     * migration creating an unmapped, unregistered table failed on <em>unmapped</em> and never
     * reached the register leg — AssertJ stops at the first failure, so the second assertion was
     * unverifiable by the only mutation that reaches it. Third time in this programme that two
     * assertions in one test let the first mask the second.
     */
    @Test
    void noMigrationAboveTheBaselineCreatesATableNothingMaps() throws IOException {
        Map<String, List<Integer>> aboveBaseline = tablesCreatedAboveBaseline();
        Map<String, String> mapped = mappedTables();

        List<String> unmapped = new ArrayList<>();
        for (var e : aboveBaseline.entrySet()) {
            if (!mapped.containsKey(e.getKey())) {
                unmapped.add("%s  created by V%s".formatted(e.getKey(), e.getValue()));
            }
        }

        assertThat(unmapped).as("""
            %d table(s) are created by a migration ABOVE the Flyway baseline (V%d) and named by no \
            JPA mapping. Every customer built from these migrations gets them, empty, and no code \
            path can read a row. That is how the platform accumulated the 36 tables holding 381 \
            unreadable rows that F2-141 is about — this gate exists so the count cannot grow.

            Either map the table, or do not create it. If it is deliberately unmapped — a pure \
            reference table read only by SQL, say — declare it in \
            TSS-GRC-Platform-Bible/Appendix/table_disposition_exceptions.csv with an owner and a \
            date, regenerate the register, and it stops being invisible.

            %s""".formatted(unmapped.size(), FLYWAY_BASELINE, String.join("\n            ", unmapped)))
            .isEmpty();
    }

    /**
     * The staleness half, in its own method so the mutation that reaches it is not swallowed by the
     * one above — see that method's javadoc.
     *
     * <p>{@link #theRegisterIsWellFormedAndAccountsForEveryMappedTable} catches staleness from the
     * <em>mapped</em> side. This catches it from the <em>migration</em> side, which is the side that
     * matters for a table nothing maps: an unmapped table is invisible to the mapped-side check by
     * construction, and that is F2-140's and F2-141's shared lesson stated as a gate.
     */
    @Test
    void everyTableCreatedAboveTheBaselineIsInTheCommittedRegister() throws IOException {
        Set<String> registered = new LinkedHashSet<>();
        for (String[] f : registerRows()) {
            registered.add(key(f[0].trim(), f[1].trim()));
        }

        List<String> unregistered = new ArrayList<>();
        for (var e : tablesCreatedAboveBaseline().entrySet()) {
            if (!registered.contains(e.getKey())) {
                unregistered.add("%s  created by V%s".formatted(e.getKey(), e.getValue()));
            }
        }

        assertThat(unregistered).as("""
            %d table(s) created above the baseline have no row in the committed register, so the \
            register is STALE and every population gate in this class is reading a world that no \
            longer exists. Regenerate it:

              py TSS-GRC-Platform-Bible/Appendix/scripts/table_xref.py

            %s""".formatted(unregistered.size(), String.join("\n            ", unregistered)))
            .isEmpty();
    }

    /**
     * The second half of the forward guard: <b>do not ship demo ballast to a customer.</b>
     *
     * <p>This is the mechanism by which production came to hold 381 rows nothing reads. Fifteen of
     * the 36 dead tables are written by a migration — {@code V120}–{@code V123} insert governance
     * demo data into {@code compliance.compliance_findings}, {@code bcm_lc.drills},
     * {@code privacy_lc.ropa_activities} and others — and all four sit below the baseline, so on the
     * deployed server they never ran and the rows arrived by dump restore instead. On a genuinely
     * fresh install they <em>would</em> run.
     *
     * <p>⚠ Several of those inserts cannot even work: this repo has measured that SQL Server's
     * deferred name resolution covers a missing <em>table</em> but not a missing <em>column</em>, so
     * 7 of 7 "guarded" V121 demo inserts were compile-time failures whose {@code CATCH} had never
     * printed. A migration writing to a table the application cannot read is therefore either dead
     * weight or a silent failure — never a feature.
     *
     * <p>Measured 2026-08-06: <b>zero</b> INSERT and <b>zero</b> ALTER above the baseline against
     * any of the 36. Hard zero, same reasoning as the gate above.
     */
    @Test
    void noMigrationAboveTheBaselineWritesToATableTheApplicationCannotRead() throws IOException {
        Set<String> deadTables = noCodeTables();
        assertThat(deadTables).as(
            "The register classifies no table as %s. Either every dead table was dropped — in which "
            + "case lower BASELINE_NO_CODE_TABLES and delete this floor — or the `code` column moved "
            + "and this gate is checking an empty set.", NO_CODE_CODES)
            .isNotEmpty();

        List<String> writes = new ArrayList<>();
        try (Stream<Path> files = Files.list(migrationDir())) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).sorted().toList()) {
                Matcher nm = MIGRATION_NAME.matcher(p.getFileName().toString());
                if (!nm.matches() || Integer.parseInt(nm.group(1)) <= FLYWAY_BASELINE) {
                    continue;
                }
                String sql = stripSqlComments(Files.readString(p));
                for (Pattern rx : List.of(INSERT_INTO, ALTER_TABLE)) {
                    Matcher m = rx.matcher(sql);
                    while (m.find()) {
                        String table = key(m.group(1), m.group(2));
                        if (deadTables.contains(table)) {
                            writes.add("%s writes to %s".formatted(p.getFileName(), table));
                        }
                    }
                }
            }
        }

        assertThat(writes.stream().distinct().toList()).as("""
            A migration above the Flyway baseline (V%d) writes to a table the application cannot \
            read — no JPA mapping, no Java reference. Every customer would receive those rows and no \
            screen, export or API could ever show them.

            If the data matters, the table needs a mapping first (and then it is no longer in this \
            set). If it does not, do not ship it.

            %s""".formatted(FLYWAY_BASELINE, String.join("\n            ", writes.stream().distinct().toList())))
            .isEmpty();
    }

    /**
     * The debt itself, recorded so that dropping it registers as progress — see
     * {@link #BASELINE_NO_CODE_TABLES} for why this one is a ratchet and its two companions are
     * hard zeros.
     *
     * <p>It also catches the one path the hard gates cannot see: a table added by the <b>Python
     * bootstrap</b> rather than by a migration. {@code setup_bcm_db.py} and friends create dev
     * schema directly, so a new dead table can appear there with no migration to fail — but the next
     * register regeneration would count 37, and this fires.
     */
    @Test
    void theNoCodeTablePopulationOnlyEverDecreases() throws IOException {
        Map<String, String> dead = new TreeMap<>();
        for (String[] f : registerRows()) {
            if (NO_CODE_CODES.contains(f[2].trim().toUpperCase(Locale.ROOT))) {
                dead.put(key(f[0].trim(), f[1].trim()), f[2].trim() + "  rows dev/prod " + f[4].trim()
                         + "/" + f[5].trim());
            }
        }

        assertThat(dead.size()).as("""
            %d tables in the committed register are classified %s — no JPA mapping and no Java \
            reference anywhere — against a baseline of %d.

            If it went UP, something added a table the application cannot read. A migration would \
            have failed noMigrationAboveTheBaselineCreatesATableNothingMaps, so this is almost \
            certainly the Python bootstrap (setup_*.py) or a hand-run DDL. Do not raise this number \
            to make it pass.

            If it went DOWN, a drop or a mapping landed: lower the baseline, which is the ratchet \
            working. The 36 are historical debt from before the modular monolith and their DROP is \
            owner-authorised (house rule 6), which is why this is a ratchet and not a zero.

            %s""".formatted(dead.size(), NO_CODE_CODES, BASELINE_NO_CODE_TABLES,
                            dead.entrySet().stream()
                                .map(e -> e.getKey() + "  " + e.getValue())
                                .reduce((a, b) -> a + "\n            " + b).orElse("")))
            .isLessThanOrEqualTo(BASELINE_NO_CODE_TABLES);
    }

    /**
     * F2-301 — every unmapped table whose name shadows a mapped one is declared in
     * {@link #NAME_SHADOWS}, in both directions.
     *
     * <p>The population is derived, never listed: the register supplies the tables, reflection over
     * the compiled {@code @Table}/{@code @JoinTable}/{@code @CollectionTable} annotations supplies
     * the mappings, and the pair is formed by the prefix rule. ⚠ A source grep for {@code @Table}
     * alone reported <b>seven</b> pairs here, because it cannot see a {@code @JoinTable}:
     * {@code iam.role_permissions} and {@code iam.user_roles} are mapped by {@code Role} and
     * {@code User} and are not shadows at all. That is the same over-reporting the
     * {@link #mappedTables()} javadoc warns about, so the population comes from reflection.
     */
    @Test
    void everyNameShadowingAMappedTableIsDeclared() throws IOException {
        Map<String, String> mapped = mappedTables();
        Map<String, String> found = new TreeMap<>();

        for (String[] f : registerRows()) {
            String schema = f[0].trim().toLowerCase(Locale.ROOT);
            String table = f[1].trim().toLowerCase(Locale.ROOT);
            String shadow = shadowOf(schema, table, mapped.keySet());
            if (shadow != null && !mapped.containsKey(key(schema, table))) {
                found.put(key(schema, table), shadow);
            }
        }

        assertThat(found).as("""
            The set of tables whose NAME shadows a mapped table has changed.

            A table that does not exist makes a query FAIL; one with a confusable name makes it             SUCCEED with the wrong rows, which is why this is asserted separately from the             BASELINE_NO_CODE_TABLES count that already includes all five.

            If an entry APPEARED: something shipped `<schema>_<thing>` into a schema that already             has `<thing>`. Rename it, or map it — do not leave two confusable names, because the             next reader measuring by name will get the wrong one (F2-300 did).

            If an entry VANISHED: the shadow was dropped or mapped. Delete it from NAME_SHADOWS in             the same commit — that is the gate working.

            derived: %s
            declared: %s""".formatted(found, NAME_SHADOWS))
            .containsExactlyInAnyOrderEntriesOf(NAME_SHADOWS);
    }

    /**
     * The narrowing is the whole instrument: {@code <schema>_<thing>} is <b>house style</b>, used by
     * 54 mapped tables with no bare twin, so a rule that fired on the shape alone would fail on
     * correct code — the one failure a gate must never have. It fires only when the prefixed table
     * is unmapped and its bare twin is mapped.
     *
     * <p>⚠ The mutation harness refuted this leg's first claim, which said a looser prefix rule
     * "reported all 34" legitimate parent/child pairs. It does not: those 34 are <b>mapped on both
     * sides</b>, and this gate's left side is always unmapped — so measured over today's register a
     * generic prefix/suffix rule and this narrow one return the <b>same five pairs</b>. The
     * narrowing is therefore <b>prospective</b>, and the only thing carrying it is the synthetic
     * corpus below. 🔑 A widening that cannot change today's answer still fails silently on the
     * first table that does not follow house style, and on a clean tree a rule that matches nothing
     * and a rule that is correct produce the same green.
     */
    @Test
    void theShadowRuleIgnoresTheHouseStylePrefix() {
        Set<String> mapped = Set.of("bcm.exercises", "ai_governance.ml_model", "cyber.incidents");

        // must match: the schema's own name prefixed onto a table that schema maps
        assertThat(shadowOf("bcm", "bcm_exercises", mapped)).isEqualTo("bcm.exercises");

        // must NOT match: a parent/child pair, where the prefix is not the schema name. That is the
        // shape 34 legitimate register pairs have, and a looser prefix/suffix rule reported all 34.
        assertThat(shadowOf("ai_governance", "ml_model_audit", mapped)).isNull();
        // must NOT match: a bare table whose prefixed sibling is the mapped one. No such case exists
        // today (measured 0), and the rule must not invent one by matching backwards.
        assertThat(shadowOf("cyber", "incidents", mapped)).isNull();
        // must NOT match: prefixed, but the bare twin is not a table anything maps.
        assertThat(shadowOf("bcm", "bcm_plans", mapped)).isNull();
        // must NOT match: a prefix that is NOT this schema's own name. Only this case fails a rule
        // that strips the first segment rather than the schema name — the harness showed the
        // ml_model_audit case above kills a TRAILING widening and lets a LEADING one through,
        // because `ml_model_audit` minus its first segment is `model_audit`, which nothing maps.
        assertThat(shadowOf("cyber", "socops_incidents", mapped)).isNull();

        // …and the legitimate population it deliberately ignores is large, so the narrowing is
        // load-bearing rather than decorative.
        Map<String, String> real = mappedTables();
        Set<String> housePrefix = real.keySet().stream()
            .filter(k -> {
                int dot = k.indexOf('.');
                String schema = k.substring(0, dot);
                String table = k.substring(dot + 1);
                return table.startsWith(schema + "_")
                    && !real.containsKey(key(schema, table.substring(schema.length() + 1)));
            })
            .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        assertThat(housePrefix.size()).as(
            "Mapped tables using the `<schema>_<thing>` shape with no bare twin: %d. This is the "
            + "population the shadow rule must never flag. If it collapsed, the prefix test broke "
            + "and everyNameShadowingAMappedTableIsDeclared is checking almost nothing.",
            housePrefix.size())
            .isGreaterThanOrEqualTo(MIN_LEGITIMATE_PREFIXED_TABLES);

        // ⚠ That floor alone cannot see the no-bare-twin condition being dropped: without it the
        // count goes 54 -> 55, so the WRONG answer is HIGHER than the right one and no floor can
        // separate them (F2-215's rule, arriving from the other side — a floor is only worth having
        // if it sits above the largest wrong answer, and here it cannot). Assert the condition
        // itself: cyber.cyber_incidents is mapped AND has a mapped bare twin, so it is the one
        // prefixed table that must never be counted as house style. An upper bound would have been
        // the obvious fix and would fail the day a legitimate prefixed table is added.
        assertThat(housePrefix).as(
            "cyber.cyber_incidents has a mapped bare twin (cyber.incidents), so it is not an "
            + "instance of the no-twin house style. Finding it here means the no-bare-twin "
            + "condition was dropped and this floor is measuring the wrong population.")
            .doesNotContain("cyber.cyber_incidents");
    }

    /**
     * {@code schema.table} of the mapped twin this table's name could be mistaken for, or
     * {@code null}. The rule is deliberately the narrow one — the schema's own name prefixed onto a
     * table the same schema maps — and never a generic prefix/suffix variant, which matches 34
     * legitimate parent/child pairs ({@code ml_model_audit} beside {@code ml_model}).
     */
    private static String shadowOf(String schema, String table, Set<String> mappedKeys) {
        String prefix = schema + "_";
        if (!table.startsWith(prefix)) {
            return null;
        }
        String bare = table.substring(prefix.length());
        return bare.isEmpty() || !mappedKeys.contains(key(schema, bare)) ? null : key(schema, bare);
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

    /**
     * Tables created by a migration <em>above</em> the Flyway baseline, with the versions.
     *
     * <p>Carries its own anti-vacuity floor, because both callers assert an <em>emptiness</em>: if
     * the {@code CREATE TABLE} parse or the version filter ever broke, each would iterate nothing
     * and report a clean zero. A shared population needs the floor in the shared place.
     */
    private static Map<String, List<Integer>> tablesCreatedAboveBaseline() throws IOException {
        Map<String, List<Integer>> out = new TreeMap<>();
        for (var e : createdByMigration().entrySet()) {
            List<Integer> above = e.getValue().stream().filter(v -> v > FLYWAY_BASELINE).sorted().toList();
            if (!above.isEmpty()) {
                out.put(e.getKey(), above);
            }
        }

        assertThat(out.size()).as(
            "Only %d tables parsed as created by a migration above V%d. Under %d means the "
            + "CREATE TABLE parse or the version filter broke, and every caller would read a clean "
            + "zero over an empty population.",
            out.size(), FLYWAY_BASELINE, MIN_TABLES_CREATED_ABOVE_BASELINE)
            .isGreaterThanOrEqualTo(MIN_TABLES_CREATED_ABOVE_BASELINE);
        return out;
    }

    // ── F2-09 — does the migration folder build the schema the platform runs? ──

    /**
     * The three tables live in dev that <b>no migration creates</b> — F2-140's residue, and after
     * V361 closed the column half, all of it.
     *
     * <p>A <b>pinned set, not a count</b>. A count of three tolerates any three, so swapping one for
     * a newly-arrived bootstrap-only table would keep a count-ratchet green — and "a new table the
     * migrations do not create" is the entire defect. Each of these is authorised only by the reason
     * beside it in the register: no JPA entity, no Java reference, sole writer a seed script.
     * ADR-017 D-2 decided against adding DDL for them, so they stay listed here rather than fixed.
     */
    private static final Set<String> LIVE_WITH_NO_CREATING_MIGRATION = Set.of(
        "compliance.compliance_assessments",   // 10 rows dev / 10 prod, seed_gov_compliance.py
        "compliance.compliance_findings",      // 18 / 18
        "compliance.evidence_items");          // 83 / 83

    /**
     * ⚠ <b>This used to be a tolerated set of three, on a false premise.</b> It listed
     * {@code risk.kri_definitions}, {@code kri_readings} and {@code kri_breach_events} as "created by
     * a migration and absent from dev", with a javadoc claiming <i>"the drop lives in a bootstrap
     * script"</i>. It does not: <b>V247 and V337 drop them as migrations</b>. The scan was
     * CREATE-only, so it saw the {@code CREATE TABLE} in V1 and never the {@code DROP TABLE} that
     * retires it. Found by running the two-database convergence diff, which reported <b>0 extra
     * tables</b> in the Flyway-built database — the chain drops them exactly as it should, and my
     * hermetic gate was the thing that disagreed with reality.
     *
     * <p>With DROP subtracted the population is <b>empty</b>, so this is now a hard zero rather than
     * a set — see {@link #everyMigrationCreatedTableIsLiveOrDeliberatelyRetired()}. Nine tables are
     * net-dropped by the migration set today: the six legacy {@code platform.workflow_*} tables the
     * workflow engine replaced (V114) and the three KRI tables (V337).
     */
    private static final int MIN_NET_CREATED_TABLES = 400;

    /** 470 today, in both directions. Under this and a scan broke rather than a platform shrank. */
    private static final int MIN_CONVERGENCE_POPULATION = 400;

    /**
     * F2-09 — every table the platform actually has must be created by some migration.
     *
     * <p>The measurement F2-09 was written for needs two databases: one built by Flyway from `V1`,
     * one bootstrapped by Python. {@code Appendix/scripts/schema_convergence.py} does that and takes
     * minutes. This is the hermetic half, and it answers the same question from committed evidence
     * only — the register (a generated snapshot of both live schemas) against the migration folder —
     * so it runs in {@code mvn -o clean package} with no SQL Server. What it cannot see is column
     * definitions and constraint bodies; that is why the script still exists.
     *
     * <p>🔑 <b>Why a set-based check and not the obvious count.</b> Measured 2026-08-06: the
     * migration set CREATEs <b>470</b> distinct tables and the register lists <b>470</b> live in dev.
     * The totals are identical <em>and three differ in each direction</em>. Anyone comparing the two
     * numbers — the cheapest possible version of this test — would have concluded the folder was
     * complete, which is precisely how F2-70 read as closed while F2-140 was true.
     */
    @Test
    void everyLiveTableIsCreatedBySomeMigration() throws IOException {
        Set<String> created = netCreatedByMigration();
        Set<String> live = liveTablesPerRegister();

        List<String> residue = live.stream().filter(t -> !created.contains(t)).sorted().toList();

        assertThat(residue).as("""
            %d live table(s) are created by no migration in the folder, and this set is pinned. \
            A fresh installation provisioned from the migrations would not have them.

            Either add the DDL, or — if the table is genuinely bootstrap-only ballast that no Java \
            reads — regenerate the register and add it to LIVE_WITH_NO_CREATING_MIGRATION with its \
            reason. Do not raise a count: the population is named on purpose, because a count of \
            three tolerates any three.

            unexpected: %s""".formatted(residue.size(),
                residue.stream().filter(t -> !LIVE_WITH_NO_CREATING_MIGRATION.contains(t)).toList()))
            .containsExactlyInAnyOrderElementsOf(LIVE_WITH_NO_CREATING_MIGRATION);
    }

    /**
     * The other direction, separately — because two assertions in one method let the first mask the
     * second, which has happened three times in this programme.
     *
     * <p>A <b>hard zero</b>, not a tolerated set, now that the scan subtracts DROPs: every table the
     * migration set leaves standing must exist in dev. A table that is created and later dropped by
     * migrations is correct history fully expressed in migrations, and does not belong in a
     * findings-shaped list.
     */
    @Test
    void everyMigrationCreatedTableIsLiveOrDeliberatelyRetired() throws IOException {
        Set<String> created = netCreatedByMigration();
        Set<String> live = liveTablesPerRegister();

        List<String> orphans = created.stream().filter(t -> !live.contains(t)).sorted().toList();

        assertThat(orphans).as("""
            %d table(s) are left standing by the migration set and are absent from the live dev \
            schema. Either dev dropped something the migrations still create — in which case the \
            DROP belongs in a migration too, or a fresh install will carry a table nobody uses — or \
            the register is stale.

            Do NOT resolve this by adding names to an allow-list: this assertion is a zero on \
            purpose, and it became one because the previous tolerated set of three turned out to be \
            an artefact of a CREATE-only scan.

            %s""".formatted(orphans.size(), orphans))
            .isEmpty();
    }

    /**
     * The residue is tolerable <b>only</b> because nothing reads those tables.
     *
     * <p>This is the assertion that makes the toleration honest rather than permanent. The day one of
     * the three gains a JPA entity or a Java reference, "no migration creates it" stops being dead
     * ballast and becomes a fresh install that cannot serve a request — F2-146's shape exactly, where
     * thirteen columns `AuditFinding` maps by name were absent from every migration. The register
     * counts those references, so the check costs nothing and fires on the commit that introduces the
     * problem rather than on the first customer provisioning.
     */
    @Test
    void nothingInTheResidueMayAcquireAReader() throws IOException {
        List<String> nowRead = new ArrayList<>();
        int checked = 0;

        for (String[] f : registerRows()) {
            String table = key(f[0].trim(), f[1].trim());
            if (!LIVE_WITH_NO_CREATING_MIGRATION.contains(table)) {
                continue;
            }
            checked++;
            int jpa = parseCount(f[6]);
            int refs = parseCount(f[7]);
            if (jpa > 0 || refs > 0) {
                nowRead.add("%s (jpa_files=%d, code_refs=%d)".formatted(table, jpa, refs));
            }
        }

        assertThat(checked).as(
            "Only %d of the %d pinned residue tables were found in the register. The register is "
            + "stale or a name changed, and this test would otherwise check nothing.",
            checked, LIVE_WITH_NO_CREATING_MIGRATION.size())
            .isEqualTo(LIVE_WITH_NO_CREATING_MIGRATION.size());

        assertThat(nowRead).as("""
            %s now has a reader, and no migration creates it. That combination ships an installation \
            where the code queries a table the schema does not have.

            Write the DDL as a new migration and remove the table from \
            LIVE_WITH_NO_CREATING_MIGRATION. Do not resolve this by deleting the entity unless the \
            reference really was a mistake.""".formatted(nowRead))
            .isEmpty();
    }

    private static final Pattern DROP_TABLE = Pattern.compile(
        "DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?\\[?(\\w+)\\]?\\s*\\.\\s*\\[?(\\w+)\\]?",
        Pattern.CASE_INSENSITIVE);

    /**
     * Tables the migration set leaves <b>standing</b> — every table whose last {@code CREATE}/
     * {@code DROP} act, in migration order, is a CREATE.
     *
     * <p>⚠ <b>Ordering is (version, offset-in-file), and the offset half is load-bearing.</b> Comparing
     * versions alone reports a table as dropped when one migration does
     * {@code DROP TABLE x; CREATE TABLE x;} — a recreate — because both acts share a version.
     * Measured: a version-only rule moved {@code bcm.crisis_events} (V102) and
     * {@code platform.controls} (V93) into the "not created by any migration" bucket, <b>inventing
     * two defects</b> on tables that are live, mapped and populated. That is the same failure as a
     * regex that matches too little: the scan is wrong in the alarming direction, and an alarming
     * wrong answer costs a reviewer's trust as surely as a reassuring one.
     *
     * <p>Comments are stripped with the ordinary collapsing stripper. ⚠ The first version of this
     * method used a length-preserving blanker instead, with a javadoc claiming that collapsing
     * "shifts every later offset, which can reorder two statements". <b>That is false</b>: deleting
     * text shifts all later offsets by the same amount, so the <em>relative</em> order of what
     * remains is unchanged — removing text cannot reorder text. Measured before deleting the helper:
     * swapping the blanker for {@link #stripSqlComments(String)} leaves all 14 tests green. What the
     * offsets are actually for is intra-file ordering, and any consistent stripping preserves it.
     */
    private static Set<String> netCreatedByMigration() throws IOException {
        // table -> {version, offset, wasCreate}
        Map<String, long[]> last = new TreeMap<>();
        try (Stream<Path> files = Files.list(migrationDir())) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).sorted().toList()) {
                Matcher nm = MIGRATION_NAME.matcher(p.getFileName().toString());
                if (!nm.matches()) {
                    continue;
                }
                long version = Long.parseLong(nm.group(1));
                String sql = stripSqlComments(Files.readString(p));
                record Act(Pattern rx, long isCreate) {}
                for (Act act : List.of(new Act(CREATE_TABLE, 1), new Act(DROP_TABLE, 0))) {
                    Matcher m = act.rx().matcher(sql);
                    while (m.find()) {
                        String table = key(m.group(1), m.group(2));
                        long[] prev = last.get(table);
                        if (prev == null || version > prev[0]
                            || (version == prev[0] && m.start() > prev[1])) {
                            last.put(table, new long[]{version, m.start(), act.isCreate()});
                        }
                    }
                }
            }
        }

        Set<String> standing = new TreeSet<>();
        for (var e : last.entrySet()) {
            if (e.getValue()[2] == 1) {
                standing.add(e.getKey());
            }
        }
        assertThat(standing.size()).as(
            "Only %d tables are left standing by the migration set. Under %d means the CREATE or "
            + "DROP parse broke, and both callers assert over a difference against this set.",
            standing.size(), MIN_NET_CREATED_TABLES)
            .isGreaterThanOrEqualTo(MIN_NET_CREATED_TABLES);
        return standing;
    }


    /**
     * Tables the register reports as present in dev.
     *
     * <p>{@code rows_dev} is {@code ABSENT} for a table JPA maps that neither database has (F2-64's
     * population, 1 row today) and a number otherwise — <b>including 0</b>, which is a real, empty,
     * existing table. ⚠ <b>45 of the 470 live tables are empty</b>, among them
     * {@code bcm.crisis_comms_cascade} (F2-147) and six that pending migrations created in dev only.
     * Treating "0 rows" as "does not exist" is the plausible tidy-up, and it moves all 45 into the
     * orphan list — mutation-tested: the count goes 3 → 48. Emptiness is not absence.
     *
     * <p>Carries the anti-vacuity floor because both callers assert over a difference: an empty
     * {@code live} makes the residue empty and the orphan list total.
     */
    private static Set<String> liveTablesPerRegister() throws IOException {
        Set<String> out = new TreeSet<>();
        for (String[] f : registerRows()) {
            if (!"ABSENT".equalsIgnoreCase(f[4].trim())) {
                out.add(key(f[0].trim(), f[1].trim()));
            }
        }
        assertThat(out.size()).as(
            "Only %d live tables read from the register. Under %d means a truncated register or a "
            + "broken rows_dev read, and the convergence assertions would compare against nothing.",
            out.size(), MIN_CONVERGENCE_POPULATION)
            .isGreaterThanOrEqualTo(MIN_CONVERGENCE_POPULATION);
        return out;
    }

    /** A register count cell — blank or non-numeric reads as 0 rather than throwing. */
    private static int parseCount(String cell) {
        String v = cell == null ? "" : cell.trim();
        try {
            return v.isEmpty() ? 0 : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The register's {@link #NO_CODE_CODES} rows as {@code schema.table} keys. */
    private static Set<String> noCodeTables() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String[] f : registerRows()) {
            if (NO_CODE_CODES.contains(f[2].trim().toUpperCase(Locale.ROOT))) {
                out.add(key(f[0].trim(), f[1].trim()));
            }
        }
        return out;
    }

    /**
     * ⚠ Strip comments before asserting a file does <em>not</em> do something. No textual shape
     * distinguishes code from a comment quoting code, and this programme has read a comment as code
     * four times in one day — a header naming a table, a whole migration that was commentary, and
     * twice in prose. The direction that failure runs in is the reassuring one: a commented-out
     * {@code INSERT INTO bcm_lc.drills} would report a violation that is not there, and a reviewer
     * who checks and finds nothing learns to distrust the gate.
     */
    private static String stripSqlComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ");
    }
}
