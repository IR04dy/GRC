package com.tss.grc.security;

import com.tss.grc.module.governance.threelines.service.ThreeLinesService;
import com.tss.grc.module.platform.licensing.service.LicensingService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * JWT token creation and validation.
 *
 * <h3>Token Strategy</h3>
 * <ul>
 *   <li><strong>Access token</strong> — short-lived (default 15 min); sent in every API request
 *       as {@code Authorization: Bearer <token>}; stored in browser {@code sessionStorage}</li>
 *   <li><strong>Refresh token</strong> — longer-lived (default 7 days); issued alongside the
 *       access token; stored as an HttpOnly, Secure, SameSite=Strict cookie by
 *       {@code AuthController}; used to obtain a new access token without re-login</li>
 * </ul>
 *
 * <h3>Claims</h3>
 * <pre>
 * {
 *   "sub":             "42",              // user ID (internal BIGINT, not username)
 *   "usr":             "jdoe",           // username for display
 *   "org":             "7",              // organisationId (tenant discriminator)
 *   "roles":           ["RISK_MANAGER"], // role names for client-side UI decisions
 *   "enabled_modules": ["RISK","BCM"],   // F9 — licensed modules at issue time
 *   "iat":             ...,
 *   "exp":             ...
 * }
 * </pre>
 *
 * <p><b>F9 licensing claim:</b> the {@code enabled_modules} claim is populated from
 * {@link LicensingService#enabledModulesFor(Long)} at token-issue time. The frontend uses
 * it to hide sidebar entries for unlicensed modules without a round trip; the backend
 * still enforces via the {@code @RequireModule} aspect on every protected method.
 * Wrapped in {@link ObjectProvider} so the security layer doesn't pull the licensing
 * bean into its hard-init dependency graph — the provider returns an empty set if the
 * service hasn't bootstrapped yet, which is fine for the very first request.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    /**
     * Optional previous-generation key used <b>only for verification</b>, never for signing.
     * Populated from {@code grc.jwt.secret-previous} (env {@code JWT_SECRET_PREVIOUS}).
     * Enables the 24-hour overlap window during scheduled key rotation — see
     * {@code docs/operations/jwt-secret-rotation.md} (Open Issue #001).
     *
     * <p>When non-null, {@link #parseClaims(String)} first attempts verification against
     * {@link #secretKey}; on signature failure it falls back to this key. All freshly
     * signed tokens use {@link #secretKey} only, so the previous key naturally ages out
     * as old tokens expire.
     */
    private final SecretKey previousSecretKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;
    private final ObjectProvider<LicensingService>    licensingProvider;
    private final ObjectProvider<ThreeLinesService>   threeLinesProvider;

    public JwtTokenProvider(
            @Value("${grc.jwt.secret:}") String secret,
            @Value("${grc.jwt.secret-previous:}") String previousSecret,
            @Value("${grc.jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs,
            @Value("${grc.jwt.refresh-token-expiry-ms:604800000}") long refreshTokenExpiryMs,
            ObjectProvider<LicensingService>  licensingProvider,
            ObjectProvider<ThreeLinesService> threeLinesProvider) {
        validateSecret(secret);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.previousSecretKey = loadPreviousSecret(previousSecret);
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
        this.licensingProvider  = licensingProvider;
        this.threeLinesProvider = threeLinesProvider;
        log.info("JwtTokenProvider initialised: access={}ms refresh={}ms keyBytes={} previousKey={}",
                accessTokenExpiryMs, refreshTokenExpiryMs, secret.getBytes().length,
                previousSecretKey == null ? "absent" : "present (rotation grace window active)");
    }

    /**
     * Load the optional previous-generation secret. Returns {@code null} when the
     * env var is unset/blank — the normal steady state. Applies the same strength
     * checks as the primary key when present, so a misconfigured rotation can't
     * silently install a weak fallback.
     */
    static SecretKey loadPreviousSecret(String previousSecret) {
        if (previousSecret == null || previousSecret.isBlank()) {
            return null;
        }
        // Re-use the same validation rules — a placeholder/short value here would
        // make rotation trivially break, and a *signed by us* token must always be
        // signed by something at least as strong as the primary.
        validateSecret(previousSecret);
        return Keys.hmacShaKeyFor(previousSecret.getBytes());
    }

    /**
     * Fail-fast validation of the JWT secret.
     *
     * <p>Refuses to start the application if the secret is missing, too short, or matches
     * one of the known placeholder strings. This prevents the historic bug where a missing
     * {@code JWT_SECRET} env var caused the app to silently fall back to a known-bad
     * default, producing a deployment that signs all tokens with a public-knowledge key.
     *
     * <p>Required: ≥ 32 ASCII characters (256 bits, the minimum HS256/HS384 strength).
     */
    static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable is not set. Refusing to start the application. " +
                "Generate a secret with: openssl rand -base64 48");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 32 characters (256 bits). Current length: " + secret.length());
        }
        // Reject the historical placeholder strings that previously lived in application*.yml
        String lower = secret.toLowerCase();
        if (lower.contains("change-this") || lower.contains("change_me") ||
            lower.contains("placeholder") || lower.contains("provide_jwt_secret")) {
            throw new IllegalStateException(
                "JWT_SECRET appears to be a placeholder value. Provide a real secret via the " +
                "JWT_SECRET environment variable.");
        }
    }

    public String generateAccessToken(Authentication authentication) {
        GrcUserDetails principal = (GrcUserDetails) authentication.getPrincipal();
        return buildToken(principal, accessTokenExpiryMs, null);
    }

    public String generateRefreshToken(Authentication authentication) {
        GrcUserDetails principal = (GrcUserDetails) authentication.getPrincipal();
        return buildToken(principal, refreshTokenExpiryMs, null);
    }

    /**
     * Generate a refresh token with an explicit server-issued {@code jti} claim
     * — used by the Open Issue #002 server-side refresh-token store. The
     * caller persists the jti via {@code RefreshTokenStoreService.recordIssuance}
     * BEFORE returning the token to the client so that the first /refresh call
     * has a row to look up.
     */
    public String generateRefreshTokenWithJti(Authentication authentication, String jti) {
        GrcUserDetails principal = (GrcUserDetails) authentication.getPrincipal();
        return buildToken(principal, refreshTokenExpiryMs, jti);
    }

    private String buildToken(GrcUserDetails principal, long expiryMs, String jti) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        var builder = Jwts.builder()
                .subject(String.valueOf(principal.getUserId()))
                .claim("usr",             principal.getUsername())
                .claim("org",             String.valueOf(principal.getOrganisationId()))
                .claim("roles",           roles)
                .claim("enabled_modules", enabledModulesAsList(principal.getOrganisationId()))
                .claim("lod_band",        lodBandsFor(principal.getUserId()))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey);

        if (jti != null && !jti.isBlank()) {
            builder.id(jti);
        }
        return builder.compact();
    }

    /**
     * Look up the user's LoD bands via {@link ThreeLinesService}. Returns
     * an empty list when the service isn't bootstrapped yet (e.g. very
     * first request, test slices without the governance module on the
     * classpath). The frontend treats an empty array as "no band
     * highlight"; the dashboard endpoint is still authoritative.
     *
     * <p>Sprint 11 — additive only, never breaks token shape for callers
     * that don't read the claim.
     */
    private List<String> lodBandsFor(Long userId) {
        if (userId == null) return Collections.emptyList();
        ThreeLinesService svc = threeLinesProvider.getIfAvailable();
        if (svc == null) return Collections.emptyList();
        try {
            List<String> bands = svc.bandsForUser(userId);
            return bands == null ? Collections.emptyList() : List.copyOf(bands);
        } catch (Exception ex) {
            log.warn("Failed to load lod_band for user {}: {}", userId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Look up the org's enabled modules, returning an empty list if the licensing service
     * is unavailable (e.g. during the very first request after boot, or in test slices that
     * exclude the licensing bean). Returning empty is safe because the LicenseGuard aspect
     * is the authoritative check — the JWT claim is purely a UX hint for the frontend.
     */
    private List<String> enabledModulesAsList(Long organisationId) {
        if (organisationId == null) return Collections.emptyList();
        LicensingService svc = licensingProvider.getIfAvailable();
        if (svc == null) return Collections.emptyList();
        try {
            Set<String> modules = svc.enabledModulesFor(organisationId);
            return modules == null ? Collections.emptyList() : List.copyOf(modules);
        } catch (Exception ex) {
            log.warn("Failed to load enabled_modules for org {}: {}", organisationId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public Long getOrganisationIdFromToken(String token) {
        return Long.parseLong(parseClaims(token).get("org", String.class));
    }

    /** Generate an access token directly from stored claims (used in refresh flow). */
    public String generateAccessTokenFromClaims(Long userId, Long organisationId) {
        return buildTokenFromIds(userId, organisationId, accessTokenExpiryMs, null);
    }

    /** Generate a refresh token directly from stored claims (token rotation). */
    public String generateRefreshTokenFromClaims(Long userId, Long organisationId) {
        return buildTokenFromIds(userId, organisationId, refreshTokenExpiryMs, null);
    }

    /**
     * Rolling-refresh path: mint a new refresh token from the previous token's
     * claims plus a fresh server-issued {@code jti}. Caller must persist the
     * new jti via {@code RefreshTokenStoreService.recordIssuance} BEFORE
     * returning the token to the client.
     */
    public String generateRefreshTokenFromClaimsWithJti(Long userId, Long organisationId, String jti) {
        return buildTokenFromIds(userId, organisationId, refreshTokenExpiryMs, jti);
    }

    private String buildTokenFromIds(Long userId, Long organisationId, long expiryMs, String jti) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("org",             String.valueOf(organisationId))
                .claim("enabled_modules", enabledModulesAsList(organisationId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey);
        if (jti != null && !jti.isBlank()) {
            builder.id(jti);
        }
        return builder.compact();
    }

    /**
     * Read the {@code jti} claim from a token without throwing on signature or
     * expiry failures (those are the caller's responsibility). Returns {@code null}
     * if the claim is absent. Used by the refresh-token-store verify path which
     * needs the jti to perform its server-side lookup BEFORE the signature check.
     */
    public String getJtiFromToken(String token) {
        try {
            return parseClaims(token).getId();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Compute the expiry timestamp of a token (for store persistence). */
    public java.time.LocalDateTime getExpiresAtFromToken(String token) {
        Date exp = parseClaims(token).getExpiration();
        if (exp == null) return null;
        return exp.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Read the {@code enabled_modules} claim if present. Returns an empty list if the claim
     * is missing or the token is malformed — the LicenseGuard aspect re-checks the licence
     * server-side regardless, so a missing claim only affects the frontend UX hint.
     */
    public List<String> getEnabledModulesFromToken(String token) {
        try {
            Object claim = parseClaims(token).get("enabled_modules");
            if (claim instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return Collections.emptyList();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            // Base JwtException covers signature mismatch (io.jsonwebtoken.security.
            // SignatureException — a stale/foreign-key refresh cookie), malformed,
            // unsupported, and empty/null tokens. Previously SignatureException was
            // NOT caught here, so a stale refresh cookie escaped as an unhandled
            // exception → HTTP 500 on /api/auth/refresh instead of a clean 401.
            // Any invalid token is simply "not valid" — never a 500.
            log.warn("Invalid JWT: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Verify the token signature and return its claims.
     *
     * <p>Tries the current {@link #secretKey} first; if that fails with a
     * signature-related error <em>and</em> a {@link #previousSecretKey} is configured,
     * retries with the previous key. This implements the 24-hour overlap window
     * described in {@code docs/operations/jwt-secret-rotation.md}: an operator sets
     * {@code JWT_SECRET_PREVIOUS} to the outgoing key, sets {@code JWT_SECRET} to the
     * new one, restarts the backend — and existing sessions stay valid until they
     * naturally expire, after which {@code JWT_SECRET_PREVIOUS} can be cleared.
     *
     * <p>Expiry, malformed-token, and unsupported-token errors are <em>not</em>
     * retried with the previous key — they're not signature problems and retrying
     * would only mask the real cause.
     */
    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (io.jsonwebtoken.security.SignatureException sigEx) {
            if (previousSecretKey == null) throw sigEx;
            // Rotation grace window — try the previous key.
            Claims claims = Jwts.parser()
                    .verifyWith(previousSecretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            log.debug("JWT verified via previous-generation key (rotation grace window).");
            return claims;
        }
    }
}
