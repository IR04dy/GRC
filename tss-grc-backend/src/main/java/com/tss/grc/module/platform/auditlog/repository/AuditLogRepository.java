package com.tss.grc.module.platform.auditlog.repository;

import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // ─── F2-31: hash-chain sealing and verification ───────────────────
    // Ordered by id, never by created_at: created_at is a wall-clock value the chain HASHES, so
    // ordering by it would let a row with a doctored timestamp change its own position in the chain.

    @Query("SELECT DISTINCT a.organisationId FROM AuditLogEntry a WHERE a.rowHash IS NULL")
    List<Long> findOrganisationIdsWithUnsealedRows();

    // No default-method conveniences here on purpose: Mockito INTERCEPTS default methods on a mocked
    // interface rather than running them, so a `findUnsealedByOrganisation(org, limit)` wrapper would
    // return empty in tests no matter how carefully the underlying query was stubbed — and a sealing
    // test that seals nothing passes every assertion about an intact chain.
    @Query("SELECT a FROM AuditLogEntry a WHERE a.organisationId = :org AND a.rowHash IS NULL "
         + "ORDER BY a.id ASC")
    List<AuditLogEntry> findUnsealed(@Param("org") Long org, Pageable pageable);

    @Query("SELECT a FROM AuditLogEntry a WHERE a.organisationId = :org AND a.rowHash IS NOT NULL "
         + "ORDER BY a.id ASC")
    List<AuditLogEntry> findSealedByOrganisationOrderById(@Param("org") Long org);

    @Query("SELECT a.rowHash FROM AuditLogEntry a WHERE a.organisationId = :org "
         + "AND a.rowHash IS NOT NULL AND a.id = "
         + "(SELECT MAX(b.id) FROM AuditLogEntry b WHERE b.organisationId = :org "
         + " AND b.rowHash IS NOT NULL)")
    String findLastSealedRowHash(@Param("org") Long org);

    @Query("SELECT MAX(a.id) FROM AuditLogEntry a WHERE a.organisationId = :org "
         + "AND a.rowHash IS NOT NULL")
    Long findLastSealedId(@Param("org") Long org);

    long countByOrganisationIdAndRowHashIsNull(Long organisationId);
}
