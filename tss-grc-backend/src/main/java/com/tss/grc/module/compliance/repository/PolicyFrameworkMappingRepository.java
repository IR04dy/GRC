package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.PolicyFrameworkMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyFrameworkMappingRepository extends JpaRepository<PolicyFrameworkMapping, Long> {
    List<PolicyFrameworkMapping> findByPolicyId(Long policyId);
    List<PolicyFrameworkMapping> findByFrameworkId(Long frameworkId);
    long countByFrameworkId(Long frameworkId);
}
