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

    /**
     * High-risk models in production / monitoring that lack a signed-off
     * AI Impact Assessment (a PASSED/PASSED_WITH_CONDITIONS/REMEDIATED 'AIA'
     * audit). SDAIA expects an AIA for high-risk AI — these are the
     * red-flag models the approval gate would now block.
     */
    @Query("""
        SELECT m FROM MlModel m
        WHERE m.organisationId = :orgId
          AND m.isDeleted = false
          AND m.riskTier IN ('CRITICAL', 'HIGH')
          AND m.lifecycleStage IN ('APPROVED_FOR_PROD', 'IN_PRODUCTION', 'MONITORING')
          AND NOT EXISTS (
              SELECT 1 FROM MlModelAudit a
              WHERE a.modelId = m.id
                AND a.isDeleted = false
                AND a.auditType = 'AIA'
                AND a.status IN ('PASSED', 'PASSED_WITH_CONDITIONS', 'REMEDIATED'))
        ORDER BY m.riskTier ASC, m.createdAt ASC
        """)
    List<MlModel> findHighRiskModelsWithoutSignedOffAia(@Param("orgId") Long orgId);
}
