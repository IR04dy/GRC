package com.tss.grc.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W0-7 — what is actually running here, and what has actually been applied.
 *
 * <h3>Why this exists</h3>
 * <p>The plan that owns this item says it plainly: W0-7 is *"a precondition for this plan ever again
 * asserting that anything 'lands on the next restart'. It would have made this claim impossible to
 * write."* The failure it is built for already happened — master carried a fix for a defect failing
 * 33–35×/day in production while production kept serving a jar from six days earlier, and nothing
 * anyone could query would have said so. Merging is not deploying, and until now the running
 * application could not tell you which of the two it was.
 *
 * <p>{@code /actuator/info} answered <b>{@code {}}</b> before this class. Not "a bit thin" — empty,
 * despite {@code management.info.app.*} being configured in {@code application.yml}, because Spring
 * Boot 3 ships the {@code env} contributor <em>disabled</em>. So the one endpoint whose job is to say
 * what this is had been quietly saying nothing.
 *
 * <h3>What it reports, and why each field earns its place</h3>
 * <ul>
 *   <li><b>{@code migrations.packagedHead} / {@code packagedCount}</b> — read from the classpath, so
 *       it describes <em>this artifact</em>. This is the fingerprint a post-deploy probe compares
 *       against the working tree: same head and same count means the jar you built is the jar that is
 *       running.</li>
 *   <li><b>{@code migrations.appliedHead}</b> — read from {@code flyway_schema_history}, so it
 *       describes <em>this database</em>. The gap between the two is the deployment gap, stated as a
 *       number instead of an assumption.</li>
 *   <li><b>{@code migrations.pending}</b> — the packaged versions above the applied head. When this
 *       is non-empty, "it will apply on the next restart" is a claim with evidence behind it.</li>
 * </ul>
 *
 * <h3>Exposure</h3>
 * <p>Measured before publishing any of it: production runs the app on {@code SERVER_PORT=8090} and
 * actuator on {@code MANAGEMENT_SERVER_PORT=8091}, a separate port that nginx does not proxy (it
 * forwards {@code /api} and {@code /ws} only). So this is an operator-local endpoint in production,
 * not an internet-facing one. Nothing here is a secret in any case — versions, counts and a build
 * timestamp — but "it is only version numbers" is how fingerprinting gets waved through, so the port
 * split is the actual reason it is safe, and it is a fact rather than a hope.
 */
@Slf4j
@Component
public class DeploymentInfoContributor implements InfoContributor {

    /** Flyway's own convention: V<version>__<description>.sql, version parts separated by dots. */
    private static final Pattern MIGRATION = Pattern.compile("^V(\\d+(?:[._]\\d+)*)__.*\\.sql$");
    private static final String MIGRATION_PATTERN = "classpath*:db/migration/sqlserver/V*.sql";

    private final JdbcTemplate jdbc;
    private final String historyTable;
    private final boolean flywayEnabled;

