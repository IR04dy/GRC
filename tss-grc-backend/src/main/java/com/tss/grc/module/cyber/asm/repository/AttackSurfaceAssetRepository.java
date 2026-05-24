package com.tss.grc.module.cyber.asm.repository;

import com.tss.grc.module.cyber.asm.entity.AttackSurfaceAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Org-scoped finders for {@link AttackSurfaceAsset}.
 *
 * <p>Every read filters by {@code organisation_id} — the
 * {@code @Where(is_deleted = 0)} clause on the entity layers in the
 * soft-delete filter automatically.
 */
public interface AttackSurfaceAssetRepository extends JpaRepository<AttackSurfaceAsset, Long> {

    List<AttackSurfaceAsset> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    Optional<AttackSurfaceAsset> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    Optional<AttackSurfaceAsset> findByOrganisationIdAndAssetCodeAndIsDeletedFalse(Long organisationId, String assetCode);

    long countByOrganisationIdAndAssetCode(Long organisationId, String assetCode);

    List<AttackSurfaceAsset> findByOrganisationIdAndLifecycleStageAndIsDeletedFalse(Long organisationId, String lifecycleStage);

    long countByOrganisationIdAndExposureAndCriticalityAndIsDeletedFalse(Long organisationId, String exposure, String criticality);
}
