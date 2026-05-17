package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmAssessmentCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcmAssessmentCycleRepository extends JpaRepository<BcmAssessmentCycle, Long> {

    List<BcmAssessmentCycle> findByOrganisationIdOrderByReportingYearDesc(Long organisationId);

    Optional<BcmAssessmentCycle> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<BcmAssessmentCycle> findByOrganisationIdAndReportingYear(Long organisationId, Short reportingYear);

    List<BcmAssessmentCycle> findByOrganisationIdAndStatusOrderByReportingYearDesc(Long organisationId, String status);
}
