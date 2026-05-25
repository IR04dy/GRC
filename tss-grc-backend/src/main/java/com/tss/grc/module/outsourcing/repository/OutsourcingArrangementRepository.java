package com.tss.grc.module.outsourcing.repository;

import com.tss.grc.module.outsourcing.entity.OutsourcingArrangement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OutsourcingArrangementRepository
        extends JpaRepository<OutsourcingArrangement, Long> {

    List<OutsourcingArrangement> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    Optional<OutsourcingArrangement> findByOrganisationIdAndReference(
            Long organisationId, String reference);

    Optional<OutsourcingArrangement> findByOrganisationIdAndId(
            Long organisationId, Long id);

    List<OutsourcingArrangement> findByOrganisationIdAndStatus(
            Long organisationId, String status);

    @Query("SELECT a FROM OutsourcingArrangement a "
            + "WHERE a.organisationId = :org AND a.materiality = 'MATERIAL' "
            + "AND a.status IN ('BOARD_APPROVED','SAMA_NOTIFIED','LIVE','UNDER_REVIEW')")
    List<OutsourcingArrangement> findActiveMaterial(@Param("org") Long org);

    @Query("SELECT a FROM OutsourcingArrangement a "
            + "WHERE a.organisationId = :org "
            + "AND a.nextReviewDue IS NOT NULL AND a.nextReviewDue <= :asOf "
            + "AND a.status IN ('LIVE','UNDER_REVIEW')")
    List<OutsourcingArrangement> findReviewsDue(
            @Param("org") Long org, @Param("asOf") LocalDate asOf);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndMateriality(Long organisationId, String materiality);
}
