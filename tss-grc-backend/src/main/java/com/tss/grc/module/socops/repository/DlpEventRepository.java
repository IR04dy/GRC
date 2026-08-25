package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.DlpEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DlpEventRepository extends JpaRepository<DlpEvent, Long> {
    Optional<DlpEvent> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<DlpEvent> findByOrganisationIdAndSourceSystemAndExternalId(
        Long organisationId, String sourceSystem, String externalId);
    Page<DlpEvent> findByOrganisationIdOrderByEventTimeDesc(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndReviewed(Long organisationId, Boolean reviewed);
    long countByOrganisationIdAndActionTaken(Long organisationId, String actionTaken);
    long countByOrganisationIdAndDataClassification(Long organisationId, String dataClassification);

    /** CRITICAL-severity events that have sat unreviewed past {@code cutoff}.
     *  Drives the sweep fire of {@code DLP_CRITICAL_UNREVIEWED} — the ingest
     *  service fires it once on landing, this catches the queue that builds
     *  if no analyst acts. */
    @Query("""
        SELECT d FROM DlpEvent d
        WHERE d.organisationId = :organisationId
          AND d.severity = 'CRITICAL'
          AND d.reviewed = false
          AND d.eventTime <= :cutoff
        ORDER BY d.eventTime ASC
    """)
    List<DlpEvent> findCriticalUnreviewedBefore(Long organisationId, LocalDateTime cutoff);

    /** F2-376 — SOC-ops sweep tenant discovery: a tenant that uses this surface but has
     *  never ingested a SecurityEvent must still be swept for its escalations. */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT e.organisationId FROM DlpEvent e")
    java.util.List<Long> findDistinctOrganisationIds();
}
