package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 9-D — Continuity strategy ROI analysis.
 *
 * <p>One row per continuity-strategy candidate per BIA. Layers on top of
 * the Quantitative BIA ({@link BiaQuantitative}) by translating the
 * monetised loss curves into a board-ready ROI table:
 *
 * <ul>
 *   <li>{@code capex_sar} + annual {@code opex_annual_sar} over a
 *       configurable planning horizon (default 5 years)</li>
 *   <li>derived {@code avoidable_loss_per_incident_sar} read from the
 *       BIA at the band matching the strategy's expected RTO</li>
 *   <li>derived totals — total avoidable loss, total cost, ROI %, NPV
 *       at the SAMA 8% reference rate, and payback period</li>
 *   <li>recommendation state machine — PENDING_REVIEW → APPROVED |
 *       DEFERRED | REJECTED, with DEFERRED → APPROVED | REJECTED</li>
 * </ul>
 *
 * <p>SAMA Operational Resilience Framework §5.3 and the NCA ECC-2 BCM
 * domain both expect every proposed continuity investment to carry
 * documented financial justification — this entity is that justification.
 */
@Entity
@Table(schema = "bcm", name = "strategy_roi_analysis")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class StrategyRoiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "analysis_code", nullable = false, length = 64)
    private String analysisCode;

    @Column(name = "bia_quantitative_id", nullable = false)
    private Long biaQuantitativeId;

    /** Optional cross-reference to the {@code bcm.continuity_strategies} register. */
    @Column(name = "strategy_id")
    private Long strategyId;

    @Column(name = "strategy_name_en", nullable = false, length = 255)
    private String strategyNameEn;

    @Column(name = "strategy_name_ar", nullable = false, length = 255)
    private String strategyNameAr;

    /** ACTIVE_ACTIVE | WARM_DR | COLD_DR | VENDOR_FAILOVER | MANUAL_WORKAROUND | INSURANCE_TRANSFER */
    @Column(name = "strategy_type", nullable = false, length = 40)
    private String strategyType;

    @Column(name = "planning_horizon_years", nullable = false)
    private Integer planningHorizonYears = 5;

    @Column(name = "capex_sar", nullable = false, precision = 18, scale = 2)
    private BigDecimal capexSar;

    @Column(name = "opex_annual_sar", nullable = false, precision = 18, scale = 2)
    private BigDecimal opexAnnualSar;

    @Column(name = "expected_rto_hours", nullable = false)
    private Integer expectedRtoHours;

    @Column(name = "expected_incidents_per_year", nullable = false, precision = 8, scale = 2)
    private BigDecimal expectedIncidentsPerYear = new BigDecimal("0.50");

    @Column(name = "avoidable_loss_per_incident_sar", nullable = false, precision = 18, scale = 2)
    private BigDecimal avoidableLossPerIncidentSar;

    @Column(name = "total_avoidable_loss_sar", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAvoidableLossSar;

    @Column(name = "total_cost_sar", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalCostSar;

    @Column(name = "roi_percent", nullable = false, precision = 8, scale = 2)
    private BigDecimal roiPercent;

    /** NULL when no payback within {@link #planningHorizonYears}. */
    @Column(name = "payback_period_months", precision = 8, scale = 2)
    private BigDecimal paybackPeriodMonths;

    /** Net Present Value at {@link #discountRatePercent} over the horizon. */
    @Column(name = "npv_sar", precision = 18, scale = 2)
    private BigDecimal npvSar;

    @Column(name = "discount_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountRatePercent = new BigDecimal("8.00");

    /** PENDING_REVIEW | APPROVED | REJECTED | DEFERRED */
    @Column(name = "recommendation", nullable = false, length = 40)
    private String recommendation = "PENDING_REVIEW";

    @Column(name = "recommendation_rationale_en", length = 1000)
    private String recommendationRationaleEn;

    @Column(name = "recommendation_rationale_ar", length = 1000)
    private String recommendationRationaleAr;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    @Column(name = "decided_by_name", length = 255)
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
