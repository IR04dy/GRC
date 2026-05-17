package com.tss.grc.module.platform.controls.repository;

import com.tss.grc.module.platform.controls.entity.Control;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ControlRepository extends JpaRepository<Control, Long> {

    List<Control> findByOrganisationIdOrderByDomainAscControlCodeAsc(Long organisationId);

    List<Control> findByOrganisationIdAndDomainOrderByControlCodeAsc(Long organisationId, String domain);

    List<Control> findByOrganisationIdAndStatusOrderByControlCodeAsc(Long organisationId, String status);

    List<Control> findByOrganisationIdAndObjectiveIdOrderByControlCodeAsc(Long organisationId, Long objectiveId);

    Optional<Control> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<Control> findByOrganisationIdAndControlCodeAndVersion(Long organisationId, String controlCode, String version);
}
