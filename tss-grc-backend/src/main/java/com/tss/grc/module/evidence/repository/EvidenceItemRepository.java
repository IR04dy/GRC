package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EvidenceItemRepository extends JpaRepository<EvidenceItem, Long> {

    Page<EvidenceItem> findByOrganisationId(Long orgId, Pageable pageable);

    /** Tenant-scoped single fetch — the reference resolution every cross-module caller owes. */
    java.util.Optional<EvidenceItem> findByIdAndOrganisationId(Long id, Long organisationId);

    /**
     * F2-243 — resolve an evidence item that another module may cite to discharge an
     * "attach evidence" requirement: it must exist, belong to {@code organisationId}, and be
     * usable as at {@code on}.
     *
     * <p>It sits here rather than in the calling module because this is where the status
     * vocabulary is declared. The GHG Rule 2 gate ({@code GhgEmissionRecordService.transition})
     * asked only {@code evidenceItemId != null}, so it was discharged by any non-null number that
     * satisfied the foreign key — including the one REJECTED item and any of the 31 EXPIRED ones.
     * A four-eyes gate that consumes a foreign key has to resolve it; a presence test on an id is
     * not a check on the thing it names.
     *
     * <p>⚠ Two questions, two places. The TENANT is verified where the reference is STORED, so a
     * foreign row can never be written to the column (F2-242's discriminator). The STATE is
     * verified here, where the reference is CONSUMED, because an item can be approved when it is
     * attached and expired by the time somebody verifies against it.
     */
    default java.util.Optional<EvidenceItem> findUsableAsEvidence(
            Long id, Long organisationId, LocalDate on) {
        return findByIdAndOrganisationId(id, organisationId)
            .filter(e -> e.isUsableAsEvidence(on));
    }

    @Query("SELECT e FROM EvidenceItem e WHERE e.organisationId = :orgId " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:type IS NULL OR e.evidenceType = :type)")
    Page<EvidenceItem> findByOrgFiltered(@Param("orgId") Long orgId,
                                          @Param("status") String status,
                                          @Param("type") String type,
                                          Pageable pageable);

    @Query("SELECT e FROM EvidenceItem e WHERE e.organisationId = :orgId " +
           "AND e.validTo IS NOT NULL AND e.validTo < :today")
    List<EvidenceItem> findExpired(@Param("orgId") Long orgId, @Param("today") LocalDate today);

    /** Cross-org: APPROVED items whose validTo has passed — driven by the
     *  nightly auto-expiry scheduler (runs outside any tenant context). */
    @Query("SELECT e FROM EvidenceItem e WHERE e.status = 'APPROVED' " +
           "AND e.validTo IS NOT NULL AND e.validTo < :today")
    List<EvidenceItem> findApprovedExpiredAllOrgs(@Param("today") LocalDate today);

    @Query("SELECT e FROM EvidenceItem e WHERE e.organisationId = :orgId " +
           "AND e.subControlId = :subControlId")
    List<EvidenceItem> findBySubControl(@Param("orgId") Long orgId, @Param("subControlId") Long subControlId);

    /** PERF-NQ-003: batch variant of {@link #findBySubControl} — fetch every
     *  evidence item for a set of sub-controls in one query so the matrix view
     *  can group them in memory instead of issuing one query per sub-control. */
    List<EvidenceItem> findByOrganisationIdAndSubControlIdIn(Long orgId, java.util.Collection<Long> subControlIds);

    long countByOrganisationIdAndStatus(Long orgId, String status);

    long countByOrganisationId(Long orgId);

    /** Per-sub-control evidence counts grouped by status — drives the GRC Expert coverage rollup. */
    @Query("SELECT e.subControlId, e.status, COUNT(e) FROM EvidenceItem e " +
           "WHERE e.organisationId = :orgId AND e.subControlId IS NOT NULL " +
           "GROUP BY e.subControlId, e.status")
    List<Object[]> aggregateStatusBySubControl(@Param("orgId") Long orgId);

    /** Per-sub-control expired vs expiring-soon counts (by valid_to) for the GRC Expert freshness badges. */
    @Query("SELECT e.subControlId, " +
           "SUM(CASE WHEN e.validTo < :today THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN e.validTo >= :today AND e.validTo <= :horizon THEN 1 ELSE 0 END) " +
           "FROM EvidenceItem e " +
           "WHERE e.organisationId = :orgId AND e.subControlId IS NOT NULL AND e.validTo IS NOT NULL " +
           "GROUP BY e.subControlId")
    List<Object[]> aggregateExpiryBySubControl(@Param("orgId") Long orgId,
                                               @Param("today") LocalDate today,
                                               @Param("horizon") LocalDate horizon);

    /** Per-sub-control count of APPROVED items that are still valid (not past valid_to) — drives
     *  freshness-aware coverage so a sub-control whose only approved evidence has expired does NOT
     *  read COVERED. A null validTo means "no expiry" and counts as fresh. */
    @Query("SELECT e.subControlId, COUNT(e) FROM EvidenceItem e " +
           "WHERE e.organisationId = :orgId AND e.subControlId IS NOT NULL " +
           "AND e.status = 'APPROVED' AND (e.validTo IS NULL OR e.validTo >= :today) " +
           "GROUP BY e.subControlId")
    List<Object[]> aggregateFreshApprovedBySubControl(@Param("orgId") Long orgId,
                                                      @Param("today") LocalDate today);
}
