package com.tss.grc.module.bcm.drp.repository;

import com.tss.grc.module.bcm.drp.entity.DisasterRecoveryPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisasterRecoveryPlanRepository extends JpaRepository<DisasterRecoveryPlan, Long> {
    Page<DisasterRecoveryPlan> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);
    Page<DisasterRecoveryPlan> findByOrganisationIdAndStatusOrderByCreatedAtDesc(Long organisationId, String status, Pageable pageable);
    Optional<DisasterRecoveryPlan> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<DisasterRecoveryPlan> findByOrganisationIdAndPlanCode(Long organisationId, String planCode);

    /** All non-deleted DRPs for a tenant — used by DR-test trending (Slice 9-B). */
    List<DisasterRecoveryPlan> findAllByOrganisationId(Long organisationId);
}
