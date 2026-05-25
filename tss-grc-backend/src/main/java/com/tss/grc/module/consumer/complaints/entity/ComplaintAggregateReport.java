package com.tss.grc.module.consumer.complaints.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Quarterly aggregate report — backs the SAMA Banking Consumer
 * Protection quarterly submission. One row per (tenant, period).
 *
 * <p>Periods follow the {@code Qn-YYYY} convention (e.g. {@code Q4-2025}).
 */
@Entity
@Table(schema = "consumer", name = "complaint_aggregate_report")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ComplaintAggregateReport extends BaseEntity {

    @Column(name = "report_period", nullable = false, length = 16)
    private String reportPeriod;

    @Column(name = "total_received", nullable = false)
    private Integer totalReceived = 0;

    @Column(name = "total_resolved", nullable = false)
    private Integer totalResolved = 0;

    @Column(name = "total_escalated_sama", nullable = false)
    private Integer totalEscalatedSama = 0;

    @Column(name = "avg_resolution_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgResolutionDays = BigDecimal.ZERO;

    @Column(name = "ack_sla_breach_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal ackSlaBreachPct = BigDecimal.ZERO;

    @Column(name = "resolution_sla_breach_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal resolutionSlaBreachPct = BigDecimal.ZERO;

    @Column(name = "top_category", length = 64)
    private String topCategory;

    @Column(name = "top_category_count", nullable = false)
    private Integer topCategoryCount = 0;

    @Column(name = "compensation_total_sar", nullable = false, precision = 18, scale = 2)
    private BigDecimal compensationTotalSar = BigDecimal.ZERO;

    @Column(name = "generated_on", nullable = false)
    private LocalDateTime generatedOn = LocalDateTime.now();

    @Column(name = "generated_by", length = 128)
    private String generatedBy;

    @Column(name = "org_id", nullable = false)
    private Long organisationId;
}
