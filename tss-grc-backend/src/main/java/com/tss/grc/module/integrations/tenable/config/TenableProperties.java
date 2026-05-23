package com.tss.grc.module.integrations.tenable.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tenable.io / Tenable.sc VM bridge configuration — Sprint 12 / Slice 4.
 *
 * <p>Bound from {@code grc.integrations.tenable.*} in {@code application.yml}.
 * Defaults are safe — integration is disabled, stub mode is selected, and
 * the polling job is gated by a separate {@link #pollingEnabled} flag so
 * the VM team can flip the master switch on without immediately starting
 * periodic pulls (manual-poll-only mode while validating).
 *
 * <p>Auth model — Tenable uses an <strong>API key pair</strong>
 * ({@code X-ApiKeys: accessKey=...; secretKey=...}) rather than OAuth.
 * Saudi banks typically split the pair across two operators (one holds
 * accessKey, one holds secretKey) so neither alone can call the API.
 *
 * <p>KSA-resident hosting — Tenable.io is currently hosted in US / EU
 * regions only. <em>There is no KSA-region Tenable.io datacenter as of
 * this slice.</em> Banks that must keep VM data inside KSA borders use
 * Tenable.sc (on-prem) — see the {@link #platform} property which
 * routes the live client between the two API shapes.
 */
@ConfigurationProperties(prefix = "grc.integrations.tenable")
public class TenableProperties {

    /** Master kill-switch. When false, both poll and recast are no-ops. */
    private boolean enabled = false;

    /** STUB (default — in-memory simulator) or LIVE (real Tenable HTTPS). */
    private Mode mode = Mode.STUB;

    /**
     * Tenable platform — IO (cloud) or SC (on-prem Security Center).
     * Drives endpoint shape selection in the live client:
     * <ul>
     *   <li>IO: {@code POST /vulns/export} → {@code /vulns/export/{uuid}/chunks/{id}}</li>
     *   <li>SC: {@code POST /rest/analysis} with type=vuln, tool=listvuln</li>
     * </ul>
     */
    private Platform platform = Platform.IO;

    /**
     * Tenable API base URL. {@code https://cloud.tenable.com} is the IO
     * default. For Tenable.sc, set to your on-prem appliance hostname
     * (e.g. {@code https://tenable-sc.example.sa}). Banks that picked SC
     * for sovereignty reasons keep the appliance inside their KSA DC.
     */
    private String baseUrl = "https://cloud.tenable.com";

    /** Tenable API access key (X-ApiKeys: accessKey=...). */
    private String accessKey = "";

    /** Tenable API secret key (X-ApiKeys: ...;secretKey=...). */
    private String secretKey = "";

    /**
     * Seconds between scheduled poll cycles. Tenable findings are
     * aggregated scan results (not streaming events like Falcon's
     * detections) so a 15-minute default is comfortable — change requires
     * restart. The {@code @Scheduled} fixed-delay timer reads this on boot.
     */
    private int pollingIntervalSeconds = 900;

    /**
     * Independent flag for the scheduled poll loop. Operators flip
     * {@code enabled=true} first (manual /poll works), then
     * {@code pollingEnabled=true} once they're happy that findings
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

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public int getPollingIntervalSeconds() { return pollingIntervalSeconds; }
    public void setPollingIntervalSeconds(int pollingIntervalSeconds) {
        this.pollingIntervalSeconds = pollingIntervalSeconds;
    }

    public boolean isPollingEnabled() { return pollingEnabled; }
    public void setPollingEnabled(boolean pollingEnabled) { this.pollingEnabled = pollingEnabled; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public Retry getRetry() { return retry; }

    /** True when the X-ApiKeys credentials are configured. */
    public boolean hasApiKeyCredentials() {
        return accessKey != null && !accessKey.isBlank()
            && secretKey != null && !secretKey.isBlank();
    }

    public enum Mode { STUB, LIVE }

    /** IO = Tenable.io (cloud); SC = Tenable.sc (on-prem Security Center). */
    public enum Platform { IO, SC }

    /**
     * Exponential-backoff retry parameters. The live client retries on 5xx
     * and {@code ResourceAccessException} (connection errors) using
     * {@code initialDelayMs * backoffMultiplier^n} up to {@code maxAttempts}
     * total attempts. Aligned with the CrowdStrike and Splunk adapters for
     * SOC consistency.
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
