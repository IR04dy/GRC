package com.tss.grc.module.integrations.servicenow.oauth;

import com.tss.grc.module.integrations.servicenow.config.ServiceNowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Caches an OAuth 2.0 client-credentials access token for ServiceNow.
 *
 * <p>Fetches from {@code auth.token-url} on first use, then re-uses the
 * token until it is within 60 seconds of expiry, then re-fetches. Thread
 * safe via synchronisation on the cached-token slot.
 *
 * <p>If the {@code auth.type} is {@code BASIC} this manager is bypassed
 * (see {@link com.tss.grc.module.integrations.servicenow.client.LiveServiceNowClient}).
 */
@Component
public class ServiceNowOAuthTokenManager {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowOAuthTokenManager.class);

    /** Safety margin — refresh when the token has < this much life left. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    private final ServiceNowProperties props;
    private final RestClient restClient;

    private volatile String cachedToken;
    private volatile Instant cachedExpiry = Instant.EPOCH;

    public ServiceNowOAuthTokenManager(
        ServiceNowProperties props,
        @Qualifier("serviceNowRestClient") RestClient restClient
    ) {
        this.props = props;
        this.restClient = restClient;
    }

    /**
     * Returns a non-expired access token, refreshing if needed. Throws
     * {@link IllegalStateException} if credentials are not configured.
     *
     * <p>PERF-IO-004: the common path (a still-valid cached token) is served
     * lock-free off the {@code volatile} fields, so readers never queue behind a
     * thread that is mid-{@link #refresh()} performing a blocking HTTP fetch.
     * Only when the token is stale do we take the monitor, and we double-check
     * inside it so a single refresh satisfies all threads that were waiting.
     */
    public String getAccessToken() {
        String token = cachedToken;
        if (token != null && Instant.now().isBefore(cachedExpiry.minus(EXPIRY_SKEW))) {
            return token; // fast path — no lock
        }
        synchronized (this) {
            // Double-check: another thread may have refreshed while we waited.
            if (cachedToken != null && Instant.now().isBefore(cachedExpiry.minus(EXPIRY_SKEW))) {
                return cachedToken;
            }
            return refresh();
        }
    }

    /** Force a fresh token fetch — useful after a 401 response. */
    public synchronized String refresh() {
        ServiceNowProperties.Auth auth = props.getAuth();
        if (!auth.hasOAuthCredentials()) {
            throw new IllegalStateException(
                "ServiceNow OAuth credentials not configured — set SERVICENOW_CLIENT_ID, "
                + "SERVICENOW_CLIENT_SECRET, SERVICENOW_TOKEN_URL env vars."
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", auth.getClientId());
        form.add("client_secret", auth.getClientSecret());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = restClient.post()
            .uri(auth.getTokenUrl())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);

        if (body == null || !body.containsKey("access_token")) {
            throw new IllegalStateException("ServiceNow OAuth response missing access_token");
        }

        cachedToken = String.valueOf(body.get("access_token"));
        long expiresInSec = body.get("expires_in") instanceof Number n ? n.longValue() : 3600L;
        cachedExpiry = Instant.now().plusSeconds(expiresInSec);

        // Never log the token itself — only the lifetime.
        log.info("[ServiceNow] OAuth token refreshed; expires_in={}s", expiresInSec);
        return cachedToken;
    }

    /** Test seam. */
    void seedToken(String token, Instant expiry) {
        this.cachedToken = token;
        this.cachedExpiry = expiry;
    }
}
