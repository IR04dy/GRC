package com.tss.grc.module.integrations.tenable.client;

import com.tss.grc.module.integrations.tenable.config.TenableProperties;
import com.tss.grc.module.integrations.tenable.dto.RecastResponse;
import com.tss.grc.module.integrations.tenable.dto.TenableFindingDto;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real-HTTPS {@link TenableClient} implementation against the Tenable API.
 *
 * <p>Two flavours selected by {@link TenableProperties#getPlatform()}:
 * <ul>
 *   <li><b>IO</b> — Tenable.io cloud:
 *     <ul>
 *       <li>POST {@code /vulns/export} — start an async export job</li>
 *       <li>GET  {@code /vulns/export/{uuid}/status} — poll job status</li>
 *       <li>GET  {@code /vulns/export/{uuid}/chunks/{id}} — download chunks</li>
 *       <li>POST {@code /workbenches/assets/{uuid}/vulnerabilities/{plugin_id}/recast} — recast</li>
 *       <li>POST {@code /workbenches/assets/{uuid}/vulnerabilities/{plugin_id}/accept-risk} — accept</li>
 *       <li>DELETE the rule_id — remove recast</li>
 *       <li>GET  {@code /session} — health probe</li>
 *     </ul>
 *   </li>
 *   <li><b>SC</b> — Tenable.sc on-prem:
 *     <ul>
 *       <li>POST {@code /rest/analysis} type=vuln tool=listvuln — list findings</li>
 *       <li>POST {@code /rest/recastRiskRule} — recast severity</li>
 *       <li>POST {@code /rest/acceptRiskRule} — accept risk</li>
 *       <li>DELETE {@code /rest/{recast|accept}RiskRule/{id}} — remove</li>
 *       <li>GET  {@code /rest/currentUser} — health probe</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Auth — both flavours use the same {@code X-ApiKeys} header pair:
 * {@code accessKey=...;secretKey=...}. The header is set on every request
 * (Tenable does not issue session cookies for API key auth).
 *
 * <p>Retries on 5xx and {@code ResourceAccessException} using exponential
 * back-off driven by {@link TenableProperties.Retry}. 4xx responses
 * are not retried — they're caller mistakes (bad plugin id, missing
 * scope, malformed payload).
 *
 * <p>This implementation intentionally favours pragmatism over endpoint
 * pedantry — the v1 slice ships against IO's workbench endpoints (the SC
 * code paths are guarded but the SC export pipeline is wired as a
 * follow-up in Sprint 13). Operators on Tenable.sc should stay on STUB
 * mode until then.
 */
@Component
@ConditionalOnProperty(name = "grc.integrations.tenable.mode", havingValue = "LIVE")
public class LiveTenableClient implements TenableClient {

    private static final Logger log = LoggerFactory.getLogger(LiveTenableClient.class);

    /** Cap exponential back-off — never sleep more than this between attempts. */
    private static final long BACKOFF_CAP_MS = 30_000L;

    // Tenable.io paths
    private static final String IO_EXPORT_PATH         = "/vulns/export";
    private static final String IO_RECAST_PATH_FMT     = "/workbenches/assets/%s/vulnerabilities/%s/recast";
    private static final String IO_ACCEPT_PATH_FMT     = "/workbenches/assets/%s/vulnerabilities/%s/accept-risk";
    private static final String IO_REMOVE_PATH_FMT     = "/workbenches/assets/%s/vulnerabilities/%s/recast";
    private static final String IO_HEALTH_PATH         = "/session";

    // Tenable.sc paths
    private static final String SC_ANALYSIS_PATH       = "/rest/analysis";
    private static final String SC_RECAST_PATH         = "/rest/recastRiskRule";
    private static final String SC_ACCEPT_PATH         = "/rest/acceptRiskRule";
    private static final String SC_HEALTH_PATH         = "/rest/currentUser";

    private final TenableProperties props;
    private final RestClient http;

    public LiveTenableClient(
        TenableProperties props,
        @Qualifier("tenableRestClient") RestClient http
    ) {
        this.props = props;
        this.http = http;
    }

