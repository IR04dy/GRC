package com.tss.grc.module.ccm.repository;

import com.tss.grc.module.ccm.entity.ControlTestRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Org-scoped finders for {@link ControlTestRun}.
 *
 * <p>Dashboards drive the read pattern — "recent runs across the
 * tenant", "the last N runs of test X", and "rolled-up pass/fail
 * counts in the last 24h".
 */
public interface ControlTestRunRepository extends JpaRepository<ControlTestRun, Long> {

    List<ControlTestRun> findByOrganisationIdAndTestDefinitionIdAndIsDeletedFalseOrderByRunAtDesc(
            Long organisationId, Long testDefinitionId, Pageable pageable);

    Page<ControlTestRun> findByOrganisationIdAndIsDeletedFalseOrderByRunAtDesc(
            Long organisationId, Pageable pageable);

    long countByOrganisationIdAndStatusAndRunAtAfterAndIsDeletedFalse(
            Long organisationId, String status, LocalDateTime since);

    /**
     * Most-recent N runs across the tenant. Equivalent to
     * {@code findByOrganisationIdAndIsDeletedFalseOrderByRunAtDesc} with
     * a {@code PageRequest.of(0, n)} — provided as a list-returning
     * method for callers that don't want Page<> ceremony.
     */
    @Query("""
        SELECT r FROM ControlTestRun r
        WHERE r.organisationId = :orgId
          AND r.isDeleted = false
        ORDER BY r.runAt DESC
        """)
    List<ControlTestRun> findRecent(@Param("orgId") Long orgId, Pageable pageable);

    /**
     * Last N runs of one test, in chronological-ascending order — useful
     * for the detail-page sparkline.
     */
    @Query("""
        SELECT r FROM ControlTestRun r
        WHERE r.organisationId = :orgId
          AND r.testDefinitionId = :defId
          AND r.isDeleted = false
        ORDER BY r.runAt DESC
        """)
    List<ControlTestRun> findRecentForTest(@Param("orgId") Long orgId,
                                           @Param("defId") Long defId,
                                           Pageable pageable);
}
