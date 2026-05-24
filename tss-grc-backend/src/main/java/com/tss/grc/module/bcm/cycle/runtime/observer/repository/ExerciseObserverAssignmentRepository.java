package com.tss.grc.module.bcm.cycle.runtime.observer.repository;

import com.tss.grc.module.bcm.cycle.runtime.observer.entity.ExerciseObserverAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Slice 10-C — tenant-scoped repository for {@link ExerciseObserverAssignment}.
 *
 * <p>Every finder is org-scoped; soft-deleted rows are hidden by the
 * entity-level {@code @Where(is_deleted = 0)} clause.
 */
public interface ExerciseObserverAssignmentRepository
        extends JpaRepository<ExerciseObserverAssignment, Long> {

    Optional<ExerciseObserverAssignment> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Looks up the active assignment for (run, observer) — used by the access gate. */
    Optional<ExerciseObserverAssignment>
        findByOrganisationIdAndExerciseRunIdAndObserverUserId(
            Long organisationId, Long exerciseRunId, Long observerUserId);

    /** Observer roster for a run (sorted by assignment time). */
    List<ExerciseObserverAssignment>
        findByOrganisationIdAndExerciseRunIdOrderByAssignedAtAsc(
            Long organisationId, Long exerciseRunId);

    /** All runs a given user is observing — used for the observer's run list. */
    List<ExerciseObserverAssignment>
        findByOrganisationIdAndObserverUserIdOrderByAssignedAtDesc(
            Long organisationId, Long observerUserId);

    long countByOrganisationIdAndExerciseRunId(Long organisationId, Long exerciseRunId);
}
