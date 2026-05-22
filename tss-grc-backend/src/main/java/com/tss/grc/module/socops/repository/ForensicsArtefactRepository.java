package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.ForensicsArtefact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ForensicsArtefactRepository extends JpaRepository<ForensicsArtefact, Long> {
    Optional<ForensicsArtefact> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<ForensicsArtefact> findByOrganisationIdAndArtefactCode(Long organisationId, String artefactCode);
    Page<ForensicsArtefact> findByOrganisationIdOrderByCapturedAtDesc(Long organisationId, Pageable pageable);
    List<ForensicsArtefact> findByOrganisationIdAndCaseCode(Long organisationId, String caseCode);
    long countByOrganisationIdAndLegalHold(Long organisationId, Boolean legalHold);
    long countByOrganisationIdAndIntegrityStatus(Long organisationId, String integrityStatus);

    /** Artefacts under legal hold whose retention deadline is approaching —
     *  the sweep fires {@code FORENSICS_HOLD_DISPOSAL_DUE} so legal counsel
     *  can confirm extension or release before the disposal action runs. */
    @Query("""
        SELECT a FROM ForensicsArtefact a
        WHERE a.organisationId = :organisationId
          AND a.legalHold = true
          AND a.disposedAt IS NULL
          AND a.retentionUntil IS NOT NULL
          AND a.retentionUntil BETWEEN :from AND :to
        ORDER BY a.retentionUntil ASC
    """)
    List<ForensicsArtefact> findHoldDisposalDueBetween(Long organisationId, LocalDateTime from, LocalDateTime to);
}
