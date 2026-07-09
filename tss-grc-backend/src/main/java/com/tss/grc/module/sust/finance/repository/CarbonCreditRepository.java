package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.CarbonCredit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Green-Finance G3 — org-scoped carbon-credit finders. */
public interface CarbonCreditRepository extends JpaRepository<CarbonCredit, Long> {

    List<CarbonCredit> findByOrganisationIdAndIsDeletedFalseOrderByVintageYearDesc(Long organisationId);

    Optional<CarbonCredit> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);
}
