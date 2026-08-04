package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * S7e / CE-5 — the conditions register (V352).
 *
 * <p>Every query filters {@code organisationId} (CLAUDE.md: developer responsibility, and the reason
 * the table carries the column rather than scoping through its parent like {@code change_tasks}).
 * The soft-delete filter comes from {@code @Where} on the entity.
 */
public interface ChangeConditionRepository extends JpaRepository<ChangeCondition, Long> {

    List<ChangeCondition> findByOrganisationIdAndChangeRequestIdOrderBySortOrderAscIdAsc(
        Long organisationId, Long changeRequestId);

    Optional<ChangeCondition> findByIdAndOrganisationId(Long id, Long organisationId);

    /** The blocker count the guard consults — the whole reason the register is not prose. */
    long countByOrganisationIdAndChangeRequestIdAndStatus(
        Long organisationId, Long changeRequestId, String status);

    /** Conditionality is derived from this, never stored (see {@code ChangeConditionStatus}). */
    long countByOrganisationIdAndChangeRequestId(Long organisationId, Long changeRequestId);

    /**
     * S8b/CE-12 — every condition in the tenant, the population the overdue count is a fraction of.
     *
     * <p>Needed because "0 overdue out of 0 conditions" and "0 overdue out of 40" are different
     * governance facts, and the KPI pack reports the first as {@code NO_DATA} rather than as a clean
     * bill of health. {@code @Where(clause = "is_deleted = 0")} on the entity covers the filter.
     */
    long countByOrganisationId(Long organisationId);

    /**
     * The tenant-wide register: every open condition, soonest due first, nulls last.
     *
     * <p>Ordering is explicit because an obligation with no due date must not sort ahead of one due
     * tomorrow — SQL Server sorts NULL first on ASC, which would put the least urgent items at the
     * top of a blocker list.
     */
    @Query("""
        SELECT c FROM ChangeCondition c
        WHERE c.organisationId = :org AND c.status = 'OPEN'
        ORDER BY CASE WHEN c.dueAt IS NULL THEN 1 ELSE 0 END ASC, c.dueAt ASC, c.id ASC
        """)
    List<ChangeCondition> findOpenForOrg(@Param("org") Long org);

    /** Open and past due — the KPI, and what an escalation sweep would read. */
    @Query("""
        SELECT COUNT(c) FROM ChangeCondition c
        WHERE c.organisationId = :org AND c.status = 'OPEN'
          AND c.dueAt IS NOT NULL AND c.dueAt < :now
        """)
    long countOverdueOpen(@Param("org") Long org, @Param("now") LocalDateTime now);
}
