package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberFramework;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CyberFrameworkRepository extends JpaRepository<CyberFramework, Long> {

    /** Tenant-scoped single-row lookup — prevents cross-tenant IDOR (SEC-CYB-001). */
    Optional<CyberFramework> findByIdAndOrganisationId(Long id, Long orgId);

    Page<CyberFramework> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberFramework> findAllByOrganisationId(Long orgId);
}
