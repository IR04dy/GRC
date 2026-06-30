package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceRequestComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRequestCommentRepository extends JpaRepository<EvidenceRequestComment, Long> {

    /** The comment thread for one request, oldest first (chronological reading order). */
    List<EvidenceRequestComment> findByOrganisationIdAndRequestIdOrderByCreatedAtAsc(Long organisationId, Long requestId);
}
