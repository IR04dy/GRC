package com.tss.grc.module.opres.repository;

import com.tss.grc.module.opres.entity.OpResAssessmentCell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OpResAssessmentCellRepository extends JpaRepository<OpResAssessmentCell, Long> {

    List<OpResAssessmentCell> findByAssessmentIdAndOrganisationIdOrderByPillarId(
            Long assessmentId, Long organisationId);

    Optional<OpResAssessmentCell> findByAssessmentIdAndPillarIdAndOrganisationId(
            Long assessmentId, Long pillarId, Long organisationId);

    long countByAssessmentIdAndOrganisationIdAndCurrentScoreIsNotNull(
            Long assessmentId, Long organisationId);
}
