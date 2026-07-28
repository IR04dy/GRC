package com.tss.grc.security;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter — runs once per request.
 *
 * <p>Extracts the Bearer token from the {@code Authorization} header,
 * validates it via {@link JwtTokenProvider}, loads the {@link UserDetails},
 * and sets the {@link org.springframework.security.core.Authentication} in
 * the {@link SecurityContextHolder}.
 *
 * <p>Also sets the tenant context ({@link TenantContext}) from the JWT
 * {@code org} claim so that all repository queries are automatically scoped
 * to the authenticated user's organisation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider         tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {
        try {
            String token = extractBearerToken(request);
            if (StringUtils.hasText(token) && tokenProvider.validateToken(token)
                    && !isRefreshTokenPresentedAsBearer(token, request)) {

                Long userId  = tokenProvider.getUserIdFromToken(token);
                Long orgId   = tokenProvider.getOrganisationIdFromToken(token);

                UserDetails userDetails = userDetailsService.loadUserById(userId);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);

                // Set tenant context for downstream repository filtering
                TenantContext.setCurrentTenant(orgId);
            }
        } catch (Exception ex) {
            log.error("Cannot set user authentication from JWT: {}", ex.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear tenant context to prevent thread-local leakage in thread pools
            TenantContext.clear();
        }
    }

    /**
     * SCR-001 — a refresh token must never authenticate an API request.
     *
     * <p>A signed, unexpired refresh token used to pass this filter and receive the user's full
     * authority set, and it kept working after logout because refresh-token revocation is checked
     * only by {@code /api/auth/refresh}. Reproduced live before this fix: the access token returned
     * 200 (control), the refresh token returned 200, and the <em>revoked</em> refresh token returned
     * 200 while the refresh endpoint correctly rejected the same token with 401.
     *
     * <p>Returning true here leaves the security context empty, so the request continues unauthenticated
     * and Spring Security answers 401 — the same shape as an absent or malformed token, and
     * deliberately not a distinct code: a caller presenting the wrong token kind learns only that it
     * was not accepted.
     *
     * <p>Logged at WARN with the path, because there is no legitimate client that does this. The SPA
     * keeps the access token in {@code sessionStorage} and never sees the refresh cookie, which is
     * {@code HttpOnly} — so a hit here is either an integration using the wrong token or someone
     * replaying a stolen one. The token itself is never logged.
     */
    private boolean isRefreshTokenPresentedAsBearer(String token, HttpServletRequest request) {
        if (!tokenProvider.isRefreshToken(token)) {
            return false;
        }
        log.warn("Refresh token presented as a bearer credential on {} {} — rejected (SCR-001). "
                 + "Refresh tokens are only valid at /api/auth/refresh.",
                 request.getMethod(), request.getRequestURI());
        return true;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
