package com.tss.grc.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-49 — no migration file may exist that Flyway will never apply.
 *
 * <h3>What happened</h3>
 * <p>Two files sat in {@code db/migration/sqlserver/} for months and were <b>silently skipped on every
 * single run</b>: {@code V46a__cyber_pdpl_bridge_seed_fix.sql} and
 * {@code V47a__evidence_chain_broken_demo.sql}. Flyway's versioned-migration filename is
 * {@code V<version>__<description>.sql} where the version is digits separated by dots or underscores.
 * {@code 46a} is not a version, so neither file was ever a migration — it was an inert text file that
 * looked exactly like a migration, including in code review, including in the packaged jar.
 *
 * <p>Nothing failed. Flyway does not warn about files it cannot parse as versions; it ignores them. The
 * suffix reads like a deliberate hotfix ({@code 46a} "after 46"), which is why nobody questioned it, and
 * both were <b>packaged into every release artifact</b> — 302 migrations shipped, 300 applicable.
 *
 * <p>This is the same defect class as the staged jar that appeared to deliver migrations it did not carry,
 * which {@code UNIFIED_IMPLEMENTATION_PLAN.md} §1 calls the plan's most consequential error: <b>an artifact
 * that looks applied and is not is strictly worse than one that is visibly missing</b>, because the
 * missing one gets noticed.
 *
 * <h3>Why they were deleted rather than renumbered</h3>
 * <p>ADR-017 D-6 set the rule: renumber if the content is required, delete if it is already present.
 * Measured before choosing, in <b>both</b> databases rather than reasoning from the filenames — dev
 * {@code TSS_GRC} and the deployed {@code TSS_GRC_Deployment} each hold 4 {@code SEED_DEMO_V46}
 * bridge-log rows, 3 {@code CYBER_TO_PDPL_V1} breaches, 2 incidents flagged
 * {@code affects_personal_data}, and 1 evidence item at {@code chain_status = 'BROKEN'}. The content
 * exists on both sides, so nothing was lost.
 *
 * <p>⚠ <b>And renumbering would have been actively harmful.</b> Both files are <em>demo seed data</em>,
 * not schema: {@code V46a} inserts fabricated PDPL breach records and {@code V47a} deliberately corrupts
 * an evidence item's hash so a chain-of-custody demo has something to flag. Giving them valid versions
 * would have made Flyway inject fabricated breach records and a knowingly-broken chain of custody into
 * <b>every future production database</b> — in a GRC platform, where {@code chain_status = 'BROKEN'} is a
 * custody-failure indicator a regulator may read. The bad filename was accidentally load-bearing.
 * Demo data belongs in {@code seed_real_data.py}, which is where the rest of it lives.
 *
 * <h3>Why a test and not a review habit</h3>
 * <p>Code review had this in front of it for months. {@code /actuator/info} now reports
 * {@code unversionedFiles} at runtime (W0-7, which found these unprompted) — this test is the
 * build-time half, so the failure is caught before an artifact ships rather than after it is deployed.
 */
class MigrationFilenameRatchetTest {

    private static final Path DIR = Path.of("src", "main", "resources", "db", "migration", "sqlserver");

    /**
     * Flyway's versioned-migration pattern: {@code V}, a version of digits separated by {@code .} or
     * {@code _}, then {@code __} and a description. Repeatable migrations ({@code R__}) are also valid
     * Flyway but this project uses none, so they are not accepted here — allowing them would let a
     * repeatable migration be introduced without anyone deciding to.
     */
    private static final Pattern VERSIONED =
        Pattern.compile("^V(\\d+(?:[._]\\d+)*)__.+\\.sql$");

    private static List<Path> migrationFiles() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void everyMigrationFilenameIsOneFlywayWillApply() throws IOException {
        List<Path> files = migrationFiles();

        // Anti-vacuity first. A test that resolves no files passes every assertion below, which is the
        // failure mode four other measurements in this repository have already hit.
        assertThat(files)
            .as("no .sql files found under %s — the scan is broken, not the tree clean", DIR.toAbsolutePath())
            .hasSizeGreaterThan(250);

        List<String> unversioned = new ArrayList<>();
        for (Path p : files) {
            if (!VERSIONED.matcher(p.getFileName().toString()).matches()) {
                unversioned.add(p.getFileName().toString());
            }
        }

        assertThat(unversioned)
            .as("Flyway ignores files it cannot parse as a version — these would ship in the jar and "
              + "never run, exactly like V46a/V47a did for months. Either renumber to the next free "
              + "version, or delete the file if its content is already applied (ADR-017 D-6). Do NOT "
              + "renumber demo/seed content: that injects it into production. See the migration ledger "
              + "in UNIFIED_IMPLEMENTATION_PLAN.md §4 for the next free version.")
            .isEmpty();
    }

    /**
     * Two files may not claim the same version. Flyway applies one and errors on the other, and which
     * one it picks is not something the filename tells you.
     */
    @Test
    void noTwoMigrationsClaimTheSameVersion() throws IOException {
        List<String> versions = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (Path p : migrationFiles()) {
            Matcher m = VERSIONED.matcher(p.getFileName().toString());
            if (!m.matches()) continue;          // reported by the test above
            String v = m.group(1).replace('_', '.');
            if (versions.contains(v)) duplicates.add(v + " -> " + p.getFileName());
            else versions.add(v);
        }
        assertThat(duplicates)
            .as("duplicate migration versions — Flyway will apply one and fail on the other")
            .isEmpty();
        assertThat(versions).as("anti-vacuity: versions were parsed").hasSizeGreaterThan(250);
    }
}
