package com.tss.grc.module.audit_eng.repository;

import com.tss.grc.module.audit_eng.entity.AuditEngagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

// Explicit bean name avoids collision with legacy
// com.tss.grc.module.compliance.repository.AuditEngagementRepository.
@Repository("auditEngAuditEngagementRepository")
public interface AuditEngagementRepository extends JpaRepository<AuditEngagement, Long> {
    List<AuditEngagement> findByOrganisationIdOrderByPlanningStartedAtDesc(Long organisationId);
    Optional<AuditEngagement> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    List<AuditEngagement> findByOrganisationIdAndStatus(Long organisationId, String status);
    List<AuditEngagement> findByOrganisationIdAndStatusNotIn(Long organisationId, List<String> excluded);
}
