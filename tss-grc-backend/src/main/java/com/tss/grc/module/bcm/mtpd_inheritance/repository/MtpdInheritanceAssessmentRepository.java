package com.tss.grc.module.bcm.mtpd_inheritance.repository;

import com.tss.grc.module.bcm.mtpd_inheritance.entity.MtpdInheritanceAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped repository for {@link MtpdInheritanceAssessment} (Slice 10-E).
 */
public interface MtpdInheritanceAssessmentRepository extends JpaRepository<MtpdInheritanceAssessment, Long> {

    List<MtpdInheritanceAssessment> findByOrganisationIdAndIsDeletedFalseOrderByComputedAtDesc(Long organisationId);

    Optional<MtpdInheritanceAssessment> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    Optional<MtpdInheritanceAssessment> findByOrganisationIdAndAssessmentCodeAndIsDeletedFalse(
        Long organisationId, String assessmentCode);

    long countByOrganisationIdAndCycleYearAndIsDeletedFalse(Long organisationId, Integer cycleYear);
}
