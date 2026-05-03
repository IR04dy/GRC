package com.tss.grc.module.ethics.repository;

import com.tss.grc.module.ethics.entity.AmlAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AmlAlertRepository extends JpaRepository<AmlAlert, Long> {

    @Query("""
        SELECT a FROM AmlAlert a
        WHERE a.organisationId = :orgId
          AND (:status   IS NULL OR a.status     = :status)
          AND (:type     IS NULL OR a.alertType  = :type)
          AND (:severity IS NULL OR a.severity   = :severity)
        ORDER BY a.firstDetectedAt DESC
        """)
    Page<AmlAlert> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("status") String status,
        @Param("type")   String type,
        @Param("severity") String severity,
        Pageable pageable
    );

    long countByOrganisationId(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndStrFiled(Long organisationId, Boolean strFiled);
    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
}
