package com.tss.grc.module.bcm.cycle.runtime.observer.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Slice 10-C — Observer's structured capture during a running exercise.
 *
 * <p>One row per discrete observation. May be linked to a specific
 * {@link com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunInject}
 * via {@link #relatedInjectId} (e.g. "the team's response to the SAMA
 * notification inject was 12 minutes late"), or general (e.g. "background
 * chatter was too loud throughout").
 *
 * <h3>Type taxonomy</h3>
 * <ul>
 *   <li>{@code STRENGTH}          — something the team did well</li>
 *   <li>{@code GAP}               — a missing capability or process</li>
 *   <li>{@code RISK}              — an emerging risk the team did not catch</li>
 *   <li>{@code COMMUNICATION}     — comms quality, channels, escalation paths</li>
 *   <li>{@code DECISION_QUALITY}  — was the decision well-reasoned?</li>
 *   <li>{@code ESCALATION}        — was escalation timely + targeted?</li>
 *   <li>{@code RECOVERY_TIME}     — RTO / decision latency observations</li>
 *   <li>{@code COMPLIANCE}        — regulator notification + audit-trail concerns</li>
 *   <li>{@code GENERAL}           — anything else</li>
 * </ul>
 *
 * <h3>AAR flag</h3>
 * <p>{@link #isFlaggedForAar} is the observer's vote that this finding should
 * appear in the After-Action Report. The
 * {@link com.tss.grc.module.bcm.cycle.runtime.service.ExerciseRuntimeService#endRun}
 * hook (extended in Slice 10-C) pulls the flagged rows into the AAR draft
 * grouped by type and severity.
 */
@Entity
@Table(schema = "bcm", name = "exercise_observation")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ExerciseObservation extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "exercise_run_id", nullable = false)
    private Long exerciseRunId;

    @Column(name = "observer_user_id", nullable = false)
    private Long observerUserId;

    /** NULL = general observation (not tied to a specific inject). */
    @Column(name = "related_inject_id")
    private Long relatedInjectId;

    @Column(name = "observation_text_en", nullable = false, length = 2000)
    private String observationTextEn;

    @Column(name = "observation_text_ar", length = 2000)
    private String observationTextAr;

    /**
     * STRENGTH | GAP | RISK | COMMUNICATION | DECISION_QUALITY
     * | ESCALATION | RECOVERY_TIME | COMPLIANCE | GENERAL
     */
    @Column(name = "observation_type", nullable = false, length = 40)
    private String observationType;

    /** INFO | MINOR | MODERATE | MAJOR | CRITICAL */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "INFO";

    @Column(name = "recommendation_en", length = 2000)
    private String recommendationEn;

    @Column(name = "recommendation_ar", length = 2000)
    private String recommendationAr;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    /** Minutes elapsed from {@code exercise_run.started_at} at capture time. */
    @Column(name = "time_offset_minutes", nullable = false)
    private Integer timeOffsetMinutes = 0;

    @Column(name = "is_flagged_for_aar", nullable = false)
    private Boolean isFlaggedForAar = false;
}
