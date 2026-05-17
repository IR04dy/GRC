package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmRisk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcmRiskRepository extends JpaRepository<BcmRisk, Long> {

    List<BcmRisk> findByOrganisationIdAndCycleIdOrderByIdDesc(Long organisationId, Long cycleId);

    List<BcmRisk> findByOrganisationIdOrderByIdDesc(Long organisationId);

    Optional<BcmRisk> findByIdAndOrganisationId(Long id, Long organisationId);

    List<BcmRisk> findByOrganisationIdAndRelatedServiceIdOrderByIdDesc(Long organisationId, Long serviceId);

    List<BcmRisk> findByOrganisationIdAndStatusOrderByIdDesc(Long organisationId, String status);
}
