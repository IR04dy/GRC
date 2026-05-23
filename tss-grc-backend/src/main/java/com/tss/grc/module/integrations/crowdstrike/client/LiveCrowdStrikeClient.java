package com.tss.grc.module.integrations.crowdstrike.client;

import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.dto.ContainmentResponse;
import com.tss.grc.module.integrations.crowdstrike.dto.FalconDetectionDto;
import com.tss.grc.module.integrations.crowdstrike.oauth.CrowdStrikeOAuthTokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Real-HTTPS {@link CrowdStrikeClient} implementation against the Falcon API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  {@code /detects/queries/detects/v1} — list new detection ids</li>
 *   <li>POST {@code /detects/entities/summaries/GET/v1} — bulk-fetch detection bodies</li>
 *   <li>POST {@code /devices/entities/devices-actions/v2?action_name=contain
 *       (lift_containment / hide_host)} — containment actions</li>
 *   <li>GET  {@code /sensors/queries/installers/v1} — cheap health probe</li>
 * </ul>
 *
 * <p>Auth is via the cached OAuth2 bearer token from
 * {@link CrowdStrikeOAuthTokenManager}. On 401 the client invokes
 * {@code refresh()} once and retries — this covers the "Falcon rotated
 * our secret out-of-band" case without burning the regular retry budget.
 *
 * <p>Retries on 5xx and {@code ResourceAccessException} using exponential
 * back-off driven by {@link CrowdStrikeProperties.Retry}. 4xx responses
 * (other than 401) are not retried — they're caller mistakes (bad host id,
 * missing scope, malformed payload).
 */
@Component
@ConditionalOnProperty(name = "grc.integrations.crowdstrike.mode", havingValue = "LIVE")
public class LiveCrowdStrikeClient implements CrowdStrikeClient {

    private static final Logger log = LoggerFactory.getLogger(LiveCrowdStrikeClient.class);

    /** Cap exponential back-off — never sleep more than this between attempts. */
    private static final long BACKOFF_CAP_MS = 30_000L;

    private static final String DETECTS_QUERY_PATH   = "/detects/queries/detects/v1?limit=50&sort=created_timestamp.desc";
    private static final String DETECTS_SUMMARY_PATH = "/detects/entities/summaries/GET/v1";
    private static final String DEVICES_ACTION_PATH  = "/devices/entities/devices-actions/v2";
    private static final String HEALTH_PATH          = "/sensors/queries/installers/v1?limit=1";

    private final CrowdStrikeProperties props;
    private final CrowdStrikeOAuthTokenManager tokens;
    private final RestClient http;

    public LiveCrowdStrikeClient(
        CrowdStrikeProperties props,
        CrowdStrikeOAuthTokenManager tokens,
        @Qualifier("crowdStrikeRestClient") RestClient http
    ) {
        this.props = props;
        this.tokens = tokens;
        this.http = http;
    }

    // ── public client API ───────────────────────────────────────────────

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<FalconDetectionDto> pollDetections() {
        // Step 1 — query for ids.
        Map<String, Object> idsResp = doWithRetryOn5xx(() ->
            http.get()
                .uri(url(DETECTS_QUERY_PATH))
                .headers(this::authHeader)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class)
        );
        List<String> ids = extractStringList(idsResp, "resources");
        if (ids.isEmpty()) {
            log.debug("[CrowdStrike] poll -> 0 new detections");
            return List.of();
        }

        // Step 2 — bulk-fetch the bodies.
        Map<String, Object> body = Map.of("ids", ids);
        Map<String, Object> summariesResp = doWithRetryOn5xx(() ->
            http.post()
                .uri(url(DETECTS_SUMMARY_PATH))
                .headers(this::authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class)
        );
        List<Map<String, Object>> rawDetections = extractMapList(summariesResp, "resources");

