package com.tss.grc.module.integrations.crowdstrike;

import com.tss.grc.module.integrations.crowdstrike.config.CrowdStrikeProperties;
import com.tss.grc.module.integrations.crowdstrike.oauth.CrowdStrikeOAuthTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks in the OAuth token cache:
 *   - First call hits the network; subsequent calls within the TTL skew
 *     return the cached token without a second network call.
 *   - When the cached token is within 60s of expiry, the next getAccessToken()
 *     refreshes (re-fetches a new token).
 *   - {@code refresh()} forces a re-fetch even when the cache is still warm —
 *     used by the live client on a 401.
 *   - Missing credentials → IllegalStateException with a descriptive message.
 *
 * <p>The test uses an in-process {@link MockClientHttpRequest}-driven
 * {@link RestClient} so no real network is involved.
 */
class CrowdStrikeOAuthTokenManagerTest {

    private CrowdStrikeProperties props;
    private RestClient http;
    private Deque<String> bodiesToReturn;
    private int requestCount;

    @BeforeEach
    void setUp() {
        props = new CrowdStrikeProperties();
        props.setClientId("client-abc");
        props.setClientSecret("super-secret");
        props.setBaseUrl("https://api.eu-1.crowdstrike.com");

        bodiesToReturn = new ArrayDeque<>();
        requestCount = 0;

        // Build a RestClient backed by a custom request factory that returns
        // the next body from `bodiesToReturn` without doing any real I/O.
        http = RestClient.builder()
            .requestFactory((uri, method) -> {
                MockClientHttpRequest req = new MockClientHttpRequest(method, uri);
                req.setResponse(nextResponse());
                requestCount++;
                return req;
            })
            .build();
    }

    private MockClientHttpResponse nextResponse() {
        String body = bodiesToReturn.isEmpty() ? defaultBody(1800L, "tok-A") : bodiesToReturn.pop();
        MockClientHttpResponse resp = new MockClientHttpResponse(
            body.getBytes(StandardCharsets.UTF_8), 200
        );
        HttpHeaders h = resp.getHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return resp;
    }

    private static String defaultBody(long expiresIn, String token) {
        return "{\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + ",\"token_type\":\"Bearer\"}";
    }

    @Test
    void getAccessToken_cacheHit_returnsSameToken_andDoesNotHitNetworkTwice() {
        bodiesToReturn.push(defaultBody(1800L, "tok-first"));
        CrowdStrikeOAuthTokenManager mgr = new CrowdStrikeOAuthTokenManager(props, http);

        String first = mgr.getAccessToken();
        String second = mgr.getAccessToken();

        assertThat(first).isEqualTo("tok-first");
        assertThat(second).isEqualTo("tok-first");
        assertThat(requestCount).isEqualTo(1);
    }

    @Test
    void getAccessToken_nearExpiry_triggersRefresh() {
        // Seed with a token that has only 30s of life left — inside the 60s skew.
        CrowdStrikeOAuthTokenManager mgr = new CrowdStrikeOAuthTokenManager(props, http);
        mgr.seedToken("tok-stale", Instant.now().plusSeconds(30));

        bodiesToReturn.push(defaultBody(1800L, "tok-fresh"));
        String t = mgr.getAccessToken();
        assertThat(t).isEqualTo("tok-fresh");
        assertThat(requestCount).isEqualTo(1);
    }

    @Test
    void refresh_forcesRefetch_evenWhenCacheIsWarm() {
        CrowdStrikeOAuthTokenManager mgr = new CrowdStrikeOAuthTokenManager(props, http);
        mgr.seedToken("tok-warm", Instant.now().plusSeconds(1200));

        bodiesToReturn.push(defaultBody(1800L, "tok-after-refresh"));
        String t = mgr.refresh();
        assertThat(t).isEqualTo("tok-after-refresh");
        assertThat(requestCount).isEqualTo(1);
    }

    @Test
    void refresh_withoutCredentials_throws() {
        CrowdStrikeProperties bad = new CrowdStrikeProperties();
        // clientId / clientSecret default to "" — hasOAuthCredentials() == false
        CrowdStrikeOAuthTokenManager mgr = new CrowdStrikeOAuthTokenManager(bad, http);
        assertThatThrownBy(mgr::refresh)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CROWDSTRIKE_CLIENT_ID");
    }
}
