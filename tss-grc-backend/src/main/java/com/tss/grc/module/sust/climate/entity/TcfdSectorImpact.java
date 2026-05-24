package com.tss.grc.module.sust.climate.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-sector exposure under a TCFD climate scenario — Sprint 11 (V186).
 *
 * <p>Sector codes follow the GICS top-level (ENERGY, MATERIALS,
 * INDUSTRIALS, REAL_ESTATE, UTILITIES, FINANCIALS, …). Transition +
 * physical risk scores are 0-100 on the bank's internal scale. PD
 * uplift is expressed in basis points on top of the baseline portfolio
 * PD.
 */
@Entity
@Table(schema = "sust", name = "tcfd_sector_impact")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class TcfdSectorImpact extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    /** GICS top-level (ENERGY, MATERIALS, …). */
    @Column(name = "sector_code", nullable = false, length = 20)
    private String sectorCode;

    @Column(name = "sector_name_en", nullable = false, length = 255)
    private String sectorNameEn;

    @Column(name = "sector_name_ar", length = 255)
    private String sectorNameAr;

    @Column(name = "exposure_sar_millions", nullable = false, precision = 18, scale = 2)
    private BigDecimal exposureSarMillions;

    @Column(name = "transition_risk_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal transitionRiskScore;

    @Column(name = "physical_risk_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal physicalRiskScore;

    @Column(name = "expected_pd_uplift_bps", nullable = false)
    private Integer expectedPdUpliftBps = 0;

    @Column(name = "mitigant_actions_en", length = 1000)
    private String mitigantActionsEn;

    @Column(name = "mitigant_actions_ar", length = 1000)
    private String mitigantActionsAr;
}
