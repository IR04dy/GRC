package com.tss.grc.module.platform.auditlog.repository;

import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    Page<AuditLogEntry> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    Page<AuditLogEntry> findByOrganisationIdAndActionTypeOrderByCreatedAtDesc(
        Long organisationId, String actionType, Pageable pageable);

    Page<AuditLogEntry> findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(
        Long organisationId, String sourceModule, Pageable pageable);

    Page<AuditLogEntry> findByOrganisationIdAndSeverityOrderByCreatedAtDesc(
        Long organisationId, String severity, Pageable pageable);

    List<AuditLogEntry> findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
        Long organisationId, String entityType, Long entityId);

    List<AuditLogEntry> findByOrganisationIdAndCorrelationIdOrderByCreatedAtAsc(
        Long organisationId, String correlationId);

    long countByOrganisationIdAndCreatedAtAfter(Long organisationId, LocalDateTime after);

    long countByOrganisationIdAndSeverityAndCreatedAtAfter(
        Long organisationId, String severity, LocalDateTime after);
}
