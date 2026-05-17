package com.tss.grc.module.ndi.evidence.repository;

import com.tss.grc.module.ndi.evidence.entity.NdiEvidenceItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NdiEvidenceItemRepository extends JpaRepository<NdiEvidenceItem, Long> {

    Page<NdiEvidenceItem> findByOrganisationIdOrderByCreatedAtDesc(Long org, Pageable p);

    Page<NdiEvidenceItem> findByOrganisationIdAndCycleIdOrderByCreatedAtDesc(Long org, Long cycleId, Pageable p);

    Page<NdiEvidenceItem> findByOrganisationIdAndEvidenceTypeOrderByCreatedAtDesc(Long org, String type, Pageable p);

    Page<NdiEvidenceItem> findByOrganisationIdAndStatusOrderByCreatedAtDesc(Long org, String status, Pageable p);

    Optional<NdiEvidenceItem> findByIdAndOrganisationId(Long id, Long org);

    List<NdiEvidenceItem> findByOrganisationIdAndCycleId(Long org, Long cycleId);

    /** ACTIVE rows whose expiry has passed — used by /check-expiry. */
    @Query("""
        select e from NdiEvidenceItem e
        where e.organisationId = :orgId
          and e.status = 'ACTIVE'
          and e.expiryDate is not null
          and e.expiryDate < :today
    """)
    List<NdiEvidenceItem> findExpiredActive(Long orgId, LocalDate today);

    /** ACTIVE rows expiring within the next N days (for the dashboard banner). */
    @Query("""
        select e from NdiEvidenceItem e
        where e.organisationId = :orgId
          and e.status = 'ACTIVE'
          and e.expiryDate is not null
          and e.expiryDate >= :today
          and e.expiryDate < :horizon
        order by e.expiryDate asc
    """)
    List<NdiEvidenceItem> findExpiringSoon(Long orgId, LocalDate today, LocalDate horizon);
}
