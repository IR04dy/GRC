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
 * Financed-emissions reduction target — Sprint 11 (V186).
 *
 * <p>One row per stated commitment (e.g. net-zero by 2050, interim 50%
 * by 2030). Alignment framework captures the methodology used to set
 * the target:
 * <ul>
 *   <li>{@code SBTI_1_5C} — Science Based Targets initiative, 1.5°C
 *       pathway. Highest credibility — requires SBTi-validated
 *       submission.</li>
 *   <li>{@code SBTI_WELL_BELOW_2C} — SBTi well-below-2°C pathway.</li>
 *   <li>{@code NZBA} — UN-convened Net-Zero Banking Alliance
 *       commitment (members commit to net-zero financed emissions by
 *       2050, with sector-pathway aligned interim targets).</li>
 *   <li>{@code CUSTOM} — board-set, not aligned to a public framework.
 *       Disclose explicitly in IFRS S2 reporting.</li>
 * </ul>
 *
 * <p>{@code sbti_aligned} is a separate boolean from
 * {@code alignment_framework} — a target can claim SBTi alignment
 * without yet being validated, and the reverse (NZBA targets can be
 * SBTi-validated). The two columns together cover both states without
 * forcing the framework taxonomy to grow.
 */
@Entity
@Table(schema = "sust", name = "financed_emissions_target")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class FinancedEmissionsTarget extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "target_code", nullable = false, length = 64)
    private String targetCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "target_year", nullable = false)
    private Integer targetYear;

    @Column(name = "baseline_year", nullable = false)
    private Integer baselineYear;

    @Column(name = "baseline_emissions_tco2e", nullable = false, precision = 18, scale = 2)
    private BigDecimal baselineEmissionsTco2e;

    @Column(name = "target_emissions_tco2e", nullable = false, precision = 18, scale = 2)
    private BigDecimal targetEmissionsTco2e;

    @Column(name = "reduction_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal reductionPercent;

    @Column(name = "sbti_aligned", nullable = false)
    private Boolean sbtiAligned = Boolean.FALSE;

    /** SBTI_1_5C / SBTI_WELL_BELOW_2C / NZBA / CUSTOM. */
    @Column(name = "alignment_framework", nullable = false, length = 40)
    private String alignmentFramework;
}
