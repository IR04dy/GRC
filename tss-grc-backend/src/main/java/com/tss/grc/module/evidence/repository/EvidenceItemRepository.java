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

    @Query("SELECT e FROM EvidenceItem e WHERE e.organisationId = :orgId " +
           "AND e.subControlId = :subControlId")
    List<EvidenceItem> findBySubControl(@Param("orgId") Long orgId, @Param("subControlId") Long subControlId);

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
}