        List<FalconDetectionDto> out = new ArrayList<>(rawDetections.size());
        for (Map<String, Object> raw : rawDetections) {
            out.add(toDto(raw));
        }
        log.info("[CrowdStrike] poll -> {} detection(s)", out.size());
        return out;
    }

    @Override
    public ContainmentResponse containHost(String falconHostId, String reasonText) {
        return runDeviceAction("CONTAIN_HOST", "contain", falconHostId);
    }

    @Override
    public ContainmentResponse liftContainment(String falconHostId) {
        return runDeviceAction("LIFT_CONTAINMENT", "lift_containment", falconHostId);
    }

    @Override
    public ContainmentResponse hideHost(String falconHostId) {
        return runDeviceAction("HIDE_HOST", "hide_host", falconHostId);
    }

    @Override
    public boolean isHealthy() {
        try {
            HttpStatusCode status = http.get()
                .uri(url(HEALTH_PATH))
                .headers(this::authHeader)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
            return status.is2xxSuccessful();
        } catch (Exception ex) {
            log.warn("[CrowdStrike] health-check failed: {}", ex.getMessage());
            return false;
        }
    }

    // ── action runner with attempt counting ─────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ContainmentResponse runDeviceAction(String actionType, String falconActionName, String hostId) {
        Map<String, Object> reqBody = Map.of("ids", List.of(hostId));

        int maxAttempts = Math.max(1, props.getRetry().getMaxAttempts());
        long delayMs = Math.max(0L, props.getRetry().getInitialDelayMs());
        double mult = props.getRetry().getBackoffMultiplier();
        RuntimeException last = null;
        boolean refreshed401 = false;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<Map> entity = http.post()
                    .uri(url(DEVICES_ACTION_PATH + "?action_name=" + falconActionName))
                    .headers(this::authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(reqBody)
                    .retrieve()
                    .toEntity(Map.class);
                Map<String, Object> respMap = entity.getBody();
                String resourceId = firstResourceId(respMap);
                int http = entity.getStatusCode().value();
                log.info("[CrowdStrike] {} host={} http={} resourceId={} attempt={}/{}",
                    actionType, hostId, http, resourceId, attempt, maxAttempts);
                return new ContainmentResponse(actionType, hostId,
                    entity.getStatusCode().is2xxSuccessful(), http, attempt, resourceId);
            } catch (HttpClientErrorException ex) {
                // 401 → try a one-shot token refresh + immediate retry (doesn't burn the budget).
                if (ex.getStatusCode().value() == 401 && !refreshed401) {
                    refreshed401 = true;
                    log.info("[CrowdStrike] 401 on {} — refreshing OAuth token and retrying once", actionType);
                    tokens.refresh();
                    continue;
                }
                throw ex; // 4xx (other) — fail fast.
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                last = ex;
                if (attempt == maxAttempts) break;
                log.warn("[CrowdStrike] {} attempt {}/{} failed ({}); retrying in {}ms",
                    actionType, attempt, maxAttempts, ex.getClass().getSimpleName(), delayMs);
                sleep(delayMs);
                delayMs = (long) Math.min(BACKOFF_CAP_MS, Math.max(delayMs + 1L, delayMs * mult));
            }
        }
        throw last == null
            ? new RuntimeException(actionType + " failed without exception")
            : last;
    }

    /** Retry harness used by GET-shaped calls (poll). */
    @SuppressWarnings("unchecked")
    private <T> T doWithRetryOn5xx(java.util.function.Supplier<T> call) {
        int maxAttempts = Math.max(1, props.getRetry().getMaxAttempts());
        long delayMs = Math.max(0L, props.getRetry().getInitialDelayMs());
        double mult = props.getRetry().getBackoffMultiplier();
        RuntimeException last = null;
        boolean refreshed401 = false;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (HttpClientErrorException ex) {
                if (ex.getStatusCode().value() == 401 && !refreshed401) {
                    refreshed401 = true;
                    log.info("[CrowdStrike] 401 on poll — refreshing OAuth token and retrying once");
                    tokens.refresh();
                    continue;
                }
                throw ex;
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                last = ex;
                if (attempt == maxAttempts) break;
                sleep(delayMs);
                delayMs = (long) Math.min(BACKOFF_CAP_MS, Math.max(delayMs + 1L, delayMs * mult));
            }
        }
        throw last == null
            ? new RuntimeException("CrowdStrike poll failed without exception")
            : last;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void authHeader(HttpHeaders h) {
        // The token manager throws if creds are missing — let that surface to the
        // caller; the @PostConstruct warning in CrowdStrikeIntegrationConfig
        // makes the misconfiguration visible at startup.
        h.setBearerAuth(tokens.getAccessToken());
    }

    /** Compose a fully-qualified URL from {@link CrowdStrikeProperties#getBaseUrl()}. */
    private String url(String path) {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank()) return "https://api.eu-1.crowdstrike.com" + path;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(Map<String, Object> resp, String key) {
        if (resp == null) return List.of();
        Object o = resp.get(key);
        if (!(o instanceof List<?> raw)) return List.of();
        List<String> out = new ArrayList<>(raw.size());
        for (Object x : raw) {
            if (x != null) out.add(String.valueOf(x));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractMapList(Map<String, Object> resp, String key) {
        if (resp == null) return List.of();
        Object o = resp.get(key);
        if (!(o instanceof List<?> raw)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(raw.size());
        for (Object x : raw) {
            if (x instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String firstResourceId(Map<String, Object> resp) {
        if (resp == null) return null;
        Object res = resp.get("resources");
        if (!(res instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        if (first instanceof Map<?, ?> m && m.containsKey("id")) {
            return String.valueOf(m.get("id"));
        }
        return String.valueOf(first);
    }

    /** Translate a raw Falcon detection JSON map into our slim DTO. */
    private static FalconDetectionDto toDto(Map<String, Object> raw) {
        if (raw == null) return null;
        Integer severity = null;
        Object sev = raw.get("max_severity");
        if (sev == null) sev = raw.get("severity");
        if (sev instanceof Number n) severity = n.intValue();

        Map<String, Object> device = (raw.get("device") instanceof Map<?, ?> d)
            ? (Map<String, Object>) (Map<?, ?>) d
            : Map.of();
        String hostname = device.get("hostname") instanceof String s ? s : null;
        String deviceId = device.get("device_id") instanceof String s ? s : null;

        // Falcon detection envelopes carry "behaviors[0].tactic / technique / technique_id".
        String tactic = null, technique = null, mitreId = null;
        if (raw.get("behaviors") instanceof List<?> bs && !bs.isEmpty() && bs.get(0) instanceof Map<?, ?> b0) {
            tactic    = stringOr(b0.get("tactic"), null);
            technique = stringOr(b0.get("technique"), null);
            mitreId   = stringOr(b0.get("technique_id"), null);
        }

        return new FalconDetectionDto(
            stringOr(raw.get("detection_id"), null),
            deviceId,
            hostname,
            severity,
            tactic,
            technique,
            mitreId,
            stringOr(raw.get("created_timestamp"), null),
            stringOr(raw.get("description"), null)
        );
    }

    private static String stringOr(Object o, String fallback) {
        return o == null ? fallback : String.valueOf(o);
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("CrowdStrike retry interrupted", ie);
        }
    }

    /** Test seam — expose body translation in isolation. */
    static FalconDetectionDto debugToDto(Map<String, Object> raw) {
        return toDto(raw);
    }
}
