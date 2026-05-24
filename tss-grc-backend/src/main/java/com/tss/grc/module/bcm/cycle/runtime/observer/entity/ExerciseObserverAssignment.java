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
 * Slice 10-C — The access-control gate for the observer dashboard.
 *
 * <p>A user is "observing" a {@code bcm.exercise_run} when an active
 * (non-soft-deleted) row of this table exists for the (run, user) pair.
 * The {@link com.tss.grc.module.bcm.cycle.runtime.observer.service.ExerciseObserverService}
 * checks this on every write — observers without an assignment cannot record
 * observations or flag them for the AAR.
 *
 * <h3>Roles</h3>
 * <p>The {@link #observerRole} is informational metadata for the AAR
 * findings section — it tells the report consumer who scored the run.
 * Authorisation is by RBAC role on the controller, not by this field.
 */
@Entity
@Table(schema = "bcm", name = "exercise_observer_assignment")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ExerciseObserverAssignment extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "exercise_run_id", nullable = false)
    private Long exerciseRunId;

    @Column(name = "observer_user_id", nullable = false)
    private Long observerUserId;

    /**
     * INTERNAL_AUDIT | EXTERNAL_AUDIT | COMPLIANCE_OBSERVER
     * | SAMA_LIAISON | NCA_LIAISON | BOARD_OBSERVER
     */
    @Column(name = "observer_role", nullable = false, length = 40)
    private String observerRole;

    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();
}
