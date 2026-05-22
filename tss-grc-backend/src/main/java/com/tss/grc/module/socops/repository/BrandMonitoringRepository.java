package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.BrandMonitoring;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandMonitoringRepository extends JpaRepository<BrandMonitoring, Long> {
    Optional<BrandMonitoring> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<BrandMonitoring> findByOrganisationIdAndSourceSystemAndExternalId(
        Long organisationId, String sourceSystem, String externalId);
    Page<BrandMonitoring> findByOrganisationIdOrderByDetectedAtDesc(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndAlertType(Long organisationId, String alertType);
    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
}
