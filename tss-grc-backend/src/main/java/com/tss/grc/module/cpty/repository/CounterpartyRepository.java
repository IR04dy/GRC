package com.tss.grc.module.cpty.repository;

import com.tss.grc.module.cpty.entity.Counterparty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

    Page<Counterparty> findByOrganisationIdOrderByReferenceAsc(
            Long organisationId, Pageable pageable);

    /**
     * Uncapped, org-scoped — the source for the dashboard's exposure/sector/grade AGGREGATES.
     * F2-352: {@code dashboard()} summed total exposure and grouped by sector/grade over a capped
     * {@code PageRequest.of(0, 50)}, so on a tenant with more than 50 counterparties the SAMA
     * total-exposure and concentration figures were computed over the first 50 by reference only.
     * Respects the entity's {@code @Where(is_deleted = 0)}.
     */
    List<Counterparty> findByOrganisationIdOrderByReferenceAsc(Long organisationId);

    Optional<Counterparty> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<Counterparty> findByReferenceAndOrganisationId(
            String reference, Long organisationId);

    List<Counterparty> findByOrganisationIdAndStatus(Long organisationId, String status);

    List<Counterparty> findByOrganisationIdAndParentGroupReference(
            Long organisationId, String parentGroupReference);

    long countByOrganisationId(Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    /**
     * F2-353: the reference-mint seed — the highest {@code CP-NNNNN} ordinal ever issued for the
     * tenant, taken as {@code MAX} across ALL rows including soft-deleted ones (collision-safe: a
     * used ordinal is never re-issued). {@code CptyService.generateReference} previously seeded from
     * {@code countByOrganisationId() + 1}, which respects {@code @Where(is_deleted = 0)} — so a
     * soft-delete gap or a concurrent create re-minted a live ordinal, and the filtered unique index
     * {@code ux_cpty_reference (organisation_id, reference) WHERE is_deleted = 0} then 500'd the
     * INSERT (the F2-337 / F2-351 count-minter collision shape). {@code TRY_CAST} ignores any
     * caller-supplied reference that only happens to start {@code CP-}; {@code ISNULL(..., 0)} makes
     * an empty register seed at 1.
     */
    @Query(value =
        "SELECT ISNULL(MAX(TRY_CAST(SUBSTRING(reference, 4, 29) AS INT)), 0) "
      + "FROM cpty.counterparty "
      + "WHERE organisation_id = :org AND reference LIKE 'CP-%'",
        nativeQuery = true)
    int findMaxReferenceSeq(@Param("org") Long org);

    /** Live-only (respects {@code @Where}) — the skip-loop's collision check for a concurrent mint. */
    boolean existsByOrganisationIdAndReference(Long organisationId, String reference);
}
