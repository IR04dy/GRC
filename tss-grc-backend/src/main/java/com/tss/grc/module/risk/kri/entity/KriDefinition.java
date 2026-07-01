package com.tss.grc.module.risk.kri.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Key Risk Indicator definition — Sprint 13.
 *
 * <p>A definition is the "what to track, where to read it, when, and
 * which numbers go GREEN/AMBER/RED" half of the KRI engine. The other
 * half is the {@link KriMeasurement} time-series.
 *
 * <p>Status evaluation is driven by {@link #valueDirection}:
 * <ul>
 *   <li>{@code LOWER_IS_BETTER} (the default — most risk KRIs):
 *       GREEN when value ≤ green_threshold;
 *       AMBER when green_threshold &lt; value ≤ amber_threshold;
 *       RED   when value &gt; amber_threshold.</li>
 *   <li>{@code HIGHER_IS_BETTER} (capital/liquidity ratios):
 *       GREEN when value ≥ green_threshold;
 *       AMBER when amber_threshold ≤ value &lt; green_threshold;
 *       RED   when value &lt; amber_threshold.</li>
 * </ul>
 *
 * <p>{@link #redThreshold} is the "screaming red" anchor — recorded
 * for transparency and regulatory reference but not used by the
 * status-evaluation function. It is the value at which the audit
 * committee is notified out-of-cycle (e.g. PDPL Art.4 30-day SLA on
 * DSR aging).
 */
@Entity
@Table(schema = "risk", name = "kri_definition")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class KriDefinition extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "kri_code", nullable = false, length = 64)
    private String kriCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /**
     * CYBER | OPERATIONAL | CREDIT | MARKET | LIQUIDITY | COMPLIANCE |
     * REPUTATIONAL | STRATEGIC | AML | PRIVACY | BCM | ESG.
     */
    @Column(name = "category", nullable = false, length = 40)
    private String category;

    /** COUNT | PERCENTAGE | RATIO | CURRENCY_SAR | DAYS | HOURS | BPS. */
    @Column(name = "measurement_unit", nullable = false, length = 40)
    private String measurementUnit;

    /** LOWER_IS_BETTER (default) | HIGHER_IS_BETTER. */
    @Column(name = "value_direction", nullable = false, length = 20)
    private String valueDirection = "LOWER_IS_BETTER";

    @Column(name = "target_value", precision = 18, scale = 6)
    private BigDecimal targetValue;

    @Column(name = "green_threshold", precision = 18, scale = 6)
    private BigDecimal greenThreshold;

    @Column(name = "amber_threshold", precision = 18, scale = 6)
    private BigDecimal amberThreshold;

    @Column(name = "red_threshold", precision = 18, scale = 6)
    private BigDecimal redThreshold;

    /** DAILY | WEEKLY | MONTHLY | QUARTERLY | ANNUAL. */
    @Column(name = "frequency", nullable = false, length = 20)
    private String frequency = "MONTHLY";

    /** MANUAL | SQL | CALLBACK | METRIC_API. */
    @Column(name = "source_query_type", nullable = false, length = 20)
    private String sourceQueryType = "MANUAL";

    @Column(name = "source_query", columnDefinition = "NVARCHAR(MAX)")
    private String sourceQuery;

    /** Soft FK to iam.users — KRI owner accountable for monthly review. */
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    /** Soft FK to risk.risk — parent risk this KRI is a leading indicator for. */
    @Column(name = "linked_risk_id")
    private Long linkedRiskId;

    /** Soft FK to compliance.frameworks — IA-5 anchor (migrated from the legacy
     *  KRI engine's framework_id during E-E consolidation; drives the
     *  compliance ↔ KRI crosswalk on FrameworkAnchorService). */
    @Column(name = "linked_framework_id")
    private Long linkedFrameworkId;

    /** Framework clause reference (e.g. "ECC-1-3-1") — display anchor for the crosswalk. */
    @Column(name = "clause_ref", length = 100)
    private String clauseRef;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;
}
