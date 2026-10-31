package com.tss.grc.module.integrations.llm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the outbound / local LLM gateway
 * ({@code grc.integrations.llm.*}).
 *
 * <p>Supports local OpenAI-compatible runtimes on customer machines
 * (Ollama, vLLM, LM Studio, llama.cpp via {@link Provider#LOCAL_OPENAI})
 * as well as hosted providers ({@link Provider#GEMINI},
 * {@link Provider#OPENAI_COMPATIBLE}, {@link Provider#AZURE_OPENAI},
 * {@link Provider#ANTHROPIC}).
 *
 * <p>Defaults to {@link Mode#STUB} so offline unit/contract builds and CI
 * never make outbound network calls or require API keys.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "grc.integrations.llm")
public class LlmProperties {

    public enum Mode {
        STUB,
        LIVE
    }

    public enum Provider {
        LOCAL_OPENAI,
        GEMINI,
        OPENAI_COMPATIBLE,
        AZURE_OPENAI,
        ANTHROPIC
    }

    /** Master kill-switch for LLM-backed features. */
    private boolean enabled = true;

    /** STUB (deterministic offline JSON) or LIVE (real HTTP inference). */
    private Mode mode = Mode.STUB;

    /** Active provider when mode=LIVE. */
    private Provider provider = Provider.GEMINI;

    /** OpenAI-compatible or local runtime base URL (e.g. http://localhost:11434/v1). */
    private String baseUrl = "http://localhost:11434/v1";

    /** Google Gemini REST API base URL. */
    private String geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";

    /** Anthropic Messages API base URL. */
    private String anthropicBaseUrl = "https://api.anthropic.com/v1";

    /** Azure OpenAI API version query parameter. */
    private String azureApiVersion = "2024-10-21";

    /** Model identifier or Azure deployment name. */
    private String model = "gemini-2.5-flash";

    /** Generic API key for OpenAI-compatible / Azure / Anthropic / authenticated local endpoints. */
    private String apiKey = "";

    /** Dedicated Gemini API key (preferred when provider=GEMINI; falls back to apiKey). */
    private String geminiApiKey = "";

    /** Dedicated Anthropic API key (preferred when provider=ANTHROPIC; falls back to apiKey). */
    private String anthropicApiKey = "";

    /** Dedicated Azure OpenAI API key (preferred when provider=AZURE_OPENAI; falls back to apiKey). */
    private String azureApiKey = "";

    /** Read timeout in seconds for inference calls (local models on CPU/GPU need headroom). */
    private int timeoutSeconds = 120;

    /** Retry count on transient 429 / 5xx / network errors. */
    private int maxRetries = 2;

    /** Default maximum output tokens when a request does not specify a tighter cap. */
    private int maxOutputTokens = 8192;

    /** Default sampling temperature (low for deterministic regulatory prose). */
    private double temperature = 0.2;

    /** Per-tenant rolling 24h token ceiling (input + output tokens). 0 or negative = unlimited. */
    private long tenantDailyTokenQuota = 500_000L;

    /** Resolve the effective API key for the active provider without leaking it in logs. */
    public String resolveApiKey() {
        return switch (provider) {
            case GEMINI -> firstNonBlank(geminiApiKey, apiKey);
            case ANTHROPIC -> firstNonBlank(anthropicApiKey, apiKey);
            case AZURE_OPENAI -> firstNonBlank(azureApiKey, apiKey);
            case LOCAL_OPENAI, OPENAI_COMPATIBLE -> trimToEmpty(apiKey);
        };
    }

    /** True when the active provider either requires no key (LOCAL_OPENAI) or has one configured. */
    public boolean hasRequiredCredentials() {
        if (provider == Provider.LOCAL_OPENAI) {
            return true;
        }
        return !resolveApiKey().isEmpty();
    }

    private static String firstNonBlank(String primary, String fallback) {
        String p = trimToEmpty(primary);
        return !p.isEmpty() ? p : trimToEmpty(fallback);
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
