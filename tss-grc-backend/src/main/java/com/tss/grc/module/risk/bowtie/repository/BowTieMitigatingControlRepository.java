package com.tss.grc.module.risk.bowtie.repository;

import com.tss.grc.module.risk.bowtie.entity.BowTieMitigatingControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BowTieMitigatingControlRepository extends JpaRepository<BowTieMitigatingControl, Long> {

    List<BowTieMitigatingControl> findByOrganisationIdAndBowtieIdAndIsDeletedFalseOrderByControlOrderAsc(Long organisationId, Long bowtieId);

    List<BowTieMitigatingControl> findByOrganisationIdAndConsequenceIdAndIsDeletedFalseOrderByControlOrderAsc(Long organisationId, Long consequenceId);

    Optional<BowTieMitigatingControl> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);
}
