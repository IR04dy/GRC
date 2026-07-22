package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.PolicyFrameworkMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PolicyFrameworkMappingRepository extends JpaRepository<PolicyFrameworkMapping, Long> {
    List<PolicyFrameworkMapping> findByPolicyId(Long policyId);
    List<PolicyFrameworkMapping> findByFrameworkId(Long frameworkId);

    /**
     * CH-15: org-scoped variant of {@link #findByFrameworkId(Long)}. The
     * {@code policy_framework_mappings} table carries no {@code organisation_id},
     * so tenant scope is derived from the parent policy. Coverage tallies and the
     * board-pack framework snapshot MUST use this method — {@code findByFrameworkId}
     * returns every tenant's mappings for the framework and leaks their counts.
     */
    @Query("SELECT m FROM PolicyFrameworkMapping m WHERE m.frameworkId = :frameworkId " +
           "AND m.policyId IN (SELECT p.id FROM Policy p WHERE p.organisationId = :org)")
    List<PolicyFrameworkMapping> findByFrameworkIdAndPolicyOrg(@Param("frameworkId") Long frameworkId,
                                                               @Param("org") Long org);
}
