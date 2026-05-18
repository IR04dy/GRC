package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Plan distribution + acknowledgement record — audit Tier-2 #11.
 *
 * <p>One row per (plan, version, recipient) tracking who received a copy of
 * the BCP/DRP and whether they acknowledged the version. Maps to ISO 22301
 * §7.4 (Communication) and SAMA BCM §6.4 (Plan distribution and awareness).
 *
 * <p>Status transitions:
 * <pre>
 *   PENDING       → ACKNOWLEDGED   (recipient confirms)
 *   PENDING       → DECLINED       (recipient pushes back)
 *   *             → STALE          (auto when plan version bumps)
 * </pre>
 *
 * <p>The recipient is identified by {@code recipientUserId} when known, with
 * {@code recipientName} + {@code recipientEmail} as the fallback / display
 * fields. This dual identity lets us distribute to external auditors and
 * non-user staff without forcing IAM enrolment.
 */
@Entity
@Table(schema = "bcm", name = "plan_acknowledgements")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcpPlanAcknowledgement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "plan_version", nullable = false)
    private Integer planVersion;

    // ── Recipient ──
    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "recipient_role", length = 120)
    private String recipientRole;

    // ── Distribution ──
    @Column(name = "distributed_at")
    private LocalDateTime distributedAt = LocalDateTime.now();

    @Column(name = "distributed_by_id")
    private Long distributedById;

    @Column(name = "distributed_by_name", length = 255)
    private String distributedByName;

    /** EMAIL | PORTAL | IN_PERSON | PRINT | OTHER */
    @Column(name = "distribution_channel", nullable = false, length = 40)
    private String distributionChannel = "EMAIL";

    @Column(name = "distribution_notes", length = 1000)
    private String distributionNotes;

    // ── Acknowledgement ──
    /** PENDING | ACKNOWLEDGED | DECLINED | STALE */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledgement_notes", length = 1000)
    private String acknowledgementNotes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
