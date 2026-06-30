package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Per-org risk methodology configuration — ERM remediation epic E-F.
 *
 * <p>Declares the active impact dimensions and the likelihood/impact scale
 * maxima. The multi-dimensional impact engine validates ratings against this
 * config (dimension must be declared; score within {@code impactScaleMax}), and
 * the UI heatmap reads the scales from here — so the methodology is configurable
 * rather than hard-coded. One row per organisation (seeded with sensible
 * defaults on first read).
 */
@Entity
@Table(schema = "risk", name = "methodology_config")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskMethodologyConfig {

    public static final String DEFAULT_DIMENSIONS =
        "FINANCIAL,OPERATIONAL,REGULATORY,REPUTATIONAL,CYBER,PRIVACY,SAFETY,STRATEGIC";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "impact_dimensions_csv", nullable = false, length = 1000)
    private String impactDimensionsCsv = DEFAULT_DIMENSIONS;

    @Column(name = "likelihood_scale_max", nullable = false)
    private Integer likelihoodScaleMax = 5;

    @Column(name = "impact_scale_max", nullable = false)
    private Integer impactScaleMax = 5;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
