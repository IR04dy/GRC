package com.tss.grc.module.aml.sanctions.repository;

import com.tss.grc.module.aml.sanctions.entity.ScreeningHit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Tenant-scoped repository for {@link ScreeningHit}. */
public interface ScreeningHitRepository extends JpaRepository<ScreeningHit, Long> {

    List<ScreeningHit> findByOrganisationIdAndCheckIdAndIsDeletedFalseOrderByMatchScoreDesc(
        Long organisationId, Long checkId);

    List<ScreeningHit> findByOrganisationIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, String status);

    Optional<ScreeningHit> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    long countByOrganisationIdAndStatusAndIsDeletedFalse(Long organisationId, String status);

    long countByOrganisationIdAndSlaBreachTrueAndIsDeletedFalse(Long organisationId);

    long countByOrganisationIdAndIsDeletedFalse(Long organisationId);

    /** SLA-sweeper query — PENDING_REVIEW hits older than the cutoff that haven't tripped yet. */
    List<ScreeningHit> findByOrganisationIdAndStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
        Long organisationId, String status, LocalDateTime cutoff);

    /** Cross-tenant sweep variant — used by the scheduler so it picks up every org. */
    List<ScreeningHit> findByStatusAndSlaBreachFalseAndCreatedAtBeforeAndIsDeletedFalse(
        String status, LocalDateTime cutoff);

    /** Used by the dashboard's false-positive-rate calc. */
    long countByOrganisationIdAndStatusInAndIsDeletedFalse(
        Long organisationId, java.util.Collection<String> statuses);
}
