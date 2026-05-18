package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcpPlanAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BcpPlanAcknowledgementRepository extends JpaRepository<BcpPlanAcknowledgement, Long> {

    List<BcpPlanAcknowledgement> findByOrganisationIdAndPlanIdOrderByDistributedAtDesc(
        Long organisationId, Long planId);

    List<BcpPlanAcknowledgement> findByOrganisationIdAndPlanIdAndPlanVersionOrderByDistributedAtDesc(
        Long organisationId, Long planId, Integer planVersion);

    Optional<BcpPlanAcknowledgement> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByOrganisationIdAndPlanIdAndPlanVersionAndStatus(
        Long organisationId, Long planId, Integer planVersion, String status);

    /**
     * Mark all open acknowledgements for an older version of a plan as STALE
     * when a newer version is published. Used by {@code PlanDistributionService.markStale}.
     */
    @Modifying
    @Query("update BcpPlanAcknowledgement a "
         + "   set a.status = 'STALE', a.updatedAt = CURRENT_TIMESTAMP "
         + " where a.organisationId = :org "
         + "   and a.planId = :planId "
         + "   and a.planVersion < :currentVersion "
         + "   and a.status in ('PENDING', 'ACKNOWLEDGED', 'DECLINED')")
    int markStaleBelow(@Param("org") Long org,
                       @Param("planId") Long planId,
                       @Param("currentVersion") Integer currentVersion);
}