    public DeploymentInfoContributor(
            JdbcTemplate jdbc,
            @org.springframework.beans.factory.annotation.Value("${spring.flyway.table:flyway_schema_history}")
            String historyTable,
            @org.springframework.beans.factory.annotation.Value("${spring.flyway.enabled:true}")
            boolean flywayEnabled) {
        this.jdbc = jdbc;
        this.historyTable = historyTable;
        this.flywayEnabled = flywayEnabled;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> migrations = new LinkedHashMap<>();

        List<String> packaged = packagedVersions();
        migrations.put("packagedCount", packaged.size());
        migrations.put("packagedHead", packaged.isEmpty() ? null : packaged.get(packaged.size() - 1));
        migrations.put("flywayEnabled", flywayEnabled);

        // Found by building this class rather than by reading anything: two files in the migration
        // folder are named V46a__ and V47a__, and a Flyway version may only contain digits separated
        // by '.' or '_'. Flyway skips a name it cannot parse instead of failing, so these have never
        // been applied and never will be — they were added 2026-05-11 and migrations through V312
        // have applied since, which is the proof that Flyway does not choke on them. Reported here so
        // the class of file cannot hide again: a name that looks like a migration and silently is not.
        List<String> unversioned = unparseableMigrationFiles();
        if (!unversioned.isEmpty()) {
            migrations.put("unversionedFiles", unversioned);
            migrations.put("unversionedNote", unversioned.size() + " file(s) in the migration folder "
                + "cannot be parsed as a Flyway version, so Flyway skips them and their contents "
                + "have never been applied");
        }

        String applied = appliedHead();
        if (applied == null) {
            // Dev bootstraps its schema with Python scripts and runs with Flyway off, so the history
            // table genuinely does not exist. Say that, rather than reporting every packaged
            // migration as "pending" — which would be true of a fresh database and false here, and
            // an operator reading it on the wrong box would draw exactly the wrong conclusion.
            migrations.put("appliedHead", null);
            migrations.put("historyAvailable", false);
            migrations.put("note", flywayEnabled
                ? "no " + historyTable + " table yet — nothing has been applied through Flyway"
                : "Flyway is disabled in this profile; schema is managed outside it");
        } else {
            migrations.put("appliedHead", applied);
            migrations.put("historyAvailable", true);
            List<String> pending = packaged.stream()
                .filter(v -> compareVersions(v, applied) > 0)
                .toList();
            migrations.put("pendingCount", pending.size());
            migrations.put("pending", pending);
            if (!pending.isEmpty()) {
                // The sentence this whole item exists to make checkable.
                migrations.put("note", pending.size() + " packaged migration(s) above the applied "
                    + "head — they apply on the next start of this application");
            }
        }

        builder.withDetail("migrations", migrations);
    }

    /**
     * Files under the migration folder whose {@code V…__} prefix is not a legal Flyway version.
     * Flyway skips these silently, so they are dead weight that looks live.
     */
    private List<String> unparseableMigrationFiles() {
        List<String> bad = new ArrayList<>();
        try {
            for (Resource r : new PathMatchingResourcePatternResolver().getResources(MIGRATION_PATTERN)) {
                String name = r.getFilename();
                if (name != null && !MIGRATION.matcher(name).matches()) {
                    bad.add(name);
                }
            }
        } catch (IOException e) {
            log.debug("Could not enumerate migration folder: {}", e.getMessage());
        }
        bad.sort(String::compareTo);
        return bad;
    }

    /** Migration versions present in THIS artifact, ascending. */
    private List<String> packagedVersions() {
        List<String> versions = new ArrayList<>();
        try {
            Resource[] found = new PathMatchingResourcePatternResolver()
                .getResources(MIGRATION_PATTERN);
            for (Resource r : found) {
                String name = r.getFilename();
                if (name == null) continue;
                Matcher m = MIGRATION.matcher(name);
                if (m.matches()) {
                    versions.add(m.group(1).replace('_', '.'));
                }
            }
        } catch (IOException e) {
            // Never let the info endpoint fail: a probe that 500s tells an operator nothing about
            // the deployment and everything about this class.
            log.warn("Could not enumerate packaged migrations: {}", e.getMessage());
            return List.of();
        }
        versions.sort(DeploymentInfoContributor::compareVersions);
        return versions;
    }

    /**
     * Highest successfully-applied version in this database, or {@code null} when the history table
     * is absent. Read with SQL rather than through the {@code Flyway} bean on purpose: with
     * {@code spring.flyway.enabled=false} there is no such bean, and the dev profile runs that way.
     */
    private String appliedHead() {
        try {
            List<String> rows = jdbc.queryForList(
                "SELECT version FROM " + historyTable + " WHERE success = 1 AND version IS NOT NULL",
                String.class);
            return rows.stream().max(DeploymentInfoContributor::compareVersions).orElse(null);
        } catch (Exception e) {
            log.debug("Flyway history table '{}' not readable: {}", historyTable, e.getMessage());
            return null;
        }
    }

    /**
     * Flyway version ordering — numeric per dot-separated part, so V9 &lt; V10 (a string compare puts
     * V10 first and would report the wrong head the moment the count crossed a power of ten).
     */
    static int compareVersions(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            long l = i < left.length ? parse(left[i]) : 0;
            long r = i < right.length ? parse(right[i]) : 0;
            if (l != r) return Long.compare(l, r);
        }
        return 0;
    }

    private static long parse(String part) {
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Comparator form, for callers that want one. */
    static Comparator<String> versionOrder() {
        return DeploymentInfoContributor::compareVersions;
    }
}
