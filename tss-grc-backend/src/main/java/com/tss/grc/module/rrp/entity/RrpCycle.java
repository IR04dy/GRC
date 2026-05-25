package com.tss.grc.module.rrp.entity;

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
 * One annual Recovery &amp; Resolution Plan cycle.
 *
 * <p>Lifecycle:
 * <pre>
 *   DRAFT → IN_PREPARATION → BOARD_REVIEW → APPROVED →
 *   SUBMITTED → ACKNOWLEDGED → CLOSED
 * </pre>
 *
 * <p>Each cycle carries its own catalogs of critical functions, recovery
 * indicators, recovery options, and rehearsed scenarios. The cycle's
 * {@code fiscalYear} is the year being planned for (FY2026 cycle is
 * prepared in 2025 and submitted Q1 2026).
 */
@Entity
@Table(schema = "rrp", name = "rrp_cycle")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RrpCycle extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** External reference, unique per tenant. e.g. {@code RRP-FY2026-001}. */
    @Column(name = "reference", nullable = false, length = 64)
    private String reference;

    /** Fiscal year being planned for. */
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    /**
     * State:
     * {@code DRAFT | IN_PREPARATION | BOARD_REVIEW | APPROVED |
     *        SUBMITTED | ACKNOWLEDGED | CLOSED}.
     */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "DRAFT";

    /** Statutory submission deadline (typically Q1 end). */
    @Column(name = "submission_due_date")
    private LocalDate submissionDueDate;

    /** Date the cycle was submitted to SAMA. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** SAMA acknowledgment reference (RBS receipt). */
    @Column(name = "regulator_ack_reference", length = 256)
    private String regulatorAckReference;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Day-to-day owner (typically CRO). */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    /** Board sponsor (typically Board Risk Committee Chair). */
    @Column(name = "sponsor_user_id")
    private Long sponsorUserId;

    /** Board approval date (when status = APPROVED). */
    @Column(name = "board_approval_date")
    private LocalDate boardApprovalDate;

    /** Board resolution reference. */
    @Column(name = "board_resolution_reference", length = 128)
    private String boardResolutionReference;

    /** Executive summary written for the Board pack. */
    @Column(name = "executive_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryEn;

    @Column(name = "executive_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryAr;

    /** Where the lodged plan is stored (S3 / SharePoint). */
    @Column(name = "filing_artifact_reference", length = 512)
    private String filingArtifactReference;

    // Denormalised counters for dashboard speed.

    @Column(name = "critical_function_count")
    private Integer criticalFunctionCount = 0;

    @Column(name = "recovery_option_count")
    private Integer recoveryOptionCount = 0;

    @Column(name = "indicator_count")
    private Integer indicatorCount = 0;

    /** Indicators currently in AMBER band. */
    @Column(name = "indicators_amber")
    private Integer indicatorsAmber = 0;

    /** Indicators currently in RED band. */
    @Column(name = "indicators_red")
    private Integer indicatorsRed = 0;

    /**
     * Aggregate recovery capacity across all options, in SAR millions.
     * Computed when options change.
     */
    @Column(name = "total_recovery_capacity_sar_m")
    private Long totalRecoveryCapacitySarM = 0L;
}
