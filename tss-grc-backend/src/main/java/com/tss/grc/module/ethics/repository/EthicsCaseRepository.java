package com.tss.grc.module.ethics.repository;

import com.tss.grc.module.ethics.entity.EthicsCase;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EthicsCaseRepository extends JpaRepository<EthicsCase, Long> {
    Page<EthicsCase> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    @Query("""
        SELECT c FROM EthicsCase c
        WHERE c.organisationId = :orgId
          AND (:status   IS NULL OR c.status   = :status)
          AND (:category IS NULL OR c.category = :category)
          AND (:severity IS NULL OR c.severity = :severity)
        ORDER BY c.createdAt DESC
        """)
    Page<EthicsCase> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("status") String status,
        @Param("category") String category,
        @Param("severity") String severity,
        Pageable pageable
    );

    long countByOrganisationId(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndCategory(Long organisationId, String category);

    long countByOrganisationIdAndStatusNotIn(Long organisationId, Collection<String> statuses);
    long countByOrganisationIdAndStatusIn(Long organisationId, Collection<String> statuses);
    long countByOrganisationIdAndCategoryNotIn(Long organisationId, Collection<String> categories);

    // ── F2-290: the open/closed predicate lives here, not in each caller ──────────────────
    // Three artefacts answered "is this case still open?" and no two agreed — the client with
    // the correct complement, analytics() with a five-token inclusion list, and the board-pack
    // aggregate with a three-token one, which prints "Open N" into a PDF and a PPTX. Measured
    // identically in dev and prod: 26 open, both server readers said 10.
    //
    // The obvious placement — EthicsCaseLifecycle imported by module/reporting — would be a new
    // module edge, and ModuleGraphRatchetTest is decrease-only. Asking the repository is both
    // legal (reporting already imports this interface) and narrower: a caller that cannot name a
    // status cannot invent its own idea of "still open". F2-196 established exactly this for
    // DsrCaseRepository; this is the second member, not a second mechanism.

    /** How many cases are still live — the COMPLEMENT of the declared terminal set. */
    default long countOpen(Long organisationId) {
        return countByOrganisationIdAndStatusNotIn(
            organisationId, com.tss.grc.module.ethics.EthicsCaseLifecycle.TERMINAL_LIST);
    }

    /** How many cases have reached a state they can never leave. */
    default long countClosed(Long organisationId) {
        return countByOrganisationIdAndStatusIn(
            organisationId, com.tss.grc.module.ethics.EthicsCaseLifecycle.TERMINAL_LIST);
    }

    /**
     * Cases whose status is not a state the machine declares — {@code INVESTIGATING} and
     * {@code TRIAGED} today, 11 of 30 rows in both databases. These are counted as OPEN by
     * {@link #countOpen} and are invisible to any per-state bucket, so the number is surfaced
     * rather than left to be inferred from a total that does not add up.
     */
    default long countWithUndeclaredStatus(Long organisationId) {
        return countByOrganisationIdAndStatusNotIn(
            organisationId, com.tss.grc.module.ethics.EthicsCaseLifecycle.STATES);
    }

    /** Cases whose category is outside the reporting taxonomy — 6 of 30 rows in both databases. */
    default long countWithUndeclaredCategory(Long organisationId) {
        return countByOrganisationIdAndCategoryNotIn(
            organisationId, com.tss.grc.module.ethics.EthicsCaseLifecycle.CATEGORY_TAXONOMY);
    }
}
