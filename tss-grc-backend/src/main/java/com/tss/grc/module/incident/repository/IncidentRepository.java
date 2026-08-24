package com.tss.grc.module.incident.repository;

import com.tss.grc.module.incident.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Page<Incident> findByOrganisationId(Long organisationId, Pageable pageable);

    /** CY-B: the canonical register row mirroring a given /cyber workspace incident (if any). */
    Optional<Incident> findByOrganisationIdAndSourceWorkspaceIncidentId(Long organisationId, Long sourceWorkspaceIncidentId);

    @Query("""
        SELECT i FROM Incident i
        WHERE i.organisationId = :orgId
          AND (:severity IS NULL OR i.severity = :severity)
          AND (:status   IS NULL OR i.status   = :status)
          AND (:category IS NULL OR i.category = :category)
        """)
    Page<Incident> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("severity") String severity,
        @Param("status") String status,
        @Param("category") String category,
        Pageable pageable
    );

    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndSeverity(Long organisationId, String severity);

    @Query("SELECT i FROM Incident i WHERE i.organisationId = :orgId ORDER BY i.createdAt DESC")
    List<Incident> findRecent(@Param("orgId") Long orgId, Pageable pageable);

    /**
     * F2-345 — every resolved incident (one carrying both a discovery and a resolution date),
     * org-scoped and <b>uncapped</b>, for the mean-time-to-resolve statistic.
     *
     * <p>{@code getStats().avgDays} used to average over {@code findRecent(200)} — the 200
     * most-recently-<em>created</em> incidents, then filtered to those with both dates. That is
     * wrong twice: it drops long-resolved older incidents once a tenant passes 200, and it orders
     * by {@code createdAt} so the window over-weights freshly-created incidents, which resolve
     * fast or are still open — biasing MTTR downward. Its six sibling figures in the same
     * {@code IncidentStats} are exact all-time {@code countBy…}; the average must match them
     * (F2-197's capped-list-then-counted shape, on a NIST 800-61r2 KPI).
     *
     * <p>The WHERE mirrors the old Java filter exactly (both dates present); it does not narrow by
     * status, because a resolution date already implies resolution and the prior code never
     * consulted status. Only rows the average needs are fetched — a smaller set than
     * {@code findRecent(200)}, not a larger one.
     */
    @Query("SELECT i FROM Incident i WHERE i.organisationId = :orgId "
         + "AND i.discoveryDate IS NOT NULL AND i.resolutionDate IS NOT NULL")
    List<Incident> findResolvedWithDates(@Param("orgId") Long orgId);
}
