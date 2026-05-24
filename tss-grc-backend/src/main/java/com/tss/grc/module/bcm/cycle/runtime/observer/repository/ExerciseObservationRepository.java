package com.tss.grc.module.bcm.cycle.runtime.observer.repository;

import com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Slice 10-C — tenant-scoped repository for {@link ExerciseObservation}.
 *
 * <p>Two main query shapes:
 * <ul>
 *   <li>By exercise_run_id ordered by recorded_at — chronological feed shown
 *       on the observer dashboard.</li>
 *   <li>By exercise_run_id + observation_type — grouped view for end-of-run
 *       scoring (STRENGTH / GAP / RISK / …).</li>
 * </ul>
 */
public interface ExerciseObservationRepository extends JpaRepository<ExerciseObservation, Long> {

    Optional<ExerciseObservation> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Chronological feed (oldest first) — drives the observer dashboard right column. */
    List<ExerciseObservation>
        findByOrganisationIdAndExerciseRunIdOrderByRecordedAtAsc(
            Long organisationId, Long exerciseRunId);

    /** Type-grouped view — drives the end-of-run scoring screen. */
    List<ExerciseObservation>
        findByOrganisationIdAndExerciseRunIdAndObservationTypeOrderByRecordedAtAsc(
            Long organisationId, Long exerciseRunId, String observationType);

    /** Drives the "my observations only" filter on the dashboard. */
    List<ExerciseObservation>
        findByOrganisationIdAndExerciseRunIdAndObserverUserIdOrderByRecordedAtAsc(
            Long organisationId, Long exerciseRunId, Long observerUserId);

    /** Pulled by ExerciseRuntimeService.endRun() into the AAR draft. */
    List<ExerciseObservation>
        findByOrganisationIdAndExerciseRunIdAndIsFlaggedForAarOrderByRecordedAtAsc(
            Long organisationId, Long exerciseRunId, Boolean isFlaggedForAar);

    long countByOrganisationIdAndExerciseRunId(Long organisationId, Long exerciseRunId);
}
