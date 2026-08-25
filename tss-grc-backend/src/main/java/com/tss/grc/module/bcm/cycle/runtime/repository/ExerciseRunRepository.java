package com.tss.grc.module.bcm.cycle.runtime.repository;

import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Slice 10-B — tenant-scoped repository for {@link ExerciseRun}.
 *
 * <p>Every finder is org-scoped — there is no method that returns rows
 * across tenants. Soft-deleted rows are hidden by the entity-level
 * {@code @Where(is_deleted = 0)} clause.
 */
public interface ExerciseRunRepository extends JpaRepository<ExerciseRun, Long> {

    Optional<ExerciseRun> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ExerciseRun> findByOrganisationIdAndRunCode(Long organisationId, String runCode);

    Page<ExerciseRun> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    List<ExerciseRun> findByOrganisationIdAndExerciseIdOrderByCreatedAtDesc(
        Long organisationId, Long exerciseId);

    List<ExerciseRun> findByOrganisationIdAndStatusOrderByCreatedAtDesc(
        Long organisationId, String status);

    long countByOrganisationIdAndExerciseId(Long organisationId, Long exerciseId);

    /**
     * The lexically-highest run code for one (tenant, exercise) — the MAX-seed source for
     * {@code generateRunCode}. F2-351: seeding from a COUNT re-mints a live ordinal after a gap
     * (a soft-deleted run lowers the count) or a concurrent create; a MAX never drops on a
     * mid-sequence delete. Respects {@code @Where(is_deleted = 0)}, so it agrees with the filtered
     * unique index {@code UX_exercise_run_run_code} — a soft-deleted code is free to be re-used.
     */
    Optional<ExerciseRun> findTopByOrganisationIdAndExerciseIdOrderByRunCodeDesc(
        Long organisationId, Long exerciseId);

    /** Skip-loop existence check for the run-code minter (agrees with the filtered unique index). */
    boolean existsByOrganisationIdAndRunCode(Long organisationId, String runCode);
}
