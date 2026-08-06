package com.tss.grc.module.platform.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "platform", name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    @Column(name = "message_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String messageEn;

    /**
     * F2-38 — {@code message_ar} has existed since {@code V1__core_schemas.sql} and was mapped by
     * nothing. The column holds real Arabic on <b>42 of the 445 live rows</b>, and no Java code could
     * read or write it: the entity mapped {@code title_ar} but not this, so notifications were
     * <i>half</i> bilingual. Adding the mapping is what makes those 42 rows reachable at all.
     *
     * <p>⚠ Reachable is not the same as written. The three public notify methods on
     * {@link com.tss.grc.module.platform.service.NotificationService} take {@code titleAr} as a
     * parameter and do <b>not</b> take a message body in Arabic — the signature itself encodes the
     * gap — so all <b>39 call sites across 14 files</b> leave this null by construction. Closing that
     * needs real Saudi GRC Arabic copy for 39 messages, not a mechanical edit, and is tracked
     * separately rather than filled with machine translation.
     */
    @Column(name = "message_ar", columnDefinition = "NVARCHAR(MAX)")
    private String messageAr;

    @Column(name = "notification_type", length = 50)
    private String notificationType;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
