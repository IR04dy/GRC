package com.tss.grc.module.cyber.asm.repository;

import com.tss.grc.module.cyber.asm.entity.AttackSurfaceFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttackSurfaceFindingRepository extends JpaRepository<AttackSurfaceFinding, Long> {

    List<AttackSurfaceFinding> findByOrganisationIdAndIsDeletedFalseOrderByOpenedAtDesc(Long organisationId);

    List<AttackSurfaceFinding> findByOrganisationIdAndAssetIdAndIsDeletedFalseOrderByOpenedAtDesc(Long organisationId, Long assetId);

    Optional<AttackSurfaceFinding> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    /** Open findings (i.e. NOT in a terminal state) for a single asset — used by risk-score recompute. */
    List<AttackSurfaceFinding> findByOrganisationIdAndAssetIdAndStatusInAndIsDeletedFalse(Long organisationId, Long assetId, List<String> statuses);

    long countByOrganisationIdAndStatusAndIsDeletedFalse(Long organisationId, String status);
}
