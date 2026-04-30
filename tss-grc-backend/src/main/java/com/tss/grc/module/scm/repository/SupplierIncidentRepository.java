package com.tss.grc.module.scm.repository;

import com.tss.grc.module.scm.entity.SupplierIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierIncidentRepository extends JpaRepository<SupplierIncident, Long> {
    Page<SupplierIncident> findByOrganisationId(Long organisationId, Pageable pageable);
    List<SupplierIncident> findTop5ByOrganisationIdOrderByReportedAtDesc(Long organisationId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndSlaBreached(Long organisationId, Boolean breached);
}
