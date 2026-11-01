package com.tss.grc.module.integrations.llm.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.integrations.llm.config.LlmProperties;
import com.tss.grc.module.integrations.llm.dto.LlmGatewayException;
import com.tss.grc.module.integrations.llm.dto.LlmRequest;
import com.tss.grc.module.integrations.llm.dto.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Stateless multi-provider HTTP {@link LlmClient} active when
 * {@code grc.integrations.llm.mode=LIVE}.
 *
 * <p>Supported providers:
 * <ul>
 *   <li>{@link LlmProperties.Provider#LOCAL_OPENAI} — local Ollama / vLLM / LM Studio / llama.cpp
 *       ({@code POST {baseUrl}/chat/completions}, optional Bearer key).</li>
 *   <li>{@link LlmProperties.Provider#OPENAI_COMPATIBLE} — OpenAI or any hosted OpenAI-compatible
 *       endpoint ({@code POST {baseUrl}/chat/completions}, required Bearer key).</li>
 *   <li>{@link LlmProperties.Provider#GEMINI} — Google Gemini REST API
 *       ({@code POST {geminiBaseUrl}/models/{model}:generateContent} with {@code x-goog-api-key}).</li>
 *   <li>{@link LlmProperties.Provider#AZURE_OPENAI} — Azure OpenAI Service
 *       ({@code POST {baseUrl}/openai/deployments/{model}/chat/completions?api-version=...}
 *       with {@code api-key} header).</li>
 *   <li>{@link LlmProperties.Provider#ANTHROPIC} — Anthropic Messages API
 *       ({@code POST {anthropicBaseUrl}/messages} with {@code x-api-key} and {@code anthropic-version}).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "grc.integrations.llm.mode", havingValue = "LIVE")
public class LiveLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LiveLlmClient.class);
    private static final String ANTHROPIC_API_VERSION = "2023-06-01";

    private final LlmProperties props;
    private final RestClient http;
    private final ObjectMapper objectMapper;

    public LiveLlmClient(
        LlmProperties props,
        @Qualifier("llmRestClient") RestClient http,
        ObjectMapper objectMapper
    ) {
        this.props = props;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        List<LlmProperties.Provider> chain = new java.util.ArrayList<>();
        if (props.getProvider() != null && props.getProvider() != LlmProperties.Provider.NONE) {
            chain.add(props.getProvider());
        }
        if (props.getFallbackProvider() != null && props.getFallbackProvider() != LlmProperties.Provider.NONE
                && !chain.contains(props.getFallbackProvider())) {
            chain.add(props.getFallbackProvider());
        }
        if (props.getTertiaryProvider() != null && props.getTertiaryProvider() != LlmProperties.Provider.NONE
                && !chain.contains(props.getTertiaryProvider())) {
            chain.add(props.getTertiaryProvider());
        }

        Exception lastException = null;
        for (LlmProperties.Provider p : chain) {
            if (!props.hasRequiredCredentials(p)) {
                log.debug("[LLM] Skipping provider {} (no API credentials)", p);
                continue;
            }
            try {
                return executeProvider(p, request);
            } catch (Exception ex) {
                lastException = ex;
                log.warn("[LLM] Provider {} failed ({}). Checking next fallback in chain...", p, ex.getMessage());
            }
        }

        if (lastException != null) {
            if (lastException instanceof LlmGatewayException gex) throw gex;
            throw new LlmGatewayException("CHAIN_FAILED",
                "All LLM providers in fallback chain failed: " + lastException.getMessage(), lastException);
        }

        throw new LlmGatewayException("CREDENTIALS_MISSING",
            "No LLM provider in fallback chain (" + chain + ") has valid API credentials configured.");
    }

    private LlmResponse executeProvider(LlmProperties.Provider p, LlmRequest request) {
        long start = System.currentTimeMillis();
        return switch (p) {
            case OPENAI -> callOpenAi(request, start);
            case GEMINI -> callGemini(request, start);
            case GLM -> callGlm(request, start);
            case LOCAL_OPENAI, OPENAI_COMPATIBLE -> callOpenAiCompatible(request, start, false);
            case AZURE_OPENAI -> callOpenAiCompatible(request, start, true);
            case ANTHROPIC -> callAnthropic(request, start);
            case NONE -> throw new IllegalStateException("No provider configured");
        };
    }

    @SuppressWarnings("unchecked")
    private LlmResponse callOpenAi(LlmRequest req, long startMs) {
        String apiKey = props.resolveApiKey(LlmProperties.Provider.OPENAI);
        String base = stripTrailingSlash(props.getOpenaiBaseUrl());
        String model = props.resolveModel(LlmProperties.Provider.OPENAI);
        String uri = base + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, String>> messages = (req.systemPrompt() != null && !req.systemPrompt().isBlank())
            ? List.of(
                Map.of("role", "system", "content", req.systemPrompt()),
                Map.of("role", "user", "content", req.userPrompt()))
            : List.of(Map.of("role", "user", "content", req.userPrompt()));
        body.put("messages", messages);
        body.put("temperature", req.temperature() != null ? req.temperature() : props.getTemperature());
        body.put("max_tokens", req.maxOutputTokens() != null ? req.maxOutputTokens() : props.getMaxOutputTokens());
        if (req.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        Map<String, Object> raw = withRetry("OPENAI", () -> http.post()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class));

        if (raw == null) {
            throw new LlmGatewayException("EMPTY_RESPONSE", "OpenAI returned an empty response body.");
        }

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
        if (text.isBlank()) {
            throw new LlmGatewayException("EMPTY_CONTENT", "OpenAI response contained no message content.");
        }

        String resolvedModel = raw.get("model") != null ? String.valueOf(raw.get("model")) : model;
        int inTok = estimateTokens(req.systemPrompt()) + estimateTokens(req.userPrompt());
        int outTok = estimateTokens(text);
        if (raw.get("usage") instanceof Map<?, ?> usage) {
            inTok = intOrDefault(usage.get("prompt_tokens"), inTok);
            outTok = intOrDefault(usage.get("completion_tokens"), outTok);
        }

        return new LlmResponse(
            text,
            "OPENAI",
            resolvedModel,
            inTok,
            outTok,
            Math.max(1L, System.currentTimeMillis() - startMs),
            finishReason
        );
    }

    @SuppressWarnings("unchecked")
    private LlmResponse callGlm(LlmRequest req, long startMs) {
        String apiKey = props.resolveApiKey(LlmProperties.Provider.GLM);
        String base = stripTrailingSlash(props.getGlmBaseUrl());
        String model = props.resolveModel(LlmProperties.Provider.GLM);
        String uri = base + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, String>> messages = (req.systemPrompt() != null && !req.systemPrompt().isBlank())
            ? List.of(
                Map.of("role", "system", "content", req.systemPrompt()),
                Map.of("role", "user", "content", req.userPrompt()))
            : List.of(Map.of("role", "user", "content", req.userPrompt()));
        body.put("messages", messages);
        body.put("temperature", req.temperature() != null ? req.temperature() : props.getTemperature());
        body.put("max_tokens", req.maxOutputTokens() != null ? req.maxOutputTokens() : props.getMaxOutputTokens());

        Map<String, Object> raw = withRetry("GLM", () -> {
            byte[] responseBytes = http.post()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(byte[].class);
            if (responseBytes == null || responseBytes.length == 0) {
                return null;
            }
            try {
                return objectMapper.readValue(responseBytes, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
                throw new LlmGatewayException("DESERIALIZATION_FAILED", "Failed to deserialize GLM response: " + ex.getMessage(), ex);
            }
        });

        if (raw == null) {
            throw new LlmGatewayException("EMPTY_RESPONSE", "GLM returned an empty response body.");
        }

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
        if (text.isBlank()) {
            throw new LlmGatewayException("EMPTY_CONTENT", "GLM response contained no message content.");
        }

        String resolvedModel = raw.get("model") != null ? String.valueOf(raw.get("model")) : model;
        int inTok = estimateTokens(req.systemPrompt()) + estimateTokens(req.userPrompt());
        int outTok = estimateTokens(text);
        if (raw.get("usage") instanceof Map<?, ?> usage) {
            inTok = intOrDefault(usage.get("prompt_tokens"), inTok);
            outTok = intOrDefault(usage.get("completion_tokens"), outTok);
        }

        return new LlmResponse(
            text,
            "GLM",
            resolvedModel,
            inTok,
            outTok,
            Math.max(1L, System.currentTimeMillis() - startMs),
            finishReason
        );
    }

    @SuppressWarnings("unchecked")
    private LlmResponse callGemini(LlmRequest req, long startMs) {
        String apiKey = props.resolveApiKey(LlmProperties.Provider.GEMINI);
        String base = stripTrailingSlash(props.getGeminiBaseUrl());
        String model = props.resolveModel(LlmProperties.Provider.GEMINI);
        String uri = base + "/models/" + model + ":generateContent";

        Map<String, Object> body = new LinkedHashMap<>();
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            body.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", req.systemPrompt()))
            ));
        }
        body.put("contents", List.of(Map.of(
            "role", "user",
            "parts", List.of(Map.of("text", req.userPrompt()))
        )));
        Map<String, Object> genCfg = new LinkedHashMap<>();
        genCfg.put("temperature", req.temperature() != null ? req.temperature() : props.getTemperature());
        genCfg.put("maxOutputTokens", req.maxOutputTokens() != null ? req.maxOutputTokens() : props.getMaxOutputTokens());
        if (req.jsonMode()) {
            genCfg.put("responseMimeType", "application/json");
        }
        body.put("generationConfig", genCfg);

        Map<String, Object> raw = withRetry("GEMINI", () -> http.post()
            .uri(uri)
            .header("x-goog-api-key", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class));

        if (raw == null) {
            throw new LlmGatewayException("EMPTY_RESPONSE", "Gemini returned an empty response body.");
        }

        String text = "";
        String finishReason = "STOP";
        if (raw.get("candidates") instanceof List<?> candidates && !candidates.isEmpty()
                && candidates.get(0) instanceof Map<?, ?> firstCandidate) {
            if (firstCandidate.get("finishReason") != null) {
                finishReason = String.valueOf(firstCandidate.get("finishReason"));
            }
            if (firstCandidate.get("content") instanceof Map<?, ?> content
                    && content.get("parts") instanceof List<?> parts) {
                StringBuilder sb = new StringBuilder();
                for (Object p : parts) {
                    if (p instanceof Map<?, ?> partMap && partMap.get("text") != null) {
                        sb.append(partMap.get("text"));
                    }
                }
                text = sb.toString();
            }
        }
        if (text.isBlank()) {
            throw new LlmGatewayException("EMPTY_CONTENT",
                "Gemini response contained no generated text (finishReason=" + finishReason + ").");
        }

        int inTok = estimateTokens(req.systemPrompt()) + estimateTokens(req.userPrompt());
        int outTok = estimateTokens(text);
        if (raw.get("usageMetadata") instanceof Map<?, ?> usage) {
            inTok = intOrDefault(usage.get("promptTokenCount"), inTok);
            outTok = intOrDefault(usage.get("candidatesTokenCount"), outTok);
        }

        return new LlmResponse(
            text,
            "GEMINI",
            model,
            inTok,
            outTok,
            Math.max(1L, System.currentTimeMillis() - startMs),
            finishReason
        );
    }

    @SuppressWarnings("unchecked")
    private LlmResponse callOpenAiCompatible(LlmRequest req, long startMs, boolean azure) {
        String apiKey = props.resolveApiKey();
        String base = stripTrailingSlash(props.getBaseUrl());
        String model = props.getModel();
        String uri = azure
            ? base + "/openai/deployments/" + model + "/chat/completions?api-version=" + props.getAzureApiVersion()
            : base + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        if (!azure) {
            body.put("model", model);
        }
        List<Map<String, String>> messages = (req.systemPrompt() != null && !req.systemPrompt().isBlank())
            ? List.of(
                Map.of("role", "system", "content", req.systemPrompt()),
                Map.of("role", "user", "content", req.userPrompt()))
            : List.of(Map.of("role", "user", "content", req.userPrompt()));
        body.put("messages", messages);
        body.put("temperature", req.temperature() != null ? req.temperature() : props.getTemperature());
        body.put("max_tokens", req.maxOutputTokens() != null ? req.maxOutputTokens() : props.getMaxOutputTokens());
        if (req.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        Map<String, Object> raw = withRetry(props.getProvider().name(), () -> http.post()
            .uri(uri)
            .headers(h -> {
                if (!apiKey.isEmpty()) {
                    if (azure) {
                        h.set("api-key", apiKey);
                    } else {
                        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
                    }
                }
            })
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class));

        if (raw == null) {
            throw new LlmGatewayException("EMPTY_RESPONSE",
                props.getProvider() + " returned an empty response body.");
        }

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
        if (text.isBlank()) {
            throw new LlmGatewayException("EMPTY_CONTENT",
                props.getProvider() + " response contained no assistant message content.");
        }

        String resolvedModel = raw.get("model") != null ? String.valueOf(raw.get("model")) : model;
        int inTok = estimateTokens(req.systemPrompt()) + estimateTokens(req.userPrompt());
        int outTok = estimateTokens(text);
        if (raw.get("usage") instanceof Map<?, ?> usage) {
            inTok = intOrDefault(usage.get("prompt_tokens"), inTok);
            outTok = intOrDefault(usage.get("completion_tokens"), outTok);
        }

        return new LlmResponse(
            text,
            props.getProvider().name(),
            resolvedModel,
            inTok,
            outTok,
            Math.max(1L, System.currentTimeMillis() - startMs),
            finishReason
        );
    }

    @SuppressWarnings("unchecked")
    private LlmResponse callAnthropic(LlmRequest req, long startMs) {
        String apiKey = props.resolveApiKey();
        String base = stripTrailingSlash(props.getAnthropicBaseUrl());
        String model = props.getModel();
        String uri = base + "/messages";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", req.maxOutputTokens() != null ? req.maxOutputTokens() : props.getMaxOutputTokens());
        body.put("temperature", req.temperature() != null ? req.temperature() : props.getTemperature());
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            body.put("system", req.systemPrompt());
        }
        body.put("messages", List.of(Map.of("role", "user", "content", req.userPrompt())));

        Map<String, Object> raw = withRetry("ANTHROPIC", () -> http.post()
            .uri(uri)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_API_VERSION)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class));

        if (raw == null) {
            throw new LlmGatewayException("EMPTY_RESPONSE", "Anthropic returned an empty response body.");
        }

        StringBuilder sb = new StringBuilder();
        if (raw.get("content") instanceof List<?> blocks) {
            for (Object b : blocks) {
                if (b instanceof Map<?, ?> block && "text".equals(block.get("type")) && block.get("text") != null) {
                    sb.append(block.get("text"));
                }
            }
        }
        String text = sb.toString();
        String finishReason = raw.get("stop_reason") != null ? String.valueOf(raw.get("stop_reason")) : "end_turn";
        if (text.isBlank()) {
            throw new LlmGatewayException("EMPTY_CONTENT", "Anthropic response contained no text block.");
        }

        int inTok = estimateTokens(req.systemPrompt()) + estimateTokens(req.userPrompt());
        int outTok = estimateTokens(text);
        if (raw.get("usage") instanceof Map<?, ?> usage) {
            inTok = intOrDefault(usage.get("input_tokens"), inTok);
            outTok = intOrDefault(usage.get("output_tokens"), outTok);
        }

        return new LlmResponse(
            text,
            "GEMINI",
            model,
            inTok,
            outTok,
            Math.max(1L, System.currentTimeMillis() - startMs),
            finishReason
        );
    }

    private <T> T withRetry(String providerLabel, Supplier<T> op) {
        int attempts = Math.max(1, props.getMaxRetries() + 1);
        long backoffMs = 400L;
        RuntimeException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return op.get();
            } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException | ResourceAccessException ex) {
                last = ex;
                if (i == attempts) {
                    break;
                }
                log.warn("[LLM:{}] attempt {}/{} failed ({}); retrying in {}ms",
                    providerLabel, i, attempts, ex.getClass().getSimpleName(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmGatewayException("INTERRUPTED", "LLM retry interrupted", ie);
                }
                backoffMs = Math.min(backoffMs * 2, 5000L);
            } catch (HttpClientErrorException ex) {
                throw new LlmGatewayException("PROVIDER_4XX",
                    "LLM provider " + providerLabel + " rejected request with HTTP " + ex.getStatusCode().value(),
                    ex);
            }
        }
        throw new LlmGatewayException("PROVIDER_UNAVAILABLE",
            "LLM provider " + providerLabel + " failed after " + attempts + " attempt(s): "
                + (last == null ? "unknown error" : last.getClass().getSimpleName()),
            last);
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static int intOrDefault(Object raw, int fallback) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.getBytes(StandardCharsets.UTF_8).length / 4);
    }
}
