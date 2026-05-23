package com.tss.grc.module.integrations.servicenow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ServiceNow ITSM integration configuration — Sprint 12.
 *
 * <p>Bound from {@code grc.integrations.servicenow.*} in {@code application.yml}.
 * Defaults are safe: integration is disabled, stub mode is selected. Operators
 * flip {@code enabled=true} and {@code mode=LIVE} once env vars for OAuth or
 * basic auth are populated (see {@code SERVICENOW.md}).
 *
 * <p>Secrets are pulled from environment variables, never committed.
 */
@ConfigurationProperties(prefix = "grc.integrations.servicenow")
public class ServiceNowProperties {

    /** Master kill-switch. When false, the outbound service short-circuits. */
    private boolean enabled = false;

    /** STUB (default — in-memory simulator) or LIVE (real ServiceNow HTTP). */
    private Mode mode = Mode.STUB;

    /** Base URL of the ServiceNow instance — e.g. https://acme.service-now.com */
    private String baseUrl = "https://example.service-now.com";

    private final Auth auth = new Auth();

    /** HTTP read timeout in seconds. */
    private int timeoutSeconds = 30;

    /** Max retries on 5xx / connection errors (exponential backoff). */
    private int maxRetries = 3;

    private final Defaults defaults = new Defaults();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Auth getAuth() { return auth; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public Defaults getDefaults() { return defaults; }

    public enum Mode { STUB, LIVE }

    public enum AuthType { OAUTH2_CLIENT_CREDENTIALS, BASIC }

    public static class Auth {
        private AuthType type = AuthType.OAUTH2_CLIENT_CREDENTIALS;
        private String clientId = "";
        private String clientSecret = "";
        private String tokenUrl = "";
        private String username = "";
        private String password = "";

        public AuthType getType() { return type; }
        public void setType(AuthType type) { this.type = type; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public boolean hasOAuthCredentials() {
            return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && tokenUrl != null && !tokenUrl.isBlank();
        }

        public boolean hasBasicCredentials() {
            return username != null && !username.isBlank()
                && password != null && !password.isBlank();
        }
    }

    public static class Defaults {
        /** ServiceNow impact 1=high, 3=low (default). */
        private int impact = 3;
        /** ServiceNow urgency 1=high, 3=low (default). */
        private int urgency = 3;
        private String category = "Security";

        public int getImpact() { return impact; }
        public void setImpact(int impact) { this.impact = impact; }
        public int getUrgency() { return urgency; }
        public void setUrgency(int urgency) { this.urgency = urgency; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
}
