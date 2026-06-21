package com.tss.grc.module.integrations.crowdstrike.repository;

import com.tss.grc.module.integrations.crowdstrike.entity.CrowdStrikeDetectionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CrowdStrikeDetectionLogRepository
        extends JpaRepository<CrowdStrikeDetectionLog, Long> {

    /** Fast-path dedupe — returns existing row if the same Falcon detection
     *  id was already ingested for this tenant. The DB-side unique index is
     *  the safety net behind this query. */
    Optional<CrowdStrikeDetectionLog> findByOrganisationIdAndFalconDetectionId(
        Long organisationId, String falconDetectionId);

    /** Latest detection ingested for this tenant — drives the /status
     *  endpoint's {@code lastSuccessfulPollAt}. */
    CrowdStrikeDetectionLog findFirstByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    /** Tenant-ownership probe for outbound containment actions — does this
     *  tenant have any ingested detection for the target Falcon host? Used to
     *  reject cross-tenant host containment (SEC-INT-003). */
    Optional<CrowdStrikeDetectionLog> findFirstByOrganisationIdAndFalconHostId(
        Long organisationId, String falconHostId);

    /** Browse history for the integrations dashboard. */
    Page<CrowdStrikeDetectionLog> findByOrganisationIdOrderByCreatedAtDesc(
        Long organisationId, Pageable pageable);

    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
}
