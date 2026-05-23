package com.tss.grc.module.integrations.crowdstrike.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CrowdStrike Falcon EDR integration configuration — Sprint 12 / Slice 3.
 *
 * <p>Bound from {@code grc.integrations.crowdstrike.*} in {@code application.yml}.
 * Defaults are safe — integration is disabled, stub mode is selected, and the
 * polling job is gated by a separate {@link #pollingEnabled} flag so the SOC
 * team can flip the master switch on without immediately starting periodic
 * pulls (manual-poll-only mode while validating).
 *
 * <p>Auth model — Falcon uses OAuth2 client credentials with a 30-minute
 * access-token TTL. The token manager refreshes 60s before expiry. Tokens are
 * never logged. KSA-resident deployments target {@code api.eu-1.crowdstrike.com}
 * (Frankfurt) so customer telemetry stays inside EU/EEA — Saudi NDMO has
 * accepted EU-1 for SAMA-supervised workloads where US-1 (default) would
 * cross the PDPL Art. 29 cross-border boundary.
 */
@ConfigurationProperties(prefix = "grc.integrations.crowdstrike")
public class CrowdStrikeProperties {

    /** Master kill-switch. When false, both poll and contain are no-ops. */
    private boolean enabled = false;

    /** STUB (default — in-memory simulator) or LIVE (real Falcon HTTPS). */
    private Mode mode = Mode.STUB;

    /**
     * Falcon API base URL. EU-1 (Frankfurt) is the KSA-resident default to keep
     * customer telemetry inside the EU/EEA per PDPL Art. 29.
     * Other clouds:
     * <ul>
     *   <li>{@code https://api.crowdstrike.com}       — US-1 (default Falcon cloud)</li>
     *   <li>{@code https://api.us-2.crowdstrike.com}  — US-2</li>
     *   <li>{@code https://api.eu-1.crowdstrike.com}  — EU-1 (Frankfurt, KSA-resident default)</li>
     *   <li>{@code https://api.laggar.gcw.crowdstrike.com} — Gov</li>
     * </ul>
     */
    private String baseUrl = "https://api.eu-1.crowdstrike.com";

    /** OAuth2 client id ("API client" in Falcon's UI). */
    private String clientId = "";

    /** OAuth2 client secret. */
    private String clientSecret = "";

    /**
     * Seconds between scheduled poll cycles. The {@code @Scheduled} fixed-delay
     * timer reads this on boot — change requires restart. Falcon's docs
     * suggest 30..120s; 60s is a safe default for Saudi-bank SOC volumes.
     */
    private int pollingIntervalSeconds = 60;

    /**
     * Independent flag for the scheduled poll loop. Operators flip
     * {@code enabled=true} first (manual /poll works), then
     * {@code pollingEnabled=true} once they're happy that detections
     * land where they expect. The two flags compose: both must be true
     * for the scheduled timer to act.
     */
    private boolean pollingEnabled = false;

    /** HTTP read+connect timeout in milliseconds (default 5s). */
    private int timeoutMs = 5_000;

    private final Retry retry = new Retry();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public int getPollingIntervalSeconds() { return pollingIntervalSeconds; }
    public void setPollingIntervalSeconds(int pollingIntervalSeconds) {
        this.pollingIntervalSeconds = pollingIntervalSeconds;
    }

    public boolean isPollingEnabled() { return pollingEnabled; }
    public void setPollingEnabled(boolean pollingEnabled) { this.pollingEnabled = pollingEnabled; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public Retry getRetry() { return retry; }

    /** True when OAuth client credentials are configured. */
    public boolean hasOAuthCredentials() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }

    public enum Mode { STUB, LIVE }

    /**
     * Exponential-backoff retry parameters. The live client retries on 5xx
     * and {@code ResourceAccessException} (connection errors) using
     * {@code initialDelayMs * backoffMultiplier^n} up to {@code maxAttempts}
     * total attempts. Aligned with Splunk's adapter for SOC consistency.
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
