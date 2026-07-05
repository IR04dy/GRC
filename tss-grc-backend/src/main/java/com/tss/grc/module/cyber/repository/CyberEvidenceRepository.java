package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberEvidence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CyberEvidenceRepository extends JpaRepository<CyberEvidence, Long> {

    /** Tenant-scoped single-row lookup — prevents cross-tenant IDOR (SEC-CYB-001). */
    Optional<CyberEvidence> findByIdAndOrganisationId(Long id, Long orgId);

    Page<CyberEvidence> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberEvidence> findAllByOrganisationId(Long orgId);

    /** CY-J expiry sweep — live evidence (still CURRENT/PENDING) whose expiry_date has passed.
     *  Cross-tenant (the scheduler runs system-wide); @Where already filters soft-deleted rows.
     *  A null expiry_date never matches an {@code <} comparison, so undated rows are ignored. */
    List<CyberEvidence> findByStatusInAndExpiryDateBefore(Collection<String> statuses, LocalDate date);
}
