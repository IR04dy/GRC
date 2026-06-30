package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskMethodologyConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskMethodologyConfigRepository extends JpaRepository<RiskMethodologyConfig, Long> {
    Optional<RiskMethodologyConfig> findByOrganisationId(Long organisationId);
}
