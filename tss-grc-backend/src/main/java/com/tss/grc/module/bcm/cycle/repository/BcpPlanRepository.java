package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcpPlanRepository extends JpaRepository<BcpPlan, Long> {

    Page<BcpPlan> findByOrganisationIdOrderByIdDesc(Long organisationId, Pageable pageable);

    Optional<BcpPlan> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<BcpPlan> findByOrganisationIdAndPlanCode(Long organisationId, String planCode);

    List<BcpPlan> findByOrganisationIdAndPlanTypeOrderByIdDesc(Long organisationId, String planType);

    List<BcpPlan> findByOrganisationIdAndSourceBiaIdOrderByIdDesc(Long organisationId, Long sourceBiaId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndPlanTypeAndStatus(Long organisationId, String planType, String status);
}
