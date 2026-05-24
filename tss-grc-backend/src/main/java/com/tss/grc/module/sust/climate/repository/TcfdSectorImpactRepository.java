package com.tss.grc.module.sust.climate.repository;

import com.tss.grc.module.sust.climate.entity.TcfdSectorImpact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TcfdSectorImpactRepository extends JpaRepository<TcfdSectorImpact, Long> {

    List<TcfdSectorImpact> findByOrganisationIdAndScenarioIdAndIsDeletedFalseOrderBySectorCodeAsc(
            Long organisationId, Long scenarioId);

    List<TcfdSectorImpact> findByOrganisationIdAndIsDeletedFalse(Long organisationId);

    Optional<TcfdSectorImpact> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);
}
