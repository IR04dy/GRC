package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmLessonLearned;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcmLessonLearnedRepository extends JpaRepository<BcmLessonLearned, Long> {
    List<BcmLessonLearned> findByOrganisationIdOrderByIdDesc(Long organisationId);
    List<BcmLessonLearned> findByOrganisationIdAndStatusOrderByIdDesc(Long organisationId, String status);
    List<BcmLessonLearned> findByOrganisationIdAndSourceTypeOrderByIdDesc(Long organisationId, String sourceType);
    Optional<BcmLessonLearned> findByIdAndOrganisationId(Long id, Long organisationId);
}
