package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommitteeResolutionRepository extends JpaRepository<CommitteeResolution, Long> {

    Optional<CommitteeResolution> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<CommitteeResolution> findByOrganisationIdAndResolutionCode(Long organisationId, String resolutionCode);

    Page<CommitteeResolution> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    List<CommitteeResolution> findByOrganisationIdAndMeetingIdOrderBySeqAsc(Long organisationId, Long meetingId);

    List<CommitteeResolution> findByOrganisationIdAndCommitteeIdOrderByCreatedAtDesc(Long organisationId, Long committeeId);

    long countByOrganisationIdAndOutcome(Long organisationId, String outcome);

    /** Resolutions whose review deadline is approaching — drives an F8
     *  reminder so the committee can re-confirm or sunset them in time. */
    @Query("""
        SELECT r FROM CommitteeResolution r
        WHERE r.organisationId = :organisationId
          AND r.reviewRequiredBy IS NOT NULL
          AND r.reviewRequiredBy BETWEEN :from AND :to
        ORDER BY r.reviewRequiredBy ASC
    """)
    List<CommitteeResolution> findReviewDueBetween(Long organisationId, LocalDate from, LocalDate to);
}
