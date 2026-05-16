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

    @Column(name = "esg_readiness_warning_threshold", nullable = false)  private Integer esgReadinessWarningThreshold  = 60;
    @Column(name = "esg_readiness_critical_threshold", nullable = false) private Integer esgReadinessCriticalThreshold = 40;

    @Column(name = "evidence_expiry_warning_days", nullable = false) private Integer evidenceExpiryWarningDays = 30;
    @Column(name = "evidence_sweep_cron", nullable = false, length = 50) private String evidenceSweepCron = "0 0 6 * * *";

    @Column(name = "scope3_partial_coverage_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal scope3PartialCoverageThreshold = new BigDecimal("70.00");

    @Column(name = "capa_overdue_warning_days",      nullable = false) private Integer capaOverdueWarningDays      = 7;
    @Column(name = "capa_executive_escalation_days", nullable = false) private Integer capaExecutiveEscalationDays = 30;

    @Column(name = "sgi_at_risk_progress_factor", nullable = false, precision = 3, scale = 2)
    private BigDecimal sgiAtRiskProgressFactor = new BigDecimal("0.70");

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
