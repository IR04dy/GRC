package com.tss.grc.module.ndi.compliance.repository;

import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiComplianceAssessmentRepository extends JpaRepository<NdiComplianceAssessment, Long> {

    Page<NdiComplianceAssessment> findByOrganisationIdAndCycleIdOrderByDomainAscIdAsc(Long org, Long cycleId, Pageable p);

    Page<NdiComplianceAssessment> findByOrganisationIdAndCycleIdAndComplianceStatusOrderByDomainAscIdAsc(
        Long org, Long cycleId, String complianceStatus, Pageable p);

    Page<NdiComplianceAssessment> findByOrganisationIdAndCycleIdAndDomainOrderByIdAsc(
        Long org, Long cycleId, String domain, Pageable p);

    Optional<NdiComplianceAssessment> findByIdAndOrganisationId(Long id, Long org);

    Optional<NdiComplianceAssessment> findByOrganisationIdAndCycleIdAndFrameworkItemId(
        Long org, Long cycleId, Long frameworkItemId);

    List<NdiComplianceAssessment> findByOrganisationIdAndCycleId(Long org, Long cycleId);
}
