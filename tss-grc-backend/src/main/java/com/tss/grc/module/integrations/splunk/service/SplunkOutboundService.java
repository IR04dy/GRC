package com.tss.grc.module.integrations.splunk.service;

import com.tss.grc.common.json.JsonText;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.integrations.splunk.client.SplunkClient;
import com.tss.grc.module.integrations.splunk.config.SplunkProperties;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventRequest;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventResponse;
import com.tss.grc.module.integrations.splunk.entity.SplunkHecOutboundLog;
import com.tss.grc.module.integrations.splunk.repository.SplunkHecOutboundLogRepository;
import com.tss.grc.module.integrations.servicenow.entity.IntegrationExternalLink;
import com.tss.grc.module.integrations.servicenow.repository.IntegrationExternalLinkRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.socops.entity.SecurityEvent;
import com.tss.grc.module.socops.repository.SecurityEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Top-level entry point for pushing GRC events into Splunk via HEC.
 *
 * <p>Sources supported:
 * <ul>
 *   <li>SOC security events ({@link #pushSecurityEvent(Long)})</li>
 *   <li>Free-form events from any module ({@link #pushGenericEvent})</li>
 *   <li>Direct DTO push for the admin smoke-test endpoint ({@link #push})</li>
 * </ul>
 *
 * <p>Every successful or failed attempt is recorded in
 * {@code platform.splunk_hec_outbound_log}. On success, an
 * {@code integration_external_link} row is upserted with
 * {@code integration='SPLUNK'} (reusing the V163 generic table).
 *
 * <p>Every push emits an F7 audit_log row and an F8
 * {@code SPLUNK_EVENT_PUSHED} domain event so automation rules can react.
 */
@Service
public class SplunkOutboundService {

    private static final Logger log = LoggerFactory.getLogger(SplunkOutboundService.class);
    private static final String INTEGRATION_KEY = "SPLUNK";

    private final SplunkClient client;
    private final SplunkProperties props;
    private final SplunkHecOutboundLogRepository logRepo;
    private final IntegrationExternalLinkRepository linkRepo;
    private final SecurityEventRepository securityEventRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    public SplunkOutboundService(
        SplunkClient client,
        SplunkProperties props,
        SplunkHecOutboundLogRepository logRepo,
        IntegrationExternalLinkRepository linkRepo,
        SecurityEventRepository securityEventRepo,
        AuditLogService audit,
        ApplicationEventPublisher publisher,
        ObjectMapper objectMapper
    ) {
        this.client = client;
        this.props = props;
        this.logRepo = logRepo;
        this.linkRepo = linkRepo;
        this.securityEventRepo = securityEventRepo;
        this.audit = audit;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    // ── public façade ──────────────────────────────────────────────────

    /**
     * Pull a {@link SecurityEvent} from the SOC ops table and push it as a
     * Splunk HEC event. The GRC record id used for de-duplication is the
     * SecurityEvent's {@code externalId} (matches the SIEM connector's
     * upstream key).
     */
    // PERF-IO-001: these push methods are deliberately NOT @Transactional —
    // push() makes a blocking Splunk HEC call (client.pushEvent); a surrounding
    // transaction would hold a DB connection across it. The audit row and the
    // external_link upsert are independent best-effort writes, each in its own
    // short repository transaction. The pre-read here touches only eager scalar
    // fields of SecurityEvent, so no LazyInitializationException under
    // open-in-view=false. Do not re-add @Transactional.
    public SplunkHecEventResponse pushSecurityEvent(Long securityEventId) {
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            throw new IllegalStateException(
                "No tenant in TenantContext — SplunkOutboundService.pushSecurityEvent requires a tenant scope.");
        }
        Optional<SecurityEvent> opt = securityEventRepo.findByIdAndOrganisationId(securityEventId, orgId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("SecurityEvent " + securityEventId + " not found in org " + orgId);
        }
        SecurityEvent ev = opt.get();

        Map<String, Object> event = new LinkedHashMap<>();
        // Map.copyOf inside the SplunkHecEventRequest ctor rejects null values, so
        // skip any field the SecurityEvent didn't populate (e.g. destination_host
        // for an authentication event that has no destination side).
        putIfNotNull(event, "external_id", ev.getExternalId());
        putIfNotNull(event, "source_system", ev.getSourceSystem());
        putIfNotNull(event, "severity", ev.getSeverity());
        putIfNotNull(event, "category", ev.getCategory());
        putIfNotNull(event, "rule_name", ev.getRuleName());
        putIfNotNull(event, "source_host", ev.getSourceHost());
        putIfNotNull(event, "source_ip", ev.getSourceIp());
        putIfNotNull(event, "destination_host", ev.getDestinationHost());
        putIfNotNull(event, "destination_ip", ev.getDestinationIp());
        putIfNotNull(event, "username", ev.getUsername());
        putIfNotNull(event, "status", ev.getStatus());

        SplunkHecEventRequest req = new SplunkHecEventRequest(
            "SECURITY_EVENT",
            ev.getExternalId() != null ? ev.getExternalId() : String.valueOf(ev.getId()),
            ev.getSourceHost(),
            ev.getSourceSystem() != null ? ev.getSourceSystem() : "tss-grc-platform",
            props.getDefaultSourcetype(),
            props.getDefaultIndex(),
            ev.getEventTime() != null
                ? ev.getEventTime().toEpochSecond(ZoneOffset.UTC)
                : null,
            event
        );
        return push(req);
    }

    /**
     * Generic push — for callers in cyber/risk/audit/capa that want to
     * surface an arbitrary event in Splunk without pulling in this module's
     * DTO directly. Returns the same response shape as {@link #push}.
     */
    public SplunkHecEventResponse pushGenericEvent(
        String sourceModule,
        String sourceId,
        Map<String, Object> payload,
        String hecIndex
    ) {
        SplunkHecEventRequest req = new SplunkHecEventRequest(
            sourceModule,
            sourceId == null ? "unknown" : sourceId,
            null,
            "tss-grc-platform",
            props.getDefaultSourcetype(),
            hecIndex != null ? hecIndex : props.getDefaultIndex(),
            null,
            payload == null ? Map.of() : payload
        );
        return push(req);
    }

    /**
     * Push directly with a fully-built request DTO — used by the admin
     * {@code POST /test-push} endpoint and by callers that want full control.
     */
    public SplunkHecEventResponse push(SplunkHecEventRequest request) {
        if (!props.isEnabled()) {
            log.info("[Splunk] disabled — push() is a no-op for {}#{}",
                request.sourceModule(), request.sourceId());
            return SplunkHecEventResponse.disabled();
        }

        long startMs = System.currentTimeMillis();
        SplunkHecEventResponse resp = null;
        Exception failure = null;
        try {
            resp = client.pushEvent(request);
        } catch (Exception ex) {
            failure = ex;
            log.warn("[Splunk] push failed for {}#{}: {}",
                request.sourceModule(), request.sourceId(), ex.getMessage());
        }
        long elapsed = System.currentTimeMillis() - startMs;

        // Persist the audit row regardless of outcome.
        persistAuditRow(request, resp, failure, (int) elapsed);

        if (failure != null) {
            // Re-throw so the caller surface (controller / scheduled job) sees the error.
            if (failure instanceof RuntimeException re) throw re;
            throw new RuntimeException(failure);
        }

        // External-link upsert + F7/F8 only on a confirmed-success response.
        if (resp != null && resp.isSuccess()) {
            recordExternalLink(request, resp);
            emitAuditAndDomainEvent(request, resp);
        }
        log.info("[Splunk] pushed {}#{} -> eventId={} httpStatus={} attempts={}",
            request.sourceModule(), request.sourceId(),
            resp == null ? null : resp.splunkEventId(),
            resp == null ? -1 : resp.httpStatus(),
            resp == null ? -1 : resp.attemptCount());
        return resp;
    }

    // ── audit/log row plumbing ──────────────────────────────────────────

    private void persistAuditRow(
        SplunkHecEventRequest req,
        SplunkHecEventResponse resp,
        Exception failure,
        int elapsedMs
    ) {
        try {
            Long orgId = TenantContext.getCurrentTenant();
            SplunkHecOutboundLog row = new SplunkHecOutboundLog();
            row.setOrganisationId(orgId != null ? orgId : 1L);
            row.setSourceModule(req.sourceModule());
            row.setSourceId(req.sourceId());
            row.setHecIndex(req.index() != null ? req.index() : props.getDefaultIndex());
            row.setEventPayload(serialiseEventPayload(req));
            row.setHttpStatus(resp != null ? resp.httpStatus() : null);
            row.setResponseTimeMs(elapsedMs);
            row.setAttemptCount(resp != null ? resp.attemptCount() : props.getRetry().getMaxAttempts());
            if (failure != null) {
                row.setErrorMessage(truncate(failure.getClass().getSimpleName() + ": " + failure.getMessage(), 2000));
            }
            logRepo.save(row);
        } catch (Exception ex) {
            // Audit row is best-effort — we don't want a logging failure to
            // bubble up over a successful HEC push or mask the real failure.
            log.warn("[Splunk] failed to persist HEC audit row: {}", ex.getMessage());
        }
    }

    private String serialiseEventPayload(SplunkHecEventRequest req) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("sourceModule", req.sourceModule());
            snapshot.put("sourceId", req.sourceId());
            snapshot.put("host", req.host());
            snapshot.put("source", req.source());
            snapshot.put("sourcetype", req.sourcetype());
            snapshot.put("index", req.index());
            snapshot.put("eventTimeEpochSec", req.eventTimeEpochSec());
            snapshot.put("event", req.event());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            return "{\"_serialise_error\":\"" + JsonText.escape(ex.getMessage()) + "\"}";
        }
    }

    // ── external_link upsert (reuses V163 generic table) ────────────────

    private void recordExternalLink(SplunkHecEventRequest req, SplunkHecEventResponse resp) {
        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) {
            log.debug("[Splunk] no tenant in context; skipping external_link row "
                + "(call originated outside an HTTP request)");
            return;
        }
        Optional<IntegrationExternalLink> existing = linkRepo
            .findByOrganisationIdAndIntegrationAndGrcModuleAndGrcRecordId(
                orgId, INTEGRATION_KEY, req.sourceModule(), req.sourceId());

        IntegrationExternalLink link = existing.orElseGet(IntegrationExternalLink::new);
        if (existing.isEmpty()) {
            link.setOrganisationId(orgId);
            link.setIntegration(INTEGRATION_KEY);
            link.setGrcModule(req.sourceModule());
            link.setGrcRecordId(req.sourceId());
        }
        link.setExternalId(resp.splunkEventId());
        link.setExternalNumber(resp.splunkEventId());
        link.setExternalUrl(null); // HEC doesn't expose a per-event URL
        link.setLastPushedAt(LocalDateTime.now());
        link.setLastKnownState("INDEXED");
        link.setUpdatedAt(LocalDateTime.now());
        linkRepo.save(link);
    }

    // ── F7 + F8 emission ────────────────────────────────────────────────

    private void emitAuditAndDomainEvent(SplunkHecEventRequest req, SplunkHecEventResponse resp) {
        Long orgId = TenantContext.getCurrentTenant();
        audit.log(
            "SPLUNK_EVENT_PUSHED",
            req.sourceModule(),
            null,
            "Pushed " + req.sourceModule() + "#" + req.sourceId() + " to Splunk (eventId=" + resp.splunkEventId() + ")"
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("splunkEventId", resp.splunkEventId());
        payload.put("hecIndex", resp.hecIndex());
        payload.put("sourceModule", req.sourceModule());
        payload.put("sourceId", req.sourceId());
        payload.put("attemptCount", resp.attemptCount());
        publisher.publishEvent(DomainEvent.of(
            orgId != null ? orgId : 1L,
            "SPLUNK_EVENT_PUSHED",
            req.sourceModule(),
            null,
            payload
        ));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }
}
