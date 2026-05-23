package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.StrategyRoiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link StrategyRoiAnalysis} (Slice 9-D).
 *
 * <p>Every finder takes {@code organisationId} explicitly — the platform
 * does not run a global Hibernate tenant filter (per CLAUDE.md). Soft-
 * deleted rows are already hidden by the entity's
 * {@code @Where(is_deleted = 0)} clause.
 */
public interface StrategyRoiAnalysisRepository extends JpaRepository<StrategyRoiAnalysis, Long> {

    List<StrategyRoiAnalysis> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    List<StrategyRoiAnalysis> findByOrganisationIdAndBiaQuantitativeIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, Long biaQuantitativeId);

    List<StrategyRoiAnalysis> findByOrganisationIdAndRecommendationAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, String recommendation);

    Optional<StrategyRoiAnalysis> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
        Long organisationId, UUID externalId);

    Optional<StrategyRoiAnalysis> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    List<StrategyRoiAnalysis> findByOrganisationIdAndIdInAndIsDeletedFalse(
        Long organisationId, List<Long> ids);

    boolean existsByOrganisationIdAndAnalysisCodeAndIsDeletedFalse(Long organisationId, String analysisCode);
}
