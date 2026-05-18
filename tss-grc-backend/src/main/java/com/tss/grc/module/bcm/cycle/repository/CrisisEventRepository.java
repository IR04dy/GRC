package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.CrisisEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrisisEventRepository extends JpaRepository<CrisisEvent, Long> {

    Page<CrisisEvent> findByOrganisationIdOrderByIdDesc(Long organisationId, Pageable pageable);

    Optional<CrisisEvent> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<CrisisEvent> findByOrganisationIdAndEventCode(Long organisationId, String eventCode);

    List<CrisisEvent> findByOrganisationIdAndStatusOrderByIdDesc(Long organisationId, String status);

    /** Active events = not CLOSED and not CANCELLED. Used by the dashboard banner. */
    List<CrisisEvent> findByOrganisationIdAndStatusNotInOrderByIdDesc(Long organisationId, List<String> excludedStatuses);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndSeverityGreaterThanEqual(Long organisationId, Integer minSeverity);
}
