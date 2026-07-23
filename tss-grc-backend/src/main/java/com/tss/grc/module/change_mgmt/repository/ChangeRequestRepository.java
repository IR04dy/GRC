package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, Long> {
    Page<ChangeRequest> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    @Query("""
        SELECT c FROM ChangeRequest c
        WHERE c.organisationId = :orgId
          AND (:status     IS NULL OR c.status     = :status)
          AND (:changeType IS NULL OR c.changeType = :changeType)
          AND (:riskLevel  IS NULL OR c.riskLevel  = :riskLevel)
          AND (:category   IS NULL OR c.category   = :category)
        ORDER BY c.createdAt DESC
        """)
    Page<ChangeRequest> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("status") String status,
        @Param("changeType") String changeType,
        @Param("riskLevel") String riskLevel,
        @Param("category") String category,
        Pageable pageable
    );

    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndChangeType(Long organisationId, String changeType);
    long countByOrganisationIdAndOutcome(Long organisationId, String outcome);
    long countByOrganisationId(Long organisationId);

    /**
     * Per-status change counts for a tenant in one query — replaces a fan-out of
     * five {@code countByOrganisationIdAndStatus} calls in board-pack change
     * reporting (QUAL-BCMP-006). Returns {@code [status, count]} rows.
     */
    @Query("""
        SELECT c.status, COUNT(c) FROM ChangeRequest c
        WHERE c.organisationId = :orgId
        GROUP BY c.status
        """)
    java.util.List<Object[]> countByStatusGrouped(@Param("orgId") Long orgId);

    /** CH-5: tenants that have any change request — the SLA scheduler iterates these. */
    @Query("SELECT DISTINCT c.organisationId FROM ChangeRequest c")
    java.util.List<Long> findDistinctOrganisationIds();

    /** CH-17: changes whose scheduled window intersects [from, to] — the change calendar. */
    @Query("""
        SELECT c FROM ChangeRequest c
        WHERE c.organisationId = :orgId
          AND c.scheduledStart IS NOT NULL
          AND c.scheduledStart <= :to
          AND COALESCE(c.scheduledEnd, c.scheduledStart) >= :from
        ORDER BY c.scheduledStart ASC
        """)
    java.util.List<ChangeRequest> findScheduledBetween(
        @Param("orgId") Long orgId,
        @Param("from") java.time.LocalDateTime from,
        @Param("to") java.time.LocalDateTime to);

    /** CH-17: other live (non-terminal) changes whose scheduled window overlaps
     *  the given interval — conflict-detection candidates. */
    @Query("""
        SELECT c FROM ChangeRequest c
        WHERE c.organisationId = :orgId
          AND c.id <> :excludeId
          AND c.status NOT IN ('CLOSED', 'CANCELLED', 'CAB_REJECTED')
          AND c.scheduledStart IS NOT NULL
          AND c.scheduledStart <= :windowEnd
          AND COALESCE(c.scheduledEnd, c.scheduledStart) >= :windowStart
        ORDER BY c.scheduledStart ASC
        """)
    java.util.List<ChangeRequest> findScheduleConflicts(
        @Param("orgId") Long orgId,
        @Param("excludeId") Long excludeId,
        @Param("windowStart") java.time.LocalDateTime windowStart,
        @Param("windowEnd") java.time.LocalDateTime windowEnd);
}
