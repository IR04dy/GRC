package com.tss.grc.module.audit_lc.repository;

import com.tss.grc.module.audit_lc.entity.AnnualPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnualPlanRepository extends JpaRepository<AnnualPlan, Long> {

    List<AnnualPlan> findByOrganisationIdOrderByYearDesc(Long org);

    Optional<AnnualPlan> findByOrganisationIdAndExternalId(Long org, String externalId);

    Optional<AnnualPlan> findByOrganisationIdAndYear(Long org, Integer year);
}
