package com.tss.grc.module.risk.emerging.repository;

import com.tss.grc.module.risk.emerging.entity.EmergingRisk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmergingRiskRepository extends JpaRepository<EmergingRisk, Long> {
    Page<EmergingRisk> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);
    Page<EmergingRisk> findByOrganisationIdAndStatusOrderByCreatedAtDesc(Long organisationId, String status, Pageable pageable);
    Optional<EmergingRisk> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<EmergingRisk> findByOrganisationIdAndRiskCode(Long organisationId, String riskCode);
}
