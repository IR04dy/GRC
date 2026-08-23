package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "bcm", name = "exercises")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmExercise {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "exercise_name_en", nullable = false, length = 255)
    private String exerciseNameEn;

    @Column(name = "exercise_name_ar", length = 255)
    private String exerciseNameAr;

    @Column(name = "exercise_type", nullable = false, length = 50)
    private String exerciseType = "TABLETOP";

    @Column(name = "scenario", columnDefinition = "NVARCHAR(MAX)")
    private String scenario;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "executed_date")
    private LocalDate executedDate;

    @Column(name = "score")
    private Integer score;

    /**
     * SCHEDULED | IN_PROGRESS | COMPLETED | CANCELLED — already constrained by
     * {@code CK_bcm_exer_status} on {@code bcm.exercises}, and the BCM page's status select offers
     * exactly those four. There is no state machine: {@code BcmService.completeExercise} owns
     * COMPLETED (and stamps {@code executedDate} with it) and nothing else here is server-owned.
     *
     * <p>⚠⚠ F2-300 — deliberately NOT initialised. This field carried {@code = "SCHEDULED"}, and
     * because {@code PUT /api/v1/bcm/exercises/{id}} binds this entity and {@code updateExercise}
     * wrote the status unconditionally, a body that OMITTED status arrived as "SCHEDULED" and was
     * written — so a partial PUT silently moved a COMPLETED exercise back to SCHEDULED. F2-238's
     * reset shape, and the sixth instance of that family after CrisisEvent / Issue /
     * BcmBiaAssessment (F2-238), BcpPlan (F2-297) and BcmPlan (F2-298). It matters here more than
     * the count suggests: <b>12 of the 13 live rows are COMPLETED</b> in both databases.
     * {@code createExercise} owns the default now, as a DEFAULT rather than a refusal, because
     * recording an exercise run offline — created straight into COMPLETED with its date — is
     * legitimate and the create form offers the full select.
     *
     * <p>⚙ The column's own default is {@code 'SCHEDULED'} too, and it is unreachable through the
     * application: this field is mapped insertable, so Hibernate always names the column. The
     * default therefore documents intent rather than supplying a value.
     *
     * <p>⚠⚠ Do not confuse this table with {@code bcm.bcm_exercises}, which <b>no entity maps</b> —
     * a V1-era orphan carrying 14 rows and zero CHECK constraints, including a {@code PLANNED}
     * status this register's CHECK would refuse. F2-300 measured that one by name and drew four
     * wrong conclusions from it before resolving {@code @Table} (the guessed-identifier landmine,
     * third instance in this programme). 🔑 <b>Two tables differing by a prefix, one live and one
     * orphaned, and the orphan sorts FIRST in a name search.</b>
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "findings", columnDefinition = "NVARCHAR(MAX)")
    private String findings;

    @Column(name = "capa_count", nullable = false)
    private Integer capaCount = 0;

    @Column(name = "facilitator", length = 255)
    private String facilitator;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Slice 10-A — pointer back to the {@code bcm.exercise_scenarios}
     * library row this exercise was instantiated from, if any. NULL for
     * manually-created exercises that were not derived from a scenario.
     */
    @Column(name = "scenario_id")
    private Long scenarioId;
}