    // ── public client API ───────────────────────────────────────────────

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<TenableFindingDto> pollFindings() {
        // The v1 slice ships an "export-status-chunks" flow for IO. SC users
        // stay on STUB until the SC pipeline lands in Sprint 13.
        if (props.getPlatform() == TenableProperties.Platform.SC) {
            log.warn("[Tenable] LIVE mode on platform=SC: the SC analysis endpoint is not yet implemented "
                + "in this slice. Returning empty batch — switch to STUB or wait for Sprint 13.");
            return List.of();
        }

        // Trigger the export — body is empty for "all severities since last call".
        Map<String, Object> exportBody = Map.of(
            "num_assets", 500,
            "filters", Map.of("severity", List.of("critical", "high", "medium", "low"))
        );

        Map<String, Object> startResp = doWithRetryOn5xx(() ->
            http.post()
                .uri(url(IO_EXPORT_PATH))
                .headers(this::authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(exportBody)
                .retrieve()
                .body(Map.class)
        );
        String exportUuid = startResp == null ? null : String.valueOf(startResp.get("export_uuid"));
        if (exportUuid == null || "null".equals(exportUuid) || exportUuid.isBlank()) {
            log.debug("[Tenable] export start returned no uuid; treating as 0 findings");
            return List.of();
        }

        // For v1 we don't poll the status loop here — we trust the export
        // is small enough to be available immediately (Tenable typically
        // makes the first chunk available within a few seconds). A future
        // slice should poll /status until FINISHED.
        Map<String, Object> chunkResp = doWithRetryOn5xx(() ->
            http.get()
                .uri(url(IO_EXPORT_PATH + "/" + exportUuid + "/chunks/1"))
                .headers(this::authHeader)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class)
        );

        List<Map<String, Object>> rawFindings = extractMapList(chunkResp, "vulnerabilities");
        if (rawFindings.isEmpty()) {
            // Tenable returns the chunk as a top-level array sometimes — try that shape too.
            if (chunkResp != null && chunkResp.get("data") instanceof List) {
                rawFindings = extractMapList(chunkResp, "data");
            }
        }

        List<TenableFindingDto> out = new ArrayList<>(rawFindings.size());
        for (Map<String, Object> raw : rawFindings) {
            TenableFindingDto dto = toDto(raw);
            if (dto != null) out.add(dto);
        }
        log.info("[Tenable] poll -> {} finding(s)", out.size());
        return out;
    }

