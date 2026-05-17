package com.tss.grc.module.sust.annex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sustainability reporting year — the parent record under which all
 * GHG / Climate / CMA / SGI / TCFD data for a given fiscal period
 * collects. Lifecycle:
 *
 * <pre>
 *   ACTIVE → DATA_COLLECTION → REVIEW → APPROVED → PUBLISHED → ARCHIVED
 * </pre>
 *
 * <p>Each {@link com.tss.grc.module.sust.annex.entity.SustainabilityReport}
 * generated for the year belongs to one ReportingYear; APPROVED + PUBLISHED
 * states lock the year so no further changes leak into already-filed
 * reports. See V57 migration for full column commentary.
 */
@Entity
@Table(schema = "sust", name = "reporting_year")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ReportingYear {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(nullable = false)         private Short year;
    @Column(name = "period_start")    private LocalDate periodStart;
    @Column(name = "period_end")      private LocalDate periodEnd;

    @Column(name = "reporting_entity_en", nullable = false, length = 255) private String reportingEntityEn;
    @Column(name = "reporting_entity_ar", length = 255)                   private String reportingEntityAr;

    @Column(nullable = false, length = 20) private String status = "ACTIVE";

    @Column(name = "prepared_by",  length = 255) private String preparedBy;
    @Column(name = "reviewed_by",  length = 255) private String reviewedBy;
    @Column(name = "approved_by",  length = 255) private String approvedBy;
    @Column(name = "approved_at")               private LocalDateTime approvedAt;
    @Column(name = "published_at")              private LocalDateTime publishedAt;

    @Column(name = "readiness_score_pct", precision = 5, scale = 2)
    private BigDecimal readinessScorePct;
    @Column(name = "open_gaps_count") private Integer openGapsCount;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100) private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (year != null) {
            if (periodStart == null) periodStart = LocalDate.of(year, 1, 1);
            if (periodEnd == null)   periodEnd   = LocalDate.of(year, 12, 31);
        }
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
