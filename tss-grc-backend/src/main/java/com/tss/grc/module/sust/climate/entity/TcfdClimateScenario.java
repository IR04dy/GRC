package com.tss.grc.module.sust.climate.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TCFD climate scenario header — Sprint 11 (V186).
 *
 * <p>One row represents a board-approved climate scenario applied to
 * the loan portfolio. Scenario types follow the NGFS taxonomy
 * (ORDERLY / DISORDERLY / HOT_HOUSE) plus CUSTOM. Lifecycle:
 * {@code DRAFT → IN_REVIEW → BOARD_APPROVED → ARCHIVED}.
 */
@Entity
@Table(schema = "sust", name = "tcfd_climate_scenario")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class TcfdClimateScenario extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "scenario_code", nullable = false, length = 64)
    private String scenarioCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** ORDERLY / DISORDERLY / HOT_HOUSE / CUSTOM. */
    @Column(name = "scenario_type", nullable = false, length = 20)
    private String scenarioType;

    /** NGFS / IEA_NZE_2050 / IPCC_RCP_2_6 / IPCC_RCP_4_5 / IPCC_RCP_8_5 / IFRS_S2 / CUSTOM. */
    @Column(name = "reference_framework", nullable = false, length = 40)
    private String referenceFramework;

    @Column(name = "time_horizon_years", nullable = false)
    private Integer timeHorizonYears;

    @Column(name = "global_warming_celsius", nullable = false, precision = 4, scale = 2)
    private BigDecimal globalWarmingCelsius;

    /** JSON describing carbon-price trajectory, energy mix, policy pathway. */
    @Column(name = "assumptions_json", columnDefinition = "NVARCHAR(MAX)")
    private String assumptionsJson;

    @Column(name = "portfolio_at_risk_sar_billions", precision = 18, scale = 2)
    private BigDecimal portfolioAtRiskSarBillions;

    @Column(name = "expected_loss_sar_millions", precision = 18, scale = 2)
    private BigDecimal expectedLossSarMillions;

    /** 0.0–1.0 — board-approved probabilistic blend across scenarios. */
    @Column(name = "probability_weight", nullable = false, precision = 4, scale = 3)
    private BigDecimal probabilityWeight = BigDecimal.ZERO;

    /** DRAFT / IN_REVIEW / BOARD_APPROVED / ARCHIVED. */
    @Column(name = "lifecycle_stage", nullable = false, length = 20)
    private String lifecycleStage = "DRAFT";

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
