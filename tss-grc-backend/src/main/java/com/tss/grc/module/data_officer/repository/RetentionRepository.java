package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.RetentionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository("doRetentionRepository")
public interface RetentionRepository extends JpaRepository<RetentionRule, Long> {
    Optional<RetentionRule> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<RetentionRule> findByOrganisationIdAndId(Long organisationId, Long id);
    List<RetentionRule> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    @Query("SELECT r FROM DoRetentionRule r WHERE r.organisationId = :o AND r.status='ACTIVE' AND r.nextReviewDue <= :d")
    List<RetentionRule> findReviewsDue(Long o, LocalDate d);
}
