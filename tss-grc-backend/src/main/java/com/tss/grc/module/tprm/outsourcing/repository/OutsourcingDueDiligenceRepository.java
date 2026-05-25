package com.tss.grc.module.tprm.outsourcing.repository;

import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingDueDiligence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutsourcingDueDiligenceRepository extends JpaRepository<OutsourcingDueDiligence, Long> {

    List<OutsourcingDueDiligence> findByOrganisationIdAndArrangementIdAndIsDeletedFalseOrderByAssessmentDateDesc(
        Long organisationId, Long arrangementId);

    Optional<OutsourcingDueDiligence> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    List<OutsourcingDueDiligence> findByOrganisationIdAndIsDeletedFalseOrderByAssessmentDateDesc(Long organisationId);

    long countByOrganisationIdAndArrangementIdAndIsDeletedFalse(Long organisationId, Long arrangementId);
}
