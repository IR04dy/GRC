package com.tss.grc.module.socops.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.dto.ScannerRunIngestRequest;
import com.tss.grc.module.socops.dto.SiemEventIngestRequest;
import com.tss.grc.module.socops.entity.ScannerRun;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.ScannerRunRepository;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Ingest endpoints for SIEM connectors and vulnerability scanners.
 *
 * <p>These are the operational entry points for the SOC ops module — the
 * platform's transition from "Chief Risk Officer's tool" to "Chief
 * Information Security Officer's tool" hinges on getting live security
 * telemetry into the model. Without these endpoints incidents are entered
 * manually after the fact, which is governance theatre, not security ops.
 *
 * <p>Idempotency: events are deduplicated by ({@code organisationId},
 * {@code sourceSystem}, {@code externalId}) so a SIEM connector replaying
 * a queue doesn't create duplicate rows. The existing row is updated with
 * any new payload data and {@link SecurityEvent#getIngestedAt()} is
 * refreshed for the timeline.
 *
 * <p>SLA timing follows NCA ECC-2 incident-response windows:
 * Critical 1h / High 4h / Medium 24h / Low/Info 72h. The F8 automation
 * engine consumes the resulting events to fire escalations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocOpsIngestService {

    private final SecurityEventRepository      securityEvents;
    private final ScannerRunRepository         scannerRuns;
    private final AuditLogService              audit;
    private final ApplicationEventPublisher    publisher;

    // ── SIEM event ingest ──────────────────────────────────────────────

    @Transactional
    public SecurityEvent ingestSiemEvent(SiemEventIngestRequest req) {
        Long org = TenantContext.getCurrentTenant();
        SecurityEvent ev = securityEvents
            .findByOrganisationIdAndSourceSystemAndExternalId(org, req.sourceSystem(), req.externalId())
            .orElseGet(SecurityEvent::new);

        // Re-ingest of a known event: update the live fields without
        // resetting the workflow (status / triagedAt / resolvedAt stay).
        boolean isNew = (ev.getId() == null);

        ev.setOrganisationId(org);
        // F2-381: the DESCRIPTIVE external fields below land in bounded SecurityEvent columns, so
        // each is capped to its column width — an oversized value from a machine SIEM feed would
        // 8152 and, because this method is @Transactional, roll back the whole ingestion (the event
        // neither recorded nor escalated). Mirrors CrowdStrikeInboundService, which truncates the
        // identical columns. externalId/sourceSystem are the upstream system's own identity AND the
        // dedup key — left RAW rather than silently mangled into a colliding key (a malformed >64
        // id is a far-fetched edge, and ClientSuppliedReferenceRatchetTest recognises these two as
        // upstream-owned references the platform must not rewrite). The full alert is on raw_payload.
        ev.setExternalId(req.externalId());
        ev.setSourceSystem(req.sourceSystem());
        ev.setEventTime(req.eventTime());
        ev.setIngestedAt(LocalDateTime.now());
        ev.setSeverity(req.severity());   // vocabulary — left raw; feeds computeSlaDue + the CRITICAL fast-lane
        ev.setCategory(truncate(req.category(), 60));
        ev.setRuleName(truncate(req.ruleName(), 300));
        ev.setSourceHost(truncate(req.sourceHost(), 255));
        ev.setSourceIp(truncate(req.sourceIp(), 45));
        ev.setDestinationHost(truncate(req.destinationHost(), 255));
        ev.setDestinationIp(truncate(req.destinationIp(), 45));
        ev.setUsername(truncate(req.username(), 255));
        ev.setAssetId(req.assetId());
        ev.setRawPayload(req.rawPayload());

        if (isNew) {
            ev.setStatus("NEW");
            ev.setSlaDueAt(computeSlaDue(req.eventTime(), req.severity()));
        }

        SecurityEvent saved = securityEvents.save(ev);

        if (isNew) {
            audit.log("INGEST_SIEM_EVENT", "SecurityEvent", saved.getId(),
                "SIEM event ingested from " + req.sourceSystem()
                    + " (" + req.severity() + " · " + (req.ruleName() == null ? "—" : req.ruleName()) + ")");

            // F8 — broadcast for AR-116..AR-130 (V151) automation rules.
            // Two events: the generic "new event landed" + the CRITICAL fast-lane
            // so the rule engine can match either signal without scanning payload.
            Map<String, Object> payload = Map.of(
                "severity",     req.severity()     == null ? "" : req.severity(),
                "sourceSystem", req.sourceSystem() == null ? "" : req.sourceSystem(),
                "category",     req.category()     == null ? "" : req.category(),
                "ruleName",     req.ruleName()     == null ? "" : req.ruleName());
            publisher.publishEvent(DomainEvent.of(
                org, "SECURITY_EVENT_INGESTED", "SecurityEvent", saved.getId(), payload));
            if ("CRITICAL".equalsIgnoreCase(req.severity())) {
                publisher.publishEvent(DomainEvent.of(
                    org, "SECURITY_EVENT_CRITICAL_NEW", "SecurityEvent", saved.getId(), payload));
            }
        }

        return saved;
    }

    /** NCA ECC-2 incident-response SLA windows. Falls through to 72h for
     *  unknown severities — defensive default that still surfaces in the
     *  SLA-burn list rather than silently dropping. */
    private LocalDateTime computeSlaDue(LocalDateTime eventTime, String severity) {
        if (eventTime == null || severity == null) return null;
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> eventTime.plusHours(1);
            case "HIGH"     -> eventTime.plusHours(4);
            case "MEDIUM"   -> eventTime.plusHours(24);
            default          -> eventTime.plusHours(72);
        };
    }

    // ── Vulnerability scanner ingest ───────────────────────────────────

    @Transactional
    public ScannerRun ingestScannerRun(ScannerRunIngestRequest req) {
        Long org = TenantContext.getCurrentTenant();
        ScannerRun run = new ScannerRun();
        run.setOrganisationId(org);
        run.setScanner(truncate(req.scanner(), 40));    // F2-381: cap to scanner column (40)
        run.setScanName(truncate(req.scanName(), 300)); // cap to scan_name column (300)
        run.setScope(req.scope());                       // NVARCHAR(MAX) — no cap
        run.setStartedAt(req.startedAt());
        run.setCompletedAt(req.completedAt());
        run.setStatus(req.status() == null ? "COMPLETED" : req.status());
        run.setFindingsTotal(nz(req.findingsTotal()));
        run.setFindingsCritical(nz(req.findingsCritical()));
        run.setFindingsHigh(nz(req.findingsHigh()));
        run.setFindingsMedium(nz(req.findingsMedium()));
        run.setFindingsLow(nz(req.findingsLow()));
        run.setAssetsScanned(nz(req.assetsScanned()));
        run.setIngestedAt(LocalDateTime.now());
        run.setRawPayload(req.rawPayload());
        run.setErrorMessage(req.errorMessage());

        ScannerRun saved = scannerRuns.save(run);
        audit.log("INGEST_SCANNER_RUN", "ScannerRun", saved.getId(),
            "Scanner run ingested from " + req.scanner()
                + " (" + saved.getFindingsCritical() + " critical, "
                + saved.getFindingsHigh() + " high)");

        // F8 — broadcast scanner completion + a CRITICAL fast-lane signal so
        // AR-126 (critical-finding ticket creation) fires without scanning
        // the rollup again from inside the rule engine.
        Map<String, Object> payload = Map.of(
            "scanner",          saved.getScanner() == null ? "" : saved.getScanner(),
            "status",           saved.getStatus()  == null ? "" : saved.getStatus(),
            "findingsCritical", saved.getFindingsCritical(),
            "findingsHigh",     saved.getFindingsHigh(),
            "findingsTotal",    saved.getFindingsTotal(),
            "assetsScanned",    saved.getAssetsScanned());
        publisher.publishEvent(DomainEvent.of(
            org, "SCANNER_RUN_COMPLETED", "ScannerRun", saved.getId(), payload));
        if (saved.getFindingsCritical() != null && saved.getFindingsCritical() > 0) {
            publisher.publishEvent(DomainEvent.of(
                org, "SCANNER_RUN_CRITICAL_FOUND", "ScannerRun", saved.getId(), payload));
        }
        return saved;
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    /** Cap an external string to its bounded SecurityEvent/ScannerRun column width (F2-381).
     *  Plain truncation, no ellipsis — a truncated id/host/IP must stay a valid literal; the same
     *  helper CrowdStrikeInboundService uses for the identical columns, kept as a private copy per
     *  ADR-012/018 (no cross-module import of a peer's internals). The full value survives on
     *  raw_payload, so nothing recoverable is lost. */
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
