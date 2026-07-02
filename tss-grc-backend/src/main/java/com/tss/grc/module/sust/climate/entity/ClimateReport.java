package com.tss.grc.module.sust.climate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Persisted IFRS S2 / TCFD annual climate report snapshot (V254 — S-G).
 *
 * <p>One row = one generated disclosure pack for a reporting year. The full
 * assembled content (scenarios, sector impacts, weighted expected loss,
 * financed emissions, the TCFD 4-pillar checklist, GHG inventory, evidence
 * index, and gaps/CAPA) is frozen into {@code contentJson}. Lifecycle
 * {@code DRAFT → IN_REVIEW → APPROVED → PUBLISHED}; on APPROVE the content is
 * SHA-256 hashed into {@code contentHash} and {@code isLocked} is set — the
 * approved numbers are immutable, so a re-generation opens a new version row.
 */
@Entity
@Table(schema = "sust", name = "climate_report")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ClimateReport {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "reporting_year", nullable = false) private Integer reportingYear;
    @Column(name = "report_code", length = 40)         private String reportCode;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;

    @Column(nullable = false, length = 20) private String status = "DRAFT";
    @Column(nullable = false)              private Integer version = 1;

    @Column(name = "content_json", columnDefinition = "NVARCHAR(MAX)") private String contentJson;
    @Column(name = "content_hash", length = 64)                        private String contentHash;

    @Column(name = "generated_at", nullable = false) private LocalDateTime generatedAt;
    @Column(name = "generated_by", length = 255)     private String generatedBy;
    @Column(name = "approved_by", length = 255)      private String approvedBy;
    @Column(name = "approved_at")                    private LocalDateTime approvedAt;
    @Column(name = "published_at")                   private LocalDateTime publishedAt;

    @Column(name = "is_locked", nullable = false) private Boolean isLocked = false;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null)   createdAt = now;
        if (generatedAt == null) generatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
