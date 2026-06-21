package com.tss.grc.module.integrations.tenable.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.Vulnerability;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.integrations.tenable.client.TenableClient;
import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.dto.TenableFindingDto;
import com.tss.grc.module.integrations.tenable.entity.TenableFindingLog;
import com.tss.grc.module.integrations.tenable.repository.TenableFindingLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Polls Tenable for new findings and materialises each one as a
 * {@code cyber.vulnerabilities} row + an {@code integration_external_link}
 * mapping (integration='TENABLE') + a {@code tenable_finding_log}
 * audit row.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #pollFindings()} — manual / test-trigger, callable from
 *       the controller or unit tests. Runs against the current tenant in
 *       {@link TenantContext}.</li>
 *   <li>{@link #scheduledPoll()} — {@code @Scheduled} fixed-delay loop
 *       gated by {@code enabled && pollingEnabled}.</li>
 * </ul>
 *
 * <p>Dedupe — a finding already in {@code tenable_finding_log} for this
 * tenant (matching on {@code (asset_uuid, plugin_id)}) is skipped (and we
 * don't write a new vulnerability row). Tenable's findings are stable
 * across re-scans, so this is safe.
 *
 * <p>Severity mapping — Tenable emits either a label
 * (CRITICAL/HIGH/MEDIUM/LOW/INFO) or a CVSS v3 score. We honour the
 * label first; on missing label we fall back to the CVSS score:
 * <pre>
 *   label=CRITICAL or CVSS v3 ≥ 9.0  → CRITICAL (1h SLA)
 *   label=HIGH     or CVSS v3 7.0-8.9 → HIGH    (4h)
 *   label=MEDIUM   or CVSS v3 4.0-6.9 → MEDIUM  (24h)
 *   label=LOW      or CVSS v3 0.1-3.9 → LOW     (72h)
 *   label=INFO     or CVSS v3 == 0.0  → INFO    (no SLA)
 *   missing both                       → MEDIUM (matches Splunk/CrowdStrike default)
 * </pre>
 *
 * <p>Each successful ingest emits an F7 audit_log entry and an F8
 * {@code TENABLE_FINDING_INGESTED} domain event so automation rules
 * (e.g. "open SAMA reportable incident on CRITICAL Tenable finding
 * affecting a PCI asset") can react.
 */
@Service
public class TenableInboundService {

    private static final Logger log = LoggerFactory.getLogger(TenableInboundService.class);

    private static final String INTEGRATION_KEY = "TENABLE";
    private static final String SOURCE_MODULE = "VULNERABILITY";

    private final TenableClient client;
    private final TenableProperties props;
    private final TenableFindingLogRepository findingLogRepo;
    private final IntegrationExternalLinkRepository linkRepo;
    private final VulnerabilityRepository vulnerabilities;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    /**
     * PERF-IO-002: per-finding transaction template. The poll method itself is
     * NOT transactional (so the blocking client.pollFindings() round-trip never
     * holds a pooled connection); each ingestOne — whose three saves
     * (vulnerability + finding_log + external_link) MUST commit atomically to
     * keep the dedupe invariant — runs in its own short REQUIRES_NEW transaction.
     */
    private final TransactionTemplate txTemplate;

    public TenableInboundService(
        TenableClient client,
        TenableProperties props,
        TenableFindingLogRepository findingLogRepo,
        IntegrationExternalLinkRepository linkRepo,
        VulnerabilityRepository vulnerabilities,
        AuditLogService audit,
        ApplicationEventPublisher publisher,
        PlatformTransactionManager txManager
    ) {
        this.client = client;
        this.props = props;
        this.findingLogRepo = findingLogRepo;
        this.linkRepo = linkRepo;
        this.vulnerabilities = vulnerabilities;
        this.audit = audit;
        this.publisher = publisher;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── scheduled poll ──────────────────────────────────────────────────

    /**
     * Periodic Tenable poll. Both flags must be true to act — operators
     * can ENABLE the adapter for manual-poll-only mode while validating,
     * then flip {@code pollingEnabled=true} once they're happy.
     *
     * <p>The {@code fixedDelayString} resolves to milliseconds —
     * Spring multiplies the seconds value by 1000 via SpEL.
     *
     * <p>The default cadence is <strong>15 minutes</strong> (not 60s like
     * CrowdStrike) because Tenable findings are aggregated scan results,
     * not streaming events; a faster cadence wastes API budget without
     * surfacing new data.
     */
    @Scheduled(fixedDelayString = "#{ ${grc.integrations.tenable.polling-interval-seconds:900} * 1000 }")
    public void scheduledPoll() {
        if (!props.isEnabled() || !props.isPollingEnabled()) {
            return; // master kill-switch + per-loop kill-switch
        }
        try {
            int ingested = pollFindings();
            log.debug("[Tenable] scheduled poll ingested {} new finding(s)", ingested);
        } catch (Exception ex) {
            // A scheduled-job exception would shut the job down for the JVM lifetime.
            // Catch + log so the next interval still runs.
            log.warn("[Tenable] scheduled poll failed: {}", ex.getMessage());
        }
    }

    // ── poll-once entry point ───────────────────────────────────────────

    /**
     * Poll Tenable once and ingest the resulting findings. Returns the
     * number of NEW vulnerability rows created (dedup'd rows are not
     * counted). Safe to call manually via the controller.
     *
     * <p>When the adapter is disabled this is a no-op that returns 0
     * without touching the client or repository.
     *
     * <p>PERF-IO-002: deliberately NOT @Transactional — the blocking remote poll
     * (client.pollFindings) runs with no DB connection held, and each finding is
     * persisted in its own short transaction (see {@link #txTemplate}).
     */
    public int pollFindings() {
        if (!props.isEnabled()) {
            log.info("[Tenable] disabled — pollFindings() is a no-op");
            return 0;
        }
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            throw new IllegalStateException(
                "No tenant in TenantContext — TenableInboundService.pollFindings requires a tenant scope.");
        }

        List<TenableFindingDto> batch;
        try {
            batch = client.pollFindings();
        } catch (Exception ex) {
            log.warn("[Tenable] poll call failed for org {}: {}", orgId, ex.getMessage());
            throw ex instanceof RuntimeException re ? re : new RuntimeException(ex);
        }
        if (batch == null || batch.isEmpty()) {
            return 0;
        }

        int ingested = 0;
        for (TenableFindingDto d : batch) {
            if (d == null || d.assetUuid() == null || d.assetUuid().isBlank()) continue;
            if (d.pluginId() == null || d.pluginId().isBlank()) continue;
            if (findingLogRepo.findByOrganisationIdAndTenableAssetUuidAndTenablePluginId(
                    orgId, d.assetUuid(), d.pluginId()).isPresent()) {
                continue; // already ingested — application-side dedupe
            }
            try {
                final TenableFindingDto finding = d;
                txTemplate.executeWithoutResult(status -> ingestOne(orgId, finding));
                ingested++;
            } catch (Exception ex) {
                log.warn("[Tenable] failed to ingest finding asset={} plugin={} for org {}: {}",
                    d.assetUuid(), d.pluginId(), orgId, ex.getMessage());
                // Best-effort — keep processing the rest of the batch. The failed
                // finding's transaction rolled back on its own; others are unaffected.
            }
        }
        return ingested;
    }

    // ── single-finding ingest path ──────────────────────────────────────

    private void ingestOne(Long orgId, TenableFindingDto d) {
        String mappedSeverity = mapTenableSeverity(d.severity(), d.cvssV3Score());
        LocalDateTime firstFound = parseTimestamp(d.firstFound());
        if (firstFound == null) firstFound = LocalDateTime.now();

        // ── vulnerability row ───────────────────────────────────────────
        Vulnerability v = new Vulnerability();
        v.setOrganisationId(orgId);
        v.setExternalId(buildExternalId(d));
        v.setAssetName(truncate(d.hostname(), 300));
        v.setTitleEn(truncate(d.pluginName() == null ? "Tenable finding " + d.pluginId() : d.pluginName(), 500));
        v.setDescriptionEn(d.description());
        v.setSeverity(mappedSeverity);
        v.setCvssScore(d.cvssV3Score());
        v.setVulnerabilityType("TENABLE");
        v.setCveId(truncate(d.cveId(), 50));
        v.setSlaDays(slaDays(mappedSeverity));
        v.setDueDate(computeDueDate(firstFound, mappedSeverity));
        v.setWorkflowStatus("REGISTERED");
        Vulnerability saved = vulnerabilities.save(v);

        // ── tenable_finding_log row ─────────────────────────────────────
        TenableFindingLog logRow = new TenableFindingLog();
        logRow.setOrganisationId(orgId);
        logRow.setTenableAssetUuid(truncate(d.assetUuid(), 80));
        logRow.setTenablePluginId(truncate(d.pluginId(), 40));
        logRow.setSeverity(mappedSeverity);
        logRow.setCvssV3Score(d.cvssV3Score());
        logRow.setCveId(truncate(d.cveId(), 50));
        logRow.setVulnerabilityFindingId(saved.getId());
        logRow.setHttpStatus(200);
        findingLogRepo.save(logRow);

        // ── integration_external_link upsert ────────────────────────────
        String linkKey = d.assetUuid() + "|" + d.pluginId();
        IntegrationExternalLink link = linkRepo
            .findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
                orgId, INTEGRATION_KEY, SOURCE_MODULE, linkKey)
            .orElseGet(IntegrationExternalLink::new);
        link.setOrganisationId(orgId);
        link.setIntegration(INTEGRATION_KEY);
        link.setGrcModule(SOURCE_MODULE);
        link.setGrcRecordId(linkKey);
        link.setExternalId(d.pluginId());
        link.setExternalNumber(d.pluginId());
        link.setLastPulledAt(LocalDateTime.now());
        link.setLastKnownState("OPEN");
        link.setUpdatedAt(LocalDateTime.now());
        linkRepo.save(link);

        // ── F7 + F8 ─────────────────────────────────────────────────────
        audit.log("TENABLE_FINDING_INGESTED", "Vulnerability", saved.getId(),
            "Tenable finding plugin=" + d.pluginId() + " asset=" + d.assetUuid()
                + " [" + mappedSeverity + "] " + (d.cveId() == null ? "" : "cve=" + d.cveId()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("severity", mappedSeverity);
        payload.put("tenablePluginId", d.pluginId());
        payload.put("tenableAssetUuid", d.assetUuid());
        payload.put("hostname", d.hostname());
        payload.put("cveId", d.cveId());
        payload.put("cvssV3Score", d.cvssV3Score());
        publisher.publishEvent(DomainEvent.of(
            orgId, "TENABLE_FINDING_INGESTED", "Vulnerability", saved.getId(), payload));
    }

    // ── severity mapping (Tenable label + CVSS v3 → NCA-ECC2) ───────────

    /**
     * Map a Tenable severity label (preferred) plus CVSS v3 fallback to the
     * NCA-ECC2 buckets the GRC platform uses everywhere. The label wins when
     * present; CVSS is the tiebreaker only if the label is missing.
     *
     * <p>Returns MEDIUM when both are missing — matches the CrowdStrike and
     * Splunk default so a missing severity is never silently queued at LOW.
     */
    public static String mapTenableSeverity(String label, BigDecimal cvssV3) {
        if (label != null && !label.isBlank()) {
            return switch (label.toUpperCase(Locale.ROOT)) {
                case "CRITICAL" -> "CRITICAL";
                case "HIGH"     -> "HIGH";
                case "MEDIUM"   -> "MEDIUM";
                case "LOW"      -> "LOW";
                case "INFO", "INFORMATIONAL" -> "INFO";
                default -> mapFromCvss(cvssV3);
            };
        }
        return mapFromCvss(cvssV3);
    }

    private static String mapFromCvss(BigDecimal cvss) {
        if (cvss == null) return "MEDIUM"; // sane default
        double v = cvss.doubleValue();
        if (v >= 9.0) return "CRITICAL";
        if (v >= 7.0) return "HIGH";
        if (v >= 4.0) return "MEDIUM";
        if (v >  0.0) return "LOW";
        return "INFO";
    }

    /** NCA-ECC2 SLA windows: CRITICAL 1h / HIGH 4h / MEDIUM 24h / LOW 72h / INFO none. */
    static LocalDate computeDueDate(LocalDateTime firstFound, String severity) {
        if (firstFound == null || severity == null) return null;
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> firstFound.plusHours(1).toLocalDate();
            case "HIGH"     -> firstFound.plusHours(4).toLocalDate();
            case "MEDIUM"   -> firstFound.plusHours(24).toLocalDate();
            case "LOW"      -> firstFound.plusHours(72).toLocalDate();
            default          -> null; // INFO — no SLA
        };
    }

    static Integer slaDays(String severity) {
        if (severity == null) return null;
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 0;
            case "HIGH"     -> 0;
            case "MEDIUM"   -> 1;
            case "LOW"      -> 3;
            default          -> null;
        };
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static String buildExternalId(TenableFindingDto d) {
        return "TEN:" + d.assetUuid() + ":" + d.pluginId();
    }

    private static LocalDateTime parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException ignore) {
            try { return LocalDateTime.parse(raw); } catch (DateTimeParseException ignore2) { /* fall through */ }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Test seam — exposes the Vulnerability we just wrote so the integration
     *  tests can pivot to the external_link lookup without re-reading. */
    Optional<Vulnerability> findIngestedFinding(Long orgId, String assetUuid, String pluginId) {
        return findingLogRepo
            .findByOrganisationIdAndTenableAssetUuidAndTenablePluginId(orgId, assetUuid, pluginId)
            .flatMap(logRow -> {
                Long vid = logRow.getVulnerabilityFindingId();
                return vid == null ? Optional.empty() : vulnerabilities.findById(vid);
            });
    }
}
