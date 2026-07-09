package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.EsgTargetActual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** G4 — annual actuals per target. */
public interface EsgTargetActualRepository extends JpaRepository<EsgTargetActual, Long> {
    List<EsgTargetActual> findByOrganisationIdAndTargetIdAndIsDeletedFalseOrderByYearAsc(Long organisationId, Long targetId);
    boolean existsByOrganisationIdAndTargetIdAndYearAndIsDeletedFalse(Long organisationId, Long targetId, Integer year);
}
