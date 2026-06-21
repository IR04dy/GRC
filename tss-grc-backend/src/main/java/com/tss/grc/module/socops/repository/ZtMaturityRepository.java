package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.ZtMaturity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZtMaturityRepository extends JpaRepository<ZtMaturity, Long> {

    Optional<ZtMaturity> findByIdAndOrganisationId(Long id, Long organisationId);

    List<ZtMaturity> findByOrganisationIdAndAssessmentCode(Long organisationId, String assessmentCode);

    List<ZtMaturity> findByOrganisationIdOrderByAssessmentCodeDescPillarAsc(Long organisationId);
}
