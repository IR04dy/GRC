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

    /**
     * The same population, UNCAPPED -- F2-422.
     *
     * <p>AuditCommitteeDashboardService#findingSection took this register through
     * {@code Pageable.ofSize(2000)} and derived the open count, the four severity counts,
     * the three ageing buckets and the closure RATE from the window.
     *
     * <p>Derived, so it inherits {@code AuditFinding}'s {@code @Where(is_deleted = 0)} and agrees
     * with the paged overload on soft-delete scoping (checked, not assumed). Deliberately NOT
     * a SQL {@code COUNT} beside the paged fetch: that leaves two populations and re-creates
     * F2-365's defect. The paged overload stays -- the list endpoints are genuine paged reads.
     */
    List<AuditFinding> findByOrganisationId(Long organisationId);

    /** C-12/item-20 — dedup auto-created gaps by their source marker (clause_ref). */
    List<AuditFinding> findByOrganisationIdAndClauseRef(Long organisationId, String clauseRef);

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

    /**
     * F2-58 — open findings created strictly before {@code cutoff}, counted in the database.
     *
     * <p>Both board-pack renderers built their aging tiers by fetching 1,000 findings and
     * bucketing them in a Java loop. That is the same defect {@link #countOpenByOrganisationId}
     * was added to fix (QUAL-BCMP-006), one layer along: correct only while a tenant stays under
     * the page size, and silently wrong above it — a board would be shown aging tiers computed
     * over an arbitrary 1,000-row subset with no error and no log line.
     *
     * <p>Callers pass {@code LocalDate.now().minusDays(N - 1).atStartOfDay()} to reproduce the
     * loop's {@code ChronoUnit.DAYS.between(createdAt.toLocalDate(), today) >= N} exactly: that
     * predicate is true iff the creation <em>date</em> is on or before {@code today - N}, i.e. iff
     * the timestamp falls before the start of day {@code today - (N - 1)}. Using
     * {@code now().minusDays(N)} instead would be off by one for any finding created later in the
     * day than the moment the report runs.
     *
     * <p>{@code createdAt IS NULL} never satisfies {@code <}, so null-dated findings fall out of
     * every tier and land in the on-track remainder — which is what the loop did with
     * {@code days = 0}. Measured 2026-07-31: zero findings have a null {@code created_at}, so the
     * clause is a guard rather than a live path.
     */
    @Query("""
        SELECT COUNT(f) FROM AuditFinding f
        WHERE f.organisationId = :orgId
          AND (f.status IS NULL OR f.status <> 'CLOSED')
          AND f.createdAt < :cutoff
        """)
    long countOpenByOrganisationIdOlderThan(@Param("orgId") Long orgId,
                                            @Param("cutoff") java.time.LocalDateTime cutoff);

    /**
     * F2-58 — the oldest open findings, ordered and limited by the database.
     *
     * <p>Replaces "fetch 1,000, sort in memory, break at 15". Pass
     * {@code PageRequest.of(0, 15)}. Closed findings are excluded in SQL, so the page size is the
     * number of rows rendered — the previous code counted only non-closed rows toward its limit
     * but paged over both, so a tenant whose first 1,000 findings were mostly closed could render
     * fewer than 15 open ones while older open findings existed.
     */
    @Query("""
        SELECT f FROM AuditFinding f
        WHERE f.organisationId = :orgId
          AND (f.status IS NULL OR f.status <> 'CLOSED')
        ORDER BY f.createdAt ASC
        """)
    java.util.List<AuditFinding> findOldestOpenByOrganisationId(@Param("orgId") Long orgId,
                                                                Pageable pageable);

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
