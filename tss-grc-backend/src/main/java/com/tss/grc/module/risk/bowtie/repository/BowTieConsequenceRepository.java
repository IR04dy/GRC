package com.tss.grc.module.risk.bowtie.repository;

import com.tss.grc.module.risk.bowtie.entity.BowTieConsequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BowTieConsequenceRepository extends JpaRepository<BowTieConsequence, Long> {

    List<BowTieConsequence> findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByConsequenceOrderAsc(Long organisationId, Long bowtieId);

    Optional<BowTieConsequence> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);
}
