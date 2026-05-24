package com.tss.grc.module.platform.health;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * SQL Server Express 10 GB size monitor (Open Issue #004).
 *
 * <p>SQL Server Express has a hard 10 GB per-database limit. Once reached, all
 * write operations fail and the platform effectively goes read-only — a noisy
 * surprise on a Saturday at 02:00. This scheduled job polls the database size
 * every 6 hours and emits graduated F8 events as we approach the cap.
 *
 * <h3>Thresholds</h3>
 * <table>
 *   <tr><th>Band</th><th>Default</th><th>Action</th></tr>
 *   <tr><td>WARN</td>  <td>7 168 MB (70%)</td><td>Email DBA, in-app banner for PLATFORM_ADMIN</td></tr>
 *   <tr><td>ALERT</td> <td>9 216 MB (90%)</td><td>WARN actions + Slack <code>#grc-ops</code> + create CAPA task</td></tr>
 *   <tr><td>HALT</td>  <td>9 728 MB (95%)</td><td>All ALERT actions + a global read-only flag (defensive — writes return HTTP 507)</td></tr>
 * </table>
 *
 * <p>The actual reaction (email/slack/banner/flag) is handled by the
 * AutomationRuleEngine listening for the {@code DB_SIZE_THRESHOLD_BREACHED}
 * trigger — this class is responsible only for measurement + emission, in
 * keeping with the F8 contract.
 *
 * <h3>Why the 95% halt threshold (not 100%)?</h3>
 * The SQL Server Express engine evicts pages out-of-band: actual cap-hit may
 * happen as early as 9.95 GB depending on auto-grow increments and free space
 * inside the file. A 95% defensive halt gives operations a 500 MB cushion to
 * shed log + temp data, drop legacy indexes, or schedule an emergency tenant
 * archive before write availability fails.
 *
 * <h3>How size is measured</h3>
 * Uses {@code sys.dm_db_partition_stats} (user data pages × 8 KB / 1024 → MB).
 * Excludes free space inside the data file (which doesn't count against the
 * Express cap) and excludes log space (the log file is separately bounded).
 *
 * @see DbSizeMonitorJobTest
 */
@Slf4j
@Component
public class DbSizeMonitorJob {

    /** F8 trigger emitted on any threshold breach. Severity in payload. */
    public static final String TRIGGER = "DB_SIZE_THRESHOLD_BREACHED";

    /** Audit action_type prefix for the audit-log row written on emission. */
    static final String AUDIT_TYPE_PREFIX = "DB_SIZE_";

    /**
     * Query returning DB size in MB. Sums the used-page count from
     * {@code sys.dm_db_partition_stats}, multiplies by the 8 KB page size,
     * divides by 1024. NOLOCK because we don't need a transactionally
     * consistent answer — a 1-row delta out of millions doesn't change the
     * threshold decision.
     */
    static final String SIZE_QUERY =
        "SELECT CAST(SUM(used_page_count) * 8.0 / 1024 AS DECIMAL(18,2)) AS size_mb " +
        "FROM sys.dm_db_partition_stats WITH (NOLOCK)";

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher publisher;
    private final AuditLogService audit;
    private final long warnMb;
    private final long alertMb;
    private final long haltMb;

    public DbSizeMonitorJob(JdbcTemplate jdbcTemplate,
                            ApplicationEventPublisher publisher,
                            AuditLogService audit,
                            @Value("${grc.health.db-size.warn-mb:7168}") long warnMb,
                            @Value("${grc.health.db-size.alert-mb:9216}") long alertMb,
                            @Value("${grc.health.db-size.halt-mb:9728}") long haltMb) {
        if (warnMb >= alertMb || alertMb >= haltMb) {
            throw new IllegalArgumentException(
                "DB size thresholds must be strictly ascending: warn < alert < halt. " +
                "Got warn=" + warnMb + " alert=" + alertMb + " halt=" + haltMb);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.publisher = publisher;
        this.audit = audit;
        this.warnMb = warnMb;
        this.alertMb = alertMb;
        this.haltMb = haltMb;
        log.info("DbSizeMonitorJob initialised: warn={}MB alert={}MB halt={}MB", warnMb, alertMb, haltMb);
    }

    /**
     * Every 6 hours, on the hour: 00:00, 06:00, 12:00, 18:00 UTC.
     * Override via {@code grc.health.db-size.check-cron}.
     */
    @Scheduled(cron = "${grc.health.db-size.check-cron:0 0 */6 * * *}")
    public void tick() {
        try {
            long sizeMb = measureSizeMb();
            Severity sev = classify(sizeMb);
            if (sev == Severity.OK) {
                log.debug("[db-size] {} MB — under WARN ({} MB)", sizeMb, warnMb);
                return;
            }
            emit(sev, sizeMb);
        } catch (Exception ex) {
            // A monitor that throws would silently disable itself. Log loudly
            // and let the next tick try again — the JdbcTemplate query is the
            // most likely failure mode (e.g. DB restart mid-tick).
            log.error("[db-size] monitor failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Run the size query and return the result in MB (rounded to the nearest
     * megabyte). Visible for testing.
     */
    long measureSizeMb() {
        Double sizeMb = jdbcTemplate.queryForObject(SIZE_QUERY, Double.class);
        return sizeMb == null ? 0L : Math.round(sizeMb);
    }

    /** Classify a measured size in MB against the configured thresholds. */
    Severity classify(long sizeMb) {
        if (sizeMb >= haltMb)  return Severity.HALT;
        if (sizeMb >= alertMb) return Severity.ALERT;
        if (sizeMb >= warnMb)  return Severity.WARN;
        return Severity.OK;
    }

    /**
     * Emit one F8 {@link DomainEvent} + one platform audit row.
     *
     * <p>Note: this is a <em>platform-wide</em> event, not tenant-scoped. The
     * cap is on the whole database, not on any single organisation. We pass
     * {@code organisationId = null} so the AutomationRuleEngine fans out to
     * platform-admin rules rather than tenant rules.
     */
    void emit(Severity sev, long sizeMb) {
        long thresholdMb = thresholdForSeverity(sev);
        double pctOfCap = sizeMb / 10240.0 * 100.0;   // 10 240 MB = 10 GB Express cap

        Map<String, Object> payload = new HashMap<>();
        payload.put("severity",        sev.name());
        payload.put("sizeMb",          sizeMb);
        payload.put("thresholdMb",     thresholdMb);
        payload.put("capMb",           10240L);
        payload.put("pctOfCap",        Math.round(pctOfCap * 100.0) / 100.0);
        payload.put("warnMb",          warnMb);
        payload.put("alertMb",         alertMb);
        payload.put("haltMb",          haltMb);

        DomainEvent de = new DomainEvent(
            null,                                       // platform-wide event
            TRIGGER,
            "Database",
            null,
            "db-size:" + sev.name() + ":" + sizeMb,
            payload);
        publisher.publishEvent(de);

        String msg = String.format(
            "DB size %d MB reached %s threshold (>=%d MB, %.2f%% of 10 GB Express cap)",
            sizeMb, sev.name(), thresholdMb, pctOfCap);

        // Log at appropriate level so ops alerts on ALERT/HALT but not WARN noise.
        switch (sev) {
            case WARN  -> log.warn("[db-size] {}", msg);
            case ALERT -> log.error("[db-size] {}", msg);
            case HALT  -> log.error("[db-size] HALT — {}", msg);
            default    -> { /* OK never emits */ }
        }

        try {
            audit.log(AUDIT_TYPE_PREFIX + sev.name(), "Database", null, msg);
        } catch (Exception auditEx) {
            // Audit failures must not stop the alert pathway — log and continue.
            log.warn("[db-size] audit log write failed: {}", auditEx.getMessage());
        }
    }

    private long thresholdForSeverity(Severity sev) {
        return switch (sev) {
            case WARN  -> warnMb;
            case ALERT -> alertMb;
            case HALT  -> haltMb;
            case OK    -> 0L;
        };
    }

    /** Threshold band classification. Public so the test can assert. */
    public enum Severity { OK, WARN, ALERT, HALT }
}
