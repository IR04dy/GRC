package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChangeTaskRepository extends JpaRepository<ChangeTask, Long> {
    List<ChangeTask> findByChangeRequestIdOrderBySortOrderAsc(Long changeRequestId);

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
}
