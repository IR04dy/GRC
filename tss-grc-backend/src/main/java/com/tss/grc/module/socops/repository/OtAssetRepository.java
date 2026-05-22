package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.OtAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtAssetRepository extends JpaRepository<OtAsset, Long> {
    Optional<OtAsset> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<OtAsset> findByOrganisationIdAndAssetCode(Long organisationId, String assetCode);
    Page<OtAsset> findByOrganisationIdOrderByPurdueLevelAscAssetCodeAsc(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndPurdueLevel(Long organisationId, Integer purdueLevel);
    long countByOrganisationIdAndCriticality(Long organisationId, String criticality);
    long countByOrganisationIdAndRemoteAccessEnabled(Long organisationId, Boolean remoteAccessEnabled);
}
