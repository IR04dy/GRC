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
 * as well as hosted providers ({@link Provider#OPENAI}, {@link Provider#GEMINI},
 * {@link Provider#OPENAI_COMPATIBLE}, {@link Provider#AZURE_OPENAI},
 * {@link Provider#ANTHROPIC}).
 *
 * <p>Supports multi-provider failover: when a primary provider is configured
 * alongside an active {@link #fallbackProvider}, transient or quota failures
 * on the primary automatically cascade to the fallback provider.
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
        OPENAI,
        GEMINI,
        LOCAL_OPENAI,
        OPENAI_COMPATIBLE,
        AZURE_OPENAI,
        ANTHROPIC,
        NONE
    }

    /** Master kill-switch for LLM-backed features. */
    private boolean enabled = true;

    /** STUB (deterministic offline JSON) or LIVE (real HTTP inference). */
    private Mode mode = Mode.STUB;

    /** Primary provider when mode=LIVE. Defaults to OPENAI. */
    private Provider provider = Provider.OPENAI;

    /** Secondary fallback provider when primary fails or lacks credentials. Defaults to GEMINI. */
    private Provider fallbackProvider = Provider.GEMINI;

    /** OpenAI official REST API base URL. */
    private String openaiBaseUrl = "https://api.openai.com/v1";

    /** Google Gemini REST API base URL. */
    private String geminiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";

    /** Anthropic Messages API base URL. */
    private String anthropicBaseUrl = "https://api.anthropic.com/v1";

    /** Local OpenAI or OpenAI-compatible endpoint base URL. */
    private String baseUrl = "http://localhost:11434/v1";

    /** Azure OpenAI API version query parameter. */
    private String azureApiVersion = "2024-10-21";

    /** Generic model identifier (used if provider-specific model is unset). */
    private String model = "gpt-4o-mini";

    /** Dedicated OpenAI model (defaults to gpt-4o-mini). */
    private String openaiModel = "gpt-4o-mini";

    /** Dedicated Gemini model (defaults to gemini-1.5-flash). */
    private String geminiModel = "gemini-1.5-flash";

    /** Generic API key for OpenAI-compatible / Azure / Anthropic / authenticated local endpoints. */
    private String apiKey = "";

    /** Dedicated OpenAI API key (preferred when provider=OPENAI; falls back to apiKey). */
    private String openaiApiKey = "";

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

    /** Resolve the effective API key for the given provider without leaking it in logs. */
    public String resolveApiKey(Provider p) {
        if (p == null) return "";
        return switch (p) {
            case OPENAI -> firstNonBlank(openaiApiKey, apiKey);
            case GEMINI -> firstNonBlank(geminiApiKey, apiKey);
            case ANTHROPIC -> firstNonBlank(anthropicApiKey, apiKey);
            case AZURE_OPENAI -> firstNonBlank(azureApiKey, apiKey);
            case OPENAI_COMPATIBLE -> firstNonBlank(openaiApiKey, apiKey);
            case LOCAL_OPENAI, NONE -> trimToEmpty(apiKey);
        };
    }

    /** Resolve the effective API key for the active primary provider. */
    public String resolveApiKey() {
        return resolveApiKey(provider);
    }

    /** True when the given provider either requires no key (LOCAL_OPENAI) or has one configured. */
    public boolean hasRequiredCredentials(Provider p) {
        if (p == null || p == Provider.NONE) {
            return false;
        }
        if (p == Provider.LOCAL_OPENAI) {
            return true;
        }
        return !resolveApiKey(p).isEmpty();
    }

    /** True when either the primary provider or the fallback provider has credentials. */
    public boolean hasRequiredCredentials() {
        return hasRequiredCredentials(provider) || hasRequiredCredentials(fallbackProvider);
    }

    /** Resolve the effective model identifier for the given provider. */
    public String resolveModel(Provider p) {
        if (p == null) return model;
        return switch (p) {
            case OPENAI -> firstNonBlank(openaiModel, model);
            case GEMINI -> firstNonBlank(geminiModel, "gemini-1.5-flash");
            default -> model;
        };
    }

    private static String firstNonBlank(String primary, String fallback) {
        String p = trimToEmpty(primary);
        return !p.isEmpty() ? p : trimToEmpty(fallback);
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
