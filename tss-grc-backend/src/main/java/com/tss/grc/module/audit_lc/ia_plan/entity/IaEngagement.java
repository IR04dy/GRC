package com.tss.grc.module.audit_lc.ia_plan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — Single internal-audit engagement.
 *
 * <p>Phase machine (enforced by {@code IaEngagementService.transitionPhase}):
 * {@code PLANNED → PLANNING → FIELDWORK → REPORTING → REPORT_ISSUED →
 * FOLLOW_UP → CLOSED}. {@code CANCELLED} is a terminal off-ramp from any
 * non-CLOSED state.
 */
@Entity
@Table(schema = "audit_lc", name = "ia_engagement")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IaEngagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "universe_entry_id")
    private Long universeEntryId;

    @Column(name = "engagement_code", nullable = false, length = 64)
    private String engagementCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 500)
    private String titleAr;

    /** OPERATIONAL | FINANCIAL | COMPLIANCE | IT_GENERAL | IT_APPLICATION
     *  | FRAUD | SPECIAL_REQUEST | FOLLOW_UP | MANAGEMENT_REVIEW */
    @Column(name = "audit_type", nullable = false, length = 40)
    private String auditType;

    @Column(name = "scope_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String scopeSummaryEn;

    @Column(name = "scope_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String scopeSummaryAr;

    @Column(name = "lead_auditor_user_id")
    private Long leadAuditorUserId;

    @Column(name = "team_member_ids", length = 500)
    private String teamMemberIds;

    @Column(name = "sponsor_user_id")
    private Long sponsorUserId;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    /** PLANNED | PLANNING | FIELDWORK | REPORTING | REPORT_ISSUED |
     *  FOLLOW_UP | CLOSED | CANCELLED */
    @Column(name = "phase", nullable = false, length = 20)
    private String phase = "PLANNED";

    @Column(name = "planning_memo_url", length = 500)
    private String planningMemoUrl;

    @Column(name = "planning_memo_signed_off_at")
    private LocalDateTime planningMemoSignedOffAt;

    /** SATISFACTORY | PARTIAL | UNSATISFACTORY | NA */
    @Column(name = "opinion", length = 20)
    private String opinion;

    @Column(name = "finding_count", nullable = false)
    private Integer findingCount = 0;

    @Column(name = "critical_finding_count", nullable = false)
    private Integer criticalFindingCount = 0;

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
