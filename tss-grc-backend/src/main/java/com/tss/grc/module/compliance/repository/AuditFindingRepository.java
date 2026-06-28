package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.AuditFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AuditFindingRepository extends JpaRepository<AuditFinding, Long> {

    Page<AuditFinding> findByOrganisationId(Long organisationId, Pageable pageable);

    @Query("""
        SELECT f FROM AuditFinding f
        WHERE f.organisationId = :orgId
          AND (:severity IS NULL OR f.severity = :severity)
          AND (:status   IS NULL OR f.status   = :status)
        """)
    Page<AuditFinding> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("severity") String severity,
        @Param("status") String status,
        Pageable pageable
    );

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndSeverity(Long organisationId, String severity);

    long countByOrganisationId(Long organisationId);

    /**
     * Open findings (anything not CLOSED) for a tenant — replaces an in-memory
     * {@code filter(f -> !"CLOSED".equals(f.getStatus())).count()} scan
     * (QUAL-BCMP-006). The {@code status IS NULL} clause keeps null-status rows
     * counted as open, exactly as {@code !"CLOSED".equals(null)} did.
     */
    @Query("""
        SELECT COUNT(f) FROM AuditFinding f
        WHERE f.organisationId = :orgId
          AND (f.status IS NULL OR f.status <> 'CLOSED')
        """)
    long countOpenByOrganisationId(@Param("orgId") Long orgId);

    /** Tenant findings tagged to a framework — org + framework filtered in SQL
     *  (QUAL-RC-006, replaces fetch-1000-then-filter). */
    java.util.List<AuditFinding> findByOrganisationIdAndFrameworkId(
        Long organisationId, Long frameworkId);

    /** Open (not-CLOSED) tenant findings for one framework — used by board-pack
     *  framework coverage (QUAL-BCMP-006). Null status counts as open. */
    @Query("""
        SELECT COUNT(f) FROM AuditFinding f
        WHERE f.organisationId = :orgId
          AND f.frameworkId = :frameworkId
          AND (f.status IS NULL OR f.status <> 'CLOSED')
        """)
    long countOpenByOrganisationIdAndFrameworkId(
        @Param("orgId") Long orgId, @Param("frameworkId") Long frameworkId);

    /**
     * Per-(framework, status) finding counts for a tenant in one query — replaces
     * a fetch-1000-then-tally scan in framework coverage (QUAL-RC-006). Returns
     * {@code [frameworkId, status, count]} rows; rows with a null frameworkId are
     * naturally excluded by the GROUP BY join semantics only if filtered, so the
     * predicate keeps just framework-tagged findings (mirrors the original
     * {@code frameworkId != null && equals} guard).
     */
    @Query("""
        SELECT f.frameworkId, f.status, COUNT(f) FROM AuditFinding f
        WHERE f.organisationId = :orgId
          AND f.frameworkId IS NOT NULL
        GROUP BY f.frameworkId, f.status
        """)
    java.util.List<Object[]> countByFrameworkAndStatusGrouped(@Param("orgId") Long orgId);

    /** Distinct tenants with at least one finding — drives the deadline scheduler's per-tenant loop. */
    @Query("SELECT DISTINCT f.organisationId FROM AuditFinding f")
    List<Long> findDistinctOrganisationIds();

    /** Open (not-CLOSED) findings past their due date — CAPA overdue reminders. */
    @Query("""
        SELECT f FROM AuditFinding f
        WHERE f.organisationId = :orgId
          AND f.dueDate IS NOT NULL
          AND f.dueDate < :today
          AND (f.status IS NULL OR f.status <> 'CLOSED')
        """)
    List<AuditFinding> findOverdueOpen(@Param("orgId") Long orgId, @Param("today") LocalDate today);
}
