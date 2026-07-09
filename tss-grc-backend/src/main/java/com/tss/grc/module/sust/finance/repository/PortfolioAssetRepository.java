package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.PortfolioAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Green-Finance G3 — org-scoped portfolio-asset finders. */
public interface PortfolioAssetRepository extends JpaRepository<PortfolioAsset, Long> {

    List<PortfolioAsset> findByOrganisationIdAndIsDeletedFalseOrderByAssetCodeAsc(Long organisationId);

    Optional<PortfolioAsset> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    boolean existsByOrganisationIdAndAssetCodeAndIsDeletedFalse(Long organisationId, String assetCode);
}
