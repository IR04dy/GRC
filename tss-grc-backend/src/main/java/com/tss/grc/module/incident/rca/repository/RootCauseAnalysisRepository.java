package com.tss.grc.module.incident.rca.repository;

import com.tss.grc.module.incident.rca.entity.RootCauseAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RootCauseAnalysisRepository extends JpaRepository<RootCauseAnalysis, Long> {

    Page<RootCauseAnalysis> findByOrganisationIdOrderByCreatedAtDesc(Long orgId, Pageable pageable);

    Optional<RootCauseAnalysis> findByIdAndOrganisationId(Long id, Long orgId);

    /** All RCAs attached to a particular parent record (e.g. incident #42). */
    List<RootCauseAnalysis> findByOrganisationIdAndRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
            Long orgId, String entityType, Long entityId);

    /** Rows in a particular workflow state — drives dashboard counters. */
    List<RootCauseAnalysis> findByOrganisationIdAndStatusOrderByCreatedAtDesc(Long orgId, String status);
}
