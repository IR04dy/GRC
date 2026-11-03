package com.tss.grc.module.integrations.aigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the external AI Gateway (LiteLLM Proxy / Portkey / custom proxy).
 *
 * <p>Replaces the embedded, multi-vendor LLM gateway with a single, unified
 * OpenAI-compatible gateway proxy handling caching, load balancing, PII masking,
 * and security guardrails.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "grc.ai-gateway")
public class AiGatewayProperties {

    public enum Mode {
        STUB,
        LIVE
    }

    /** Master kill-switch for AI Gateway backed features. */
    private boolean enabled = true;

    /** STUB (deterministic offline) or LIVE (HTTP inference to AI Gateway). */
    private Mode mode = Mode.LIVE;

    /** Base URL of the OpenAI-compatible AI Gateway (e.g. http://localhost:4000/v1). */
    private String baseUrl = "http://localhost:4000/v1";

    /** Gateway model alias (e.g. grc-default, gpt-4o-mini, gemini-1.5-flash). */
    private String model = "grc-default";

    /** Virtual Master API key for the AI Gateway. */
    private String apiKey = "sk-tss-grc-gateway-master-key";

    /** Gateway HTTP request timeout in seconds. */
    private int timeoutSeconds = 60;

    /** Daily token quota per tenant enforced at the application tier. */
    private long tenantDailyTokenQuota = 500000L;

    public boolean hasRequiredCredentials() {
        return apiKey != null && !apiKey.isBlank();
    }
}
