package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskImpactRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskImpactRatingRepository extends JpaRepository<RiskImpactRating, Long> {
    List<RiskImpactRating> findByOrganisationIdAndRiskIdOrderByDimensionAsc(Long organisationId, Long riskId);
}
