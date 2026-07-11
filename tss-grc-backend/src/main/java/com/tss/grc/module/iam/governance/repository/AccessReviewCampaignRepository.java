package com.tss.grc.module.iam.governance.repository;

import com.tss.grc.module.iam.governance.entity.AccessReviewCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** RBAC Access-Governance P2 — recertification campaign finders. */
public interface AccessReviewCampaignRepository extends JpaRepository<AccessReviewCampaign, Long> {

    List<AccessReviewCampaign> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    Optional<AccessReviewCampaign> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);
}
