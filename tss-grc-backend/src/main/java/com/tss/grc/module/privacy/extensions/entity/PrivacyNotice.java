package com.tss.grc.module.privacy.extensions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * PDPL Art. 12 versioned bilingual privacy notice.
 *
 * <p>State machine: DRAFT → PUBLISHED → ARCHIVED. Unique
 * (organisation_id, notice_code, version) so a new version supersedes
 * the previous without overwriting it.
 */
@Entity
@Table(schema = "privacy", name = "privacy_notices")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class PrivacyNotice {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "notice_code", nullable = false, length = 64) private String noticeCode;
    @Column(nullable = false)                                    private Integer version = 1;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", nullable = false, length = 255) private String titleAr;

    @Column(name = "purpose_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String purposeEn;
    @Column(name = "purpose_ar", columnDefinition = "NVARCHAR(MAX)") private String purposeAr;

    @Column(name = "data_categories", columnDefinition = "NVARCHAR(MAX)") private String dataCategories;
    @Column(name = "recipients", columnDefinition = "NVARCHAR(MAX)") private String recipients;
    @Column(name = "retention_period_en", columnDefinition = "NVARCHAR(MAX)") private String retentionPeriodEn;
    @Column(name = "retention_period_ar", columnDefinition = "NVARCHAR(MAX)") private String retentionPeriodAr;
    @Column(name = "dpo_contact", length = 255) private String dpoContact;
    @Column(name = "lawful_basis", length = 50) private String lawfulBasis;

    @Column(name = "body_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String bodyEn;
    @Column(name = "body_ar", columnDefinition = "NVARCHAR(MAX)") private String bodyAr;

    @Column(name = "linked_ropa_codes", columnDefinition = "NVARCHAR(MAX)") private String linkedRopaCodes;

    /** DRAFT | PUBLISHED | ARCHIVED. */
    @Column(nullable = false, length = 20) private String status = "DRAFT";

    @Column(name = "effective_from") private LocalDateTime effectiveFrom;
    @Column(name = "effective_to")   private LocalDateTime effectiveTo;
    @Column(name = "published_at")   private LocalDateTime publishedAt;
    @Column(name = "published_by")   private Long publishedBy;
    @Column(name = "archived_at")    private LocalDateTime archivedAt;
    @Column(name = "archived_by")    private Long archivedBy;
    @Column(name = "archive_reason_en", length = 500) private String archiveReasonEn;

    @Column(name = "supersedes_id") private Long supersedesId;

    @Column(name = "external_id", length = 64) private String externalId;
    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active", nullable = false) private Short isActive = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
