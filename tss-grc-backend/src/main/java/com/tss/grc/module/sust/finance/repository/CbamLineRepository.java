package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.CbamLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** G4 — CBAM export-line finders. */
public interface CbamLineRepository extends JpaRepository<CbamLine, Long> {
    List<CbamLine> findByOrganisationIdAndIsDeletedFalseOrderByPeriodYearDescPeriodQuarterDesc(Long organisationId);
}
