package com.tss.grc.module.opres.repository;

import com.tss.grc.module.opres.entity.OpResAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OpResAssessmentRepository extends JpaRepository<OpResAssessment, Long> {

    Page<OpResAssessment> findByOrganisationIdOrderByFiscalYearDescCreatedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<OpResAssessment> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    List<OpResAssessment> findByOrganisationIdAndFiscalYear(Long organisationId, Integer fiscalYear);
}
