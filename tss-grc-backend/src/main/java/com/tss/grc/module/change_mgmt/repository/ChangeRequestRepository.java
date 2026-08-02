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

    /**
     * A17-D3: emergency changes whose ratification clock has expired.
     *
     * <p>The predicate is total by construction — {@code PENDING} implies a
     * non-null {@code ratificationDueAt} ({@code ck_change_ratification_clock},
     * V345), so no row can slip through on SQL's UNKNOWN. That is not a
     * theoretical worry: the one pre-existing EMERGENCY row has a null
     * implementation timestamp, and against a null due date this comparison
     * would have filtered it out and reported it compliant forever.
     *
     * <p>{@code ratificationEscalatedAt IS NULL} is a cheap first-pass filter, not
     * the dedup: the audit-ledger marker is (mirroring {@code ChangeTaskSlaScheduler}),
     * because the marker survives a manual clearing of the column.
     */
    @Query("""
        SELECT c FROM ChangeRequest c
        WHERE c.organisationId = :orgId
          AND c.ratificationState = 'PENDING'
          AND c.ratificationDueAt < :now
        ORDER BY c.ratificationDueAt ASC
        """)
    java.util.List<ChangeRequest> findRatificationBreaches(
        @Param("orgId") Long orgId,
        @Param("now") java.time.LocalDateTime now);

    /** A17-D3: the interim unauthorised-change register — every emergency change
     *  that is not in a settled ratification state. CE-11 will widen this. */
    @Query("""
        SELECT c FROM ChangeRequest c
        WHERE c.organisationId = :orgId
          AND c.ratificationState <> 'NOT_REQUIRED'
        ORDER BY CASE c.ratificationState
                     WHEN 'PENDING' THEN 0
                     WHEN 'AWAITING_IMPLEMENTATION' THEN 1
                     WHEN 'REJECTED' THEN 2
                     ELSE 3 END,
                 c.ratificationDueAt ASC, c.id ASC
        """)
    java.util.List<ChangeRequest> findRatificationRegister(@Param("orgId") Long orgId);

    /** A17-D3 KPI counts: [ratificationState, count] for a tenant. */
    @Query("""
        SELECT c.ratificationState, COUNT(c) FROM ChangeRequest c
        WHERE c.organisationId = :orgId
        GROUP BY c.ratificationState
        """)
    java.util.List<Object[]> countByRatificationStateGrouped(@Param("orgId") Long orgId);

    /**
     * A17-D3: stamp the escalation timestamp without loading or saving the entity.
     *
     * <p>Native single-statement UPDATE on purpose. The sweep runs off-request and
     * the ROWVERSION landmine (never save an entity twice around a native
     * {@code @Modifying} on the same table) is easiest to avoid by never loading
     * the entity for writing at all. {@code ChangeTaskSlaScheduler} sidestepped this
     * by writing nothing; D-3 has to record the escalation, so it writes once.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        UPDATE change_mgmt.change_requests
           SET ratification_escalated_at = :now
         WHERE id = :id AND ratification_escalated_at IS NULL
        """, nativeQuery = true)
    int stampRatificationEscalation(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

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
