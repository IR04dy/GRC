package com.tss.grc.module.workflow.approval_chains.repository;

import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped repository for {@link ApprovalChainTemplate}.
 */
public interface ApprovalChainTemplateRepository extends JpaRepository<ApprovalChainTemplate, Long> {

    List<ApprovalChainTemplate> findByOrganisationIdAndIsDeletedFalseOrderByTemplateCodeAsc(Long organisationId);

    List<ApprovalChainTemplate> findByOrganisationIdAndAppliesToModuleAndIsDeletedFalseOrderByTemplateCodeAsc(
        Long organisationId, String appliesToModule);

    Optional<ApprovalChainTemplate> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    Optional<ApprovalChainTemplate> findByOrganisationIdAndTemplateCodeAndIsDeletedFalse(
        Long organisationId, String templateCode);

    boolean existsByOrganisationIdAndTemplateCodeAndIsDeletedFalse(
        Long organisationId, String templateCode);

    /** Default template for a given module scope (used when wizard doesn't pick a template). */
    Optional<ApprovalChainTemplate> findByOrganisationIdAndAppliesToModuleAndIsDefaultTrueAndIsDeletedFalse(
        Long organisationId, String appliesToModule);

    /** Used by set-default to clear other defaults in the same scope. */
    List<ApprovalChainTemplate> findByOrganisationIdAndAppliesToModuleAndIsDefaultTrueAndIsDeletedFalseAndIdNot(
        Long organisationId, String appliesToModule, Long id);

    long countByOrganisationIdAndIsDeletedFalse(Long organisationId);
}
