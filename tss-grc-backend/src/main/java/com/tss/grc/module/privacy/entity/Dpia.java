package com.tss.grc.module.privacy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Protection Impact Assessment — assessment §5.4 critical recommendation #3.
 * State machine: DRAFT → IN_REVIEW → APPROVED, with REJECTED branch and EXPIRED
 * terminal state once next_review_date passes.
 */
@Entity
@Table(schema = "privacy", name = "dpia")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Dpia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "processing_activity_id")
    private Long processingActivityId;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    /** NEW_HIGH_RISK | MAJOR_CHANGE | REGULATORY_REQUEST | PERIODIC | SPECIAL_CATEGORY | CROSS_BORDER | AUTO_DECISION | LARGE_SCALE_MONITORING */
    @Column(name = "trigger_reason", nullable = false, length = 50)
    private String triggerReason;

    /** DRAFT | IN_REVIEW | APPROVED | REJECTED | EXPIRED */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    /** CRITICAL | HIGH | MEDIUM | LOW */
    @Column(name = "overall_residual_risk", length = 20)
    private String overallResidualRisk;

    @Column(name = "dpo_id")
    private Long dpoId;

    @Column(name = "assessor_id")
    private Long assessorId;

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason;

    @Column(name = "conclusion", columnDefinition = "NVARCHAR(MAX)")
    private String conclusion;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
