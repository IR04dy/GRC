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
 * <h3>⚠ F2-73 — this class was blind in production, and reported its blindness as good news</h3>
 *
 * <p>Found running W0-3's post-deploy verification on 2026-08-01. Production's
 * {@code /actuator/info} answered:
 *
 * <pre>{@code "historyAvailable": false,
 * "note": "no flyway_schema_history table yet — nothing has been applied through Flyway"}</pre>
 *
 * <p>while {@code platform.flyway_schema_history} held <b>45 successful rows through V336</b>, applied
 * at 16:47:23 the previous evening. The cause: {@code application-prod.yml} sets
 * {@code spring.flyway.default-schema: platform} — and its own comment says <em>"History table becomes
 * platform.flyway_schema_history"</em> — but {@link #appliedHead()} interpolated only
 * {@code spring.flyway.table}, <b>unqualified</b>. The connection's default schema for the app login is
 * {@code dbo}, which is empty, so the query raised {@code Invalid object name
 * 'flyway_schema_history'}, the {@code catch} logged it at {@code DEBUG} and returned {@code null}.
 *
 * <p><b>Why that is not cosmetic.</b> {@code pending} is computed only in the branch where
 * {@code appliedHead != null}. So in production {@code pending} was <em>always empty</em> — and
 * {@code Appendix/scripts/deployment_identity.py} raises its "packaged but NOT applied" problem from
 * exactly that field. The detector the plan calls RC-B's instrument could not fire in the only
 * environment RC-B is about. On the morning this was found, V337 and V338 were packaged-and-unapplied
 * and the probe reported no problem.
 *
 * <p><b>The shape of the bug is worth more than the bug.</b> The {@code null} branch was written for
 * development, where Flyway is off and the table genuinely does not exist, and its message is correct
 * there. In production the same {@code null} meant "I could not read it" and the message asserted
 * "nothing has been applied" — <b>a read failure rendered as a statement about the world.</b> So the
 * fix is two things, not one: qualify the table, and never again let unreadable be reported as empty.
 * {@code tableExists()} separates them, an unreadable history logs at {@code WARN} when Flyway is
 * enabled, and the payload carries the driver's own error instead of a reassuring sentence.
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
            // F2-73: without this, the table name below is unqualified and resolves to the
            // connection's default schema (dbo), where the history table has never lived in
            // production. Flyway itself creates the table inside `default-schema`.
            @org.springframework.beans.factory.annotation.Value("${spring.flyway.default-schema:}")
            String flywaySchema,
            @org.springframework.beans.factory.annotation.Value("${spring.flyway.enabled:true}")
            boolean flywayEnabled) {
        this.jdbc = jdbc;
        this.historyTable = (flywaySchema == null || flywaySchema.isBlank())
            ? historyTable
            : flywaySchema.trim() + "." + historyTable;
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
            // ⚠ F2-73: this used to state "nothing has been applied" for BOTH cases — a table that
            // is genuinely absent (dev, Flyway off, Python-bootstrapped) and a table this class
            // simply could not read (prod, where the name was unqualified and resolved to an empty
            // dbo). The two are opposite facts and only one of them is good news. Separate them,
            // and when Flyway is enabled and the history is unreadable, say so loudly: a blind
            // instrument must announce its blindness, because every consumer downstream —
            // deployment_identity.py's "packaged but NOT applied" check above all — reads absence
            // as "there is nothing to report".
            migrations.put("appliedHead", null);
            migrations.put("historyAvailable", false);
            String readError = historyReadError;
            if (readError != null && tableExists()) {
                migrations.put("historyReadable", false);
                migrations.put("note", "the " + historyTable + " table EXISTS but could not be read, "
                    + "so `pending` below is unknown rather than empty — this instrument is blind: "
                    + readError);
                if (flywayEnabled) {
                    log.warn("[W0-7] {} exists but is unreadable — /actuator/info cannot report the "
                        + "applied head or the pending set: {}", historyTable, readError);
                }
            } else {
                migrations.put("note", flywayEnabled
                    ? "no " + historyTable + " table yet — nothing has been applied through Flyway"
                    : "Flyway is disabled in this profile; schema is managed outside it");
            }
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
    /**
     * Set when {@link #appliedHead()} could not read the history table. Distinguishes "there is no
     * history" from "I cannot see the history", which F2-73 conflated into one reassuring sentence.
     */
    private volatile String historyReadError;

    private String appliedHead() {
        historyReadError = null;
        try {
            List<String> rows = jdbc.queryForList(
                "SELECT version FROM " + historyTable + " WHERE success = 1 AND version IS NOT NULL",
                String.class);
            return rows.stream().max(DeploymentInfoContributor::compareVersions).orElse(null);
        } catch (Exception e) {
            // DEBUG was how this hid for nine days. The caller decides the severity, because only
            // it knows whether the table exists — see the F2-73 note in the class Javadoc.
            historyReadError = describe(e);
            log.debug("Flyway history table '{}' not readable: {}", historyTable, historyReadError);
            return null;
        }
    }

    /**
     * The failure, described the way an operator can act on.
     *
     * <p>Spring wraps a driver error, and its own {@code getMessage()} is the useless half:
     * {@code "select; bad SQL grammar [SELECT version FROM flyway_schema_history]"}. The actionable
     * sentence — {@code "Invalid object name 'flyway_schema_history'"} — is down in the cause. So walk
     * to the root and carry both, because "bad SQL grammar" would have told nobody that the answer was
     * a missing schema qualifier.
     */
    private static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String outer = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String inner = root.getMessage();
        String combined = (inner == null || inner.equals(outer) || outer.contains(inner))
            ? outer
            : inner + " (" + outer + ")";
        return combined.replaceAll("\\s+", " ").trim();
    }

    /**
     * Does the history table exist at all? Asked only when the read failed, and asked through
     * {@code OBJECT_ID} so a permission error on the table itself still answers.
     */
    private boolean tableExists() {
        try {
            Integer id = jdbc.queryForObject("SELECT OBJECT_ID(?)", Integer.class, historyTable);
            return id != null;
        } catch (Exception e) {
            log.debug("could not test existence of '{}': {}", historyTable, e.getMessage());
            return false;
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
