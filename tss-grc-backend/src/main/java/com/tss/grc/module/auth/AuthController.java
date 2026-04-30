package com.tss.grc.module.auth;

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
@Tag(name = "Authentication", description = "JWT login, token refresh, and logout")
public class AuthController {

    private final AuthenticationManager authManager;
    private final JwtTokenProvider      tokenProvider;

    @Value("${grc.jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "Authenticate with username and password")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        GrcUserDetails principal   = (GrcUserDetails) auth.getPrincipal();
        String          accessToken = tokenProvider.generateAccessToken(auth);
        String          refreshToken = tokenProvider.generateRefreshToken(auth);

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

        // Build a minimal authentication for token generation
        String newAccessToken = tokenProvider.generateAccessTokenFromClaims(userId, orgId);

        // Roll the refresh token (rotation)
        String newRefreshToken = tokenProvider.generateRefreshTokenFromClaims(userId, orgId);
        setRefreshTokenCookie(response, newRefreshToken);

        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the refresh token cookie")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("grc_refresh", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/auth/refresh");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("grc_refresh", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);                        // HTTPS only in production
        cookie.setPath("/api/auth/refresh");           // Scoped — not sent on every API call
        cookie.setMaxAge((int) (refreshTokenExpiryMs / 1000));
        // SameSite=Strict set via header (Cookie API doesn't expose SameSite pre-Java 21)
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
