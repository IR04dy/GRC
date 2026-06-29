package com.tss.grc.module.policy.repository;

import com.tss.grc.module.policy.entity.PolicyDocumentControlLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * C-12 — policy ↔ control FK links ({@code policy.document_control_links}).
 * Org-scoped per the multi-tenant rule.
 */
@Repository
public interface PolicyDocumentControlLinkRepository extends JpaRepository<PolicyDocumentControlLink, Long> {

    List<PolicyDocumentControlLink> findByOrganisationIdAndPolicyDocumentId(Long organisationId, Long policyDocumentId);
}
