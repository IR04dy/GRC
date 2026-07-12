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
 * PCAF Scope-3 Category 15 (Financed Emissions) record — Sprint 11
 * (V186).
 *
 * <p>One row per (reporting year × reporting period × asset class).
 * {@code total_financed_emissions_tco2e} is the materialised result of
 * {@code (scope1 + scope2 + scope3) × attribution_factor} — held as a
 * column rather than a computed expression because IFRS S2 requires
 * the published number to be stable even if the underlying components
 * are restated later.
 *
 * <p>Data quality follows PCAF's 1-5 scale (1 = best — verified
 * reported emissions; 5 = worst — proxied from sector intensity).
 *
 * <p>Lifecycle: {@code DRAFT → IN_REVIEW → APPROVED → PUBLISHED →
 * ARCHIVED}.
 */
@Entity
@Table(schema = "sust", name = "financed_emissions_record")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class FinancedEmissionsRecord extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "reporting_year", nullable = false)
    private Integer reportingYear;

    /** ANNUAL / Q1 / Q2 / Q3 / Q4. */
    @Column(name = "reporting_period", nullable = false, length = 10)
    private String reportingPeriod = "ANNUAL";

    /** LISTED_EQUITY / CORPORATE_BONDS / BUSINESS_LOANS / PROJECT_FINANCE
     *  / COMMERCIAL_REAL_ESTATE / MORTGAGES / MOTOR_VEHICLE_LOANS / SOVEREIGN_DEBT. */
    @Column(name = "asset_class", nullable = false, length = 40)
    private String assetClass;

    @Column(name = "asset_class_label_en", nullable = false, length = 255)
    private String assetClassLabelEn;

    @Column(name = "asset_class_label_ar", length = 255)
    private String assetClassLabelAr;

    /** V293 (plan §5.1) — soft FK to sust.portfolio_assets for per-asset PCAF rollups. */
    @Column(name = "asset_ref")
    private Long assetRef;

    @Column(name = "outstanding_amount_sar_millions", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingAmountSarMillions;

    /** PCAF attribution factor — outstanding / EVIC, or analogous. */
    @Column(name = "attribution_factor", nullable = false, precision = 8, scale = 6)
    private BigDecimal attributionFactor;

    /** PCAF data quality 1-5 (1 best). */
    @Column(name = "data_quality_score", nullable = false)
    private Integer dataQualityScore;

    @Column(name = "emissions_scope1_tco2e", nullable = false, precision = 18, scale = 2)
    private BigDecimal emissionsScope1Tco2e = BigDecimal.ZERO;

    @Column(name = "emissions_scope2_tco2e", nullable = false, precision = 18, scale = 2)
    private BigDecimal emissionsScope2Tco2e = BigDecimal.ZERO;

    @Column(name = "emissions_scope3_tco2e", nullable = false, precision = 18, scale = 2)
    private BigDecimal emissionsScope3Tco2e = BigDecimal.ZERO;

    @Column(name = "total_financed_emissions_tco2e", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalFinancedEmissionsTco2e = BigDecimal.ZERO;

    @Column(name = "methodology_en", length = 1000)
    private String methodologyEn;

    @Column(name = "methodology_ar", length = 1000)
    private String methodologyAr;

    /** DRAFT / IN_REVIEW / APPROVED / PUBLISHED / ARCHIVED. */
    @Column(name = "lifecycle_stage", nullable = false, length = 20)
    private String lifecycleStage = "DRAFT";
}
