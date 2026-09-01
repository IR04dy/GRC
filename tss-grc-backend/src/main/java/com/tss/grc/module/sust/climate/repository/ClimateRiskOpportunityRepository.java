package com.tss.grc.module.sust.climate.repository;

import com.tss.grc.module.sust.climate.entity.ClimateRiskOpportunity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClimateRiskOpportunityRepository extends JpaRepository<ClimateRiskOpportunity, Long> {

    Page<ClimateRiskOpportunity> findByOrganisationIdOrderByRiskScoreDescIdDesc(Long org, Pageable p);

    Page<ClimateRiskOpportunity> findByOrganisationIdAndKindOrderByRiskScoreDescIdDesc(Long org, String kind, Pageable p);

    Page<ClimateRiskOpportunity> findByOrganisationIdAndStatusOrderByRiskScoreDescIdDesc(Long org, String status, Pageable p);

    Optional<ClimateRiskOpportunity> findByIdAndOrganisationId(Long id, Long org);

    /** Used by the dashboard widget — top-5 highest-scoring open items. */
    List<ClimateRiskOpportunity> findTop5ByOrganisationIdAndStatusNotOrderByRiskScoreDescIdDesc(Long org, String excludeStatus);

    /** Full per-org pull — used by the aggregate / treated-rate computation. */
    List<ClimateRiskOpportunity> findByOrganisationId(Long org);

    /**
     * F2-415 — the same ordering as the paged overload above, <b>uncapped</b>, for a
     * document that leaves the platform. {@code AnnexGeneratorService} fetched the CMA
     * annual-report annex's climate section through {@code PageRequest.of(0, 50)} and
     * then published {@code climates.size()} as {@code climateRiskCount} in the
     * machine-readable JSON — a windowed figure wearing an all-time label (F2-197: a
     * capped list is fine to RENDER and wrong to COUNT). Both overloads respect the
     * entity's {@code @Where(is_deleted = 0)}, so they agree on soft-delete scoping.
     */
    List<ClimateRiskOpportunity> findByOrganisationIdOrderByRiskScoreDescIdDesc(Long org);
}
