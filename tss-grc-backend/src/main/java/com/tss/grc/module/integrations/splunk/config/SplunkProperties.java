package com.tss.grc.module.integrations.splunk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Splunk SIEM integration configuration — Sprint 12 / Slice 2.
 *
 * <p>Bound from {@code grc.integrations.splunk.*} in {@code application.yml}.
 * Defaults are safe: integration is disabled, stub mode is selected. Operators
 * flip {@code enabled=true} and {@code mode=LIVE} once an HEC token is
 * provisioned (see {@code SPLUNK.md}).
 *
 * <p>Auth model — Splunk HEC uses a per-token bearer credential, not OAuth,
 * so there is no token manager equivalent to the ServiceNow adapter. The
 * {@code hecToken} is rendered into the {@code Authorization: Splunk &lt;token&gt;}
 * header verbatim. Pull it from an environment variable in production.
 */
@ConfigurationProperties(prefix = "grc.integrations.splunk")
public class SplunkProperties {

    /** Master kill-switch. When false, the outbound service short-circuits. */
    private boolean enabled = false;

    /** STUB (default — in-memory simulator) or LIVE (real HEC HTTP). */
    private Mode mode = Mode.STUB;

    /**
     * Full URL of the Splunk HEC endpoint, including path —
     * e.g. {@code https://splunk.example.sa:8088/services/collector/event}.
     */
    private String hecUrl = "https://splunk.example.sa:8088/services/collector/event";

    /** HEC token. Render as {@code Authorization: Splunk <token>}. */
    private String hecToken = "";

    /** Default Splunk index for events that don't specify one. */
    private String defaultIndex = "tss_grc";

    /** Default Splunk sourcetype for events that don't specify one. */
    private String defaultSourcetype = "grc:event:json";

    /** When false, the live client trusts the HEC URL's TLS certificate without
     *  validation — only ever useful for short-lived smoke tests against a
     *  self-signed dev instance. Stays {@code true} in every other environment. */
    private boolean verifySsl = true;

    /** HTTP read+connect timeout in milliseconds (default 5s). */
    private int timeoutMs = 5_000;

    /** Optional shared secret used to HMAC-verify inbound webhook bodies.
     *  When blank, signature verification is skipped (role guard still applies). */
    private String webhookSharedSecret = "";

    private final Retry retry = new Retry();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public String getHecUrl() { return hecUrl; }
    public void setHecUrl(String hecUrl) { this.hecUrl = hecUrl; }

    public String getHecToken() { return hecToken; }
    public void setHecToken(String hecToken) { this.hecToken = hecToken; }

    public String getDefaultIndex() { return defaultIndex; }
    public void setDefaultIndex(String defaultIndex) { this.defaultIndex = defaultIndex; }

    public String getDefaultSourcetype() { return defaultSourcetype; }
    public void setDefaultSourcetype(String defaultSourcetype) { this.defaultSourcetype = defaultSourcetype; }

    public boolean isVerifySsl() { return verifySsl; }
    public void setVerifySsl(boolean verifySsl) { this.verifySsl = verifySsl; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public String getWebhookSharedSecret() { return webhookSharedSecret; }
    public void setWebhookSharedSecret(String webhookSharedSecret) { this.webhookSharedSecret = webhookSharedSecret; }

    public Retry getRetry() { return retry; }

    /** True when an HEC token is configured. */
    public boolean hasHecToken() {
        return hecToken != null && !hecToken.isBlank();
    }

    public enum Mode { STUB, LIVE }

    /**
     * Exponential-backoff retry parameters. The live client retries on 5xx
     * and connection errors using {@code initialDelayMs * backoffMultiplier^n}
     * up to {@code maxAttempts} total attempts.
     */
    public static class Retry {
        private int maxAttempts = 3;
        private long initialDelayMs = 500L;
        private double backoffMultiplier = 2.0;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }

        public double getBackoffMultiplier() { return backoffMultiplier; }
        public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
    }
}
