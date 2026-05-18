package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskControlRepository extends JpaRepository<RiskControl, Long> {
    List<RiskControl> findByOrganisationIdAndRiskIdOrderByIdAsc(Long organisationId, Long riskId);
    List<RiskControl> findByOrganisationIdAndControlIdOrderByIdAsc(Long organisationId, Long controlId);
    Optional<RiskControl> findByOrganisationIdAndRiskIdAndControlId(Long organisationId, Long riskId, Long controlId);
    long countByOrganisationIdAndEffectiveness(Long organisationId, String effectiveness);
}
