package com.tss.grc.module.sust.metrics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ESG metric (V63 — slice 9). Unified register for Environmental,
 * Social, and Governance metrics. Each metric carries baseline / current
 * / target with a unit; service computes progress on read using the
 * SGI direction-aware algorithm (raise-to vs lower-to target).
 *
 * <p>Status: {@code ACTIVE | AT_TARGET | OFF_TRACK | ARCHIVED}.
 *
 * <p>Links to {@code sgi_commitment} via {@code linkedSgiCommitmentId}
 * so a metric like "Renewable energy share" rolls up to the
 * "50% renewable mix by 2030" commitment.
 */
@Entity
@Table(schema = "sust", name = "esg_metric")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EsgMetric {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)          private String externalId;
    @Column(name = "metric_code", length = 40)          private String metricCode;

    @Column(name = "reporting_year", nullable = false) private Short reportingYear;

    @Column(nullable = false, length = 20) private String category;
    @Column(length = 40)                   private String subcategory;

    @Column(name = "metric_name_en", nullable = false, length = 255) private String metricNameEn;
    @Column(name = "metric_name_ar", length = 255)                   private String metricNameAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(nullable = false, length = 40) private String unit;

    @Column(name = "baseline_value", precision = 20, scale = 4) private BigDecimal baselineValue;
    @Column(name = "baseline_year") private Short baselineYear;
    @Column(name = "current_value", precision = 20, scale = 4) private BigDecimal currentValue;
    @Column(name = "current_value_as_of") private LocalDate currentValueAsOf;
    @Column(name = "target_value", precision = 20, scale = 4) private BigDecimal targetValue;
    @Column(name = "target_year") private Short targetYear;

    @Column(nullable = false, length = 20) private String status = "ACTIVE";

    @Column(name = "owner_name",    length = 255) private String ownerName;
    @Column(name = "owner_email",   length = 255) private String ownerEmail;
    @Column(name = "responsible_department", length = 120) private String responsibleDepartment;

    @Column(name = "linked_sgi_commitment_id") private Long linkedSgiCommitmentId;
    @Column(name = "evidence_ref", length = 255) private String evidenceRef;

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
