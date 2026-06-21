package com.tss.grc.module.outsourcing.repository;

import com.tss.grc.module.outsourcing.entity.OutsourcingArrangement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository("outsourcingCloudArrangementRepository")
public interface OutsourcingArrangementRepository
        extends JpaRepository<OutsourcingArrangement, Long> {

    List<OutsourcingArrangement> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    /** Bounded most-recent arrangements for the dashboard preview list
     *  (QUAL-TI-008) — replaces an in-memory {@code all.stream().limit(n)}. */
    List<OutsourcingArrangement> findByOrganisationIdOrderByCreatedAtDesc(
            Long organisationId, Pageable pageable);

    /** Arrangement counts grouped by status for a tenant in one query
     *  (QUAL-TI-008) — replaces an in-memory {@code groupingBy(status)} over the
     *  full arrangement list. Returns {@code [status, count]} rows. */
    @Query("SELECT a.status, COUNT(a) FROM OutsourcingCloudArrangement a "
            + "WHERE a.organisationId = :org GROUP BY a.status")
    List<Object[]> countByStatusGrouped(@Param("org") Long org);

    /** Count variant of {@link #findActiveMaterial} (QUAL-TI-008) — replaces a
     *  {@code findActiveMaterial(o).size()} fetch. */
    @Query("SELECT COUNT(a) FROM OutsourcingCloudArrangement a "
            + "WHERE a.organisationId = :org AND a.materiality = 'MATERIAL' "
            + "AND a.status IN ('BOARD_APPROVED','SAMA_NOTIFIED','LIVE','UNDER_REVIEW')")
    long countActiveMaterial(@Param("org") Long org);

    Optional<OutsourcingArrangement> findByOrganisationIdAndReference(
            Long organisationId, String reference);

    Optional<OutsourcingArrangement> findByOrganisationIdAndId(
            Long organisationId, Long id);

    List<OutsourcingArrangement> findByOrganisationIdAndStatus(
            Long organisationId, String status);

    @Query("SELECT a FROM OutsourcingCloudArrangement a "
            + "WHERE a.organisationId = :org AND a.materiality = 'MATERIAL' "
            + "AND a.status IN ('BOARD_APPROVED','SAMA_NOTIFIED','LIVE','UNDER_REVIEW')")
    List<OutsourcingArrangement> findActiveMaterial(@Param("org") Long org);

    @Query("SELECT a FROM OutsourcingCloudArrangement a "
            + "WHERE a.organisationId = :org "
            + "AND a.nextReviewDue IS NOT NULL AND a.nextReviewDue <= :asOf "
            + "AND a.status IN ('LIVE','UNDER_REVIEW')")
    List<OutsourcingArrangement> findReviewsDue(
            @Param("org") Long org, @Param("asOf") LocalDate asOf);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndMateriality(Long organisationId, String materiality);

    /** Total arrangements for a tenant (QUAL-TI-008) — replaces
     *  {@code findByOrganisationIdOrderByCreatedAtDesc(o).size()} in the dashboard. */
    long countByOrganisationId(Long organisationId);
}
