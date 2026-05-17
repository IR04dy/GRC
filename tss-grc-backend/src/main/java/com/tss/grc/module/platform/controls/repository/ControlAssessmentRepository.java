package com.tss.grc.module.platform.controls.repository;

import com.tss.grc.module.platform.controls.entity.ControlAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ControlAssessmentRepository extends JpaRepository<ControlAssessment, Long> {

    List<ControlAssessment> findByOrganisationIdAndControlIdOrderByAssessmentDateDesc(Long organisationId, Long controlId);

    List<ControlAssessment> findByOrganisationIdAndAssessmentCycleIdOrderByAssessmentDateDesc(Long organisationId, Long cycleId);

    List<ControlAssessment> findByOrganisationIdAndWorkflowStatusOrderByAssessmentDateDesc(Long organisationId, String workflowStatus);

    Optional<ControlAssessment> findByIdAndOrganisationId(Long id, Long organisationId);
}
