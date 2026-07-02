package com.tss.grc.module.sust.annex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * A generated Sustainability report instance — typically an
 * {@code ANNUAL_ANNEX} for the parent {@link ReportingYear}, but the
 * same row shape also serves Board ESG packs, CMA disclosure matrices,
 * TCFD reports, GHG inventory exports, etc.
 *
 * <p>Lifecycle: {@code DRAFT → SUBMITTED → APPROVED → PUBLISHED |
 * SUPERSEDED}. Content snapshots (Markdown / HTML / JSON) freeze at
 * APPROVED so PUBLISHED versions are immutable — re-generation goes to
 * a new row with bumped version and the previous row marked SUPERSEDED.
 */
@Entity
@Table(schema = "sust", name = "sustainability_report")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class SustainabilityReport {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)   private Long organisationId;
    @Column(name = "reporting_year_id", nullable = false) private Long reportingYearId;
    @Column(name = "external_id", length = 64)            private String externalId;

    @Column(name = "report_code", length = 40)            private String reportCode;

    @Column(name = "report_type", nullable = false, length = 30) private String reportType;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;

    @Column(nullable = false, length = 20) private String version = "v1.0";
    @Column(nullable = false, length = 20) private String status  = "DRAFT";

    @Column(name = "generated_at", nullable = false, updatable = false) private LocalDateTime generatedAt;
    @Column(name = "generated_by", length = 255) private String generatedBy;
    @Column(name = "reviewed_by",  length = 255) private String reviewedBy;
    @Column(name = "approved_by",  length = 255) private String approvedBy;
    @Column(name = "approved_at")                private LocalDateTime approvedAt;
    @Column(name = "published_at")               private LocalDateTime publishedAt;

    @Column(name = "content_markdown", columnDefinition = "NVARCHAR(MAX)") private String contentMarkdown;
    @Column(name = "content_html",     columnDefinition = "NVARCHAR(MAX)") private String contentHtml;
    @Column(name = "content_json",     columnDefinition = "NVARCHAR(MAX)") private String contentJson;

    /** S-I — SHA-256 of the frozen content, stamped on APPROVE. */
    @Column(name = "content_hash", length = 64) private String contentHash;
    /** S-I — set on APPROVE; the approved content is immutable (re-gen makes a new version). */
    @Column(name = "is_locked", nullable = false) private Boolean isLocked = false;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100) private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null)   createdAt   = now;
        if (generatedAt == null) generatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
