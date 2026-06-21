package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CyberIncidentRepository extends JpaRepository<CyberIncident, Long> {

    /** Tenant-scoped single-row lookup — prevents cross-tenant IDOR (SEC-CYB-001). */
    Optional<CyberIncident> findByIdAndOrganisationId(Long id, Long orgId);

    Page<CyberIncident> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberIncident> findAllByOrganisationId(Long orgId);

    Page<CyberIncident> findByOrganisationIdAndWorkflowStatus(Long orgId, String workflowStatus, Pageable pageable);
}
