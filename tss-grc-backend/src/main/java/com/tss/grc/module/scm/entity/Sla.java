package com.tss.grc.module.scm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "scm", name = "slas")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Sla {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "metric_name_en", length = 255)
    private String metricNameEn;

    @Column(name = "metric_name_ar", length = 255)
    private String metricNameAr;

    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "measurement_frequency", length = 30)
    private String measurementFrequency;

    @Column(name = "current_value", precision = 10, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "status", length = 30)
    private String status = "ON_TRACK";

    @Column(name = "last_measured_at")
    private LocalDateTime lastMeasuredAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
