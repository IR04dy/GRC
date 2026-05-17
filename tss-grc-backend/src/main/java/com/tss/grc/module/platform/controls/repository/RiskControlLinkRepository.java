package com.tss.grc.module.platform.controls.repository;

import com.tss.grc.module.platform.controls.entity.RiskControlLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskControlLinkRepository extends JpaRepository<RiskControlLink, Long> {

    List<RiskControlLink> findByOrganisationIdAndRiskIdOrderByIdAsc(Long organisationId, Long riskId);

    List<RiskControlLink> findByOrganisationIdAndControlIdOrderByIdAsc(Long organisationId, Long controlId);

    Optional<RiskControlLink> findByRiskIdAndControlId(Long riskId, Long controlId);

    Optional<RiskControlLink> findByIdAndOrganisationId(Long id, Long organisationId);
}
