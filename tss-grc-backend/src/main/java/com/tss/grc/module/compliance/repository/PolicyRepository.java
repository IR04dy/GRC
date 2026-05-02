package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.Policy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    Page<Policy> findByOrganisationId(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
