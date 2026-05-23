package com.tss.grc.module.risk.bowtie.repository;

import com.tss.grc.module.risk.bowtie.entity.BowTieThreat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BowTieThreatRepository extends JpaRepository<BowTieThreat, Long> {

    List<BowTieThreat> findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByThreatOrderAsc(Long organisationId, Long bowtieId);

    Optional<BowTieThreat> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);
}
