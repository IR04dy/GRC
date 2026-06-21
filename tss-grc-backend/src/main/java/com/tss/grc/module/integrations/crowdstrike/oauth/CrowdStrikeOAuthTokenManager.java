package com.tss.grc.module.integrations.crowdstrike.oauth;

import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
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
 * Caches an OAuth 2.0 client-credentials access token for the Falcon API.
 *
 * <p>Falcon issues access tokens via {@code POST /oauth2/token} with a
 * default 30-minute (1800s) TTL. This manager refreshes the cached token
 * when it has less than 60s of life remaining, so callers never block on
 * a token that's expired mid-request. Tokens are stored only in memory
 * and never logged.
 *
 * <p>Thread-safe via synchronisation on the cached-token slot — the
 * synchronised method is short and the network call only runs on the
 * refresh path, so contention is negligible (Falcon polling is single-
 * threaded by design).
 *
 * <p>Mirrors {@code ServiceNowOAuthTokenManager} so a developer who has
 * worked on one adapter recognises the other.
 */
@Component
public class CrowdStrikeOAuthTokenManager {

    private static final Logger log = LoggerFactory.getLogger(CrowdStrikeOAuthTokenManager.class);

    /** Safety margin — refresh when the token has < this much life left. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(60);

    /** Falcon's OAuth path is fixed across all clouds (EU-1, US-1, US-2, Gov). */
    private static final String TOKEN_PATH = "/oauth2/token";

    private final CrowdStrikeProperties props;
    private final RestClient restClient;

    private volatile String cachedToken;
    private volatile Instant cachedExpiry = Instant.EPOCH;

    public CrowdStrikeOAuthTokenManager(
        CrowdStrikeProperties props,
        @Qualifier("crowdStrikeRestClient") RestClient restClient
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
     * Only when the token is stale do we take the monitor, double-checking inside
     * it so one refresh satisfies all threads that were waiting.
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

    /**
     * Force a fresh token fetch — invoked by the live client after a 401
     * (in case Falcon rotated the secret out-of-band) and on first use.
     */
    public synchronized String refresh() {
        if (!props.hasOAuthCredentials()) {
            throw new IllegalStateException(
                "CrowdStrike OAuth credentials not configured — set CROWDSTRIKE_CLIENT_ID + "
                + "CROWDSTRIKE_CLIENT_SECRET env vars (see docs/integrations/CROWDSTRIKE.md)."
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());

        // Falcon ignores grant_type on /oauth2/token (it only supports
        // client_credentials anyway), but include it for OAuth-correctness.
        form.add("grant_type", "client_credentials");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = restClient.post()
            .uri(tokenUrl())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .body(form)
            .retrieve()
            .body(Map.class);

        if (body == null || !body.containsKey("access_token")) {
            throw new IllegalStateException("CrowdStrike OAuth response missing access_token");
        }

        cachedToken = String.valueOf(body.get("access_token"));
        long expiresInSec = body.get("expires_in") instanceof Number n ? n.longValue() : 1800L;
        cachedExpiry = Instant.now().plusSeconds(expiresInSec);

        // Never log the token itself — only its lifetime.
        log.info("[CrowdStrike] OAuth token refreshed; expires_in={}s (next refresh at {})",
            expiresInSec, cachedExpiry.minus(EXPIRY_SKEW));
        return cachedToken;
    }

    /** Compose {@code {baseUrl}/oauth2/token}, tolerating a trailing slash. */
    private String tokenUrl() {
        String base = props.getBaseUrl();
        if (base == null || base.isBlank()) return "https://api.eu-1.crowdstrike.com" + TOKEN_PATH;
        return base.endsWith("/")
            ? base.substring(0, base.length() - 1) + TOKEN_PATH
            : base + TOKEN_PATH;
    }

    /** Test seam — pre-seed the cache to avoid hitting the real /oauth2/token. */
    public void seedToken(String token, Instant expiry) {
        this.cachedToken = token;
        this.cachedExpiry = expiry;
    }

    /** Test seam — expose the cached expiry for assertion. */
    Instant peekExpiry() {
        return this.cachedExpiry;
    }

    /** Test seam — current cached token (for assertion that refresh swapped it). */
    String peekToken() {
        return this.cachedToken;
    }
}
