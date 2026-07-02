package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskLinkRepository extends JpaRepository<RiskLink, Long> {
    List<RiskLink> findByOrganisationIdAndRiskIdOrderByIdAsc(Long organisationId, Long riskId);
    Optional<RiskLink> findByIdAndOrganisationId(Long id, Long organisationId);

    /**
     * All live links of a given type pointing at one target row — used by S-F to
     * find/dedupe the CLIMATE bridge links for a climate risk/opportunity across
     * risks (@Where filters out soft-deleted rows).
     */
    List<RiskLink> findByOrganisationIdAndLinkTypeAndTargetId(Long organisationId, String linkType, Long targetId);
}
