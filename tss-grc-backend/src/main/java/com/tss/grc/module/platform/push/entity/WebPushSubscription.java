package com.tss.grc.module.platform.push.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 9-F — Web Push subscription.
 *
 * <p>One row per (user × device) — created when a browser calls
 * {@code PushManager.subscribe()} on the mobile Crisis Room and POSTs
 * the resulting subscription envelope to
 * {@code /api/v1/platform/push/subscriptions}. The backend then has
 * everything it needs to send server-initiated Web Push notifications
 * (e.g. when a CRITICAL crisis is declared via the F8 rule engine).
 *
 * <p>The {@code endpoint} field can be several KB long (push services
 * embed JWTs and routing data) so it lives in NVARCHAR(MAX). To keep
 * the {@code (user_id, endpoint)} UQ index small the SQL migration
 * persists a {@code endpoint_hash} computed column and uniques on that.
 *
 * <p>Body-signing (VAPID + ECDH against {@code p256dhKey}/{@code authKey})
 * is performed by {@code WebPushService.dispatchToUserIds(...)} — v1 of
 * which stubs the send and just records the intent in the audit log.
 */
@Entity
@Table(schema = "platform", name = "web_push_subscription")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WebPushSubscription {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "endpoint", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, length = 255)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 255)
    private String authKey;

    @Column(name = "device_label", length = 255)
    private String deviceLabel;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
