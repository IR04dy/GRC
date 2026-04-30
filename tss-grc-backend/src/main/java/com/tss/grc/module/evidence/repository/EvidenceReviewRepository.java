package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EvidenceReviewRepository extends JpaRepository<EvidenceReview, Long> {

    Page<EvidenceReview> findByOrganisationId(Long orgId, Pageable pageable);

    @Query("SELECT r FROM EvidenceReview r WHERE r.organisationId = :orgId " +
           "AND (:status IS NULL OR r.status = :status)")
    Page<EvidenceReview> findByOrgAndStatus(@Param("orgId") Long orgId,
                                             @Param("status") String status,
                                             Pageable pageable);

    List<EvidenceReview> findByEvidenceIdAndOrganisationId(Long evidenceId, Long orgId);
}
