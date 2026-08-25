package com.tss.grc.module.reginspect.repository;

import com.tss.grc.module.reginspect.entity.RegDocumentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegDocumentRequestRepository
        extends JpaRepository<RegDocumentRequest, Long> {

    List<RegDocumentRequest> findByInspectionIdAndOrganisationIdOrderByRequestedAtDesc(
            Long inspectionId, Long organisationId);

    Optional<RegDocumentRequest> findByIdAndOrganisationId(Long id, Long organisationId);

    long countByInspectionIdAndOrganisationIdAndStatus(
            Long inspectionId, Long organisationId, String status);

    /**
     * Find document requests still open whose due date is before {@code today}.
     * Used by the F8 scheduler to fire {@code REG_DOCUMENT_REQUEST_OVERDUE}.
     */
    List<RegDocumentRequest> findByOrganisationIdAndStatusInAndDueDateBefore(
            Long organisationId, java.util.Collection<String> statuses, LocalDate today);

    /**
     * F2-355: the DR reference-mint seed — the highest ordinal (the last {@code -NNN} segment)
     * across ALL document requests for an inspection, incl. soft-deleted ones (collision-safe: a
     * used ordinal is never re-issued). {@code createDocumentRequest} previously seeded from
     * {@code findBy…OrderByRequestedAtDesc(...).size() + 1}, which respects {@code @Where(is_deleted =
     * 0)} — so a soft-delete gap or a concurrent create re-mints an ordinal already held by a live
     * DR, producing a DUPLICATE {@code <inspRef>-DR-NNN} reference (milder than F2-354's 500:
     * {@code reginspect_document_request.reference} carries no unique index, so the collision is a
     * silent duplicate a regulator could cite ambiguously). Every DR reference ends {@code -NNN}, so
     * {@code RIGHT(reference, CHARINDEX('-', REVERSE(reference)) - 1)} is the ordinal; {@code TRY_CAST}
     * ignores a non-numeric tail (e.g. a random-suffix backstop); {@code ISNULL(..., 0)} seeds the
     * first DR at 1. The F2-337 / F2-351 / F2-353 / F2-354 count-minter shape.
     */
    @Query(value =
        "SELECT ISNULL(MAX(TRY_CAST("
      + "RIGHT(reference, CHARINDEX('-', REVERSE(reference)) - 1) AS INT)), 0) "
      + "FROM reginspect.reginspect_document_request "
      + "WHERE inspection_id = :insp AND organisation_id = :org",
        nativeQuery = true)
    int findMaxDrOrdinal(@Param("insp") Long inspectionId, @Param("org") Long org);

    /** Live-only (respects {@code @Where}) — the skip-loop's collision check for a concurrent mint. */
    boolean existsByInspectionIdAndOrganisationIdAndReference(
            Long inspectionId, Long organisationId, String reference);
}
