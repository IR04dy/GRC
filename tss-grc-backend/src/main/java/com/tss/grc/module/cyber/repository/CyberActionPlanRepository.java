package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberActionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CyberActionPlanRepository extends JpaRepository<CyberActionPlan, Long> {

    /** Tenant-scoped single-row lookup — prevents cross-tenant IDOR (SEC-CYB-001). */
    Optional<CyberActionPlan> findByIdAndOrganisationId(Long id, Long orgId);

    Page<CyberActionPlan> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberActionPlan> findAllByOrganisationId(Long orgId);

    Page<CyberActionPlan> findByOrganisationIdAndWorkflowStatus(Long orgId, String workflowStatus, Pageable pageable);
}
