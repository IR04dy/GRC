package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.ExerciseScenarioInject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Slice 10-A — repository for {@link ExerciseScenarioInject}.
 *
 * <p>Injects are reference content (one set per scenario, shared across
 * all tenants — same model as {@link com.tss.grc.module.bcm.cycle.entity.BcmExerciseScenario}),
 * so there is no {@code organisationId} filter here. Tenant scoping is
 * enforced upstream at the parent scenario query.
 *
 * <p>Soft-deleted rows are hidden by the entity-level
 * {@code @Where(is_deleted = 0)} clause.
 */
public interface ExerciseScenarioInjectRepository
    extends JpaRepository<ExerciseScenarioInject, Long> {

    /**
     * Returns all injects for a scenario in play order — oldest
     * {@code inject_order} first. Used both by the detail page''s timeline
     * and by the scenario→exercise wizard when copying injects onto a
     * freshly instantiated exercise.
     */
    List<ExerciseScenarioInject> findByScenarioIdOrderByInjectOrderAsc(Long scenarioId);
}
