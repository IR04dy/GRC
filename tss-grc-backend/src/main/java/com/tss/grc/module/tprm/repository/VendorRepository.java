package com.tss.grc.module.tprm.repository;

import com.tss.grc.module.tprm.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, Long> {
    Page<Vendor> findByOrganisationId(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndRiskRating(Long organisationId, String riskRating);
}
