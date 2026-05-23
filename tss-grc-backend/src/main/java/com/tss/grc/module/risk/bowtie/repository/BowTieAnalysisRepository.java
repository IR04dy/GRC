package com.tss.grc.module.risk.bowtie.repository;

import com.tss.grc.module.risk.bowtie.entity.BowTieAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BowTieAnalysisRepository extends JpaRepository<BowTieAnalysis, Long> {

    List<BowTieAnalysis> findByOrganisationIdAndIsDeletedFalseOrderByAssessmentDateDesc(Long organisationId);

    Optional<BowTieAnalysis> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    List<BowTieAnalysis> findByOrganisationIdAndLinkedRiskIdAndIsDeletedFalse(Long organisationId, Long linkedRiskId);

    List<BowTieAnalysis> findByOrganisationIdAndStatusInAndIsDeletedFalse(Long organisationId, List<String> statuses);

    long countByOrganisationIdAndBowtieCode(Long organisationId, String bowtieCode);
}
