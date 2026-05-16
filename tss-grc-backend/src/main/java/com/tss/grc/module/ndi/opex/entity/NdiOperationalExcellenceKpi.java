package com.tss.grc.module.ndi.opex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NDI Operational Excellence KPI — slice 5.
 *
 * <p>Per-cycle indicator register. Unlike maturity/compliance, rows are
 * NOT generated from framework items — they're standalone metrics with
 * baseline, target, actual, frequency.
 *
 * <p>Status (Rule 13) is computed by the service from actual vs target
 * with direction-awareness (a lower target than baseline indicates a
 * "lower is better" KPI like MTTR, and the comparison inverts).
 */
@Entity
@Table(schema = "ndi", name = "operational_excellence_kpi")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class NdiOperationalExcellenceKpi {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;

    @Column(name = "cycle_id", nullable = false) private Long cycleId;
    @Column(name = "kpi_code", length = 40)      private String kpiCode;
    @Column(nullable = false, length = 60)       private String category;

    @Column(name = "indicator_name_en", nullable = false, length = 255) private String indicatorNameEn;
    @Column(name = "indicator_name_ar", length = 255)                   private String indicatorNameAr;

    @Column(name = "definition_en", columnDefinition = "NVARCHAR(MAX)") private String definitionEn;
    @Column(name = "definition_ar", columnDefinition = "NVARCHAR(MAX)") private String definitionAr;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String formula;
    @Column(name = "data_source", length = 255) private String dataSource;

    @Column(precision = 20, scale = 4) private BigDecimal baseline;
    @Column(nullable = false, precision = 20, scale = 4) private BigDecimal target;
    @Column(precision = 20, scale = 4) private BigDecimal actual;
    @Column(length = 40)               private String unit;
    @Column(length = 30)               private String frequency;

    @Column(name = "owner_name",  length = 255) private String ownerName;
    @Column(name = "owner_email", length = 255) private String ownerEmail;

    @Column(nullable = false, length = 20) private String status = "NOT_STARTED";
    @Column(length = 20)                   private String trend;
    @Column(name = "last_actual_at")       private LocalDate lastActualAt;

    @Column(name = "evidence_ref", length = 255) private String evidenceRef;
    @Column(name = "gap_id")               private Long gapId;
    @Column(name = "corrective_action_id") private Long correctiveActionId;

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
