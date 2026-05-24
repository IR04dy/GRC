package com.tss.grc.module.auth.refresh_token.controller;

import com.tss.grc.module.auth.refresh_token.entity.RefreshTokenStore;
import com.tss.grc.module.auth.refresh_token.service.RefreshTokenStoreService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Session-management endpoints (Open Issue #002).
 *
 * <p>Three user-facing endpoints + one admin endpoint:
 *
 * <pre>
 * GET  /api/v1/auth/sessions                 — list my active sessions
 * DELETE /api/v1/auth/sessions/{id}          — revoke one of my own sessions
 * POST /api/v1/auth/sessions/revoke-all      — log out of every device
 * POST /api/v1/auth/sessions/revoke-user/{id} — admin: log out another user
 *                                               (suspected compromise)
 * </pre>
 *
 * <p>The admin endpoint is restricted to {@code PLATFORM_ADMIN} via
 * {@code @PreAuthorize}. The three self-service endpoints are open to any
 * authenticated user but enforce ownership via the {@code userId} on the
 * row before allowing the revoke.
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "Active refresh-token session management")
public class RefreshTokenAdminController {

    private final RefreshTokenStoreService service;

    // ── Self-service ──────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List my active refresh-token sessions")
    public ResponseEntity<List<Map<String, Object>>> listMine(Authentication auth) {
        Long userId = principalUserId(auth);
        List<Map<String, Object>> out = service.listActive(userId).stream()
            .map(RefreshTokenAdminController::toDto)
            .toList();
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Revoke one of my own active sessions")
    public ResponseEntity<?> revokeOne(@PathVariable Long id, Authentication auth) {
        Long userId = principalUserId(auth);
        // List my sessions, find the matching id, revoke its jti.
        return service.listAll(userId).stream()
            .filter(r -> r.getId().equals(id))
            .findFirst()
            .map(row -> {
                if (row.getRevokedAt() != null) {
                    return ResponseEntity.ok(Map.<String, Object>of(
                        "id", id, "status", "ALREADY_REVOKED"));
                }
                service.revoke(row.getJti(), userId, RefreshTokenStoreService.REASON_USER_LOGOUT);
                return ResponseEntity.ok(Map.<String, Object>of(
                    "id", id, "status", "REVOKED"));
            })
            .orElseGet(() -> ResponseEntity.status(404).body(Map.<String, Object>of(
                "message", "Session not found or does not belong to current user")));
    }

    @PostMapping("/revoke-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Log out of every device (revoke all my sessions)")
    public ResponseEntity<Map<String, Object>> revokeAllMine(Authentication auth) {
        Long userId = principalUserId(auth);
        int revoked = service.revokeAllForUser(userId, userId,
            RefreshTokenStoreService.REASON_USER_LOGOUT);
        return ResponseEntity.ok(Map.of(
            "userId", userId, "revoked", revoked, "status", "OK"));
    }

    // ── Admin (PLATFORM_ADMIN only) ───────────────────────────────────

    @PostMapping("/revoke-user/{userId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin: revoke every active session for a user (suspected compromise)")
    public ResponseEntity<Map<String, Object>> revokeUser(@PathVariable Long userId,
                                                         Authentication auth) {
        Long actorId = principalUserId(auth);
        int revoked = service.revokeAllForUser(userId, actorId,
            RefreshTokenStoreService.REASON_SUSPECTED_COMPROMISE);
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "revokedBy", actorId,
            "revoked", revoked,
            "reason", RefreshTokenStoreService.REASON_SUSPECTED_COMPROMISE,
            "status", "OK"));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static Long principalUserId(Authentication auth) {
        Object p = auth.getPrincipal();
        if (p instanceof GrcUserDetails u) return u.getUserId();
        throw new IllegalStateException("Authentication principal is not a GrcUserDetails");
    }

    private static Map<String, Object> toDto(RefreshTokenStore r) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id",          r.getId());
        m.put("issuedAt",    r.getIssuedAt());
        m.put("expiresAt",   r.getExpiresAt());
        m.put("ipAddress",   r.getIpAddress());
        m.put("userAgent",   r.getUserAgent());
        m.put("revokedAt",   r.getRevokedAt());
        m.put("revokeReason", r.getRevokeReason());
        m.put("active",      r.isActive());
        return m;
    }
}
