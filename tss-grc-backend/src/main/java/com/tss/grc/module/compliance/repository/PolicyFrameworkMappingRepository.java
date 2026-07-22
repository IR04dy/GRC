package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.PolicyFrameworkMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Legacy policy↔framework mappings (the {@code compliance.policies} register).
 * CH-6 moved framework coverage, the board-pack snapshot and the per-framework
 * policy list to the canonical {@code policy.document_framework_mappings}
 * (see PolicyDocumentFrameworkMappingRepository). The only remaining reader here
 * is {@code FrameworkAnchorService.mappingsForPolicy()} for the legacy
 * {@code /policies/:id} detail page, pending its repoint in CH-9. The CH-15
 * org-scoped coverage subquery was retired with that move.
 */
public interface PolicyFrameworkMappingRepository extends JpaRepository<PolicyFrameworkMapping, Long> {
    List<PolicyFrameworkMapping> findByPolicyId(Long policyId);
}
