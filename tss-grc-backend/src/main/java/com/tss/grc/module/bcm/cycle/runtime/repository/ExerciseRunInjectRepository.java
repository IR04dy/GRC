package com.tss.grc.module.bcm.cycle.runtime.repository;

import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRunInject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Slice 10-B — tenant-scoped repository for {@link ExerciseRunInject}.
 *
 * <p>Run-injects are queried in two shapes:
 * <ul>
 *   <li>By exercise_run_id ordered by planned_time_offset_minutes — for the
 *       facilitator console's left-column queue.</li>
 *   <li>By exercise_run_id ordered by actual_dispatched_at — for the
 *       after-action timeline (chronological order).</li>
 * </ul>
 */
public interface ExerciseRunInjectRepository extends JpaRepository<ExerciseRunInject, Long> {

    Optional<ExerciseRunInject> findByIdAndOrganisationId(Long id, Long organisationId);

    List<ExerciseRunInject> findByOrganisationIdAndExerciseRunIdOrderByPlannedTimeOffsetMinutesAsc(
        Long organisationId, Long exerciseRunId);

    List<ExerciseRunInject> findByOrganisationIdAndExerciseRunIdAndStatusOrderByPlannedTimeOffsetMinutesAsc(
        Long organisationId, Long exerciseRunId, String status);

    long countByOrganisationIdAndExerciseRunIdAndStatus(
        Long organisationId, Long exerciseRunId, String status);
}
