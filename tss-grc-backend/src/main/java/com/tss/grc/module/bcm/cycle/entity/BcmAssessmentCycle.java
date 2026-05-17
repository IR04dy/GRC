package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BCM Assessment Cycle — one row per (org, reporting year).
 *
 * <p>Bears the aggregate readiness counters (BIA / BCP / DRP / evidence
 * completion %, gap counts, overall_score). Sprint BCM-1 of the
 * improvement plan; field-level rationale in
 * {@code docs/bcm/BCM_IMPROVEMENT_PLAN.md} §5.
 */
@Entity
@Table(schema = "bcm", name = "assessment_cycles")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmAssessmentCycle {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "cycle_name_en", nullable = false, length = 255)
    private String cycleNameEn;

    @Column(name = "cycle_name_ar", length = 255)
    private String cycleNameAr;

    @Column(name = "reporting_year", nullable = false)
    private Short reportingYear;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String scope;

    @Column(name = "bcm_manager_id")
    private Long bcmManagerId;

    @Column(name = "executive_sponsor_id")
    private Long executiveSponsorId;

    /** DRAFT | IN_PROGRESS | UNDER_REVIEW | APPROVED | ARCHIVED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "bia_completion_percent", precision = 5, scale = 2)
    private BigDecimal biaCompletionPercent;

    @Column(name = "bcp_completion_percent", precision = 5, scale = 2)
    private BigDecimal bcpCompletionPercent;

    @Column(name = "drp_completion_percent", precision = 5, scale = 2)
    private BigDecimal drpCompletionPercent;

    @Column(name = "evidence_completion_percent", precision = 5, scale = 2)
    private BigDecimal evidenceCompletionPercent;

    @Column(name = "open_gap_count", nullable = false)
    private Integer openGapCount = 0;

    @Column(name = "critical_gap_count", nullable = false)
    private Integer criticalGapCount = 0;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
