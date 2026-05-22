package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeMeetingPaper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CommitteeMeetingPaperRepository extends JpaRepository<CommitteeMeetingPaper, Long> {

    Optional<CommitteeMeetingPaper> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<CommitteeMeetingPaper> findByOrganisationIdAndPaperCode(Long organisationId, String paperCode);

    /** All papers for one meeting — ordered by agenda item then circulation. */
    @Query("""
        SELECT p FROM CommitteeMeetingPaper p
        WHERE p.organisationId = :organisationId
          AND p.meetingId = :meetingId
        ORDER BY COALESCE(p.agendaItemSeq, 999) ASC, p.createdAt ASC
    """)
    List<CommitteeMeetingPaper> findForMeeting(Long organisationId, Long meetingId);

    /** Late papers (circulated within 24h of meeting) — drives the
     *  "late papers" KPI on the meeting detail and the AR-156 F8
     *  governance-discipline rule. */
    List<CommitteeMeetingPaper> findByOrganisationIdAndMeetingIdAndLatePaperOrderByCreatedAtAsc(
        Long organisationId, Long meetingId, Short latePaper);

    long countByOrganisationIdAndMeetingId(Long organisationId, Long meetingId);
}
