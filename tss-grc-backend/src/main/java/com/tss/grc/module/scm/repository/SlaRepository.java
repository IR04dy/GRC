package com.tss.grc.module.scm.repository;

import com.tss.grc.module.scm.entity.Sla;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlaRepository extends JpaRepository<Sla, Long> {
    Page<Sla> findByOrganisationId(Long organisationId, Pageable pageable);
    List<Sla> findByOrganisationIdAndVendorId(Long organisationId, Long vendorId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
