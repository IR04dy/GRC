package com.tss.grc.module.ai.governance.entity;

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
 * Post-market monitoring observation for an {@link MlModel}.
 *
 * <p>{@code observation_type} ∈ {DATA_DRIFT, CONCEPT_DRIFT,
 * PERFORMANCE_DEGRADATION, FAIRNESS_REGRESSION}.
 * {@code severity} ∈ {INFO, MINOR, MODERATE, MAJOR, CRITICAL}.
 *
 * <p>When a {@code CRITICAL} severity observation is recorded the
 * service auto-opens an Issue (intake record kind {@code RISK_ISSUE})
 * and flips the parent model's lifecycle stage to {@code REVISION}.
 * The created Issue id is stored back into {@link #triggeredIssueId}
 * so the UI can deep-link.
 */
@Entity
@Table(schema = "ai_governance", name = "ml_model_drift_observation")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class MlModelDriftObservation extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt = LocalDateTime.now();

    /** DATA_DRIFT/CONCEPT_DRIFT/PERFORMANCE_DEGRADATION/FAIRNESS_REGRESSION. */
    @Column(name = "observation_type", nullable = false, length = 40)
    private String observationType;

    /** INFO/MINOR/MODERATE/MAJOR/CRITICAL. */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "metric_name", nullable = false, length = 255)
    private String metricName;

    @Column(name = "metric_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal metricValue;

    @Column(name = "baseline_value", precision = 18, scale = 6)
    private BigDecimal baselineValue;

    @Column(name = "drift_threshold", precision = 18, scale = 6)
    private BigDecimal driftThreshold;

    @Column(name = "notes_en", length = 2000)
    private String notesEn;

    @Column(name = "notes_ar", length = 2000)
    private String notesAr;

    @Column(name = "is_acknowledged", nullable = false)
    private Boolean isAcknowledged = Boolean.FALSE;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by_user_id")
    private Long acknowledgedByUserId;

    /** PK of the auto-created Issue (IntakeRecord) when severity = CRITICAL. */
    @Column(name = "triggered_issue_id")
    private Long triggeredIssueId;
}
