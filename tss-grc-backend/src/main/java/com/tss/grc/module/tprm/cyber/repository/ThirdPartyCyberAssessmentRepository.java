package com.tss.grc.module.tprm.cyber.repository;

import com.tss.grc.module.tprm.cyber.entity.ThirdPartyCyberAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThirdPartyCyberAssessmentRepository extends JpaRepository<ThirdPartyCyberAssessment, Long> {
    Page<ThirdPartyCyberAssessment> findByOrganisationIdOrderByResidualRiskTierDescIdDesc(Long org, Pageable p);
    Page<ThirdPartyCyberAssessment> findByOrganisationIdAndStatusOrderByResidualRiskTierDescIdDesc(Long org, String status, Pageable p);
    Optional<ThirdPartyCyberAssessment> findByIdAndOrganisationId(Long id, Long org);
}
