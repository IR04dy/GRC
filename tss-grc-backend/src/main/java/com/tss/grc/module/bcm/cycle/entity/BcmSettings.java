package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * BCM tenant settings — audit Tier-1 #6.
 *
 * <p>One row per tenant holding the 13 configurable settings from spec §R.
 * Scalar settings (thresholds, frequencies, grace periods) sit in typed
 * columns so the hot-path readers don't parse JSON. Compound shapes
 * (risk matrix, approval matrix, notification rules) live in JSON columns
 * because the shape varies wildly per tenant / sector.
 *
 * <p>{@link com.tss.grc.module.bcm.cycle.service.BcmSettingsService}
 * exposes a {@code getOrInit(orgId)} that lazily creates the default row on
 * first read, so callers never have to handle a missing-row case.
 */
@Entity
@Table(schema = "bcm", name = "settings")
@Getter @Setter @NoArgsConstructor
public class BcmSettings {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false, unique = true)
    private Long organisationId;

    // ── Recovery target thresholds (hours) ──
    @Column(name = "rto_warning_hours",  nullable = false) private Integer rtoWarningHours  = 12;
    @Column(name = "rto_critical_hours", nullable = false) private Integer rtoCriticalHours = 24;
    @Column(name = "rpo_warning_hours",  nullable = false) private Integer rpoWarningHours  = 4;
    @Column(name = "rpo_critical_hours", nullable = false) private Integer rpoCriticalHours = 12;

    // ── Exercise frequency (months) ──
    @Column(name = "exercise_frequency_months_default",  nullable = false)
    private Integer exerciseFrequencyMonthsDefault  = 12;
    @Column(name = "exercise_frequency_months_critical", nullable = false)
    private Integer exerciseFrequencyMonthsCritical = 6;

    // ── Evidence + CAPA bookkeeping ──
    @Column(name = "evidence_expiry_warning_days", nullable = false)
    private Integer evidenceExpiryWarningDays = 60;

    @Column(name = "capa_overdue_grace_days", nullable = false)
    private Integer capaOverdueGraceDays = 7;

    // ── Risk matrix shape ──
    @Column(name = "risk_matrix_likelihood_max", nullable = false)
    private Integer riskMatrixLikelihoodMax = 5;
    @Column(name = "risk_matrix_impact_max", nullable = false)
    private Integer riskMatrixImpactMax = 5;

    // ── Approval + notification rules (free-form JSON) ──
    @Column(name = "approval_matrix_json",  columnDefinition = "NVARCHAR(MAX)") private String approvalMatrixJson;
    @Column(name = "notification_rules_json", columnDefinition = "NVARCHAR(MAX)") private String notificationRulesJson;

    // ── Framework versions ──
    @Column(name = "iso_22301_version",       length = 20) private String iso22301Version;
    @Column(name = "sama_opres_version",      length = 20) private String samaOpresVersion;
    @Column(name = "dga_framework_version",   length = 20) private String dgaFrameworkVersion;
    @Column(name = "ncema_framework_version", length = 20) private String ncemaFrameworkVersion;

    /** PLACEHOLDER | PARTIAL | COMPLETE */
    @Column(name = "regulatory_import_status", nullable = false, length = 30)
    private String regulatoryImportStatus = "PLACEHOLDER";

    @Column(name = "regulatory_import_notes", length = 1000)
    private String regulatoryImportNotes;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at")                    private LocalDateTime updatedAt;
    @Column(name = "created_by")                    private Long createdBy;
    @Column(name = "updated_by")                    private Long updatedBy;
}
