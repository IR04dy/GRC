package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChangeTaskRepository extends JpaRepository<ChangeTask, Long> {
    /**
     * The implementation checklist, in the order an implementer works through it.
     *
     * <p>F2-239 — the {@code IdAsc} tie-break is not decoration. Every task the UI creates carries
     * {@code sort_order = 0} (the add-task form has no ordering input), so ties are the normal case
     * and without a second key their relative order is whatever the database happens to return —
     * F2-197's "declaration order presented as meaning". {@code ChangeConditionRepository}'s sibling
     * finder has always ended {@code OrderBySortOrderAscIdAsc}; this one did not.
     */
    List<ChangeTask> findByChangeRequestIdOrderBySortOrderAscIdAsc(Long changeRequestId);

    /**
     * CH-5: overdue, still-open tasks for a tenant — dueAt in the past and the task
     * not yet terminal (DONE/SKIPPED). Org scope comes from the parent change request
     * ({@code change_tasks} has no organisation_id). Drives the SLA scheduler + the
     * overdue-tasks KPI.
     */
    @Query("""
        SELECT t FROM ChangeTask t
        WHERE t.dueAt < :now
          AND t.status NOT IN ('DONE','SKIPPED')
          AND t.changeRequestId IN (SELECT c.id FROM ChangeRequest c WHERE c.organisationId = :org)
        ORDER BY t.dueAt ASC
        """)
    List<ChangeTask> findOverdueOpen(@Param("org") Long org, @Param("now") LocalDateTime now);

    @Query("""
        SELECT COUNT(t) FROM ChangeTask t
        WHERE t.dueAt < :now
          AND t.status NOT IN ('DONE','SKIPPED')
          AND t.changeRequestId IN (SELECT c.id FROM ChangeRequest c WHERE c.organisationId = :org)
        """)
    long countOverdueOpen(@Param("org") Long org, @Param("now") LocalDateTime now);

    /**
     * S8h — every open task in the tenant, whatever its due date.
     *
     * <p>⚠ Deliberately not {@link #findOverdueOpen}: that one requires {@code dueAt < now}, which
     * silently excludes the 18 of 41 tasks that have no due date at all. An addressability report
     * built on it would have reported only the tasks somebody already dated, and a task with no owner
     * AND no due date is the worse record of the two.
     */
    @Query("""
        SELECT t FROM ChangeTask t
        WHERE t.status IN :statuses
          AND t.changeRequestId IN (SELECT c.id FROM ChangeRequest c WHERE c.organisationId = :org)
        ORDER BY t.dueAt ASC, t.id ASC
        """)
    List<ChangeTask> findOpenByOrganisation(@Param("org") Long org,
                                           @Param("statuses") List<String> statuses);
}
