package com.tss.grc.module.outsourcing.repository;

import com.tss.grc.module.outsourcing.entity.OngoingReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OngoingReviewRepository extends JpaRepository<OngoingReview, Long> {

    List<OngoingReview> findByOrganisationIdAndArrangementIdOrderByCreatedAtDesc(
            Long organisationId, Long arrangementId);

    Optional<OngoingReview> findByOrganisationIdAndId(Long organisationId, Long id);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
