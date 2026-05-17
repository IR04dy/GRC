package com.tss.grc.module.ndi.plan.repository;

import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiImprovementPlanRepository extends JpaRepository<NdiImprovementPlan, Long> {

    List<NdiImprovementPlan> findByOrganisationIdAndCycleIdOrderByPeriodStartDesc(Long org, Long cycleId);

    Optional<NdiImprovementPlan> findByIdAndOrganisationId(Long id, Long org);

    List<NdiImprovementPlan> findByOrganisationIdOrderByPeriodStartDesc(Long org);
}
