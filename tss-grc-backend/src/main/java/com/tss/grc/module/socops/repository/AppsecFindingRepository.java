package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.AppsecFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppsecFindingRepository extends JpaRepository<AppsecFinding, Long> {
    Optional<AppsecFinding> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<AppsecFinding> findByOrganisationIdAndScannerAndExternalId(
        Long organisationId, String scanner, String externalId);
    Page<AppsecFinding> findByOrganisationIdOrderByDiscoveredAtDesc(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndSeverity(Long organisationId, String severity);
    long countByOrganisationIdAndScanType(Long organisationId, String scanType);
}
