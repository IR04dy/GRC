package com.tss.grc.module.sust.climate.repository;

import com.tss.grc.module.sust.climate.entity.TcfdClimateScenario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TcfdClimateScenarioRepository extends JpaRepository<TcfdClimateScenario, Long> {

    List<TcfdClimateScenario> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    List<TcfdClimateScenario> findByOrganisationIdAndLifecycleStageAndIsDeletedFalseOrderByCreatedAtDesc(
            Long organisationId, String lifecycleStage);

    Optional<TcfdClimateScenario> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    Optional<TcfdClimateScenario> findByOrganisationIdAndScenarioCodeAndIsDeletedFalse(Long organisationId, String scenarioCode);

    long countByOrganisationIdAndScenarioCode(Long organisationId, String scenarioCode);
}
