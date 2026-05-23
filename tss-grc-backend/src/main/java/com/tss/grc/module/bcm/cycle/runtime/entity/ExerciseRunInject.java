package com.tss.grc.module.bcm.cycle.runtime.entity;

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
 * Slice 10-B — Per-run cloned inject.
 *
 * <p>Cloned at {@code startRun} from the source
 * {@link com.tss.grc.module.bcm.cycle.entity.ExerciseScenarioInject}. The
 * body (text + expected action) is DENORMALISED — copied verbatim — so
 * editing the library scenario later does not retroactively rewrite the
 * historical run timeline.
 *
 * <p>{@link #sourceInjectId} is {@code null} for facilitator-added on-the-fly
 * injects (the "Add custom inject" path on the console).
 *
 * <h3>Status lifecycle</h3>
 * <pre>
 *   PENDING ──dispatchInject──▶ DISPATCHED
 *                                  │
 *                                  ├──first response──▶ ACKNOWLEDGED
 *                                  │
 *                                  ├──complete──────▶ COMPLETED
 *                                  │
 *                                  └──skip──────────▶ SKIPPED
 * </pre>
 */
@Entity
@Table(schema = "bcm", name = "exercise_run_inject")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ExerciseRunInject extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "exercise_run_id", nullable = false)
    private Long exerciseRunId;

    /** NULL for custom on-the-fly injects added by the facilitator. */
    @Column(name = "source_inject_id")
    private Long sourceInjectId;

    @Column(name = "planned_time_offset_minutes", nullable = false)
    private Integer plannedTimeOffsetMinutes = 0;

    @Column(name = "actual_dispatched_at")
    private LocalDateTime actualDispatchedAt;

    /** INFORMATION | ESCALATION | DECISION | MEDIA | REGULATOR | CUSTOM */
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

    /** PENDING | DISPATCHED | ACKNOWLEDGED | COMPLETED | SKIPPED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";
}
