package com.tss.grc.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

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
 *   "sub":   "42",              // user ID (internal BIGINT, not username)
 *   "usr":   "jdoe",           // username for display
 *   "org":   "7",              // organisationId (tenant discriminator)
 *   "roles": ["RISK_MANAGER"], // role names for client-side UI decisions
 *   "iat":   ...,
 *   "exp":   ...
 * }
 * </pre>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtTokenProvider(
            @Value("${grc.jwt.secret}") String secret,
            @Value("${grc.jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs,
            @Value("${grc.jwt.refresh-token-expiry-ms:604800000}") long refreshTokenExpiryMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
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
                .claim("usr",   principal.getUsername())
                .claim("org",   String.valueOf(principal.getOrganisationId()))
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
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
                .claim("org", String.valueOf(organisationId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
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
