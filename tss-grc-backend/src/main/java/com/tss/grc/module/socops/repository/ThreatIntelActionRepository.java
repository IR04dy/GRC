package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.ThreatIntelAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThreatIntelActionRepository extends JpaRepository<ThreatIntelAction, Long> {

    Optional<ThreatIntelAction> findByIdAndOrganisationId(Long id, Long organisationId);

    /** The full action history for one IOC (newest first), tenant-scoped. */
    List<ThreatIntelAction> findByOrganisationIdAndThreatIntelIdOrderByActionedAtDesc(
        Long organisationId, Long threatIntelId);

    long countByOrganisationIdAndThreatIntelId(Long organisationId, Long threatIntelId);
}
