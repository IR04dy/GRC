package com.tss.grc.module.audit_lc.ia_plan.repository;

import com.tss.grc.module.audit_lc.ia_plan.entity.IaEngagement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped repository for {@link IaEngagement}. */
public interface IaEngagementRepository extends JpaRepository<IaEngagement, Long> {

    List<IaEngagement> findByOrganisationIdAndIsDeletedFalseOrderByEngagementCodeAsc(Long organisationId);

    List<IaEngagement> findByOrganisationIdAndPlanIdAndIsDeletedFalseOrderByEngagementCodeAsc(
        Long organisationId, Long planId);

    List<IaEngagement> findByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(
        Long organisationId, Long planId, String phase);

    Optional<IaEngagement> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    Optional<IaEngagement> findByOrganisationIdAndEngagementCodeAndIsDeletedFalse(
        Long organisationId, String engagementCode);

    boolean existsByOrganisationIdAndEngagementCodeAndIsDeletedFalse(
        Long organisationId, String engagementCode);

    long countByOrganisationIdAndPlanIdAndIsDeletedFalse(Long organisationId, Long planId);

    long countByOrganisationIdAndPlanIdAndPhaseAndIsDeletedFalse(
        Long organisationId, Long planId, String phase);
}
