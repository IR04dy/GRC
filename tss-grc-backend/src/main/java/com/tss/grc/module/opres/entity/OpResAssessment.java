package com.tss.grc.module.opres.entity;

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
 * One Operational-Resilience self-assessment instance.
 *
 * <p>Lifecycle:
 * <pre>
 *   DRAFT → IN_PROGRESS → APPROVED → CLOSED
 * </pre>
 *
 * <p>Each instance carries 5 {@link OpResAssessmentCell} rows (one per
 * pillar) plus the aggregate maturity scores rolled up by the service
 * layer.
 *
 * <p>SAMA Op-Res 2024 expects each licensed entity to perform this
 * annually and submit results to the board's Risk Committee within
 * 90 days of fiscal year-end.
 */
@Entity
@Table(schema = "opres", name = "opres_assessment")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OpResAssessment extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    /** {@code DRAFT | IN_PROGRESS | APPROVED | CLOSED}. */
    @Column(name = "status", nullable = false, length = 24)
    private String status = "DRAFT";

    /** Target submission date — usually FY-end + 90 days per SAMA. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Optional executive sponsor — usually the CRO. */
    @Column(name = "sponsor_user_id")
    private Long sponsorUserId;

    /** Aggregate current-maturity score, mean across pillars (1.0-5.0). */
    @Column(name = "avg_current_score", precision = 4, scale = 2)
    private java.math.BigDecimal avgCurrentScore;

    /** Aggregate target-maturity score, mean across pillars (1.0-5.0). */
    @Column(name = "avg_target_score", precision = 4, scale = 2)
    private java.math.BigDecimal avgTargetScore;

    /** {@code "5/5"} — pillars assessed / total pillars. */
    @Column(name = "completeness", length = 16)
    private String completeness;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "executive_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryEn;

    @Column(name = "executive_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryAr;
}
