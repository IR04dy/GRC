package com.tss.grc.module.audit_lc.repository;

import com.tss.grc.module.audit_lc.entity.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findByOrganisationIdOrderByRaisedAtDesc(Long organisationId);
    List<Finding> findByOrganisationIdAndEngagementCodeOrderByRaisedAtDesc(Long organisationId, String engagementCode);
    Optional<Finding> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
}
