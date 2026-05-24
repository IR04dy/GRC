package com.tss.grc.module.audit_lc.ia_plan.repository;

import com.tss.grc.module.audit_lc.ia_plan.entity.IaEngagementMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped repository for {@link IaEngagementMilestone}. */
public interface IaEngagementMilestoneRepository extends JpaRepository<IaEngagementMilestone, Long> {

    List<IaEngagementMilestone> findByOrganisationIdAndEngagementIdAndIsDeletedFalseOrderByPlannedDateAsc(
        Long organisationId, Long engagementId);

    Optional<IaEngagementMilestone> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    Optional<IaEngagementMilestone> findByOrganisationIdAndEngagementIdAndMilestoneTypeAndIsDeletedFalse(
        Long organisationId, Long engagementId, String milestoneType);

    long countByOrganisationIdAndEngagementIdAndIsDeletedFalse(Long organisationId, Long engagementId);
}
