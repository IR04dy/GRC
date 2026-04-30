package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.AuditFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditFindingRepository extends JpaRepository<AuditFinding, Long> {

    Page<AuditFinding> findByOrganisationId(Long organisationId, Pageable pageable);

    @Query("""
        SELECT f FROM AuditFinding f
        WHERE f.organisationId = :orgId
          AND (:severity IS NULL OR f.severity = :severity)
          AND (:status   IS NULL OR f.status   = :status)
        """)
    Page<AuditFinding> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("severity") String severity,
        @Param("status") String status,
        Pageable pageable
    );

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
