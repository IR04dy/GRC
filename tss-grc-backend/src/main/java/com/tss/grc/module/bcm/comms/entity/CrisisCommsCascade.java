package com.tss.grc.module.bcm.comms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 9-E — multi-channel cascade send-log.
 *
 * <p>One row per dispatched channel — e.g. firing an EMAIL + SMS + TEAMS
 * cascade against a single template creates three rows linked by the
 * same {@code crisis_event_id} + {@code template_id} pair. Each row
 * carries success/failure counters and a snapshot of the rendered
 * subject + body so the auditor can reconstruct exactly what was sent.
 */
@Entity
@Table(schema = "bcm", name = "crisis_comms_cascade")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrisisCommsCascade {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "crisis_event_id", nullable = false)
    private Long crisisEventId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    /** EMAIL | SMS | WHATSAPP | TEAMS | VOICE_BLAST */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "audience", length = 40)
    private String audience;

    @Column(name = "rendered_subject", length = 500)
    private String renderedSubject;

    @Column(name = "rendered_body", columnDefinition = "NVARCHAR(MAX)")
    private String renderedBody;

    @Column(name = "recipient_count", nullable = false)
    private Integer recipientCount = 0;

    @Column(name = "success_count", nullable = false)
    private Integer successCount = 0;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 0;

    @Column(name = "dispatched_by_user_id")
    private Long dispatchedByUserId;

    @Column(name = "dispatched_by_name", length = 255)
    private String dispatchedByName;

    @Column(name = "dispatched_at", nullable = false)
    private LocalDateTime dispatchedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** QUEUED | DISPATCHING | COMPLETED | FAILED | PARTIAL */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "QUEUED";

    @Column(name = "response_payload", columnDefinition = "NVARCHAR(MAX)")
    private String responsePayload;

    /**
     * True when no gateway was involved — the counters above are what
     * <em>would</em> have been sent (F2-147, V363).
     *
     * <p>This column is why the flag is not merely a response field. A
     * cascade row is the BCM evidence that people were notified, and it is
     * read months later by an auditor who never saw the dispatch screen. A
     * row that says {@code COMPLETED, 12/12} and cannot say "simulated" is
     * an assertion that twelve people were told.
     */
    @Column(name = "simulated", nullable = false)
    private Boolean simulated = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
