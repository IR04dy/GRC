package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.BcmExercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BcmExerciseRepository extends JpaRepository<BcmExercise, Long> {
    Page<BcmExercise> findByOrganisationId(Long organisationId, Pageable pageable);
}
