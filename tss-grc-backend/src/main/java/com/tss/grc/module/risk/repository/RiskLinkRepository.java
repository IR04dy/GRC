package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskLinkRepository extends JpaRepository<RiskLink, Long> {
    List<RiskLink> findByOrganisationIdAndRiskIdOrderByIdAsc(Long organisationId, Long riskId);
    Optional<RiskLink> findByIdAndOrganisationId(Long id, Long organisationId);
}
