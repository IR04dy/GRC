package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.EnvPermit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** G4 — NCEC permit finders. */
public interface EnvPermitRepository extends JpaRepository<EnvPermit, Long> {
    List<EnvPermit> findByOrganisationIdAndIsDeletedFalseOrderByExpiryDateAsc(Long organisationId);
    Optional<EnvPermit> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);
    boolean existsByOrganisationIdAndPermitNumberAndIsDeletedFalse(Long organisationId, String permitNumber);
}
