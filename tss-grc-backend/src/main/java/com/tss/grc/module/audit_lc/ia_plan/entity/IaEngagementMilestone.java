package com.tss.grc.module.audit_lc.ia_plan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sprint 13 — Engagement milestone (kickoff, draft report, …).
 *
 * <p>Recorded via {@code IaEngagementService.recordMilestone(...)} which
 * sets the {@code actualDate}, {@code completedByUserId} and emits the
 * {@code IA_MILESTONE_REACHED} F8 event.
 */
@Entity
@Table(schema = "audit_lc", name = "ia_engagement_milestone")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IaEngagementMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "engagement_id", nullable = false)
    private Long engagementId;

    /** KICKOFF | FIELDWORK_START | DRAFT_REPORT | MANAGEMENT_RESPONSE |
     *  FINAL_REPORT | FOLLOW_UP_DUE */
    @Column(name = "milestone_type", nullable = false, length = 40)
    private String milestoneType;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    @Column(name = "completed_by_user_id")
    private Long completedByUserId;

    @Column(name = "notes_en", length = 500)
    private String notesEn;

    @Column(name = "notes_ar", length = 500)
    private String notesAr;

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
