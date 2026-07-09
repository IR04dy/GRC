package com.tss.grc.module.sust.finance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Green-Finance uplift G1 (plan §4) — an eligible green project that instrument
 * proceeds are allocated to. Category follows the ICMA GBP / CCE eligible set.
 * Impact carries expected vs actual pairs; only ACTUAL figures count toward an
 * instrument's attributed impact (plan §4.2) unless explicitly flagged as estimates.
 */
@Entity
@Table(schema = "sust", name = "green_projects")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class GreenProject extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "project_code", nullable = false, length = 40)
    private String projectCode;

    @Column(name = "name_en", nullable = false, length = 300)
    private String nameEn;

    @Column(name = "name_ar", length = 300)
    private String nameAr;

    /** RENEWABLE_ENERGY | ENERGY_EFFICIENCY | CLEAN_TRANSPORT | GREEN_BUILDINGS | WATER | WASTE | CCUS | OTHER_ELIGIBLE. */
    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "total_cost_sar_mn", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalCostSarMn;

    @Column(name = "expected_tco2e_avoided_yr", precision = 18, scale = 4)
    private BigDecimal expectedTco2eAvoidedYr;

    @Column(name = "actual_tco2e_avoided_yr", precision = 18, scale = 4)
    private BigDecimal actualTco2eAvoidedYr;

    @Column(name = "expected_mwh_renewable_yr", precision = 18, scale = 4)
    private BigDecimal expectedMwhRenewableYr;

    @Column(name = "actual_mwh_renewable_yr", precision = 18, scale = 4)
    private BigDecimal actualMwhRenewableYr;

    @Column(name = "expected_water_m3_saved_yr", precision = 18, scale = 4)
    private BigDecimal expectedWaterM3SavedYr;

    @Column(name = "actual_water_m3_saved_yr", precision = 18, scale = 4)
    private BigDecimal actualWaterM3SavedYr;

    /** PLANNED | ACTIVE | COMPLETED. */
    @Column(nullable = false, length = 20)
    private String status = "PLANNED";
}
