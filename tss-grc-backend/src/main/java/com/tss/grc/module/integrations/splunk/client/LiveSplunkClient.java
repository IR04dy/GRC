package com.tss.grc.module.integrations.splunk.client;

import com.tss.grc.module.integrations.splunk.config.SplunkProperties;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventRequest;
import com.tss.grc.module.integrations.splunk.dto.SplunkHecEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real-HTTP {@link SplunkClient} implementation against the Splunk HEC.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>POST {hecUrl} — push one event (HEC "event" mode, not "raw")</li>
 *   <li>GET  {hecUrl-prefix}/services/collector/health — reachability check</li>
 * </ul>
 *
 * <p>Authentication is via Splunk's per-token header:
 * {@code Authorization: Splunk &lt;token&gt;}.
 *
 * <p>Retries on 5xx and on connection errors using exponential back-off
 * driven by {@link SplunkProperties.Retry}. 4xx responses are not retried —
 * they are caller mistakes (bad token, malformed payload, disabled HEC).
 *
 * <p>The response carries {@code attemptCount} so the outbound service can
 * persist the audit row with the correct retry tally.
 */
@Component
@ConditionalOnProperty(name = "grc.integrations.splunk.mode", havingValue = "LIVE")
public class LiveSplunkClient implements SplunkClient {

    private static final Logger log = LoggerFactory.getLogger(LiveSplunkClient.class);

    /** Cap exponential back-off — never sleep more than this between attempts. */
    private static final long BACKOFF_CAP_MS = 30_000L;

    private final SplunkProperties props;
    private final RestClient http;

    public LiveSplunkClient(
        SplunkProperties props,
        @Qualifier("splunkRestClient") RestClient http
    ) {
        this.props = props;
        this.http = http;
    }

    // ── public client API ───────────────────────────────────────────────

    @Override
    public SplunkHecEventResponse pushEvent(SplunkHecEventRequest req) {
        Map<String, Object> body = toHecBody(req);
        String idx = req.index() != null ? req.index() : props.getDefaultIndex();

        int maxAttempts = Math.max(1, props.getRetry().getMaxAttempts());
        long delayMs = Math.max(0L, props.getRetry().getInitialDelayMs());
        double mult = props.getRetry().getBackoffMultiplier();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Instant started = Instant.now();
            try {
                ResponseEntity<Map> entity = http.post()
                    .uri(props.getHecUrl())
                    .headers(this::authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = entity.getBody();
                int httpStatus = entity.getStatusCode().value();
                int hecCode = resp != null && resp.get("code") instanceof Number n ? n.intValue() : 0;
                String hecText = resp != null && resp.get("text") != null ? String.valueOf(resp.get("text")) : "";
                String eventId = extractEventId(resp);
                long ms = Math.max(0L, Instant.now().toEpochMilli() - started.toEpochMilli());
                log.info("[Splunk] push -> http={} hecCode={} hecText={} attempt={}/{} ({}ms)",
                    httpStatus, hecCode, hecText, attempt, maxAttempts, ms);
                return new SplunkHecEventResponse(eventId, hecCode, hecText, httpStatus, idx, attempt);
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                last = ex;
                if (attempt == maxAttempts) break;
                log.warn("[Splunk] attempt {}/{} failed ({}); retrying in {}ms",
                    attempt, maxAttempts, ex.getClass().getSimpleName(), delayMs);
                sleep(delayMs);
                delayMs = (long) Math.min(BACKOFF_CAP_MS, Math.max(delayMs + 1L, delayMs * mult));
            }
        }
        // All attempts exhausted on a retriable failure path — surface to caller.
        throw last == null
            ? new RuntimeException("Splunk push failed without exception")
            : last;
    }

    @Override
    public boolean isHealthy() {
        try {
            HttpStatusCode status = http.get()
                .uri(healthUrl())
                .headers(this::authHeader)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
            return status.is2xxSuccessful();
        } catch (Exception ex) {
            log.warn("[Splunk] health-check failed: {}", ex.getMessage());
            return false;
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void authHeader(HttpHeaders h) {
        if (!props.hasHecToken()) {
            // Don't throw here — the caller will see a 401 from HEC and we
            // emit a clear warning at startup if the operator forgot the token.
            return;
        }
        h.set(HttpHeaders.AUTHORIZATION, "Splunk " + props.getHecToken());
    }

    private Map<String, Object> toHecBody(SplunkHecEventRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        // HEC top-level envelope fields
        long epochSec = req.eventTimeEpochSec() != null
            ? req.eventTimeEpochSec()
            : Instant.now().getEpochSecond();
        body.put("time", epochSec);
        if (req.host() != null) body.put("host", req.host());
        if (req.source() != null) body.put("source", req.source());
        body.put("sourcetype", req.sourcetype() != null ? req.sourcetype() : props.getDefaultSourcetype());
        body.put("index", req.index() != null ? req.index() : props.getDefaultIndex());

        // Nested event payload — GRC correlation always carried
        Map<String, Object> event = new LinkedHashMap<>(req.event());
        event.put("grc_source_module", req.sourceModule());
        event.put("grc_source_id", req.sourceId());
        body.put("event", event);
        return body;
    }

    private static String extractEventId(Map<String, Object> resp) {
        if (resp == null) return null;
        // HEC returns an "ackId" only when Indexer Acknowledgement is enabled.
        // Otherwise we fall back to the response's serialised shape (good enough
        // to write a deterministic row in integration_external_link).
        Object ack = resp.get("ackId");
        if (ack != null) return String.valueOf(ack);
        // Stamp a synthetic id if HEC didn't give us one — caller still has
        // the http_status to distinguish success from failure.
        return "HEC-" + Instant.now().toEpochMilli();
    }

    /** Derive {@code /services/collector/health} URL from the configured HEC URL. */
    private String healthUrl() {
        String url = props.getHecUrl();
        if (url == null || url.isBlank()) return "http://localhost:8088/services/collector/health";
        int idx = url.indexOf("/services/collector");
        if (idx < 0) {
            // Operator pointed us at a custom endpoint; best-effort sibling probe.
            return url + (url.endsWith("/") ? "" : "/") + "health";
        }
        return url.substring(0, idx) + "/services/collector/health";
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Splunk retry interrupted", ie);
        }
    }

    // Test seam — exercise the body-builder in isolation.
    Map<String, Object> debugToHecBody(SplunkHecEventRequest req) {
        return toHecBody(req);
    }
}