    @Override
    public RecastResponse recastSeverity(String pluginId, String assetUuid, String newSeverity, String justification) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("severity", normaliseSeverity(newSeverity));
        body.put("justification", justification);
        if (assetUuid != null && !assetUuid.isBlank()) {
            body.put("scope", "asset");
        } else {
            body.put("scope", "global");
        }
        return runOutbound("RECAST_SEVERITY", pluginId, assetUuid,
            assetUuid == null ? IO_RECAST_PATH_FMT.formatted("*", pluginId)
                              : IO_RECAST_PATH_FMT.formatted(assetUuid, pluginId),
            body);
    }

    @Override
    public RecastResponse acceptRisk(String pluginId, String assetUuid, String justification, LocalDateTime expiresAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("justification", justification);
        if (expiresAt != null) body.put("expires_at", expiresAt.toString());
        if (assetUuid != null && !assetUuid.isBlank()) {
            body.put("scope", "asset");
        } else {
            body.put("scope", "global");
        }
        return runOutbound("ACCEPT_RISK", pluginId, assetUuid,
            assetUuid == null ? IO_ACCEPT_PATH_FMT.formatted("*", pluginId)
                              : IO_ACCEPT_PATH_FMT.formatted(assetUuid, pluginId),
            body);
    }

    @Override
    public RecastResponse removeRecast(String pluginId, String assetUuid) {
        // Tenable.io: DELETE the recast rule. We model it as a POST with an
        // explicit "remove" body so the audit row carries the same shape as
        // the other actions — the live HTTP call is still a DELETE.
        return runOutboundDelete("REMOVE_RECAST", pluginId, assetUuid,
            assetUuid == null ? IO_REMOVE_PATH_FMT.formatted("*", pluginId)
                              : IO_REMOVE_PATH_FMT.formatted(assetUuid, pluginId));
    }

    @Override
    public boolean isHealthy() {
        String path = props.getPlatform() == TenableProperties.Platform.SC ? SC_HEALTH_PATH : IO_HEALTH_PATH;
        try {
            HttpStatusCode status = http.get()
                .uri(url(path))
                .headers(this::authHeader)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
            return status.is2xxSuccessful();
        } catch (Exception ex) {
            log.warn("[Tenable] health-check failed: {}", ex.getMessage());
            return false;
        }
    }

    // ── outbound runners with attempt counting ──────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RecastResponse runOutbound(String actionType, String pluginId, String assetUuid, String path, Map<String, Object> reqBody) {
        int maxAttempts = Math.max(1, props.getRetry().getMaxAttempts());
        long delayMs = Math.max(0L, props.getRetry().getInitialDelayMs());
        double mult = props.getRetry().getBackoffMultiplier();
        RuntimeException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<Map> entity = http.post()
                    .uri(url(path))
                    .headers(this::authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(reqBody)
                    .retrieve()
                    .toEntity(Map.class);
                Map<String, Object> respMap = entity.getBody();
                String ruleId = firstRuleId(respMap);
                int httpStatus = entity.getStatusCode().value();
                log.info("[Tenable] {} plugin={} asset={} http={} ruleId={} attempt={}/{}",
                    actionType, pluginId, assetUuid, httpStatus, ruleId, attempt, maxAttempts);
                return new RecastResponse(actionType, pluginId, assetUuid,
                    entity.getStatusCode().is2xxSuccessful(), httpStatus, attempt, ruleId);
            } catch (HttpClientErrorException ex) {
                throw ex; // 4xx — fail fast (caller-side bug).
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                last = ex;
                if (attempt == maxAttempts) break;
                log.warn("[Tenable] {} attempt {}/{} failed ({}); retrying in {}ms",
                    actionType, attempt, maxAttempts, ex.getClass().getSimpleName(), delayMs);
                sleep(delayMs);
                delayMs = (long) Math.min(BACKOFF_CAP_MS, Math.max(delayMs + 1L, delayMs * mult));
            }
        }
        throw last == null
            ? new RuntimeException(actionType + " failed without exception")
            : last;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RecastResponse runOutboundDelete(String actionType, String pluginId, String assetUuid, String path) {
        int maxAttempts = Math.max(1, props.getRetry().getMaxAttempts());
        long delayMs = Math.max(0L, props.getRetry().getInitialDelayMs());
        double mult = props.getRetry().getBackoffMultiplier();
        RuntimeException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<Map> entity = http.delete()
                    .uri(url(path))
                    .headers(this::authHeader)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(Map.class);
                Map<String, Object> respMap = entity.getBody();
                String ruleId = firstRuleId(respMap);
                int httpStatus = entity.getStatusCode().value();
                log.info("[Tenable] {} plugin={} asset={} http={} attempt={}/{}",
                    actionType, pluginId, assetUuid, httpStatus, attempt, maxAttempts);
                return new RecastResponse(actionType, pluginId, assetUuid,
                    entity.getStatusCode().is2xxSuccessful(), httpStatus, attempt, ruleId);
            } catch (HttpClientErrorException ex) {
                throw ex;
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                last = ex;
                if (attempt == maxAttempts) break;
                sleep(delayMs);
                delayMs = (long) Math.min(BACKOFF_CAP_MS, Math.max(delayMs + 1L, delayMs * mult));
            }
        }
        throw last == null
            ? new RuntimeException(actionType + " failed without exception")
            : last;
    }

    /** Retry harness used by GET / POST-shaped reads (poll start + chunk). */
    private <T> T doWithRetryOn5xx(java.util.function.Supplier<T> call) {
        int maxAttempts = Math.max(1, props.getRetry().getMaxAttempts());
        long delayMs = Math.max(0L, props.getRetry().getInitialDelayMs());
        double mult = props.getRetry().getBackoffMultiplier();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (HttpClientErrorException ex) {
                throw ex;
            } catch (HttpServerErrorException | ResourceAccessException ex) {
                last = ex;
                if (attempt == maxAttempts) break;
                sleep(delayMs);
                delayMs = (long) Math.min(BACKOFF_CAP_MS, Math.max(delayMs + 1L, delayMs * mult));
            }
        }
        throw last == null
            ? new RuntimeException("Tenable poll failed without exception")
            : last;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void authHeader(HttpHeaders h) {
        // Tenable uses an X-ApiKeys header carrying the pair joined by ";".
        // Throws nothing — invalid creds surface as 401 from Tenable, which
        // the @PostConstruct warning in TenableIntegrationConfig flags at
        // startup.
        String headerValue = String.format(
            "accessKey=%s;secretKey=%s",
            props.getAccessKey() == null ? "" : props.getAccessKey(),
            props.getSecretKey() == null ? "" : props.getSecretKey()
        );
        h.add("X-ApiKeys", headerValue);
    }

    /** Compose a fully-qualified URL from {@link TenableProperties#getBaseUrl()}. */
    private String url(String path) {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank()) return "https://cloud.tenable.com" + path;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
    }

    /** Lowercase + clamp to the values Tenable accepts. */
    private static String normaliseSeverity(String s) {
        if (s == null) return "info";
        return switch (s.toUpperCase(java.util.Locale.ROOT)) {
            case "CRITICAL" -> "critical";
            case "HIGH"     -> "high";
            case "MEDIUM"   -> "medium";
            case "LOW"      -> "low";
            default          -> "info";
        };
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
    private static String firstRuleId(Map<String, Object> resp) {
        if (resp == null) return null;
        // Tenable.io workbench responses carry "rule_id" at the top level.
        if (resp.get("rule_id") != null) return String.valueOf(resp.get("rule_id"));
        // SC carries it inside response.id.
        Object inner = resp.get("response");
        if (inner instanceof Map<?, ?> m && m.containsKey("id")) {
            return String.valueOf(m.get("id"));
        }
        return null;
    }

    /** Translate a raw Tenable finding JSON map into our slim DTO. */
    @SuppressWarnings("unchecked")
    private static TenableFindingDto toDto(Map<String, Object> raw) {
        if (raw == null) return null;

        Object pluginField = raw.get("plugin");
        Map<String, Object> plugin = pluginField instanceof Map<?, ?> p
            ? (Map<String, Object>) (Map<?, ?>) p
            : Map.of();
        String pluginId = stringOr(plugin.get("id"), stringOr(raw.get("plugin_id"), null));
        String pluginName = stringOr(plugin.get("name"), stringOr(raw.get("plugin_name"), null));

        Object assetField = raw.get("asset");
        Map<String, Object> asset = assetField instanceof Map<?, ?> a
            ? (Map<String, Object>) (Map<?, ?>) a
            : Map.of();
        String assetUuid = stringOr(asset.get("uuid"), stringOr(raw.get("asset_uuid"), null));
        String hostname = stringOr(asset.get("hostname"), stringOr(raw.get("hostname"), null));

        // Severity: Tenable returns either a numeric (0..4) or label form.
        Object sev = raw.get("severity");
        String severity = null;
        if (sev instanceof String s) {
            severity = s.toUpperCase(java.util.Locale.ROOT);
        } else if (sev instanceof Number n) {
            severity = switch (n.intValue()) {
                case 4 -> "CRITICAL";
                case 3 -> "HIGH";
                case 2 -> "MEDIUM";
                case 1 -> "LOW";
                default -> "INFO";
            };
        }

        // CVSS v3 score
        BigDecimal cvss = null;
        Object cvssRaw = plugin.get("cvss3_base_score");
        if (cvssRaw == null) cvssRaw = raw.get("cvss3_base_score");
        if (cvssRaw instanceof Number n) cvss = BigDecimal.valueOf(n.doubleValue());
        else if (cvssRaw instanceof String cs && !cs.isBlank()) {
            try { cvss = new BigDecimal(cs); } catch (NumberFormatException ignore) { /* keep null */ }
        }

        // CVE — Tenable findings carry "cve" as a list; first wins.
        String cveId = null;
        Object cveField = plugin.get("cve");
        if (cveField == null) cveField = raw.get("cve");
        if (cveField instanceof List<?> list && !list.isEmpty()) {
            cveId = stringOr(list.get(0), null);
        } else if (cveField instanceof String cs && !cs.isBlank()) {
            cveId = cs;
        }

        return new TenableFindingDto(
            assetUuid,
            pluginId,
            pluginName,
            hostname,
            severity,
            cvss,
            cveId,
            stringOr(raw.get("first_found"), stringOr(raw.get("first_seen"), null)),
            stringOr(plugin.get("description"), stringOr(raw.get("description"), null))
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
            throw new RuntimeException("Tenable retry interrupted", ie);
        }
    }

    /** Test seam — expose body translation in isolation. */
    static TenableFindingDto debugToDto(Map<String, Object> raw) {
        return toDto(raw);
    }
}
