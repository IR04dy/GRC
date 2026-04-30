package com.tss.grc.module.scm.repository;

import com.tss.grc.module.scm.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("scmVendorRepository")
public interface VendorRepository extends JpaRepository<Vendor, Long> {
    Page<Vendor> findByOrganisationId(Long organisationId, Pageable pageable);
    List<Vendor> findByOrganisationId(Long organisationId);
    long countByOrganisationId(Long organisationId);
    long countByOrganisationIdAndCriticality(Long organisationId, String criticality);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
