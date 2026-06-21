package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberSubControl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CyberSubControlRepository extends JpaRepository<CyberSubControl, Long> {

    /** Tenant-scoped single-row lookup — prevents cross-tenant IDOR (SEC-CYB-001). */
    Optional<CyberSubControl> findByIdAndOrganisationId(Long id, Long orgId);

    Page<CyberSubControl> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberSubControl> findAllByOrganisationId(Long orgId);

    Page<CyberSubControl> findByOrganisationIdAndControlId(Long orgId, Long controlId, Pageable pageable);

    List<CyberSubControl> findAllByOrganisationIdAndControlId(Long orgId, Long controlId);
}
