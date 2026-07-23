package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeMeetingAttendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CommitteeMeetingAttendeeRepository extends JpaRepository<CommitteeMeetingAttendee, Long> {
    List<CommitteeMeetingAttendee> findByCommitteeMeetingId(Long committeeMeetingId);

    @Transactional
    void deleteByCommitteeMeetingId(Long committeeMeetingId);
}
