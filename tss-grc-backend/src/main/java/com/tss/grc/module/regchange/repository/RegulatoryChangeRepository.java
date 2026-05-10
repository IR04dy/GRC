package com.tss.grc.module.regchange.repository;

import com.tss.grc.module.regchange.entity.RegulatoryChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

// Explicit bean name avoids collision with the legacy
// com.tss.grc.module.compliance.repository.RegulatoryChangeRepository.
@Repository("regchangeRegulatoryChangeRepository")
public interface RegulatoryChangeRepository extends JpaRepository<RegulatoryChange, Long> {
    List<RegulatoryChange> findByOrganisationIdOrderByPublishedAtDesc(Long organisationId);
    Optional<RegulatoryChange> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    List<RegulatoryChange> findByOrganisationIdAndRegulator(Long organisationId, String regulator);
    List<RegulatoryChange> findByOrganisationIdAndStatus(Long organisationId, String status);
}
