package com.tss.grc.module.auth.refresh_token.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.auth.refresh_token.entity.RefreshTokenStore;
import com.tss.grc.module.auth.refresh_token.repository.RefreshTokenStoreRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side refresh-token store (Open Issue #002).
 *
 * <p>Lifecycle:
 * <ol>
 *   <li><b>Issuance</b> — every {@code AuthService.login()} /
 *       {@code AuthService.refresh()} mints a refresh JWT with a server-generated
 *       {@code jti} claim (UUIDv4), then calls
 *       {@link #recordIssuance(Long, String, String, LocalDateTime, String, String)}
 *       to persist the row.</li>
 *   <li><b>Verify</b> — every {@code POST /api/auth/refresh} calls
 *       {@link #assertValid(String)} BEFORE the JWT signature verify path. This is
 *       a sub-millisecond seek on {@code UX_refresh_token_jti}. A missing,
 *       revoked, or expired row throws {@link InvalidRefreshTokenException}.</li>
 *   <li><b>Rolling refresh</b> — on every successful refresh the old {@code jti}
 *       is revoked with reason {@code ROLLING_REFRESH} and a new row is recorded
 *       for the new jti. A captured-then-replayed refresh token detects the
 *       attack: the legitimate user's next refresh will fail because their old
 *       jti was already used to mint a new one (and is now revoked).</li>
 *   <li><b>Logout</b> — {@code POST /api/auth/logout} calls
 *       {@link #revoke(String, Long, String)} with reason {@code USER_LOGOUT}.</li>
 *   <li><b>Admin revoke</b> — PLATFORM_ADMIN can call
 *       {@link #revokeAllForUser(Long, Long, String)} on a suspected-compromise
 *       account to flush every active session in {@code < 1s}.</li>
 * </ol>
 *
 * <p>F7 audit + F8 domain events on every mutation. Audit/event failures are
 * logged but never block the security-critical revoke path.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenStoreService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStoreService.class);

    /** F8 trigger names. */
    public static final String EV_ISSUED       = "REFRESH_TOKEN_ISSUED";
    public static final String EV_REVOKED      = "REFRESH_TOKEN_REVOKED";
    public static final String EV_BULK_REVOKED = "REFRESH_TOKEN_BULK_REVOKED";

    /** Revocation reasons accepted by {@link #revoke}/{@link #revokeAllForUser}. */
    public static final String REASON_USER_LOGOUT         = "USER_LOGOUT";
    public static final String REASON_ROLLING_REFRESH     = "ROLLING_REFRESH";
    public static final String REASON_ADMIN_REVOKE_USER   = "ADMIN_REVOKE_USER";
    public static final String REASON_SUSPECTED_COMPROMISE = "SUSPECTED_COMPROMISE";
    public static final String REASON_PASSWORD_RESET      = "PASSWORD_RESET";
    public static final String REASON_TENANT_SUSPENDED    = "TENANT_SUSPENDED";

    private final RefreshTokenStoreRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    /** Generate a fresh opaque jti for embedding in a new refresh JWT. */
    public String newJti() {
        return UUID.randomUUID().toString();
    }

    /**
     * Persist a freshly-minted refresh token. Called from
     * {@code AuthController.login} and the rolling-refresh path of
     * {@code AuthController.refresh}.
     */
    @Transactional
    public RefreshTokenStore recordIssuance(Long userId,
                                            String username,
                                            String jti,
                                            LocalDateTime expiresAt,
                                            String userAgent,
                                            String ipAddress) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (jti == null || jti.isBlank()) throw new IllegalArgumentException("jti is required");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt is required");

        Long orgId = TenantContext.getCurrentTenant();
        if (orgId == null) orgId = 1L;   // login is the only path where TenantContext isn't yet set

        RefreshTokenStore row = new RefreshTokenStore();
        row.setOrganisationId(orgId);
        row.setUserId(userId);
        row.setUsername(username);
        row.setJti(jti);
        row.setIssuedAt(LocalDateTime.now());
        row.setExpiresAt(expiresAt);
        row.setUserAgent(truncate(userAgent, 500));
        row.setIpAddress(truncate(ipAddress, 64));
        row.setCreatedBy(userId);

        RefreshTokenStore saved = repo.save(row);

        emitAudit(EV_ISSUED, saved.getId(),
            "Refresh token issued for user " + userId + " (jti=" + jti + ")");
        emitEvent(EV_ISSUED, saved, null);

        log.debug("Refresh token issued: user={} jti={} expires={}", userId, jti, expiresAt);
        return saved;
    }

    /**
     * Verify a presented refresh token's {@code jti} against the server-side
     * store. Throws {@link InvalidRefreshTokenException} if the row is missing,
     * revoked, or expired.
     */
    @Transactional(readOnly = true)
    public RefreshTokenStore assertValid(String jti) {
        if (jti == null || jti.isBlank()) {
            throw new InvalidRefreshTokenException("jti claim is missing");
        }
        Optional<RefreshTokenStore> opt = repo.findByJti(jti);
        if (opt.isEmpty()) {
            throw new InvalidRefreshTokenException("refresh token not found (jti=" + jti + ")");
        }
        RefreshTokenStore row = opt.get();
        if (row.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException(
                "refresh token revoked at " + row.getRevokedAt() + " (reason=" + row.getRevokeReason() + ")");
        }
        if (row.getExpiresAt() == null || row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("refresh token expired");
        }
        return row;
    }

    /** Revoke a single token by jti. Idempotent — repeated revoke is a no-op. */
    @Transactional
    public boolean revoke(String jti, Long revokedByUserId, String reason) {
        Optional<RefreshTokenStore> opt = repo.findByJti(jti);
        if (opt.isEmpty()) {
            log.debug("Revoke called for unknown jti={} — ignoring", jti);
            return false;
        }
        RefreshTokenStore row = opt.get();
        if (row.getRevokedAt() != null) {
            log.debug("Revoke called for already-revoked jti={} — no-op", jti);
            return false;
        }
        row.setRevokedAt(LocalDateTime.now());
        row.setRevokedByUserId(revokedByUserId);
        row.setRevokeReason(reason == null ? REASON_USER_LOGOUT : reason);
        row.setUpdatedAt(row.getRevokedAt());
        row.setUpdatedBy(revokedByUserId);
        repo.save(row);

        emitAudit(EV_REVOKED, row.getId(),
            "Refresh token revoked: jti=" + jti + " reason=" + row.getRevokeReason());
        emitEvent(EV_REVOKED, row, reason);

        log.info("Refresh token revoked: user={} jti={} reason={}", row.getUserId(), jti, row.getRevokeReason());
        return true;
    }

    /**
     * Revoke every active session for a user. Returns the number of rows
     * revoked. Idempotent — already-revoked rows are not touched.
     */
    @Transactional
    public int revokeAllForUser(Long userId, Long revokedByUserId, String reason) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        String resolvedReason = reason == null ? REASON_ADMIN_REVOKE_USER : reason;
        LocalDateTime now = LocalDateTime.now();
        int count = repo.revokeAllByUserId(userId, now, revokedByUserId, resolvedReason);

        emitAudit(EV_BULK_REVOKED, null,
            "Bulk-revoked " + count + " refresh tokens for user " + userId + " (reason=" + resolvedReason + ")");
        try {
            Long orgId = TenantContext.getCurrentTenant();
            publisher.publishEvent(new DomainEvent(
                orgId, EV_BULK_REVOKED, "User", userId, null,
                java.util.Map.of(
                    "userId", userId,
                    "tokensRevoked", count,
                    "reason", resolvedReason
                )));
        } catch (Exception ex) {
            log.warn("F8 emit failed for {}: {}", EV_BULK_REVOKED, ex.getMessage());
        }

        log.warn("Bulk-revoked {} refresh tokens for user {} (reason={})", count, userId, resolvedReason);
        return count;
    }

    /** "Your active sessions" — for the user-facing session manager UI. */
    @Transactional(readOnly = true)
    public List<RefreshTokenStore> listActive(Long userId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(orgId, userId);
    }

    /** Full history including revoked rows — for admin / forensic review. */
    @Transactional(readOnly = true)
    public List<RefreshTokenStore> listAll(Long userId) {
        Long orgId = TenantContext.getCurrentTenant();
        return repo.findByOrganisationIdAndUserIdOrderByIssuedAtDesc(orgId, userId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private void emitAudit(String action, Long entityId, String message) {
        try {
            audit.log(action, "RefreshToken", entityId, message);
        } catch (Exception ex) {
            // Audit must never break the security-critical revoke path.
            log.warn("F7 audit failed for {}: {}", action, ex.getMessage());
        }
    }

    private void emitEvent(String trigger, RefreshTokenStore row, String reason) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("jti",    row.getJti());
            payload.put("userId", row.getUserId());
            payload.put("issuedAt", row.getIssuedAt() == null ? null : row.getIssuedAt().toString());
            if (row.getRevokedAt() != null) payload.put("revokedAt", row.getRevokedAt().toString());
            if (reason != null) payload.put("reason", reason);
            publisher.publishEvent(new DomainEvent(
                row.getOrganisationId(), trigger, "RefreshToken", row.getId(), null, payload));
        } catch (Exception ex) {
            log.warn("F8 emit failed for {}: {}", trigger, ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Thrown when a presented refresh token's jti is missing/revoked/expired. */
    public static final class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) { super(message); }
    }
}
