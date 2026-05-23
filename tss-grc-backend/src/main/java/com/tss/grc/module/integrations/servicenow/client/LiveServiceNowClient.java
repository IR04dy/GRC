package com.tss.grc.module.integrations.servicenow.client;

import com.tss.grc.module.integrations.servicenow.config.ServiceNowProperties;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentRequest;
import com.tss.grc.module.integrations.servicenow.dto.ServiceNowIncidentResponse;
import com.tss.grc.module.integrations.servicenow.oauth.ServiceNowOAuthTokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Real-HTTP {@link ServiceNowClient} implementation.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST {base}/api/now/table/incident — create</li>
 *   <li>GET  {base}/api/now/table/incident?sysparm_query=number=INC0012345 — lookup</li>
 *   <li>PATCH {base}/api/now/table/incident/{sys_id} — update</li>
 * </ul>
 *
 * <p>Retries on 5xx and on connection errors using a simple exponential
 * back-off loop (no Spring-Retry dependency required). 4xx responses
 * are not retried — they are caller mistakes.
 */
@Component
@ConditionalOnProperty(name = "grc.integrations.servicenow.mode", havingValue = "LIVE")
public class LiveServiceNowClient implements ServiceNowClient {

    private static final Logger log = LoggerFactory.getLogger(LiveServiceNowClient.class);

    private final ServiceNowProperties props;
    private final RestClient http;
    private final ServiceNowOAuthTokenManager tokenManager;

    public LiveServiceNowClient(
        ServiceNowProperties props,
        @Qualifier("serviceNowRestClient") RestClient http,
        ServiceNowOAuthTokenManager tokenManager
    ) {
        this.props = props;
        this.http = http;
        this.tokenManager = tokenManager;
    }

    // ── public client API ───────────────────────────────────────────────

    @Override
    public ServiceNowIncidentResponse createIncident(ServiceNowIncidentRequest req) {
        Map<String, Object> body = toSnowBody(req);
        Map<String, Object> result = withRetry(() -> http.post()
            .uri(props.getBaseUrl() + "/api/now/table/incident")
            .headers(this::authHeader)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class));
        return parseResult(result);
    }

    @Override
    public ServiceNowIncidentResponse getIncident(String snowNumber) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = withRetry(() -> http.get()
            .uri(props.getBaseUrl() + "/api/now/table/incident?sysparm_query=number={n}&sysparm_limit=1",
                snowNumber)
            .headers(this::authHeader)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(Map.class));
        if (result == null || !(result.get("result") instanceof java.util.List<?> list) || list.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) list.get(0);
        return parseRecord(first);
    }

    @Override
    public ServiceNowIncidentResponse updateIncident(String snowNumber, Map<String, Object> patch) {
        ServiceNowIncidentResponse existing = getIncident(snowNumber);
        if (existing == null) return null;
        Map<String, Object> result = withRetry(() -> http.patch()
            .uri(props.getBaseUrl() + "/api/now/table/incident/{sys_id}", existing.snowSysId())
            .headers(this::authHeader)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(patch)
            .retrieve()
            .body(Map.class));
        return parseResult(result);
    }

    @Override
    public boolean closeIncident(String snowNumber, String closureNotes) {
        Map<String, Object> patch = new HashMap<>();
        patch.put("state", 7);                 // 7 = Closed
        patch.put("close_code", "Resolved by caller");
        patch.put("close_notes", closureNotes == null ? "" : closureNotes);
        ServiceNowIncidentResponse resp = updateIncident(snowNumber, patch);
        return resp != null;
    }

    @Override
    public boolean isHealthy() {
        try {
            HttpStatusCode status = http.get()
                .uri(props.getBaseUrl() + "/api/now/table/incident?sysparm_limit=1")
                .headers(this::authHeader)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
            return status.is2xxSuccessful();
        } catch (Exception ex) {
            log.warn("[ServiceNow] health-check failed: {}", ex.getMessage());
            return false;
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void authHeader(HttpHeaders h) {
        switch (props.getAuth().getType()) {
            case OAUTH2_CLIENT_CREDENTIALS ->
                h.setBearerAuth(tokenManager.getAccessToken());
            case BASIC -> {
                String creds = props.getAuth().getUsername() + ":" + props.getAuth().getPassword();
                h.set(HttpHeaders.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    private Map<String, Object> toSnowBody(ServiceNowIncidentRequest req) {
        Map<String, Object> body = new HashMap<>();
        body.put("short_description", req.shortDescription());
        if (req.description() != null) body.put("description", req.description());
        if (req.category() != null) body.put("category", req.category());
        if (req.assignmentGroup() != null) body.put("assignment_group", req.assignmentGroup());
        if (req.callerEmail() != null) body.put("caller_id", req.callerEmail());

        // Severity mapping is computed by the outbound service (single source of truth)
        // and forwarded via customFields keys u_grc_impact / u_grc_urgency if present.
        // Standard ServiceNow fields impact + urgency are also set explicitly there.
        body.putAll(req.customFields());

        // Correlation back to GRC for forensics
        body.put("u_grc_module", req.grcSourceModule());
        body.put("u_grc_id", req.grcSourceId());
        return body;
    }

    @SuppressWarnings("unchecked")
    private ServiceNowIncidentResponse parseResult(Map<String, Object> envelope) {
        if (envelope == null) return null;
        Object inner = envelope.get("result");
        if (inner instanceof Map<?, ?> m) {
            return parseRecord((Map<String, Object>) m);
        }
        return null;
    }

    private ServiceNowIncidentResponse parseRecord(Map<String, Object> rec) {
        String sysId = str(rec.get("sys_id"));
        String number = str(rec.get("number"));
        String state = str(rec.get("state"));
        String createdAt = str(rec.get("sys_created_on"));
        String url = sysId == null ? null :
            props.getBaseUrl() + "/nav_to.do?uri=incident.do?sys_id=" + sysId;
        return new ServiceNowIncidentResponse(sysId, number, state, createdAt, url);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    /**
     * Tiny exponential-backoff retry. Retries up to {@code maxRetries} times on:
     * 5xx HTTP responses and connection-level exceptions. 4xx is fatal.
     */
    private <T> T withRetry(Supplier<T> op) {
        int attempts = Math.max(1, props.getMaxRetries() + 1);
        long backoffMs = 250L;
        RuntimeException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return op.get();
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                last = ex;
                if (i == attempts) break;
                log.warn("[ServiceNow] attempt {}/{} failed ({}); retrying in {}ms",
                    i, attempts, ex.getClass().getSimpleName(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                backoffMs = Math.min(backoffMs * 2, 5000);
            }
        }
        throw last == null ? new RuntimeException("ServiceNow call failed without exception") : last;
    }
}
