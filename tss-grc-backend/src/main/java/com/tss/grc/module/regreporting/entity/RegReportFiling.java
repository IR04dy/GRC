package com.tss.grc.module.regreporting.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One cycle (instance) of a {@link RegReportObligation}.
 *
 * <p>Each scheduled run becomes a filing record carrying its own state
 * machine and timeline. Lifecycle:
 * <pre>
 *   SCHEDULED → IN_PREPARATION → IN_REVIEW → APPROVED → SUBMITTED →
 *   ACKNOWLEDGED → CLOSED
 *
 *   ↪ REJECTED  — regulator rejected, kicks back to IN_PREPARATION
 *   ↪ EXEMPTED  — formal waiver granted (terminal)
 * </pre>
 *
 * <p>The {@code dueDate} is computed from period-end + offset business days
 * on the obligation. {@code overdue} (LATE) is derived at read time:
 * status is not terminal and today &gt; dueDate.
 */
@Entity
@Table(schema = "regreporting", name = "regreport_filing")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RegReportFiling extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "obligation_id", nullable = false)
    private Long obligationId;

    /** External reference (e.g. "SAMA-DLR-2026-05-25"). */
    @Column(name = "reference", nullable = false, length = 96)
    private String reference;

    /**
     * State:
     * {@code SCHEDULED | IN_PREPARATION | IN_REVIEW | APPROVED | SUBMITTED |
     *        ACKNOWLEDGED | CLOSED | REJECTED | EXEMPTED}.
     */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "SCHEDULED";

    /** Reporting period start (inclusive). */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** Reporting period end (inclusive). */
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    /** Statutory deadline. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Internal target — typically due - 1 to 3 business days. */
    @Column(name = "internal_target_date")
    private LocalDate internalTargetDate;

    /** Person assembling the filing. */
    @Column(name = "preparer_user_id")
    private Long preparerUserId;

    /** Second-line reviewer. */
    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    /** Accountable approver. */
    @Column(name = "approver_user_id")
    private Long approverUserId;

    /** When the filing was lodged with the regulator. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Regulator's acknowledgment ref (portal receipt, ticket #, etc.). */
    @Column(name = "regulator_ack_reference", length = 256)
    private String regulatorAckReference;

    /** When regulator acknowledgment was recorded. */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** Closure date (post-acknowledgment archive). */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Where the lodged file is stored (S3 key, SharePoint URL, etc.). */
    @Column(name = "filing_artifact_reference", length = 512)
    private String filingArtifactReference;

    /** Notes carried from cycle to cycle. */
    @Column(name = "preparation_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String preparationNotesEn;

    @Column(name = "preparation_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String preparationNotesAr;

    /** Regulator-rejection reason (when status = REJECTED). */
    @Column(name = "rejection_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonEn;

    @Column(name = "rejection_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReasonAr;

    /** Exemption justification + reference (when status = EXEMPTED). */
    @Column(name = "exemption_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String exemptionReasonEn;

    @Column(name = "exemption_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String exemptionReasonAr;

    @Column(name = "exemption_reference", length = 256)
    private String exemptionReference;
}
