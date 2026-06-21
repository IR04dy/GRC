package com.tss.grc.module.integrations.crowdstrike.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.crowdstrike.client.CrowdStrikeClient;
import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.dto.FalconDetectionDto;
import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeDetectionLog;
import com.tss.grc.module.integrations.crowdstrike.repository.CrowdStrikeDetectionLogRepository;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Polls Falcon for new detections and materialises each one as a
 * {@code socops.security_event} row + an {@code integration_external_link}
 * mapping (integration='CROWDSTRIKE') + a {@code crowdstrike_detection_log}
 * audit row.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #pollDetections()} — manual / test-trigger, callable from
 *       the controller or unit tests. Runs against the current tenant in
 *       {@link TenantContext}.</li>
 *   <li>{@link #scheduledPoll()} — {@code @Scheduled} fixed-delay loop
 *       gated by {@code enabled && pollingEnabled}.</li>
 * </ul>
 *
 * <p>Dedupe — a detection already in {@code crowdstrike_detection_log} for
 * this tenant is skipped (and we don't write a new SE row). Falcon's
 * own detection ids are stable across re-fetches, so this is safe.
 *
 * <p>Severity mapping — Falcon emits 1..100. We bucket into the NCA ECC-2
 * severities GRC uses everywhere else:
 * <pre>
 *   90..100 → CRITICAL (1h)
 *   70..89  → HIGH     (4h)
 *   40..69  → MEDIUM   (24h)
 *   1..39   → LOW      (72h)
 *   missing → MEDIUM (matches Splunk's default — avoids silent LOW queueing)
 * </pre>
 *
 * <p>Each successful ingest emits an F7 audit_log entry and an F8
 * {@code CROWDSTRIKE_DETECTION_INGESTED} domain event so automation rules
 * (e.g. "CONTAIN the host if severity=CRITICAL and asset is in PCI zone")
 * can react.
 */
@Service
public class CrowdStrikeInboundService {

    private static final Logger log = LoggerFactory.getLogger(CrowdStrikeInboundService.class);

    private static final String INTEGRATION_KEY = "CROWDSTRIKE";
    private static final String SOURCE_SYSTEM = "CROWDSTRIKE";
    private static final String SOURCE_MODULE = "SECURITY_EVENT";
    private static final String CATEGORY = "EDR";

    private final CrowdStrikeClient client;
    private final CrowdStrikeProperties props;
    private final CrowdStrikeDetectionLogRepository detectionLogRepo;
    private final IntegrationExternalLinkRepository linkRepo;
    private final SecurityEventRepository securityEvents;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    /**
     * PERF-IO-002: per-detection transaction template. pollDetections() is NOT
     * transactional (the blocking client.pollDetections() round-trip holds no
     * pooled connection); each ingestOne — whose security_event + finding_log +
     * external_link saves MUST commit atomically to keep the dedupe invariant —
     * runs in its own short REQUIRES_NEW transaction.
     */
    private final TransactionTemplate txTemplate;

    public CrowdStrikeInboundService(
        CrowdStrikeClient client,
        CrowdStrikeProperties props,
        CrowdStrikeDetectionLogRepository detectionLogRepo,
        IntegrationExternalLinkRepository linkRepo,
        SecurityEventRepository securityEvents,
        AuditLogService audit,
        ApplicationEventPublisher publisher,
        ObjectMapper objectMapper,
        PlatformTransactionManager txManager
    ) {
        this.client = client;
        this.props = props;
        this.detectionLogRepo = detectionLogRepo;
        this.linkRepo = linkRepo;
        this.securityEvents = securityEvents;
        this.audit = audit;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── scheduled poll ──────────────────────────────────────────────────

    /**
     * Periodic Falcon poll. Both flags must be true to act — operators
     * can ENABLE the adapter for manual-poll-only mode while validating,
     * then flip {@code pollingEnabled=true} once they're happy.
     *
     * <p>The {@code fixedDelayString} resolves to milliseconds —
     * Spring multiplies the seconds value by 1000 via SpEL.
     */
    @Scheduled(fixedDelayString = "#{ ${grc.integrations.crowdstrike.polling-interval-seconds:60} * 1000 }")
    public void scheduledPoll() {
        if (!props.isEnabled() || !props.isPollingEnabled()) {
            return; // master kill-switch + per-loop kill-switch
        }
        try {
            int ingested = pollDetections();
            log.debug("[CrowdStrike] scheduled poll ingested {} new detection(s)", ingested);
        } catch (Exception ex) {
            // A scheduled-job exception would shut the job down for the JVM lifetime.
            // Catch + log so the next interval still runs.
            log.warn("[CrowdStrike] scheduled poll failed: {}", ex.getMessage());
        }
    }

    // ── poll-once entry point ───────────────────────────────────────────

    /**
     * Poll Falcon once and ingest the resulting detections. Returns the
     * number of NEW security_event rows created (dedup'd rows are not
     * counted). Safe to call manually via the controller.
     *
     * <p>When the adapter is disabled this is a no-op that returns 0
     * without touching the client or repository.
     *
     * <p>PERF-IO-002: deliberately NOT @Transactional — the blocking remote poll
     * (client.pollDetections) runs with no DB connection held, and each detection
     * is persisted in its own short transaction (see {@link #txTemplate}).
     */
    public int pollDetections() {
        if (!props.isEnabled()) {
            log.info("[CrowdStrike] disabled — pollDetections() is a no-op");
            return 0;
        }
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            throw new IllegalStateException(
                "No tenant in TenantContext — CrowdStrikeInboundService.pollDetections requires a tenant scope.");
        }

        List<FalconDetectionDto> batch;
        try {
            batch = client.pollDetections();
        } catch (Exception ex) {
            log.warn("[CrowdStrike] poll call failed for org {}: {}", orgId, ex.getMessage());
            throw ex instanceof RuntimeException re ? re : new RuntimeException(ex);
        }
        if (batch == null || batch.isEmpty()) {
            return 0;
        }

        int ingested = 0;
        for (FalconDetectionDto d : batch) {
            if (d == null || d.detectionId() == null || d.detectionId().isBlank()) continue;
            if (detectionLogRepo.findByOrganisationIdAndFalconDetectionId(orgId, d.detectionId()).isPresent()) {
                continue; // already ingested — application-side dedupe
            }
            try {
                final FalconDetectionDto detection = d;
                txTemplate.executeWithoutResult(status -> ingestOne(orgId, detection));
                ingested++;
            } catch (Exception ex) {
                log.warn("[CrowdStrike] failed to ingest detection {} for org {}: {}",
                    d.detectionId(), orgId, ex.getMessage());
                // Best-effort — keep processing the rest of the batch. The failed
                // detection's transaction rolled back on its own; others unaffected.
            }
        }
        return ingested;
    }

    // ── single-detection ingest path ────────────────────────────────────

    private void ingestOne(Long orgId, FalconDetectionDto d) {
        String mappedSeverity = mapFalconSeverity(d.severity());
        LocalDateTime eventTime = parseTimestamp(d.detectionTimestamp());
        if (eventTime == null) eventTime = LocalDateTime.now();

        // ── security_event row ──────────────────────────────────────────
        SecurityEvent ev = new SecurityEvent();
        ev.setOrganisationId(orgId);
        ev.setExternalId(truncate(d.detectionId(), 64));
        ev.setSourceSystem(SOURCE_SYSTEM);
        ev.setEventTime(eventTime);
        ev.setIngestedAt(LocalDateTime.now());
        ev.setSeverity(mappedSeverity);
        ev.setCategory(CATEGORY);
        ev.setRuleName(buildRuleName(d));
        ev.setSourceHost(truncate(d.hostname(), 255));
        ev.setStatus("NEW");
        ev.setSlaDueAt(computeSlaDue(eventTime, mappedSeverity));
        ev.setRawPayload(serialiseDto(d));
        SecurityEvent savedEvent = securityEvents.save(ev);

        // ── crowdstrike_detection_log row ───────────────────────────────
        CrowdStrikeDetectionLog logRow = new CrowdStrikeDetectionLog();
        logRow.setOrganisationId(orgId);
        logRow.setFalconDetectionId(truncate(d.detectionId(), 160));
        logRow.setFalconHostId(truncate(d.deviceId(), 64));
        logRow.setSeverity(mappedSeverity);
        logRow.setSeverityRaw(d.severity());
        logRow.setTactic(truncate(d.tactic(), 120));
        logRow.setTechnique(truncate(d.technique(), 120));
        logRow.setMitreAttackId(truncate(d.mitreAttackId(), 40));
        logRow.setSecurityEventId(savedEvent.getId());
        logRow.setHttpStatus(200);
        detectionLogRepo.save(logRow);

        // ── integration_external_link upsert ────────────────────────────
        IntegrationExternalLink link = linkRepo
            .findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
                orgId, INTEGRATION_KEY, SOURCE_MODULE, ev.getExternalId())
            .orElseGet(IntegrationExternalLink::new);
        link.setOrganisationId(orgId);
        link.setIntegration(INTEGRATION_KEY);
        link.setGrcModule(SOURCE_MODULE);
        link.setGrcRecordId(ev.getExternalId());
        link.setExternalId(ev.getExternalId());
        link.setExternalNumber(ev.getExternalId());
        link.setLastPulledAt(LocalDateTime.now());
        link.setLastKnownState("DETECTED");
        link.setUpdatedAt(LocalDateTime.now());
        linkRepo.save(link);

        // ── F7 + F8 ─────────────────────────────────────────────────────
        audit.log("CROWDSTRIKE_DETECTION_INGESTED", "SecurityEvent", savedEvent.getId(),
            "CrowdStrike detection " + d.detectionId() + " [" + mappedSeverity + "] "
                + "from " + (d.hostname() == null ? "—" : d.hostname()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("severity", mappedSeverity);
        payload.put("falconDetectionId", d.detectionId());
        payload.put("falconHostId", d.deviceId());
        payload.put("hostname", d.hostname());
        payload.put("mitreAttackId", d.mitreAttackId());
        payload.put("tactic", d.tactic());
        payload.put("technique", d.technique());
        publisher.publishEvent(DomainEvent.of(
            orgId, "CROWDSTRIKE_DETECTION_INGESTED", "SecurityEvent", savedEvent.getId(), payload));
    }

    // ── severity mapping (Falcon 1..100 → NCA ECC-2) ────────────────────

    /**
     * Map a Falcon 1..100 severity to NCA ECC-2 buckets. Unknown / null maps
     * to MEDIUM so an alert is never silently queued at LOW — matches Splunk.
     */
    public static String mapFalconSeverity(Integer raw) {
        if (raw == null) return "MEDIUM";
        int v = raw;
        if (v >= 90) return "CRITICAL";
        if (v >= 70) return "HIGH";
        if (v >= 40) return "MEDIUM";
        if (v >= 1)  return "LOW";
        return "MEDIUM";
    }

    /** NCA ECC-2 SLA windows: Critical 1h / High 4h / Medium 24h / Low 72h. */
    private static LocalDateTime computeSlaDue(LocalDateTime eventTime, String severity) {
        if (eventTime == null || severity == null) return null;
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> eventTime.plusHours(1);
            case "HIGH"     -> eventTime.plusHours(4);
            case "MEDIUM"   -> eventTime.plusHours(24);
            default          -> eventTime.plusHours(72);
        };
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** Compose a human-readable rule_name from MITRE + Falcon's description. */
    private static String buildRuleName(FalconDetectionDto d) {
        List<String> parts = new ArrayList<>(4);
        if (d.mitreAttackId() != null && !d.mitreAttackId().isBlank()) parts.add(d.mitreAttackId());
        if (d.technique() != null && !d.technique().isBlank()) parts.add(d.technique());
        if (d.tactic() != null && !d.tactic().isBlank()) parts.add(d.tactic());
        if (d.description() != null && !d.description().isBlank() && parts.size() < 3) parts.add(d.description());
        if (parts.isEmpty()) return "CrowdStrike detection";
        return truncate(String.join(" · ", parts), 300);
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

    private String serialiseDto(FalconDetectionDto d) {
        try {
            return objectMapper.writeValueAsString(d);
        } catch (JsonProcessingException ex) {
            return "{\"_serialise_error\":\"" + ex.getMessage() + "\"}";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Test seam — exposes the SecurityEvent we just wrote so the integration
     *  tests can pivot to the external_link lookup without re-reading. */
    Optional<SecurityEvent> findIngestedEvent(Long orgId, String falconDetectionId) {
        return securityEvents.findByOrganisationIdAndSourceSystemAndExternalId(orgId, SOURCE_SYSTEM, falconDetectionId);
    }
}
