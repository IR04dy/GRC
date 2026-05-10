package com.tss.grc.module.compliance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Policy lifecycle entity — assessment §5.3 critical recommendation #2.
 * State machine:
 *   DRAFT → LEGAL_REVIEW → EXEC_APPROVAL → PUBLISHED → REVIEW_DUE → RETIRED
 *   LEGAL_REVIEW or EXEC_APPROVAL → REJECTED → DRAFT
 */
@Entity
@Table(schema = "compliance", name = "policies")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Policy extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "version", nullable = false, length = 20)
    private String version = "1.0";

    /** INFORMATION_SECURITY | PRIVACY | RISK_MANAGEMENT | BUSINESS_CONTINUITY | ACCEPTABLE_USE | INCIDENT_RESPONSE | HR | FINANCE */
    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "content_en", columnDefinition = "NVARCHAR(MAX)")
    private String contentEn;

    @Column(name = "content_ar", columnDefinition = "NVARCHAR(MAX)")
    private String contentAr;

    /** DRAFT | LEGAL_REVIEW | EXEC_APPROVAL | PUBLISHED | REVIEW_DUE | RETIRED | REJECTED */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "legal_reviewer_id")
    private Long legalReviewerId;

    @Column(name = "legal_reviewed_at")
    private LocalDateTime legalReviewedAt;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason;
}
