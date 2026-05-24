package com.tss.grc.module.audit_lc.ia_plan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — Internal Audit annual plan (one row per cycle_year per org).
 *
 * <p>Lifecycle:
 * {@code DRAFT → BOARD_REVIEW → APPROVED → EXECUTING → COMPLETED → ARCHIVED}.
 * The state machine is enforced by {@code IaAnnualPlanService.transition}.
 */
@Entity
@Table(schema = "audit_lc", name = "ia_annual_plan")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IaAnnualPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "plan_code", nullable = false, length = 64)
    private String planCode;

    @Column(name = "cycle_year", nullable = false)
    private Integer cycleYear;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 500)
    private String titleAr;

    @Column(name = "prepared_by_user_id")
    private Long preparedByUserId;

    /** DRAFT | BOARD_REVIEW | APPROVED | EXECUTING | COMPLETED | ARCHIVED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "methodology_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String methodologySummaryEn;

    @Column(name = "methodology_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String methodologySummaryAr;

    @Column(name = "audit_universe_count", nullable = false)
    private Integer auditUniverseCount = 0;

    @Column(name = "total_engagements_planned", nullable = false)
    private Integer totalEngagementsPlanned = 0;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

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
