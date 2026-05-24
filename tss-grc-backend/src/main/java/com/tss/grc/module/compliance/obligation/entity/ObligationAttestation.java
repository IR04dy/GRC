package com.tss.grc.module.compliance.obligation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 11 — Per-cycle attestation record for a single
 * {@link Obligation}.
 *
 * <p>Materialised either by {@link ObligationAttestationCampaign} when
 * the Compliance Office opens a quarterly / annual roll, or manually
 * by the obligation owner when the obligation triggers ON_EVENT
 * (e.g. SAMA 24-hour cyber-incident notification).
 *
 * <h3>Per-cycle state machine</h3>
 * <pre>
 *   DUE                       → IN_PROGRESS | ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE | OVERDUE
 *   OVERDUE                   → IN_PROGRESS | ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE
 *   IN_PROGRESS               → ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE | OVERDUE
 *   ATTESTED                  (reviewer can ACCEPT / REJECT / REQUEST_CHANGES)
 *   ATTESTED_WITH_EXCEPTIONS  (reviewer can ACCEPT / REJECT / REQUEST_CHANGES)
 *   NOT_APPLICABLE            (terminal)
 * </pre>
 *
 * <p>The reviewer outcome is captured separately in
 * {@link #reviewerDecision} so the lifecycle can record both
 * "officer attested" AND "audit committee accepted" — two separate
 * events the regulator expects to be on file.
 */
@Entity
@Table(schema = "compliance", name = "obligation_attestation")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ObligationAttestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "obligation_id", nullable = false)
    private Long obligationId;

    /** Null when the attestation is ad-hoc (owner submits outside a campaign). */
    @Column(name = "campaign_id")
    private Long campaignId;

    /** e.g. {@code Q2-2026}, {@code 2026-04}, {@code 2026}. */
    @Column(name = "period_label", length = 64)
    private String periodLabel;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /**
     * DUE | OVERDUE | IN_PROGRESS | ATTESTED | ATTESTED_WITH_EXCEPTIONS | NOT_APPLICABLE.
     */
    @Column(name = "attestation_status", nullable = false, length = 40)
    private String attestationStatus = "DUE";

    @Column(name = "attested_by_user_id")
    private Long attestedByUserId;

    @Column(name = "attested_at")
    private LocalDateTime attestedAt;

    @Column(name = "attestation_text_en", length = 2000)
    private String attestationTextEn;

    @Column(name = "attestation_text_ar", length = 2000)
    private String attestationTextAr;

    /** JSON list of evidence-pack ids or URLs. */
    @Column(name = "evidence_refs", columnDefinition = "NVARCHAR(MAX)")
    private String evidenceRefs;

    @Column(name = "exceptions_noted_en", length = 2000)
    private String exceptionsNotedEn;

    @Column(name = "exceptions_noted_ar", length = 2000)
    private String exceptionsNotedAr;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** ACCEPTED | REJECTED | REQUEST_CHANGES. */
    @Column(name = "reviewer_decision", length = 20)
    private String reviewerDecision;

    @Column(name = "reviewer_comment_en", length = 1000)
    private String reviewerCommentEn;

    @Column(name = "reviewer_comment_ar", length = 1000)
    private String reviewerCommentAr;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
