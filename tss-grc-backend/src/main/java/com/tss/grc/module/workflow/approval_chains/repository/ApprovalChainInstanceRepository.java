package com.tss.grc.module.workflow.approval_chains.repository;

import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped repository for {@link ApprovalChainInstance}.
 */
public interface ApprovalChainInstanceRepository extends JpaRepository<ApprovalChainInstance, Long> {

    List<ApprovalChainInstance> findByOrganisationIdAndIsDeletedFalseOrderByStartedAtDesc(Long organisationId);

    List<ApprovalChainInstance> findByOrganisationIdAndStatusAndIsDeletedFalseOrderByStartedAtDesc(
        Long organisationId, String status);

    List<ApprovalChainInstance> findByOrganisationIdAndOriginatorUserIdAndIsDeletedFalseOrderByStartedAtDesc(
        Long organisationId, Long originatorUserId);

    Optional<ApprovalChainInstance> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    /**
     * Find all in-flight instances whose current step is older than
     * {@code cutoff} — driven by the {@code ApprovalChainSchedulerService}
     * to flag stale rungs for escalation. Org filter intentionally not
     * applied here — the scheduler operates across tenants.
     */
    @Query("SELECT i FROM ApprovalChainInstance i "
        + "WHERE i.isDeleted = false "
        + "  AND i.status IN ('PENDING', 'IN_PROGRESS') "
        + "  AND (i.updatedAt IS NULL OR i.updatedAt < :cutoff) "
        + "  AND i.startedAt < :cutoff "
        + "ORDER BY i.startedAt ASC")
    List<ApprovalChainInstance> findStaleInstances(@Param("cutoff") LocalDateTime cutoff);

    long countByOrganisationIdAndStatusAndIsDeletedFalse(Long organisationId, String status);
}
