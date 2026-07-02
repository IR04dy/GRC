package com.tss.grc.module.sust.metrics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Append-only reading history for an {@link EsgMetric} (epic S-R).
 *
 * <p>Each {@code record-reading} appends a row here in addition to updating the
 * metric's {@code current_value}, so an auditor can trace the full time-series
 * instead of seeing only the latest overwrite.
 */
@Entity
@Table(schema = "sust", name = "esg_metric_reading")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EsgMetricReading {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "metric_id", nullable = false)        private Long metricId;

    @Column(name = "reading_value", nullable = false, precision = 20, scale = 4)
    private BigDecimal readingValue;

    @Column(name = "as_of", nullable = false) private LocalDate asOf;

    @Column(length = 1000) private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100) private String createdBy;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
