package com.tss.grc.module.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Time-bound deviation from a published policy.
 *
 * <p>Status machine: REQUESTED → UNDER_REVIEW → APPROVED | REJECTED.
 * EXPIRED and REVOKED are terminal post-APPROVED.
 */
@Entity(name = "PolicyException")
@Table(schema = "policy", name = "exceptions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class PolicyException {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "policy_id", nullable = false)         private Long policyId;
    @Column(name = "exception_code", length = 40)         private String exceptionCode;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;

    @Column(name = "justification_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String justificationEn;
    @Column(name = "justification_ar", columnDefinition = "NVARCHAR(MAX)") private String justificationAr;
    @Column(name = "compensating_controls_en", columnDefinition = "NVARCHAR(MAX)") private String compensatingControlsEn;
    @Column(name = "compensating_controls_ar", columnDefinition = "NVARCHAR(MAX)") private String compensatingControlsAr;

    @Column(name = "requestor_name",  length = 255) private String requestorName;
    @Column(name = "requestor_email", length = 255) private String requestorEmail;
    @Column(name = "reviewer_name",   length = 255) private String reviewerName;
    @Column(name = "approver_name",   length = 255) private String approverName;

    @Column(name = "risk_level", nullable = false, length = 20) private String riskLevel;
    @Column(name = "risk_id")                                   private Long riskId;

    @Column(name = "requested_at",  nullable = false) private LocalDateTime requestedAt;
    @Column(name = "reviewed_at")                     private LocalDateTime reviewedAt;
    @Column(name = "approved_at")                     private LocalDateTime approvedAt;
    @Column(name = "rejected_at")                     private LocalDateTime rejectedAt;
    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)") private String rejectionReason;

    @Column(name = "effective_date") private LocalDate effectiveDate;
    @Column(name = "expiry_date")    private LocalDate expiryDate;

    @Column(nullable = false, length = 20) private String status;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null)   createdAt = LocalDateTime.now();
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (status == null)      status = "REQUESTED";
        if (riskLevel == null)   riskLevel = "MEDIUM";
        if (isDeleted == null)   isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
