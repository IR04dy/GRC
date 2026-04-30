package com.tss.grc.module.scm.repository;

import com.tss.grc.module.scm.entity.VendorOnboarding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorOnboardingRepository extends JpaRepository<VendorOnboarding, Long> {
    Page<VendorOnboarding> findByOrganisationId(Long organisationId, Pageable pageable);
    List<VendorOnboarding> findByOrganisationId(Long organisationId);
    Optional<VendorOnboarding> findByOrganisationIdAndVendorId(Long organisationId, Long vendorId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
