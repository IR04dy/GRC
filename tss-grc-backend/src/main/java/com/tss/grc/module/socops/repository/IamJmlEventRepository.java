package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.IamJmlEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IamJmlEventRepository extends JpaRepository<IamJmlEvent, Long> {
    Optional<IamJmlEvent> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<IamJmlEvent> findByOrganisationIdAndEventCode(Long organisationId, String eventCode);
    Page<IamJmlEvent> findByOrganisationIdOrderByOccurredAtDesc(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndPrivilegedAndOutcome(Long organisationId, Boolean privileged, String outcome);
    long countByOrganisationIdAndSodViolationDetected(Long organisationId, Boolean sodViolationDetected);

    /** Privileged-access reviews due in the window — feeds the F8 access-
     *  attestation reminder loop. */
    @Query("""
        SELECT e FROM IamJmlEvent e
        WHERE e.organisationId = :organisationId
          AND e.privileged = true
          AND e.reviewDueAt IS NOT NULL
          AND e.reviewDueAt BETWEEN :from AND :to
          AND e.revokedAt IS NULL
        ORDER BY e.reviewDueAt ASC
    """)
    List<IamJmlEvent> findPrivilegedReviewsDueBetween(Long organisationId, LocalDateTime from, LocalDateTime to);
}
