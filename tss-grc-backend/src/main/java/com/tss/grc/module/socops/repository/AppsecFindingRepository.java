package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.AppsecFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppsecFindingRepository extends JpaRepository<AppsecFinding, Long> {
    Optional<AppsecFinding> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<AppsecFinding> findByOrganisationIdAndScannerAndExternalId(
        Long organisationId, String scanner, String externalId);
    Page<AppsecFinding> findByOrganisationIdOrderByDiscoveredAtDesc(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
    long countByOrganisationIdAndScanType(Long organisationId, String scanType);

    /** Open findings whose SLA window has already lapsed. Status is anything
     *  other than the terminal verbs — the ingest service fires
     *  APPSEC_SLA_BREACHED for findings already breached at first sight; this
     *  sweep catches findings that breach over time. */
    @Query("""
        SELECT f FROM AppsecFinding f
        WHERE f.organisationId = :organisationId
          AND f.slaDueAt IS NOT NULL
          AND f.slaDueAt < :now
          AND f.status NOT IN ('CLOSED', 'WAIVED', 'FALSE_POSITIVE', 'FIXED')
        ORDER BY f.slaDueAt ASC
    """)
    List<AppsecFinding> findSlaBreached(Long organisationId, LocalDateTime now);
}
