package com.tss.grc.module.ai.governance.repository;

import com.tss.grc.module.ai.governance.entity.MlModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MlModelRepository extends JpaRepository<MlModel, Long> {

    List<MlModel> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    Optional<MlModel> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    Optional<MlModel> findByOrganisationIdAndModelCodeAndIsDeletedFalse(Long organisationId, String modelCode);

    long countByOrganisationIdAndModelCode(Long organisationId, String modelCode);

    List<MlModel> findByOrganisationIdAndLifecycleStageAndIsDeletedFalse(Long organisationId, String lifecycleStage);

    List<MlModel> findByOrganisationIdAndRiskTierAndIsDeletedFalse(Long organisationId, String riskTier);

    /**
     * High-risk models in production / monitoring that don't have a fairness audit
     * — these are the audit-committee top-of-list red flags.
     */
    @Query("""
        SELECT m FROM MlModel m
        WHERE m.organisationId = :orgId
          AND m.isDeleted = false
          AND m.riskTier IN ('CRITICAL', 'HIGH')
          AND m.lifecycleStage IN ('APPROVED_FOR_PROD', 'IN_PRODUCTION', 'MONITORING')
          AND m.hasFairnessAudit = false
        ORDER BY m.riskTier ASC, m.createdAt ASC
        """)
    List<MlModel> findHighRiskModelsWithoutFairnessAudit(@Param("orgId") Long orgId);
}
