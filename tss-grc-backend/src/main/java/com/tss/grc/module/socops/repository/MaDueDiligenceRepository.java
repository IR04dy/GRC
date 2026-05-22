package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.MaDueDiligence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaDueDiligenceRepository extends JpaRepository<MaDueDiligence, Long> {
    Optional<MaDueDiligence> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<MaDueDiligence> findByOrganisationIdAndEngagementCode(Long organisationId, String engagementCode);
    Page<MaDueDiligence> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndPhase(Long organisationId, String phase);
}
