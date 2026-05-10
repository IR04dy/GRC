package com.tss.grc.module.risk_lc.repository;

import com.tss.grc.module.risk_lc.entity.TreatmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Treatment-plan repository — every finder is org-scoped per the multi-tenant
 * pattern documented in CLAUDE.md.
 */
public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, Long> {

    List<TreatmentPlan> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    List<TreatmentPlan> findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(Long organisationId, Long riskId);

    Optional<TreatmentPlan> findByOrganisationIdAndExternalId(Long organisationId, String externalId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
