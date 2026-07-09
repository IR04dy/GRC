package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.EsgTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** G4 — org-scoped ESG target finders. */
public interface EsgTargetRepository extends JpaRepository<EsgTarget, Long> {
    List<EsgTarget> findByOrganisationIdAndIsDeletedFalseOrderByTargetCodeAsc(Long organisationId);
    Optional<EsgTarget> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);
    boolean existsByOrganisationIdAndTargetCodeAndIsDeletedFalse(Long organisationId, String targetCode);
}
