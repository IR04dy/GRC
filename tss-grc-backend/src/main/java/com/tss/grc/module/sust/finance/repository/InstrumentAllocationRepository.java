package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.InstrumentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Green-Finance G1 — org-scoped finders for the use-of-proceeds ledger. */
public interface InstrumentAllocationRepository extends JpaRepository<InstrumentAllocation, Long> {

    List<InstrumentAllocation> findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByAllocationDateAsc(
        Long organisationId, Long instrumentId);

    List<InstrumentAllocation> findByOrganisationIdAndProjectIdAndStatusAndIsDeletedFalse(
        Long organisationId, Long projectId, String status);

    Optional<InstrumentAllocation> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);
}
