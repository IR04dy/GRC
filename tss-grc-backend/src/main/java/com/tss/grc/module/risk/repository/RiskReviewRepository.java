package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskReviewRepository extends JpaRepository<RiskReview, Long> {

    /** Newest first — for the per-risk review-history panel. */
    List<RiskReview> findByOrganisationIdAndRiskIdOrderByReviewedAtDesc(Long organisationId, Long riskId);

    long countByOrganisationIdAndRiskId(Long organisationId, Long riskId);
}
