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

                // SCR-002 — the account's live state must gate the request.
                //
                // Authorities were always re-read from the database here, so a revoked ROLE takes
                // effect on the next request; that half of the report was wrong and no fix was
                // needed for it. `CustomUserDetailsService` also computes the account state
                // correctly, honouring `lockedUntil`. The defect was that **nothing consumed it**:
                // building a UsernamePasswordAuthenticationToken by hand skips the enabled /
                // accountNonLocked checks a DaoAuthenticationProvider applies at login. So a user
                // deactivated or locked out mid-session kept working until their access token
                // expired — up to an hour, and lockout is the control that answers a brute-force.
                if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                    log.warn("Rejected bearer token for {} account: user={} {} {}",
                        !userDetails.isEnabled() ? "disabled" : "locked",
                        userId, request.getMethod(), request.getRequestURI());
                    reject(response);
                    return;
                }

                Long tenantId = resolveTenant(userDetails, orgId, userId, request);
                if (tenantId == null) {
                    reject(response);
                    return;
                }

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);

                // Set tenant context for downstream repository filtering
                TenantContext.setCurrentTenant(tenantId);
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
     * SCR-002 — the tenant comes from the loaded user, not from the token, with one authorised
     * exception.
     *
     * <p>{@code TenantContext} previously took the {@code org} claim at face value. Every repository
     * query filters on that value, so it is the single most load-bearing number in a request — and it
     * came from a string the client presented rather than from the row that says which organisation
     * the user belongs to.
     *
     * <p><b>The exception is real and had to be measured, not assumed.</b>
     * {@code POST /organisations/switch/{id}} re-issues an access token carrying a <em>different</em>
     * {@code org} and deliberately does <b>not</b> change the user record — that is how a platform
     * admin operates a customer tenant. So a blanket "token org must equal the user's org → 401",
     * which is the obvious reading of the finding, would have broken tenant switching on the first
     * request after every switch. The endpoint is {@code @PreAuthorize("hasRole('PLATFORM_ADMIN')")},
     * so the override is exactly as privileged as the role that can request it.
     *
     * @return the tenant to bind, or {@code null} when the mismatch is not authorised (caller 401s)
     */
    private Long resolveTenant(UserDetails userDetails, Long tokenOrg, Long userId,
                               HttpServletRequest request) {
        Long userOrg = (userDetails instanceof GrcUserDetails u) ? u.getOrganisationId() : null;

        // No org on the loaded user (should not happen — the column is NOT NULL): trust nothing.
        if (userOrg == null) {
            log.warn("Rejected bearer token: loaded user {} has no organisation ({} {})",
                userId, request.getMethod(), request.getRequestURI());
            return null;
        }
        if (tokenOrg == null || userOrg.equals(tokenOrg)) {
            return userOrg;                        // the ordinary case — they agree
        }
        boolean platformAdmin = userDetails.getAuthorities().stream()
            .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
        if (platformAdmin) {
            // An authorised switch. Logged at INFO because a privileged cross-tenant session is
            // worth seeing in the record even when it is legitimate.
            log.info("Platform admin {} operating tenant {} (home {}) — {} {}",
                userId, tokenOrg, userOrg, request.getMethod(), request.getRequestURI());
            return tokenOrg;
        }
        // A non-admin presenting a token for another tenant. Never legitimate: the org claim is
        // signed, so this means either a leaked admin-issued token or a tampered one.
        log.warn("Rejected bearer token: user {} is in org {} but the token claims org {} ({} {})",
            userId, userOrg, tokenOrg, request.getMethod(), request.getRequestURI());
        return null;
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

    /**
     * SCR-002 — a rejected request leaves no state behind and never reaches the chain.
     *
     * <p>Both clears matter. The early {@code return} skips the {@code finally} that normally clears
     * {@code TenantContext}, because that block guards the {@code filterChain.doFilter} call rather
     * than this one — and servlet threads are pooled, so anything left set here would be inherited by
     * whatever request runs next on this thread. Nothing has been set on the reject path today, but
     * relying on that is exactly how the F2-41 thread-local nearly went wrong; clearing is free.
     *
     * <p>A bare 401 with no body, deliberately: it matches what SCR-001's slice 1 does on the
     * refresh-token-as-bearer path, and it tells an attacker probing with a stolen token nothing about
     * <em>why</em> it was refused — disabled, locked, and wrong-tenant are indistinguishable from
     * outside.
     */
    private static void reject(HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
