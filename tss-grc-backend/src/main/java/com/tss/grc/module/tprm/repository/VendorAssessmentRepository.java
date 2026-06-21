package com.tss.grc.module.tprm.repository;

import com.tss.grc.module.tprm.entity.VendorAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendorAssessmentRepository extends JpaRepository<VendorAssessment, Long> {
    Page<VendorAssessment> findByVendorId(Long vendorId, Pageable pageable);
    List<VendorAssessment> findByVendorIdIn(List<Long> vendorIds);

    /** PERF-PG-002: paged, org-scoped — replaces the unpaged vendor scan + unbounded
     *  findByVendorIdIn for the org-wide assessment listing. */
    Page<VendorAssessment> findByOrganisationId(Long organisationId, Pageable pageable);
}
