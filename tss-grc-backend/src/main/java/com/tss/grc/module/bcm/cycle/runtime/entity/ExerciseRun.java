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
 * Slice 10-B — One live "session" of a {@link com.tss.grc.module.bcm.entity.BcmExercise}.
 *
 * <p>The same exercise can be run multiple times against different audiences
 * (board tabletop, then operations tabletop, then full-scale on the same
 * scenario), so each ExerciseRun is a discrete row with its own injects,
 * responses, and after-action timeline.
 *
 * <h3>State machine</h3>
 * <pre>
 *   SCHEDULED ──startRun──▶ IN_PROGRESS ──pauseRun──▶ PAUSED
 *                              ▲                       │
 *                              └──── resumeRun ────────┘
 *                              │
 *                              └──endRun───▶ COMPLETED
 *                              │
 *                              └──abortRun──▶ ABORTED
 * </pre>
 *
 * <p>Status transitions are validated in {@code ExerciseRuntimeService} —
 * any attempt to dispatch an inject on a non-IN_PROGRESS run throws.
 */
@Entity
@Table(schema = "bcm", name = "exercise_run")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ExerciseRun extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "exercise_id", nullable = false)
    private Long exerciseId;

    /** Human-friendly run reference, unique per tenant (e.g. {@code RUN-2026-Q2-001}). */
    @Column(name = "run_code", nullable = false, length = 64)
    private String runCode;

    @Column(name = "facilitator_user_id")
    private Long facilitatorUserId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** SCHEDULED | IN_PROGRESS | PAUSED | COMPLETED | ABORTED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "SCHEDULED";

    @Column(name = "participant_count", nullable = false)
    private Integer participantCount = 0;

    /** IN_PERSON | VIRTUAL_MEET | HYBRID | CHAT_ONLY */
    @Column(name = "channel", nullable = false, length = 40)
    private String channel = "IN_PERSON";

    @Column(name = "facilitator_notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String facilitatorNotesEn;

    @Column(name = "facilitator_notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String facilitatorNotesAr;
}
