package com.tss.grc.module.compliance.obligation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 11 — Quarterly / annual cycle launcher for obligation
 * attestations.
 *
 * <p>The Compliance Office opens a campaign for {@code Q2-2026 SAMA
 * obligations}, the service materialises one
 * {@link ObligationAttestation} per matching active obligation, and
 * the per-owner reminders / escalations are tracked off the campaign
 * roll-up.
 *
 * <h3>State machine</h3>
 * <pre>
 *   DRAFT       → OPEN | CANCELLED
 *   OPEN        → IN_PROGRESS | ESCALATED | CLOSED | CANCELLED
 *   IN_PROGRESS → ESCALATED | CLOSED | CANCELLED
 *   ESCALATED   → CLOSED | CANCELLED
 *   CLOSED      (terminal)
 *   CANCELLED   (terminal)
 * </pre>
 */
@Entity
@Table(schema = "compliance", name = "obligation_attestation_campaign")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ObligationAttestationCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "campaign_code", nullable = false, length = 64)
    private String campaignCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 500)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "cycle_year", nullable = false)
    private Integer cycleYear;

    /** Q1..Q4 or NULL for an annual cycle. */
    @Column(name = "cycle_quarter", length = 10)
    private String cycleQuarter;

    /** Null = include all regulators in scope. */
    @Column(name = "regulator_filter", length = 40)
    private String regulatorFilter;

    /** Null = include all frequencies. */
    @Column(name = "frequency_filter", length = 40)
    private String frequencyFilter;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closes_at", nullable = false)
    private LocalDateTime closesAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "reminder_cadence_days", nullable = false)
    private Integer reminderCadenceDays = 7;

    /** DRAFT | OPEN | IN_PROGRESS | ESCALATED | CLOSED | CANCELLED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "total_invited", nullable = false)
    private Integer totalInvited = 0;

    @Column(name = "total_in_progress", nullable = false)
    private Integer totalInProgress = 0;

    @Column(name = "total_attested", nullable = false)
    private Integer totalAttested = 0;

    @Column(name = "total_attested_with_excs", nullable = false)
    private Integer totalAttestedWithExcs = 0;

    @Column(name = "total_overdue", nullable = false)
    private Integer totalOverdue = 0;

    @Column(name = "total_na", nullable = false)
    private Integer totalNa = 0;

    @Column(name = "closing_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String closingSummaryEn;

    @Column(name = "closing_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String closingSummaryAr;

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
