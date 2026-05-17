package com.tss.grc.security;

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
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;
    private final ObjectProvider<LicensingService> licensingProvider;

    public JwtTokenProvider(
            @Value("${grc.jwt.secret:}") String secret,
            @Value("${grc.jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs,
            @Value("${grc.jwt.refresh-token-expiry-ms:604800000}") long refreshTokenExpiryMs,
            ObjectProvider<LicensingService> licensingProvider) {
        validateSecret(secret);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
        this.licensingProvider = licensingProvider;
        log.info("JwtTokenProvider initialised: access={}ms refresh={}ms keyBytes={}",
                accessTokenExpiryMs, refreshTokenExpiryMs, secret.getBytes().length);
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
        return buildToken(principal, accessTokenExpiryMs);
    }

    public String generateRefreshToken(Authentication authentication) {
        GrcUserDetails principal = (GrcUserDetails) authentication.getPrincipal();
        return buildToken(principal, refreshTokenExpiryMs);
    }

    private String buildToken(GrcUserDetails principal, long expiryMs) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(String.valueOf(principal.getUserId()))
                .claim("usr",             principal.getUsername())
                .claim("org",             String.valueOf(principal.getOrganisationId()))
                .claim("roles",           roles)
                .claim("enabled_modules", enabledModulesAsList(principal.getOrganisationId()))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
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
        return buildTokenFromIds(userId, organisationId, accessTokenExpiryMs);
    }

    /** Generate a refresh token directly from stored claims (token rotation). */
    public String generateRefreshTokenFromClaims(Long userId, Long organisationId) {
        return buildTokenFromIds(userId, organisationId, refreshTokenExpiryMs);
    }

    private String buildTokenFromIds(Long userId, Long organisationId, long expiryMs) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("org",             String.valueOf(organisationId))
                .claim("enabled_modules", enabledModulesAsList(organisationId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
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
        } catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
