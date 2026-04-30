package com.tss.grc.module.incident.repository;

import com.tss.grc.module.incident.entity.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Page<Incident> findByOrganisationId(Long organisationId, Pageable pageable);

    @Query("""
        SELECT i FROM Incident i
        WHERE i.organisationId = :orgId
          AND (:severity IS NULL OR i.severity = :severity)
          AND (:status   IS NULL OR i.status   = :status)
          AND (:category IS NULL OR i.category = :category)
        """)
    Page<Incident> findWithFilters(
        @Param("orgId") Long orgId,
        @Param("severity") String severity,
        @Param("status") String status,
        @Param("category") String category,
        Pageable pageable
    );

    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndSeverity(Long organisationId, String severity);

    @Query("SELECT i FROM Incident i WHERE i.organisationId = :orgId ORDER BY i.createdAt DESC")
    List<Incident> findRecent(@Param("orgId") Long orgId, Pageable pageable);
}
