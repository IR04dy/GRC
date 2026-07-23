package com.tss.grc.module.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Policy Document — V79 table {@code policy.documents}.
 *
 * <p>Versioned policy with lifecycle: DRAFT → REVIEW → APPROVED →
 * PUBLISHED → EXPIRED → ARCHIVED. Each row is one (policy_code, version)
 * pair; supersedes by promoting a new version through the lifecycle.
 */
@Entity
@Table(schema = "policy", name = "documents")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class PolicyDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "policy_code", nullable = false, length = 40) private String policyCode;
    @Column(nullable = false, length = 20)                       private String version;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;
    @Column(name = "summary_en", columnDefinition = "NVARCHAR(MAX)") private String summaryEn;
    @Column(name = "summary_ar", columnDefinition = "NVARCHAR(MAX)") private String summaryAr;
    @Column(name = "body_url", length = 500)                   private String bodyUrl;
    // CH-10: inline structured policy body (markdown). Authored in-app; rendered
    // to a controlled PDF/Word and satisfies the require-a-document publish gate.
    @Column(name = "content_md", columnDefinition = "NVARCHAR(MAX)") private String contentMd;

    @Column(nullable = false, length = 60) private String category;
    @Column(name = "applicability_scope", columnDefinition = "NVARCHAR(MAX)") private String applicabilityScope;

    @Column(name = "owner_name",     length = 255) private String ownerName;
    @Column(name = "owner_email",    length = 255) private String ownerEmail;
    @Column(name = "approver_name",  length = 255) private String approverName;
    @Column(name = "approver_email", length = 255) private String approverEmail;

    @Column(name = "effective_date")    private LocalDate effectiveDate;
    @Column(name = "expiry_date")       private LocalDate expiryDate;
    @Column(name = "next_review_date")  private LocalDate nextReviewDate;
    @Column(name = "review_cadence", length = 20) private String reviewCadence;

    @Column(nullable = false, length = 20)                            private String status;
    @Column(name = "published_at")                                    private LocalDateTime publishedAt;
    @Column(name = "retired_reason", columnDefinition = "NVARCHAR(MAX)") private String retiredReason;

    // C-12: free-text cross-links are deprecated in favour of FK references —
    // the policy.document_framework_mappings (V302) and policy.document_control_links
    // tables. They are retained during the expand-contract window so legacy
    // references whose codes are not in the control library are preserved.
    @Column(name = "related_framework", length = 120)                   private String relatedFramework;
    @Column(name = "related_controls",  columnDefinition = "NVARCHAR(MAX)") private String relatedControls;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (version == null)   version = "1.0";
        if (status == null)    status = "DRAFT";
        if (category == null)  category = "GENERAL";
        if (isDeleted == null) isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
