package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.VulnException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VulnExceptionRepository extends JpaRepository<VulnException, Long> {

    /** Tenant-scoped single-row lookup — prevents cross-tenant IDOR (SEC-CYB-001). */
    Optional<VulnException> findByIdAndOrganisationId(Long id, Long orgId);

    Page<VulnException> findByOrganisationId(Long orgId, Pageable pageable);

    List<VulnException> findAllByOrganisationId(Long orgId);
}
