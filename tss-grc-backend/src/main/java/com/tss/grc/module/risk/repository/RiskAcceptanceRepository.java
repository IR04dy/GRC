package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RiskAcceptanceRepository extends JpaRepository<RiskAcceptance, Long> {

    List<RiskAcceptance> findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(Long organisationId, Long riskId);

    Optional<RiskAcceptance> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Expiry sweep (system-wide, all tenants) — acceptances in a status past their expiry. */
    List<RiskAcceptance> findByStatusAndExpiresAtLessThan(String status, LocalDate date);
}
