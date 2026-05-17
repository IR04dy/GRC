package com.tss.grc.module.ndi.maturity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * NDI Maturity Assessment — slice 3.
 *
 * <p>One row per (cycle, framework_item). Captures the assessor's
 * current_level (0..5) and target_level (0..5) plus the reviewer +
 * approver chain.
 *
 * <p>Status machine:
 * <pre>
 *   DRAFT → IN_PROGRESS → SUBMITTED → UNDER_REVIEW
 *                                   → RETURNED → IN_PROGRESS (loop)
 *                                   → APPROVED   (terminal until reopened)
 * </pre>
 *
 * <p>Service rules:
 * <ul>
 *   <li><b>Rule 1</b> — target_level > current_level marks
 *       improvementActionSuggested=true so the improvement-plan tab
 *       (slice 9) can surface candidates for the roadmap.</li>
 *   <li><b>Rule 2</b> — current_level ≥ 4 requires evidence_ref. The
 *       service refuses submit/approve without it.</li>
 *   <li><b>D.Validation</b> — target_level cannot be lower than
 *       current_level unless justification is captured.</li>
 * </ul>
 */
@Entity
@Table(schema = "ndi", name = "maturity_assessment")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiMaturityAssessment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "cycle_id", nullable = false)          private Long cycleId;
    @Column(name = "framework_item_id", nullable = false) private Long frameworkItemId;

    @Column(nullable = false, length = 30) private String component;
    @Column(nullable = false, length = 80) private String domain;
    @Column(length = 80)                   private String subdomain;

    @Column(name = "current_level") private Short currentLevel;
    @Column(name = "target_level")  private Short targetLevel;
    @Column(precision = 5, scale = 2) private BigDecimal score;
    @Column(nullable = false, precision = 5, scale = 2) private BigDecimal weight = BigDecimal.ONE;

    @Column(name = "response_text_en", columnDefinition = "NVARCHAR(MAX)") private String responseTextEn;
    @Column(name = "response_text_ar", columnDefinition = "NVARCHAR(MAX)") private String responseTextAr;
    @Column(name = "evidence_ref", length = 255)                           private String evidenceRef;
    @Column(name = "evidence_required", nullable = false)                  private Boolean evidenceRequired = false;

    @Column(name = "improvement_action_suggested", nullable = false) private Boolean improvementActionSuggested = false;
    @Column(columnDefinition = "NVARCHAR(MAX)")                       private String justification;

    @Column(name = "assessor_name",      length = 255) private String assessorName;
    @Column(name = "assessor_email",     length = 255) private String assessorEmail;
    @Column(name = "domain_owner_name",  length = 255) private String domainOwnerName;
    @Column(name = "domain_owner_email", length = 255) private String domainOwnerEmail;
    @Column(name = "reviewer_name",      length = 255) private String reviewerName;
    @Column(name = "reviewer_email",     length = 255) private String reviewerEmail;
    @Column(name = "approver_name",      length = 255) private String approverName;

    @Column(name = "gap_id")                  private Long gapId;
    @Column(name = "improvement_action_id")   private Long improvementActionId;

    @Column(nullable = false, length = 20) private String status = "DRAFT";
    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @Column(name = "reviewed_at")  private LocalDateTime reviewedAt;
    @Column(name = "approved_at")  private LocalDateTime approvedAt;
    @Column(name = "returned_at")  private LocalDateTime returnedAt;
    @Column(name = "return_reason", columnDefinition = "NVARCHAR(MAX)") private String returnReason;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
