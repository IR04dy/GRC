package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.AuditEngagement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditEngagementRepository extends JpaRepository<AuditEngagement, Long> {

    Page<AuditEngagement> findByOrganisationIdOrderByEngagementLetterDateDesc(Long organisationId, Pageable pageable);

    List<AuditEngagement> findByOrganisationIdAndStatusOrderByEngagementLetterDateDesc(Long organisationId, String status);

    @Query("""
        SELECT a FROM AuditEngagement a
        WHERE a.organisationId = :orgId
          AND (:auditType IS NULL OR a.auditType = :auditType)
          AND (:status    IS NULL OR a.status    = :status)
          AND (:framework IS NULL OR a.framework = :framework)
        ORDER BY a.engagementLetterDate DESC
        """)
    Page<AuditEngagement> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("auditType") String auditType,
        @Param("status") String status,
        @Param("framework") String framework,
        Pageable pageable
    );

    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationId(Long organisationId);
}
