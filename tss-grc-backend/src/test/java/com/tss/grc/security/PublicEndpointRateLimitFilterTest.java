package com.tss.grc.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-CFG-006 — verifies the per-IP throttle on the public token/portal
 * endpoints: protected paths are limited and rejected with 429 once the
 * window cap is exceeded, while non-public paths and other clients are
 * unaffected.
 */
class PublicEndpointRateLimitFilterTest {

    private PublicEndpointRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PublicEndpointRateLimitFilter();
        // Small, deterministic limits for the test.
        ReflectionTestUtils.setField(filter, "maxRequests", 3);
        ReflectionTestUtils.setField(filter, "windowSeconds", 60);
    }

    private MockHttpServletResponse fire(String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void protectedTokenPath_blocksAfterLimit() throws Exception {
        String uri = "/api/v1/tprm/bcp-attestations/by-token/abc";
        // First 3 pass.
        assertThat(fire(uri, "10.0.0.1").getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(fire(uri, "10.0.0.1").getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(fire(uri, "10.0.0.1").getStatus()).isEqualTo(HttpStatus.OK.value());
        // 4th over the cap → 429 with Retry-After.
        MockHttpServletResponse fourth = fire(uri, "10.0.0.1");
        assertThat(fourth.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(fourth.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void perIp_independentBuckets() throws Exception {
        String uri = "/api/v1/complaints/public/track/XYZ";
        for (int i = 0; i < 3; i++) fire(uri, "10.0.0.1");
        // Other client still within its own budget.
        assertThat(fire(uri, "10.0.0.2").getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void nonPublicPath_neverThrottled() throws Exception {
        String uri = "/api/v1/risk/risks";
        for (int i = 0; i < 10; i++) {
            assertThat(fire(uri, "10.0.0.1").getStatus()).isEqualTo(HttpStatus.OK.value());
        }
    }

    @Test
    void whistleblowerAndIntakePortals_areProtected() throws Exception {
        // Both public token surfaces are covered by the pattern list.
        for (int i = 0; i < 3; i++) fire("/api/v1/ethics/whistleblower/public/status/t1", "10.0.0.3");
        assertThat(fire("/api/v1/ethics/whistleblower/public/status/t1", "10.0.0.3").getStatus())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        for (int i = 0; i < 3; i++) fire("/api/v1/intake/track/REF-1", "10.0.0.4");
        assertThat(fire("/api/v1/intake/track/REF-1", "10.0.0.4").getStatus())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
