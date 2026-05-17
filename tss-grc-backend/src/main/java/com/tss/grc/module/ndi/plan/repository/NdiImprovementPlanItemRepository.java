package com.tss.grc.module.ndi.plan.repository;

import com.tss.grc.module.ndi.plan.entity.NdiImprovementPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiImprovementPlanItemRepository extends JpaRepository<NdiImprovementPlanItem, Long> {

    List<NdiImprovementPlanItem> findByOrganisationIdAndPlanIdOrderBySequenceOrderAscIdAsc(Long org, Long planId);

    Optional<NdiImprovementPlanItem> findByIdAndOrganisationId(Long id, Long org);

    Optional<NdiImprovementPlanItem> findByOrganisationIdAndPlanIdAndCapaId(Long org, Long planId, Long capaId);
}
