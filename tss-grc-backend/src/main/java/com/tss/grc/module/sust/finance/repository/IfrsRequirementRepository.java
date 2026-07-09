package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.IfrsRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** G4 — IFRS S1/S2 checklist finders. */
public interface IfrsRequirementRepository extends JpaRepository<IfrsRequirement, Long> {
    List<IfrsRequirement> findByOrganisationIdAndIsDeletedFalseOrderByReqCodeAsc(Long organisationId);
    Optional<IfrsRequirement> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);
}
