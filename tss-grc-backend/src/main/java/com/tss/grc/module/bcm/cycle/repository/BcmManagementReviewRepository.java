package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmManagementReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcmManagementReviewRepository extends JpaRepository<BcmManagementReview, Long> {
    List<BcmManagementReview> findByOrganisationIdOrderByReviewDateDesc(Long organisationId);
    Optional<BcmManagementReview> findByIdAndOrganisationId(Long id, Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
