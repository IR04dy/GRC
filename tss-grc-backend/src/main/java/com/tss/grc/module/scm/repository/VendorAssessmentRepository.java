package com.tss.grc.module.scm.repository;

import com.tss.grc.module.scm.entity.VendorAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("scmVendorAssessmentRepository")
public interface VendorAssessmentRepository extends JpaRepository<VendorAssessment, Long> {
    Page<VendorAssessment> findByOrganisationId(Long organisationId, Pageable pageable);
    List<VendorAssessment> findByOrganisationIdAndVendorIdOrderByAssessmentDateDesc(Long organisationId, Long vendorId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
