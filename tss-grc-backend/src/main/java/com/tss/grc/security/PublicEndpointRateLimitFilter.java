package com.tss.grc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SEC-CFG-006 — per-IP brute-force / enumeration throttle for the public,
 * unauthenticated portal endpoints where a per-record token IS the only
 * credential (TPRM vendor BCP attestation by-token, whistleblower portal,
 * customer-complaints public tracker, case-intake tracker, PDPL public
 * sub-processor list).
 *
 * <p>These paths are intentionally {@code permitAll} in {@link
 * com.tss.grc.config.SecurityConfig}: authorization is delegated entirely to
 * token possession. The submission tokens are 122-bit random type-4 UUIDs, so
 * the token space itself is not feasibly guessable; the gap this filter closes
 * is the <em>absence of any compensating throttle</em> behind the permit, which
 * left the surface open to high-rate online guessing/enumeration with no
 * lockout (the login lockout governs {@code /api/auth/login} only, not these
 * token lookups).
 *
 * <p>Implementation is a dependency-free fixed-window counter keyed by client
 * IP. When a client exceeds {@code grc.security.public-rate-limit.max-requests}
 * within {@code window-seconds}, the request is rejected with HTTP 429 and a
 * {@code Retry-After} header. Authenticated/internal traffic is unaffected —
 * the filter only inspects the public token/portal path prefixes below and
 * passes everything else straight through.
 *
 * <p>The filter is wired <em>before</em> {@link JwtAuthenticationFilter} so the
 * throttle applies to anonymous callers (which never reach the JWT filter's
 * authentication logic anyway). It is a coarse in-memory backstop; a
 * distributed deployment behind multiple nodes should front this with a shared
 * gateway throttle, but the single-node guard removes the wide-open default.
 */
@Slf4j
@Component
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {

    /**
     * The public, token-credentialed paths to throttle. Mirrors the
     * {@code permitAll} matchers in {@link com.tss.grc.config.SecurityConfig}
     * that are gated solely by a per-record token. Login/refresh/logout,
     * Swagger, actuator health and the WebSocket upgrade are deliberately NOT
     * listed — login already has its own lockout and the rest are not
     * token-enumeration surfaces.
     */
    private static final List<String> PROTECTED_PATTERNS = List.of(
        "/api/v1/tprm/bcp-attestations/by-token/**",
        "/api/v1/ethics/whistleblower/public/**",
        "/api/v1/complaints/public/**",
        "/api/v1/intake/track/**",
        "/api/v1/privacy/subprocessors/public",
        "/api/public/workflow-requests/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** Max requests per client IP within the window before 429. */
    @Value("${grc.security.public-rate-limit.max-requests:30}")
    private int maxRequests;

    /** Length of the fixed counting window, in seconds. */
    @Value("${grc.security.public-rate-limit.window-seconds:60}")
    private int windowSeconds;

    /** ip -> current window's counter. */
    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        long nowMs = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        Window window = counters.compute(ip, (k, existing) -> {
            if (existing == null || nowMs - existing.startMs >= windowMs) {
                return new Window(nowMs);
            }
            return existing;
        });

        int count = window.count.incrementAndGet();

        // Opportunistic cleanup so the map cannot grow unbounded under a
        // distributed-source flood: prune stale windows when the map gets large.
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(e -> nowMs - e.getValue().startMs >= windowMs);
        }

        if (count > maxRequests) {
            long retryAfterSec = Math.max(1, (window.startMs + windowMs - nowMs) / 1000);
            log.warn("[public-rate-limit] 429 for ip={} path={} ({} reqs in window)",
                     ip, request.getRequestURI(), count);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSec));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : PROTECTED_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Best-effort client IP. Honours the first hop of X-Forwarded-For when set
     * (the reverse proxy in prod terminates TLS and forwards the real client
     * IP); falls back to the socket remote address. Note: X-Forwarded-For is
     * spoofable by a direct caller, so this is a throttle, not an authz control.
     */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    /** A single fixed counting window for one client IP. */
    private static final class Window {
        final long startMs;
        final AtomicInteger count = new AtomicInteger(0);
        Window(long startMs) { this.startMs = startMs; }
    }
}
