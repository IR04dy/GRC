package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitteeResolutionVoteRepository extends JpaRepository<CommitteeResolutionVote, Long> {
    List<CommitteeResolutionVote> findByResolutionId(Long resolutionId);
    Optional<CommitteeResolutionVote> findByResolutionIdAndMemberName(Long resolutionId, String memberName);
}
