package com.tss.grc.module.policy.repository;

import com.tss.grc.module.policy.entity.PolicyDocumentFrameworkMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Canonical policy-document ↔ framework mappings (CH-6 / PM-C1, V302). All queries
 * are org-scoped via the row's own {@code organisation_id} — no parent-policy join
 * needed. Consumed by FrameworkAnchorService (coverage + per-policy/-framework
 * views) and BoardPackService once Phase B repoints them off the legacy register.
 */
public interface PolicyDocumentFrameworkMappingRepository
        extends JpaRepository<PolicyDocumentFrameworkMapping, Long> {

    List<PolicyDocumentFrameworkMapping> findByDocumentId(Long documentId);

    List<PolicyDocumentFrameworkMapping> findByOrganisationIdAndFrameworkId(Long organisationId, Long frameworkId);

    List<PolicyDocumentFrameworkMapping> findByOrganisationId(Long organisationId);
}
