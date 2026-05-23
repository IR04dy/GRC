package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Slice 10-A — One timed inject inside an exercise scenario.
 *
 * <p>The library's {@link BcmExerciseScenario} (V111) carries the
 * legacy {@code injects_json} blob for back-compat callers. This
 * normalised row is the source of truth for the new scenario-library
 * UI and the scenario→exercise wizard: each inject is queried with
 * {@code findByScenarioIdOrderByInjectOrderAsc}, copied verbatim onto
 * the instantiated exercise, and rendered on the detail page''s
 * inject timeline.
 *
 * <p>Inject type taxonomy ({@link #injectType}):
 * <ul>
 *   <li>{@code INFORMATION}  — situational update (no action expected)</li>
 *   <li>{@code ESCALATION}   — severity escalates; bridge composition expands</li>
 *   <li>{@code DECISION}     — explicit decision point requiring named approver</li>
 *   <li>{@code MEDIA}        — media / customer-facing pressure</li>
 *   <li>{@code REGULATOR}    — regulator interaction (SAMA / NCA / SDAIA / MEIM / etc.)</li>
 * </ul>
 */
@Entity
@Table(schema = "bcm", name = "exercise_scenario_inject")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ExerciseScenarioInject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    @Column(name = "inject_order", nullable = false)
    private Integer injectOrder;

    @Column(name = "time_offset_minutes", nullable = false)
    private Integer timeOffsetMinutes;

    /** INFORMATION | ESCALATION | DECISION | MEDIA | REGULATOR */
    @Column(name = "inject_type", nullable = false, length = 40)
    private String injectType;

    @Column(name = "inject_text_en", nullable = false, length = 2000)
    private String injectTextEn;

    @Column(name = "inject_text_ar", length = 2000)
    private String injectTextAr;

    @Column(name = "expected_action_en", length = 1000)
    private String expectedActionEn;

    @Column(name = "expected_action_ar", length = 1000)
    private String expectedActionAr;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
