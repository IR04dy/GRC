package com.tss.grc.module.auth.refresh_token.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Server-side record of an issued refresh token (Open Issue #002 — refresh
 * token revocation).
 *
 * <p>One row per refresh token minted by {@code AuthService.login} or
 * rolled by {@code AuthService.refresh}. The JWT carries an opaque
 * {@code jti} claim (UUIDv4) that points back to this row; on every
 * {@code POST /api/auth/refresh} the service does a fast
 * {@code WHERE jti = ?} lookup before verifying the JWT signature.
 * If the row is missing, the {@code revoked_at} column is non-null,
 * or {@code expires_at} is in the past, the refresh is rejected.
 *
 * <p>This collapses the previous 7-day "stolen token is valid until
 * expiry" window down to {@code < 1 second} once the user (or an admin)
 * invokes the revoke endpoint.
 *
 * <p>Multi-tenancy: every row carries {@code organisation_id} from the
 * principal's JWT — the user-facing "list my active sessions" endpoint
 * filters by {@code user_id} AND {@code organisation_id}.
 *
 * <p>Audit: no soft-delete column — revocation IS the soft-delete here.
 * The row stays in the table after revocation so the "Your sessions"
 * UI can show "Logged out at HH:MM from IP X.X.X.X" history.
 */
@Entity
@Table(schema = "platform", name = "refresh_token")
@Getter @Setter @NoArgsConstructor
public class RefreshTokenStore {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 120)
    private String username;

    /** Opaque random JWT ID claim — globally unique, primary lookup key. */
    @Column(name = "jti", nullable = false, length = 64, updatable = false)
    private String jti;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    /** NULL = active. Any non-null timestamp means the token is dead. */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_user_id")
    private Long revokedByUserId;

    /**
     * USER_LOGOUT | ROLLING_REFRESH | ADMIN_REVOKE_USER |
     * SUSPECTED_COMPROMISE | PASSWORD_RESET | TENANT_SUSPENDED
     */
    @Column(name = "revoke_reason", length = 100)
    private String revokeReason;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;

    /** Convenience: true if the row is still usable as a refresh token. */
    public boolean isActive() {
        return revokedAt == null
            && expiresAt != null
            && expiresAt.isAfter(LocalDateTime.now());
    }
}
