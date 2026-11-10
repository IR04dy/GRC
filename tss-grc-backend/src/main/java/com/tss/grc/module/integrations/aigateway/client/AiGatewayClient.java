package com.tss.grc.module.integrations.aigateway.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.integrations.aigateway.config.AiGatewayProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance HTTP client for the unified OpenAI-compatible AI Gateway.
 *
 * <p>Handles:
 * <ul>
 *   <li>Standard OpenAI-compatible JSON payload dispatch</li>
 *   <li>Guardrail response header inspection (Cache hits, PII masked, Hallucination risk)</li>
 *   <li>Security violation interception (e.g. Prompt Injection HTTP 400)</li>
 *   <li>An explicit, labelled STUB mode for offline environments and unit testing</li>
 * </ul>
 *
 * <p>LIVE mode never falls back to the stub. A transport failure, a timeout or an
 * unparseable reply throws {@code GATEWAY_UNAVAILABLE}: a silent fallback would hand the
 * caller text no model produced, and on a regulated surface (a policy draft, a control
 * assessment) that text would be read as generated content. The stub itself fabricates
 * nothing — it returns a marker saying no model was called.
 */
@Component
public class AiGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayClient.class);

    private final AiGatewayProperties props;
    private final RestClient http;
    private final ObjectMapper objectMapper;

    public AiGatewayClient(
        AiGatewayProperties props,
        @Qualifier("aiGatewayRestClient") RestClient http,
        ObjectMapper objectMapper
    ) {
        this.props = props;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    public LlmResponse complete(Long organisationId, LlmRequest req) {
        long startMs = System.currentTimeMillis();

        if (props.getMode() == AiGatewayProperties.Mode.STUB) {
            return generateStubResponse(req, startMs);
        }

        String base = props.getBaseUrl().replaceAll("/+$", "");
        String uri = base.endsWith("/chat/completions") ? base : base + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());

        List<Map<String, String>> messages = (req.systemPrompt() != null && !req.systemPrompt().isBlank())
            ? List.of(
                Map.of("role", "system", "content", req.systemPrompt()),
                Map.of("role", "user", "content", req.userPrompt()))
            : List.of(Map.of("role", "user", "content", req.userPrompt()));
        body.put("messages", messages);
        body.put("temperature", req.temperature() != null ? req.temperature() : 0.2);
        if (req.maxOutputTokens() != null) {
            body.put("max_tokens", req.maxOutputTokens());
        }
        if (req.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            String requestJson = objectMapper.writeValueAsString(body);

            ResponseEntity<String> response = http.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .header("X-Organisation-Id", String.valueOf(organisationId))
                .header("X-Feature-Code", req.featureCode())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (requestObj, responseObj) -> {
                    String errJson = new String(responseObj.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (errJson.contains("PROMPT_INJECTION_DETECTED")) {
                        throw new LlmGatewayException("PROMPT_INJECTION_BLOCKED",
                            "Request was blocked by enterprise AI Gateway security guardrails (Prompt Injection Detected).");
                    }
                    // The upstream body can echo provider internals or prompt fragments; it is
                    // logged here and kept out of the exception, which is persisted to the call
                    // log and may reach a client.
                    log.error("[AI Gateway] uri={} returned HTTP {} (body length {})",
                        uri, responseObj.getStatusCode(), errJson.length());
                    throw new LlmGatewayException("GATEWAY_ERROR",
                        "AI Gateway returned HTTP " + responseObj.getStatusCode().value());
                })
                .toEntity(String.class);

            long latency = System.currentTimeMillis() - startMs;
            String cacheHeader = response.getHeaders().getFirst("X-Cache");
            String piiHeader = response.getHeaders().getFirst("X-Guardrail-Pii-Masked");
            String halHeader = response.getHeaders().getFirst("X-Guardrail-Hallucination-Risk");

            if (cacheHeader != null || piiHeader != null) {
                log.info("[AI Gateway] uri={} status={} cache={} piiMasked={} halRisk={} latency={}ms",
                    uri, response.getStatusCode(), cacheHeader, piiHeader, halHeader, latency);
            }

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new LlmGatewayException("EMPTY_RESPONSE", "AI Gateway returned empty response body");
            }

            Map<String, Object> raw = objectMapper.readValue(
                responseBody,
                new TypeReference<Map<String, Object>>() {}
            );

            String text = "";
            String finishReason = "stop";
            if (raw.get("choices") instanceof List<?> choices && !choices.isEmpty()
                    && choices.get(0) instanceof Map<?, ?> firstChoice) {
                if (firstChoice.get("finish_reason") != null) {
                    finishReason = String.valueOf(firstChoice.get("finish_reason"));
                }
                if (firstChoice.get("message") instanceof Map<?, ?> msg && msg.get("content") != null) {
                    text = String.valueOf(msg.get("content"));
                }
            }

            int inTok = 0;
            int outTok = 0;
            if (raw.get("usage") instanceof Map<?, ?> usage) {
                if (usage.get("prompt_tokens") instanceof Number n) inTok = n.intValue();
                if (usage.get("completion_tokens") instanceof Number n) outTok = n.intValue();
            }

            return new LlmResponse(
                text,
                "AI_GATEWAY",
                props.getModel(),
                inTok,
                outTok,
                latency,
                finishReason
            );

        } catch (LlmGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[AI Gateway] Request to {} failed: {}", uri, ex.getClass().getSimpleName(), ex);
            throw new LlmGatewayException("GATEWAY_UNAVAILABLE",
                "AI Gateway request failed: " + ex.getClass().getSimpleName(), ex);
        }
    }

    /** Marker returned for a JSON-mode request in STUB mode. Carries no generated content. */
    public static final String STUB_JSON = "{\"stub\": true, \"notice\": \"STUB mode - no model was called\"}";

    /** Marker returned for a text request in STUB mode. Carries no generated content. */
    public static final String STUB_TEXT = "[STUB - no model was called; this is not generated content]";

    /** Provider label on every STUB response, so a caller and the call log can tell it apart. */
    public static final String STUB_PROVIDER = "STUB";

    /**
     * The STUB response. It deliberately fabricates nothing: no policy text, no control codes,
     * no scores or verdicts. A caller that needs shaped output must treat a stub response as
     * "no draft available", which is what it is. Token counts are zero because no model ran,
     * so stub traffic never consumes tenant quota.
     */
    private LlmResponse generateStubResponse(LlmRequest req, long startMs) {
        String content = req.jsonMode() ? STUB_JSON : STUB_TEXT;
        long latency = Math.max(1L, System.currentTimeMillis() - startMs);
        return new LlmResponse(content, STUB_PROVIDER, props.getModel(), 0, 0, latency, "stub");
    }
}
