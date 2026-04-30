package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.BcmPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BcmPlanRepository extends JpaRepository<BcmPlan, Long> {
    Page<BcmPlan> findByOrganisationId(Long organisationId, Pageable pageable);
}
