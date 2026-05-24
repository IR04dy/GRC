package com.tss.grc.module.workflow.approval_chains.repository;

import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped repository for {@link ApprovalChainStep}.
 */
public interface ApprovalChainStepRepository extends JpaRepository<ApprovalChainStep, Long> {

    List<ApprovalChainStep> findByOrganisationIdAndTemplateIdAndIsDeletedFalseOrderByStepOrderAscIdAsc(
        Long organisationId, Long templateId);

    List<ApprovalChainStep> findByOrganisationIdAndTemplateIdAndStepOrderAndIsDeletedFalse(
        Long organisationId, Long templateId, Integer stepOrder);

    Optional<ApprovalChainStep> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    long countByOrganisationIdAndTemplateIdAndIsDeletedFalse(Long organisationId, Long templateId);
}
