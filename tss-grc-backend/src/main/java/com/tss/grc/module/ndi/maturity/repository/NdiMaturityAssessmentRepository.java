package com.tss.grc.module.ndi.maturity.repository;

import com.tss.grc.module.ndi.maturity.entity.NdiMaturityAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiMaturityAssessmentRepository extends JpaRepository<NdiMaturityAssessment, Long> {

    Page<NdiMaturityAssessment> findByOrganisationIdAndCycleIdOrderByDomainAscIdAsc(Long org, Long cycleId, Pageable p);

    Page<NdiMaturityAssessment> findByOrganisationIdAndCycleIdAndStatusOrderByDomainAscIdAsc(
        Long org, Long cycleId, String status, Pageable p);

    Page<NdiMaturityAssessment> findByOrganisationIdAndCycleIdAndDomainOrderByIdAsc(
        Long org, Long cycleId, String domain, Pageable p);

    Optional<NdiMaturityAssessment> findByIdAndOrganisationId(Long id, Long org);

    Optional<NdiMaturityAssessment> findByOrganisationIdAndCycleIdAndFrameworkItemId(
        Long org, Long cycleId, Long frameworkItemId);

    List<NdiMaturityAssessment> findByOrganisationIdAndCycleId(Long org, Long cycleId);
}
