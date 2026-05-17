package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.ContinuityStrategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContinuityStrategyRepository extends JpaRepository<ContinuityStrategy, Long> {

    List<ContinuityStrategy> findByOrganisationIdOrderByIdDesc(Long organisationId);

    List<ContinuityStrategy> findByOrganisationIdAndCycleIdOrderByIdDesc(Long organisationId, Long cycleId);

    Optional<ContinuityStrategy> findByIdAndOrganisationId(Long id, Long organisationId);

    List<ContinuityStrategy> findByOrganisationIdAndRelatedServiceIdOrderByIdDesc(Long organisationId, Long serviceId);

    List<ContinuityStrategy> findByOrganisationIdAndApprovalStatusOrderByIdDesc(Long organisationId, String approvalStatus);
}
