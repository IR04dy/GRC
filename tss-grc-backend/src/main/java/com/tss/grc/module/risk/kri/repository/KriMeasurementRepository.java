package com.tss.grc.module.risk.kri.repository;

import com.tss.grc.module.risk.kri.entity.KriMeasurement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Org-scoped finders for {@link KriMeasurement}.
 *
 * <p>Two read shapes dominate:
 * <ol>
 *   <li>"Latest measurement per KRI" — drives the dashboard heatmap.</li>
 *   <li>"Last 90 days of measurements for KRI X" — drives the
 *       detail-page sparkline.</li>
 * </ol>
 */
public interface KriMeasurementRepository extends JpaRepository<KriMeasurement, Long> {

    /**
     * Most-recent measurement for one KRI within a tenant. Returned
     * as Optional because a fresh KRI has no measurements yet.
     */
    Optional<KriMeasurement> findFirstByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
            Long organisationId, Long kriId);

    /**
     * Most-recent N measurements for one KRI, descending. Pageable
     * caps the size — the controller defaults to 90 (≈90 days for a
     * daily KRI).
     */
    List<KriMeasurement> findByOrganisationIdAndKriIdAndIsDeletedFalseOrderByMeasuredAtDesc(
            Long organisationId, Long kriId, Pageable pageable);

    /**
     * Time-window range for a KRI — ascending so the sparkline can
     * plot it left-to-right without reversing the list.
     */
    @Query("""
        SELECT m FROM KriMeasurement m
        WHERE m.organisationId = :orgId
          AND m.kriId = :kriId
          AND m.measuredAt >= :since
          AND m.isDeleted = false
        ORDER BY m.measuredAt ASC
        """)
    List<KriMeasurement> findRangeForKri(@Param("orgId") Long orgId,
                                         @Param("kriId") Long kriId,
                                         @Param("since") LocalDateTime since);

    long countByOrganisationIdAndKriIdAndStatusAndIsDeletedFalse(
            Long organisationId, Long kriId, String status);

    long countByOrganisationIdAndStatusAndMeasuredAtAfterAndIsDeletedFalse(
            Long organisationId, String status, LocalDateTime since);

    /**
     * E-E consolidation: latest measurement per KRI for a set of KRI ids —
     * {@code [kriId, status, value]} rows. One query instead of a per-KRI N+1,
     * replacing the legacy {@code KriReadingRepository.findLatestReadingByKriIdIn}
     * used by the board-pack / KRI-dashboard exporters.
     */
    @Query("""
        SELECT m.kriId, m.status, m.value FROM KriMeasurement m
        WHERE m.kriId IN :kriIds AND m.isDeleted = false
          AND m.measuredAt = (SELECT MAX(m2.measuredAt) FROM KriMeasurement m2
                              WHERE m2.kriId = m.kriId AND m2.isDeleted = false)
        """)
    List<Object[]> findLatestPerKriIn(@Param("kriIds") java.util.Collection<Long> kriIds);
}
