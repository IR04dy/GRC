package com.tss.grc.module.auth;

import com.tss.grc.module.auth.refresh_token.service.RefreshTokenStoreService;
import com.tss.grc.security.GrcUserDetails;
import com.tss.grc.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Authentication REST endpoints consumed by the tss-grc-ui frontend.
 *
 * <pre>
 * POST /api/auth/login    — credential login → access token (body) + refresh token (HttpOnly cookie)
 * POST /api/auth/refresh  — use refresh cookie to obtain a new access token
 * POST /api/auth/logout   — invalidate the refresh token cookie
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "JWT login, token refresh, and logout")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider      tokenProvider;
    private final LoginAttemptService   loginAttempts;
    /**
     * Optional — server-side refresh-token store (Open Issue #002). When the
     * bean isn't on the classpath (e.g. in slim test slices) the controller
     * falls back to the legacy stateless refresh path.
     */
    private final org.springframework.beans.factory.ObjectProvider<RefreshTokenStoreService> refreshStoreProvider;

    @Value("${grc.jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Authenticate with username and password")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password()));
        } catch (AuthenticationException ex) {
            // SEC-AUTH-001: record the failed attempt (and lock at the threshold).
            // Generic 401 either way — never reveal whether the account is locked.
            loginAttempts.onFailure(loginRequest.username());
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        GrcUserDetails principal   = (GrcUserDetails) auth.getPrincipal();
        // SEC-AUTH-001: successful login clears the failure counter + lock and stamps lastLoginAt.
        loginAttempts.onSuccess(principal.getUserId());
        String          accessToken = tokenProvider.generateAccessToken(auth);

        // Open Issue #002 — if the refresh-token store is available, mint the
        // refresh token with a server-generated jti and persist it BEFORE
        // emitting the cookie. Falls back to a stateless refresh token only
        // when the store bean is absent (slim test slices).
        RefreshTokenStoreService store = refreshStoreProvider.getIfAvailable();
        String refreshToken;
        if (store != null) {
            String jti = store.newJti();
            refreshToken = tokenProvider.generateRefreshTokenWithJti(auth, jti);
            try {
                java.time.LocalDateTime expiresAt = tokenProvider.getExpiresAtFromToken(refreshToken);
                String ua = request.getHeader("User-Agent");
                String ip = extractIp(request);
                // Set TenantContext so the audit row gets the right org_id —
                // login() runs before JwtAuthenticationFilter has a chance.
                com.tss.grc.common.tenant.TenantContext.setCurrentTenant(principal.getOrganisationId());
                try {
                    store.recordIssuance(principal.getUserId(), principal.getUsername(),
                        jti, expiresAt, ua, ip);
                } finally {
                    com.tss.grc.common.tenant.TenantContext.clear();
                }
            } catch (Exception ex) {
                // If persistence fails, the refresh token would be unverifiable
                // — fail closed so the user gets a clean 500 rather than a
                // silently broken session.
                log.error("Failed to record refresh-token issuance for userId={}", principal.getUserId(), ex);
                return ResponseEntity.status(500).body(Map.of(
                    "message", "Unable to record refresh token — please retry."));
            }
        } else {
            refreshToken = tokenProvider.generateRefreshToken(auth);
        }

        // Refresh token → HttpOnly, Secure, SameSite=Strict cookie
        setRefreshTokenCookie(response, refreshToken);

        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return ResponseEntity.ok(LoginResponse.of(
                accessToken,
                principal.getUserId(),
                principal.getUsername(),
                principal.getOrganisationId(),
                roles
        ));
    }

    // ── Token Refresh ─────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Obtain a new access token using the refresh token cookie")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null || !tokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("message", "Refresh token expired or invalid"));
        }

        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        Long orgId  = tokenProvider.getOrganisationIdFromToken(refreshToken);
        String oldJti = tokenProvider.getJtiFromToken(refreshToken);

        // Open Issue #002 — server-side verify before signature trust + rolling
        // revocation of the old jti. The signature was already checked above
        // via validateToken(); now ensure the row still exists and is active.
        RefreshTokenStoreService store = refreshStoreProvider.getIfAvailable();
        if (store != null) {
            try {
                store.assertValid(oldJti);
            } catch (RefreshTokenStoreService.InvalidRefreshTokenException ex) {
                return ResponseEntity.status(401).body(Map.of(
                    "message", "Refresh token revoked or unknown — please sign in again."));
            }
        }

        // Build a minimal authentication for token generation
        String newAccessToken = tokenProvider.generateAccessTokenFromClaims(userId, orgId);

        // Roll the refresh token (rotation). When the store is wired, mint
        // the new token with a fresh server-generated jti, persist it, and
        // revoke the previous jti — a captured-then-replayed old token will
        // fail on the next refresh because its row is now revoked.
        String newRefreshToken;
        if (store != null) {
            String newJti = store.newJti();
            newRefreshToken = tokenProvider.generateRefreshTokenFromClaimsWithJti(userId, orgId, newJti);
            try {
                java.time.LocalDateTime expiresAt = tokenProvider.getExpiresAtFromToken(newRefreshToken);
                String ua = request.getHeader("User-Agent");
                String ip = extractIp(request);
                com.tss.grc.common.tenant.TenantContext.setCurrentTenant(orgId);
                try {
                    store.recordIssuance(userId, /*username unknown here*/ null,
                        newJti, expiresAt, ua, ip);
                    if (oldJti != null && !oldJti.isBlank()) {
                        store.revoke(oldJti, userId, RefreshTokenStoreService.REASON_ROLLING_REFRESH);
                    }
                } finally {
                    com.tss.grc.common.tenant.TenantContext.clear();
                }
            } catch (Exception ex) {
                log.error("Failed to rotate refresh token for userId={}", userId, ex);
                return ResponseEntity.status(500).body(Map.of(
                    "message", "Unable to rotate refresh token — please retry."));
            }
        } else {
            // SCR-001 — this fallback used to mint a refresh token with NO jti, and that would have
            // punched a hole straight through the interim fix: the bearer filter identifies a refresh
            // token by the presence of a jti, so a jti-less refresh token would still have been
            // accepted as an API bearer credential. The store being absent is a reason not to
            // *persist* the id, never a reason to leave the token unmarked. The jti is random and
            // unverifiable here, which is exactly as much as this branch can promise — rotation
            // revocation needs the store; marking the token's purpose does not.
            newRefreshToken = tokenProvider.generateRefreshTokenFromClaimsWithJti(
                userId, orgId, java.util.UUID.randomUUID().toString());
        }
        setRefreshTokenCookie(response, newRefreshToken);

        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the refresh token cookie")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Open Issue #002 — server-side revoke of the refresh token jti, so
        // a stolen cookie can't survive logout. Best-effort: if the cookie is
        // already absent or malformed we still clear the cookie below.
        RefreshTokenStoreService store = refreshStoreProvider.getIfAvailable();
        if (store != null) {
            String refreshToken = extractRefreshCookie(request);
            if (refreshToken != null) {
                String jti = tokenProvider.getJtiFromToken(refreshToken);
                if (jti != null && !jti.isBlank()) {
                    try {
                        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
                        store.revoke(jti, userId, RefreshTokenStoreService.REASON_USER_LOGOUT);
                    } catch (Exception ignored) {
                        // Token may already be expired/malformed — clearing
                        // the cookie below is still the right outcome.
                    }
                }
            }
        }

        // Send a single, fully-attributed Set-Cookie matching the
        // login-time cookie so the browser clears it. The Cookie API
        // doesn't expose SameSite pre-Java 21 / Servlet 6, so we
        // write the header directly — same approach as setRefreshTokenCookie
        // below for consistency.
        response.addHeader("Set-Cookie",
            "grc_refresh=; HttpOnly; Secure; Path=/api/auth/refresh; SameSite=Strict; Max-Age=0");
        return ResponseEntity.noContent().build();
    }

    /** Best-effort client IP — honours X-Forwarded-For when behind nginx. */
    private static String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return req.getRemoteAddr();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        // Single source of truth — write the Set-Cookie header directly with
        // every attribute (HttpOnly, Secure, Path, SameSite, Max-Age). The
        // earlier code ALSO called response.addCookie(new Cookie(...))
        // which emitted a SECOND Set-Cookie header without SameSite, so
        // browsers received two cookies for the same name and the resulting
        // attribute set was implementation-defined. SameSite=Strict is what
        // pins refresh tokens to first-party navigation only — if it gets
        // dropped, CSRF protection on the refresh endpoint disappears.
        response.addHeader("Set-Cookie",
            String.format("grc_refresh=%s; HttpOnly; Secure; Path=/api/auth/refresh; SameSite=Strict; Max-Age=%d",
                refreshToken, refreshTokenExpiryMs / 1000));
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "grc_refresh".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
    ) {}

    public record LoginResponse(
        String       accessToken,
        UserInfo     user
    ) {
        public static LoginResponse of(String token, Long userId, String username,
                                        Long orgId, List<String> roles) {
            return new LoginResponse(token, new UserInfo(userId, username, orgId, roles));
        }
    }

    public record UserInfo(
        Long         userId,
        String       username,
        Long         organisationId,
        List<String> roles
    ) {}
}
