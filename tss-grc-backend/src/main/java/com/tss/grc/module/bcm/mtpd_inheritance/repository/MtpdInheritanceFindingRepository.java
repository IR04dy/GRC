package com.tss.grc.module.bcm.mtpd_inheritance.repository;

import com.tss.grc.module.bcm.mtpd_inheritance.entity.MtpdInheritanceFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped repository for {@link MtpdInheritanceFinding} (Slice 10-E).
 */
public interface MtpdInheritanceFindingRepository extends JpaRepository<MtpdInheritanceFinding, Long> {

    List<MtpdInheritanceFinding> findByOrganisationIdAndAssessmentIdAndIsDeletedFalseOrderBySeverityAscBusinessProcessNameEnAsc(
        Long organisationId, Long assessmentId);

    List<MtpdInheritanceFinding> findByOrganisationIdAndAssessmentIdAndFindingTypeAndIsDeletedFalse(
        Long organisationId, Long assessmentId, String findingType);

    Optional<MtpdInheritanceFinding> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    /** Trend: all findings for one process across all cycles. */
    List<MtpdInheritanceFinding> findByOrganisationIdAndBusinessProcessIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, Long businessProcessId);

    long countByOrganisationIdAndAssessmentIdAndFindingTypeAndIsDeletedFalse(
        Long organisationId, Long assessmentId, String findingType);
}
