package com.tss.grc.module.sust.settings.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Per-organisation Sustainability settings (V65 — slice 14-A).
 *
 * <p>One row per org. New thresholds get added as new columns with
 * defaults — not as new rows in an EAV table.
 *
 * <p>Drives runtime behaviour of:
 * <ul>
 *   <li>Rule 1 — GHG aggregate's scope3PartialFlag boundary.</li>
 *   <li>Rule 3 — SGI at-risk classification factor.</li>
 *   <li>Rule 9 — Evidence-expiry warning window + sweep cron.</li>
 *   <li>Rule 13 — CAPA escalation windows (warning + executive).</li>
 *   <li>Rule 14 — Dashboard ESG-readiness banner thresholds.</li>
 * </ul>
 */
@Entity
@Table(schema = "sust", name = "organisation_settings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OrganisationSettings {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;

    @Column(name = "esg_readiness_warning_threshold", nullable = false)  private Integer esgReadinessWarningThreshold;
    @Column(name = "esg_readiness_critical_threshold", nullable = false) private Integer esgReadinessCriticalThreshold;

    @Column(name = "evidence_expiry_warning_days", nullable = false) private Integer evidenceExpiryWarningDays;
    @Column(name = "evidence_sweep_cron", nullable = false, length = 50) private String evidenceSweepCron;

    @Column(name = "scope3_partial_coverage_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal scope3PartialCoverageThreshold;

    @Column(name = "capa_overdue_warning_days",      nullable = false) private Integer capaOverdueWarningDays;
    @Column(name = "capa_executive_escalation_days", nullable = false) private Integer capaExecutiveEscalationDays;

    @Column(name = "sgi_at_risk_progress_factor", nullable = false, precision = 3, scale = 2)
    private BigDecimal sgiAtRiskProgressFactor;

    /**
     * S-E — strict probability-weight validation on scenario approval.
     * false (default): over-allocation is a soft warning on the weighted-EL read.
     * true: approving a scenario that pushes total board-approved weight &gt; 1.0
     * is rejected (400).
     */
    @Column(name = "climate_scenario_weights_strict", nullable = false)
    // ⚠⚠ F2-294 removed `= false` here and `= new BigDecimal("10")` from
    // `carbonOffsetCapPct` below. This entity is the THIRD kind in the sweep: its
    // initialisers were not merely a hazard, they were the register's declared FACTORY
    // DEFAULTS, read by `getOrCreate()` through the constructor. So the declaration moved
    // rather than being deleted — see `OrganisationSettingsService.DEFAULTS`.
    // 🔑 And that relocation closed a drift the initialisers were hiding:
    // `resetToDefaults()` wrote all nine of its defaults as LITERALS and never touched
    // `carbonOffsetCapPct` at all, so "reset to defaults" left the carbon-offset cap
    // exactly where it was. Two copies of one fact with nobody maintaining the agreement
    // (F2-177); one declaration now, with both readers naming it.
    private Boolean climateScenarioWeightsStrict;

    /** V295 (decision #4) — retired carbon offsets may not exceed this % of the
     *  disclosed gross reduction (anti-greenwashing cap; default 10). */
    @Column(name = "carbon_offset_cap_pct", nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal carbonOffsetCapPct;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        // Defaults live here (not in field initializers) so a partial PUT that
        // OMITS a threshold leaves it null and update()'s null-guard preserves
        // the stored value instead of silently snapping it back to default.
        // getOrCreate() persists a bare new row, so @PrePersist still seeds a
        // brand-new tenant's settings with the V65 schema defaults on INSERT.
        if (esgReadinessWarningThreshold   == null) esgReadinessWarningThreshold   = 60;
        if (esgReadinessCriticalThreshold  == null) esgReadinessCriticalThreshold  = 40;
        if (evidenceExpiryWarningDays      == null) evidenceExpiryWarningDays      = 30;
        if (evidenceSweepCron              == null) evidenceSweepCron              = "0 0 6 * * *";
        if (scope3PartialCoverageThreshold == null) scope3PartialCoverageThreshold = new BigDecimal("70.00");
        if (capaOverdueWarningDays         == null) capaOverdueWarningDays         = 7;
        if (capaExecutiveEscalationDays    == null) capaExecutiveEscalationDays    = 30;
        if (sgiAtRiskProgressFactor        == null) sgiAtRiskProgressFactor        = new BigDecimal("0.70");
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
