package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskAttestation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskAttestationRepository extends JpaRepository<RiskAttestation, Long> {
    /** Newest first — for the per-risk audit trail panel. */
    List<RiskAttestation> findByOrganisationIdAndRiskIdOrderByAttestedAtDesc(
        Long organisationId, Long riskId);

    long countByOrganisationIdAndRiskId(Long organisationId, Long riskId);
}
