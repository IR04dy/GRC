package com.tss.grc.module.ndi.gap.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NDI Gap — slice 8. Identified deficiency feeding the corrective-action
 * pipeline. Sources: COMPLIANCE / MATURITY / KPI / AUDIT / MANUAL.
 *
 * <p>Rollup fields (capa_count, capa_completed_count, rollup_progress) are
 * service-set whenever a CAPA is created / updated / deleted under this gap.
 */
@Entity
@Table(schema = "ndi", name = "gap")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiGap {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "cycle_id", nullable = false) private Long cycleId;
    @Column(name = "gap_code", length = 40)      private String gapCode;

    @Column(name = "source_type", nullable = false, length = 20) private String sourceType;
    @Column(name = "source_id")                                  private Long sourceId;
    @Column(length = 80)                                         private String domain;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;
    @Column(name = "root_cause",     columnDefinition = "NVARCHAR(MAX)") private String rootCause;
    @Column(name = "impact_summary", columnDefinition = "NVARCHAR(MAX)") private String impactSummary;

    @Column(nullable = false, length = 20) private String severity;
    @Column(length = 20)                   private String likelihood;
    @Column(name = "risk_score")           private Integer riskScore;

    @Column(name = "owner_name",  length = 255) private String ownerName;
    @Column(name = "owner_email", length = 255) private String ownerEmail;

    @Column(name = "open_date",         nullable = false) private LocalDate openDate;
    @Column(name = "target_date")                         private LocalDate targetDate;
    @Column(name = "actual_close_date")                   private LocalDate actualCloseDate;

    @Column(name = "rollup_progress",      nullable = false) private Integer rollupProgress;
    @Column(name = "capa_count",           nullable = false) private Integer capaCount;
    @Column(name = "capa_completed_count", nullable = false) private Integer capaCompletedCount;

    @Column(nullable = false, length = 20) private String status;
    @Column(name = "risk_id")              private Long riskId;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null)        createdAt = LocalDateTime.now();
        if (openDate == null)          openDate = LocalDate.now();
        if (severity == null)          severity = "MEDIUM";
        if (status == null)            status = "OPEN";
        if (rollupProgress == null)    rollupProgress = 0;
        if (capaCount == null)         capaCount = 0;
        if (capaCompletedCount == null) capaCompletedCount = 0;
        if (isDeleted == null)         isDeleted = false;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
