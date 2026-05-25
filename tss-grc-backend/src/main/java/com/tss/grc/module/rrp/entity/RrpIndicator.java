package com.tss.grc.module.rrp.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One recovery indicator — a live early-warning metric with RAG bands.
 *
 * <p>SAMA's Recovery Planning Rules require a minimum set of indicators
 * across capital, liquidity, profitability, asset quality, and market
 * confidence. Each indicator has GREEN / AMBER / RED bands. Crossing
 * AMBER triggers a management-attention escalation; crossing RED forces
 * the CRO to convene the Recovery Committee and consider firing
 * recovery options.
 *
 * <p>The indicator is "live" — its {@code currentValue} is refreshed
 * daily/monthly from upstream risk and finance systems. The crossing-
 * threshold check fires the {@code RRP_INDICATOR_THRESHOLD_BREACHED}
 * F8 event so the CRO + CFO get a same-day notification.
 *
 * <p>Typical Saudi-bank recovery indicators (numbers SAMA expects):
 *
 * <table border="1" summary="indicator thresholds">
 *  <tr><th>Indicator</th><th>GREEN ≥</th><th>AMBER</th><th>RED ≤</th></tr>
 *  <tr><td>CET1 ratio</td><td>13.0%</td><td>11.0–13.0%</td><td>11.0%</td></tr>
 *  <tr><td>LCR</td><td>120%</td><td>110–120%</td><td>110%</td></tr>
 *  <tr><td>NSFR</td><td>110%</td><td>105–110%</td><td>105%</td></tr>
 *  <tr><td>NPL ratio</td><td>≤3%</td><td>3–5%</td><td>≥5%</td></tr>
 *  <tr><td>Cost of risk</td><td>≤80bps</td><td>80–150bps</td><td>≥150bps</td></tr>
 * </table>
 */
@Entity
@Table(schema = "rrp", name = "rrp_indicator")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RrpIndicator extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    /** Indicator code, unique per cycle. e.g. {@code IND-CET1}. */
    @Column(name = "indicator_code", nullable = false, length = 32)
    private String indicatorCode;

    /**
     * Category:
     * {@code CAPITAL | LIQUIDITY | PROFITABILITY | ASSET_QUALITY | MARKET_CONFIDENCE | OPERATIONAL | OTHER}.
     */
    @Column(name = "category", nullable = false, length = 24)
    private String category;

    @Column(name = "title_en", nullable = false, length = 256)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 256)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** Display unit (e.g. {@code %}, {@code bps}, {@code SAR M}). */
    @Column(name = "unit", length = 16)
    private String unit;

    /**
     * Direction:
     * {@code HIGHER_IS_BETTER} (CAR, LCR) or {@code LOWER_IS_BETTER} (NPL, cost of risk).
     * Drives the RAG band orientation.
     */
    @Column(name = "direction", nullable = false, length = 20)
    private String direction = "HIGHER_IS_BETTER";

    /** Green-band threshold (boundary with AMBER). */
    @Column(name = "green_threshold", precision = 18, scale = 4)
    private BigDecimal greenThreshold;

    /** Amber-band threshold (boundary with RED). */
    @Column(name = "amber_threshold", precision = 18, scale = 4)
    private BigDecimal amberThreshold;

    /** Current observed value. */
    @Column(name = "current_value", precision = 18, scale = 4)
    private BigDecimal currentValue;

    /** Current RAG band: {@code GREEN | AMBER | RED}. Derived on update. */
    @Column(name = "current_band", length = 8)
    private String currentBand = "GREEN";

    /** Date the current value was measured. */
    @Column(name = "measured_at")
    private LocalDate measuredAt;

    /** Upstream source (e.g. "Finance datamart"). */
    @Column(name = "data_source", length = 256)
    private String dataSource;

    /** Owner role (CRO / CFO / Treasurer). */
    @Column(name = "owner_role", length = 64)
    private String ownerRole;
}
