package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmExerciseScenario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcmExerciseScenarioRepository extends JpaRepository<BcmExerciseScenario, Long> {
    List<BcmExerciseScenario> findByScenarioCategoryOrderByTitleEnAsc(String category);
    List<BcmExerciseScenario> findByExerciseTypeOrderByTitleEnAsc(String type);
    List<BcmExerciseScenario> findAllByOrderByTitleEnAsc();
    Optional<BcmExerciseScenario> findByScenarioCode(String code);
}
