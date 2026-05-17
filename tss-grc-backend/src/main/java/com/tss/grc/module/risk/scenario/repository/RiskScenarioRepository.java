package com.tss.grc.module.risk.scenario.repository;

import com.tss.grc.module.risk.scenario.entity.RiskScenario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskScenarioRepository extends JpaRepository<RiskScenario, Long> {

    Page<RiskScenario> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    Optional<RiskScenario> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<RiskScenario> findByOrganisationIdAndScenarioCode(Long organisationId, String scenarioCode);

    /** All scenarios attached to one risk — used by the risk-detail page. */
    List<RiskScenario> findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(Long organisationId, Long riskId);

    /** Scenarios filtered by status — DRAFT inbox, APPROVED list, etc. */
    Page<RiskScenario> findByOrganisationIdAndStatusOrderByCreatedAtDesc(
            Long organisationId, String status, Pageable pageable);
}
