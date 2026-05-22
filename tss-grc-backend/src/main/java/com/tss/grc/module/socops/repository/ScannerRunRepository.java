package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.ScannerRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScannerRunRepository extends JpaRepository<ScannerRun, Long> {

    Optional<ScannerRun> findByIdAndOrganisationId(Long id, Long organisationId);

    Page<ScannerRun> findByOrganisationIdOrderByStartedAtDesc(Long organisationId, Pageable pageable);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
