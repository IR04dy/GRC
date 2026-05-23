package com.tss.grc.module.risk.bowtie.repository;

import com.tss.grc.module.risk.bowtie.entity.BowTiePreventiveControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BowTiePreventiveControlRepository extends JpaRepository<BowTiePreventiveControl, Long> {

    List<BowTiePreventiveControl> findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(Long organisationId, Long bowtieId);

    List<BowTiePreventiveControl> findByOrganisationIdAndThreatIdAndIsDeletedFalseOrderByControlOrderAsc(Long organisationId, Long threatId);

    Optional<BowTiePreventiveControl> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);
}
