package com.tss.grc.module.scm.resilience.repository;

import com.tss.grc.module.scm.resilience.entity.ExitPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExitPlanRepository extends JpaRepository<ExitPlan, Long> {

    Page<ExitPlan> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    Optional<ExitPlan> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ExitPlan> findByOrganisationIdAndPlanCode(Long organisationId, String planCode);

    List<ExitPlan> findByOrganisationIdAndVendorIdOrderByCreatedAtDesc(Long organisationId, Long vendorId);

    Page<ExitPlan> findByOrganisationIdAndStatusOrderByCreatedAtDesc(
            Long organisationId, String status, Pageable pageable);
}
