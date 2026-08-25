package com.tss.grc.module.reginspect.repository;

import com.tss.grc.module.reginspect.entity.RegInspection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegInspectionRepository extends JpaRepository<RegInspection, Long> {

    Page<RegInspection> findByOrganisationIdOrderByCreatedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<RegInspection> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<RegInspection> findByExternalReferenceAndOrganisationId(
            String externalReference, Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatusIn(
            Long organisationId, java.util.Collection<String> statuses);

    /**
     * F2-354: the OSI reference-mint seed — the highest ordinal (the last {@code -NNN} segment)
     * across ALL on-site-inspection references for the tenant in a given year+quarter, incl.
     * soft-deleted rows (collision-safe: a used ordinal is never re-issued). {@code generateReference}
     * previously seeded from a {@code count(...) + 1} over a capped {@code PageRequest.of(0, 200)}
     * window, which respects {@code @Where(is_deleted = 0)} and is also windowed — so a soft-delete
     * gap, a concurrent create, or more than 200 inspections re-minted a live ordinal, and the
     * filtered unique index {@code ux_reginspect_external_reference (organisation_id,
     * external_reference) WHERE is_deleted = 0} (V341) then 500'd the INSERT (the F2-337 / F2-351 /
     * F2-353 count-minter collision shape). {@code pat} is {@code %-OSI-<year>-Q<q>-%}, matching every
     * regulator's inspection that quarter (the ordinal is a shared per-quarter sequence);
     * {@code TRY_CAST} ignores a non-numeric tail (e.g. a random-suffix backstop reference);
     * {@code ISNULL(..., 0)} seeds an empty quarter at 1.
     */
    @Query(value =
        "SELECT ISNULL(MAX(TRY_CAST("
      + "RIGHT(external_reference, CHARINDEX('-', REVERSE(external_reference)) - 1) AS INT)), 0) "
      + "FROM reginspect.reginspect_inspection "
      + "WHERE organisation_id = :org AND external_reference LIKE :pat",
        nativeQuery = true)
    int findMaxOsiOrdinal(@Param("org") Long org, @Param("pat") String pat);

    /** Live-only (respects {@code @Where}) — the skip-loop's collision check for a concurrent mint. */
    boolean existsByOrganisationIdAndExternalReference(Long organisationId, String externalReference);
}
