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

    /**
     * ⚠⚠ F2-324 — every row a module wrote, <b>including the ones written before
     * {@code source_module} was derived</b>.
     *
     * <p>{@code BcmEvidencePackageService} selected its {@code audit_log.csv} on
     * {@code source_module = 'bcm'} alone, and measured over the live ledger that carried <b>15.0% of
     * the BCM trail in dev and 6.8% in prod</b> — omitting 136 and 119 {@code BcmAssessmentCycle}
     * rows respectively, i.e. the audit trail of the very cycle the package documents. F2-324 fills
     * the column for every row written from now on; it may not fill it retrospectively, because the
     * ledger is append-only and F2-31 hash-chains it, so a back-fill would rewrite history <b>and</b>
     * break every seal.
     *
     * <p>🔑 So the historical rows are recovered through the column that IS populated. The
     * {@code entityTypes} set must be <b>derived</b> (see {@code BcmEvidencePackageService}) rather
     * than typed out: a hand-list is the artefact [[F2-217]] found measuring its own contents.
     *
     * <p>⚠ Not paginated, matching the caller — the package deliberately excludes no rows.
     */
    @Query("""
        SELECT a FROM AuditLogEntry a
         WHERE a.organisationId = :org
           AND (a.sourceModule = :module OR a.entityType IN :entityTypes)
         ORDER BY a.createdAt DESC
        """)
    List<AuditLogEntry> findForModuleOrEntityTypes(
        @Param("org") Long org,
        @Param("module") String module,
        @Param("entityTypes") java.util.Collection<String> entityTypes);

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
