package com.tss.grc.module.bcm_lc.repository;

import com.tss.grc.module.bcm_lc.entity.Invocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InvocationRepository extends JpaRepository<Invocation, Long> {
    List<Invocation> findByOrganisationIdOrderByDetectedAtDesc(Long organisationId);
    Optional<Invocation> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    /** Active = anything past STANDBY and not yet CLOSED. */
    List<Invocation> findByOrganisationIdAndStatusNotInOrderByDetectedAtDesc(Long organisationId, List<String> excludedStatuses);
    List<Invocation> findByOrganisationIdAndSeverityInAndStatusNotIn(Long organisationId, List<String> severities, List<String> excludedStatuses);
}
