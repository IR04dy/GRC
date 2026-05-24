package com.tss.grc.module.compliance.obligation.repository;

import com.tss.grc.module.compliance.obligation.entity.Obligation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link Obligation}.
 *
 * <p>Every finder takes {@code organisationId} explicitly per the
 * platform's tenant-scope contract (CLAUDE.md). Soft-deleted rows are
 * hidden by the entity's {@code @Where} clause.
 */
public interface ObligationRepository extends JpaRepository<Obligation, Long> {

    List<Obligation> findByOrganisationIdAndIsDeletedFalseOrderByNextDueDateAsc(Long organisationId);

    List<Obligation> findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByNextDueDateAsc(
        Long organisationId, String lifecycleStage);

    List<Obligation> findByOrganisationIdAndRegulatorAndIsDeletedFalseOrderByNextDueDateAsc(
        Long organisationId, String regulator);

    List<Obligation> findByOrganisationIdAndOwnerUserIdAndIsDeletedFalseOrderByNextDueDateAsc(
        Long organisationId, Long ownerUserId);

    /** Used by the campaign materialiser — optionally filter by regulator and/or frequency. */
    List<Obligation> findByOrganisationIdAndLifecycleStageAndRegulatorAndIsDeletedFalse(
        Long organisationId, String lifecycleStage, String regulator);

    List<Obligation> findByOrganisationIdAndLifecycleStageAndFrequencyAndIsDeletedFalse(
        Long organisationId, String lifecycleStage, String frequency);

    List<Obligation> findByOrganisationIdAndLifecycleStageAndRegulatorAndFrequencyAndIsDeletedFalse(
        Long organisationId, String lifecycleStage, String regulator, String frequency);

    Optional<Obligation> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    Optional<Obligation> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
        Long organisationId, UUID externalId);

    boolean existsByOrganisationIdAndObligationCodeAndIsDeletedFalse(
        Long organisationId, String obligationCode);

    /**
     * "What's due in the next N days?" — drives the register's KPI tile and
     * the homepage attention block.
     */
    @Query("SELECT o FROM Obligation o "
        + "WHERE o.organisationId = :orgId "
        + "  AND o.isDeleted = false "
        + "  AND o.lifecycleStage = 'ACTIVE' "
        + "  AND o.nextDueDate BETWEEN :from AND :to "
        + "ORDER BY o.nextDueDate ASC")
    List<Obligation> findDueWithinDays(@Param("orgId") Long orgId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /**
     * "What is owner X already late on?" — drives the per-owner queue and
     * the F8 OVERDUE escalation rule.
     */
    @Query("SELECT o FROM Obligation o "
        + "WHERE o.organisationId = :orgId "
        + "  AND o.ownerUserId = :ownerId "
        + "  AND o.isDeleted = false "
        + "  AND o.lifecycleStage = 'ACTIVE' "
        + "  AND o.nextDueDate < :today "
        + "ORDER BY o.nextDueDate ASC")
    List<Obligation> findOverdueByOwner(@Param("orgId") Long orgId,
                                         @Param("ownerId") Long ownerUserId,
                                         @Param("today") LocalDate today);

    long countByOrganisationIdAndLifecycleStageAndIsDeletedFalse(Long organisationId, String lifecycleStage);
}
