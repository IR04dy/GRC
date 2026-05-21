package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Risk → Control linkage — buyer assessment §5.1 ERM #2.
 *
 * <p>Bridge entity that wires a risk to one or more controls that mitigate
 * it. Tier-1 platforms (ServiceNow IRM, MetricStream, Archer) all expose
 * this as a first-class relationship so the platform can:
 *
 * <ul>
 *   <li>Aggregate mitigation effectiveness per risk (weighted by
 *       {@code contributionPct}) and recompute residual scores automatically.</li>
 *   <li>Surface "uncontrolled risks" (risks with zero linked controls).</li>
 *   <li>Trace control failures back to the risks they invalidate.</li>
 * </ul>
 */
@Entity
@Table(schema = "risk", name = "risk_controls")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskControl {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(name = "control_id", nullable = false)
    private Long controlId;

    /** EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED */
    @Column(nullable = false, length = 30)
    private String effectiveness = "NOT_TESTED";

    /**
     * Optional 0-100 weight — how much this control contributes to the
     * risk's overall mitigation. Used by RiskExposureService.recomputeResidual.
     */
    @Column(name = "contribution_pct", precision = 5, scale = 2)
    private BigDecimal contributionPct;

    @Column(length = 1000)
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
