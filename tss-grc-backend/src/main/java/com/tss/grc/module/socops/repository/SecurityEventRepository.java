package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    Optional<SecurityEvent> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<SecurityEvent> findByOrganisationIdAndSourceSystemAndExternalId(
        Long organisationId, String sourceSystem, String externalId);

    Page<SecurityEvent> findByOrganisationIdOrderByEventTimeDesc(Long organisationId, Pageable pageable);

    List<SecurityEvent> findByOrganisationIdAndStatusOrderByEventTimeDesc(Long organisationId, String status);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndSeverity(Long organisationId, String severity);

    @Query("""
        SELECT e FROM SecurityEvent e
        WHERE e.organisationId = :organisationId
          AND e.status NOT IN ('RESOLVED', 'FALSE_POSITIVE')
          AND e.slaDueAt IS NOT NULL
          AND e.slaDueAt <= :threshold
        ORDER BY e.slaDueAt ASC
    """)
    List<SecurityEvent> findApproachingSlaBreach(Long organisationId, LocalDateTime threshold);

    /** Compute mean-time-to-resolve in seconds over events resolved in the
     *  trailing window. SQL Server's DATEDIFF returns seconds; AVG returns
     *  a double. Used by the SOC ops dashboard. */
    @Query(value = """
        SELECT AVG(CAST(DATEDIFF(SECOND, event_time, resolved_at) AS BIGINT))
        FROM socops.security_event
        WHERE organisation_id = :organisationId
          AND is_deleted = 0
          AND status = 'RESOLVED'
          AND resolved_at IS NOT NULL
          AND resolved_at >= :since
    """, nativeQuery = true)
    Double meanTimeToResolveSeconds(Long organisationId, LocalDateTime since);

    @Query(value = """
        SELECT AVG(CAST(DATEDIFF(SECOND, event_time, triaged_at) AS BIGINT))
        FROM socops.security_event
        WHERE organisation_id = :organisationId
          AND is_deleted = 0
          AND triaged_at IS NOT NULL
          AND triaged_at >= :since
    """, nativeQuery = true)
    Double meanTimeToDetectSeconds(Long organisationId, LocalDateTime since);
}
