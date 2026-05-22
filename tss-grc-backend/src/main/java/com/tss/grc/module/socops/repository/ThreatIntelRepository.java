package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.ThreatIntel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThreatIntelRepository extends JpaRepository<ThreatIntel, Long> {

    Optional<ThreatIntel> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<ThreatIntel> findByOrganisationIdAndSourceAndExternalId(
        Long organisationId, String source, String externalId);

    Page<ThreatIntel> findByOrganisationIdOrderByPublishedAtDesc(Long organisationId, Pageable pageable);

    long countByOrganisationIdAndActioned(Long organisationId, Boolean actioned);

    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
}
