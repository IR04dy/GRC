package com.tss.grc.module.audit_lc.ia_plan.repository;

import com.tss.grc.module.audit_lc.ia_plan.entity.IaAnnualPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped repository for {@link IaAnnualPlan}. */
public interface IaAnnualPlanRepository extends JpaRepository<IaAnnualPlan, Long> {

    List<IaAnnualPlan> findByOrganisationIdAndIsDeletedFalseOrderByCycleYearDesc(Long organisationId);

    Optional<IaAnnualPlan> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    Optional<IaAnnualPlan> findByOrganisationIdAndPlanCodeAndIsDeletedFalse(Long organisationId, String planCode);

    boolean existsByOrganisationIdAndPlanCodeAndIsDeletedFalse(Long organisationId, String planCode);

    long countByOrganisationIdAndIsDeletedFalse(Long organisationId);
}
