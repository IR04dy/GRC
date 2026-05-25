package com.tss.grc.module.tprm.outsourcing.repository;

import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingArrangement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped repository for the outsourcing arrangement register
 * (Sprint 13). Soft-deleted rows are filtered by the entity-level
 * {@code @Where(is_deleted = 0)} clause; every finder still passes
 * {@code organisationId} explicitly because the platform deliberately
 * does NOT run a global Hibernate tenant filter (see CLAUDE.md).
 */
public interface OutsourcingArrangementRepository extends JpaRepository<OutsourcingArrangement, Long> {

    List<OutsourcingArrangement> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    List<OutsourcingArrangement> findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, String lifecycleStage);

    List<OutsourcingArrangement> findByOrganisationIdAndIsMaterialAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, Boolean isMaterial);

    List<OutsourcingArrangement> findByOrganisationIdAndCriticalFunctionAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, String criticalFunction);

    Optional<OutsourcingArrangement> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    boolean existsByOrganisationIdAndArrangementCodeAndIsDeletedFalse(
        Long organisationId, String arrangementCode);

    long countByOrganisationIdAndLifecycleStageAndIsDeletedFalse(Long organisationId, String lifecycleStage);

    long countByOrganisationIdAndIsMaterialAndIsDeletedFalse(Long organisationId, Boolean isMaterial);

    @Query("""
        SELECT o FROM OutsourcingArrangement o
         WHERE o.organisationId = :orgId
           AND o.isMaterial = true
           AND o.nextReviewDueDate IS NOT NULL
           AND o.nextReviewDueDate <= :asOf
           AND o.lifecycleStage NOT IN ('EXITED','EXIT_IN_PROGRESS')
        ORDER BY o.nextReviewDueDate ASC
        """)
    List<OutsourcingArrangement> findMaterialOverdueForReview(@Param("orgId") Long orgId,
                                                                @Param("asOf") LocalDate asOf);

    @Query("""
        SELECT o FROM OutsourcingArrangement o
         WHERE o.organisationId = :orgId
           AND o.isMaterial = true
           AND o.samaNonObjectionRequired = true
           AND o.samaNonObjectionObtained = false
           AND o.lifecycleStage IN ('PLANNED','DUE_DILIGENCE','AWAITING_SAMA')
        ORDER BY o.createdAt ASC
        """)
    List<OutsourcingArrangement> findRequiringSamaNonObjection(@Param("orgId") Long orgId);
}
