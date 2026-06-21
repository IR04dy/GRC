package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberControl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CyberControlRepository extends JpaRepository<CyberControl, Long> {

    /** Tenant-scoped single-row lookup — prevents cross-tenant IDOR (SEC-CYB-001). */
    Optional<CyberControl> findByIdAndOrganisationId(Long id, Long orgId);

    Page<CyberControl> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberControl> findAllByOrganisationId(Long orgId);

    Page<CyberControl> findByOrganisationIdAndFrameworkId(Long orgId, Long frameworkId, Pageable pageable);
}
