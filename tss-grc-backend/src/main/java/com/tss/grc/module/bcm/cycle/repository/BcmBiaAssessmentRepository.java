package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmBiaAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcmBiaAssessmentRepository extends JpaRepository<BcmBiaAssessment, Long> {

    Page<BcmBiaAssessment> findByOrganisationIdOrderByIdDesc(Long organisationId, Pageable pageable);

    Optional<BcmBiaAssessment> findByIdAndOrganisationId(Long id, Long organisationId);

    List<BcmBiaAssessment> findByOrganisationIdAndCycleIdOrderByIdDesc(Long organisationId, Long cycleId);

    List<BcmBiaAssessment> findByOrganisationIdAndServiceIdOrderByIdDesc(Long organisationId, Long serviceId);

    List<BcmBiaAssessment> findByOrganisationIdAndProcessIdOrderByIdDesc(Long organisationId, Long processId);

    List<BcmBiaAssessment> findByOrganisationIdAndApprovalStatusOrderByIdDesc(Long organisationId, String approvalStatus);

    long countByOrganisationIdAndApprovalStatus(Long organisationId, String approvalStatus);

    long countByOrganisationIdAndOverallCriticality(Long organisationId, String overallCriticality);
}
