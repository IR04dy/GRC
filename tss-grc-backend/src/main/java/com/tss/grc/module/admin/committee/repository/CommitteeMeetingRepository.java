package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitteeMeetingRepository extends JpaRepository<CommitteeMeeting, Long> {
    Page<CommitteeMeeting> findByOrganisationIdOrderByScheduledAtDesc(Long organisationId, Pageable pageable);
    Page<CommitteeMeeting> findByOrganisationIdAndStatusOrderByScheduledAtDesc(Long organisationId, String status, Pageable pageable);
    List<CommitteeMeeting> findByOrganisationIdAndCommitteeIdOrderByScheduledAtDesc(Long organisationId, Long committeeId);
    Optional<CommitteeMeeting> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<CommitteeMeeting> findByOrganisationIdAndMeetingCode(Long organisationId, String meetingCode);
}
