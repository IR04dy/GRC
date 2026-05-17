package com.tss.grc.module.platform.controls.repository;

import com.tss.grc.module.platform.controls.entity.ControlObjective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ControlObjectiveRepository extends JpaRepository<ControlObjective, Long> {

    List<ControlObjective> findByOrganisationIdOrderByDomainAscObjectiveCodeAsc(Long organisationId);

    List<ControlObjective> findByOrganisationIdAndDomainOrderByObjectiveCodeAsc(Long organisationId, String domain);

    Optional<ControlObjective> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ControlObjective> findByOrganisationIdAndObjectiveCode(Long organisationId, String objectiveCode);
}
