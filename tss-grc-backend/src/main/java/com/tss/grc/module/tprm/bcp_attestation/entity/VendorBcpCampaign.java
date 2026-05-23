package com.tss.grc.module.tprm.bcp_attestation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 10-D — Vendor BCP attestation campaign.
 *
 * <p>One row per campaign-cycle the TPRM Lead launches against the
 * third-party register. Vendors materialised into per-row
 * {@link VendorBcpAttestation} entries at "open campaign" time, filtered
 * by {@link #mandatoryThreshold}.
 *
 * <p>SAMA BCF §3.4 and NCA ECC-2 Annex H both require evidence of
 * vendor business-continuity readiness on file at least annually — this
 * entity is the campaign that produces that evidence.
 *
 * <h3>State machine</h3>
 * <pre>
 *   DRAFT       → OPEN | CANCELLED
 *   OPEN        → REMINDED_R1 | ESCALATED | CLOSED | CANCELLED
 *   REMINDED_R1 → REMINDED_R2 | ESCALATED | CLOSED | CANCELLED
 *   REMINDED_R2 → ESCALATED | CLOSED | CANCELLED
 *   ESCALATED   → CLOSED | CANCELLED
 *   CLOSED      (terminal)
 *   CANCELLED   (terminal)
 * </pre>
 */
@Entity
@Table(schema = "tprm", name = "vendor_bcp_campaign")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class VendorBcpCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "campaign_code", nullable = false, length = 64)
    private String campaignCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 255)
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

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closes_at")
    private LocalDateTime closesAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "reminder_cadence_days", nullable = false)
    private Integer reminderCadenceDays = 14;

    @Column(name = "escalation_threshold_days", nullable = false)
    private Integer escalationThresholdDays = 30;

    /** DRAFT | OPEN | REMINDED_R1 | REMINDED_R2 | ESCALATED | CLOSED | CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    /** ALL | CRITICAL_ONLY | HIGH_AND_ABOVE */
    @Column(name = "mandatory_threshold", nullable = false, length = 20)
    private String mandatoryThreshold = "CRITICAL_ONLY";

    @Column(name = "regulator_reference", length = 500)
    private String regulatorReference;

    @Column(name = "total_invited", nullable = false)
    private Integer totalInvited = 0;

    @Column(name = "total_submitted", nullable = false)
    private Integer totalSubmitted = 0;

    @Column(name = "total_accepted", nullable = false)
    private Integer totalAccepted = 0;

    @Column(name = "total_rejected", nullable = false)
    private Integer totalRejected = 0;

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
