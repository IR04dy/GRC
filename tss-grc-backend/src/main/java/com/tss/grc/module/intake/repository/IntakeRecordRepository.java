package com.tss.grc.module.intake.repository;

import com.tss.grc.module.intake.entity.IntakeRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * IntakeRecord repository — DD-82.
 *
 * Every finder is org-scoped per the multi-tenancy contract. Reference-code
 * lookup is the public case-tracker's primary access path so it stays here
 * even though it doesn't appear to need org-scoping (anonymous submitters
 * already know their code; the service-layer org-check applies on the
 * authenticated triager paths).
 */
@Repository
public interface IntakeRecordRepository extends JpaRepository<IntakeRecord, Long> {

    /** Triager inbox — list every intake for the current org. */
    Page<IntakeRecord> findByOrganisationId(Long organisationId, Pageable pageable);

    /** Triager inbox filtered by status (open / overdue / promoted / closed). */
    Page<IntakeRecord> findByOrganisationIdAndStatus(Long organisationId, String status, Pageable pageable);

    /** Triager inbox filtered by kind (e.g. only DSR_REQUEST submissions). */
    Page<IntakeRecord> findByOrganisationIdAndKind(Long organisationId, String kind, Pageable pageable);

    /** Public case-tracker lookup. Returns Optional so the controller can
     *  emit a 404 without leaking whether the code "exists but is wrong tenant". */
    Optional<IntakeRecord> findByReferenceCode(String referenceCode);
}
