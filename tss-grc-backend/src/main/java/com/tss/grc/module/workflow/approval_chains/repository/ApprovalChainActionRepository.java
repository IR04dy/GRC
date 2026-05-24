package com.tss.grc.module.workflow.approval_chains.repository;

import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Tenant-scoped repository for {@link ApprovalChainAction}.
 */
public interface ApprovalChainActionRepository extends JpaRepository<ApprovalChainAction, Long> {

    List<ApprovalChainAction> findByOrganisationIdAndInstanceIdAndIsDeletedFalseOrderByStepOrderAscDecidedAtAsc(
        Long organisationId, Long instanceId);

    List<ApprovalChainAction> findByOrganisationIdAndInstanceIdAndStepOrderAndIsDeletedFalse(
        Long organisationId, Long instanceId, Integer stepOrder);

    List<ApprovalChainAction> findByOrganisationIdAndActorUserIdAndIsDeletedFalseOrderByDecidedAtDesc(
        Long organisationId, Long actorUserId);
}
