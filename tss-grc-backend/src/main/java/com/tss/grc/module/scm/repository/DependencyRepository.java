package com.tss.grc.module.scm.repository;

import com.tss.grc.module.scm.entity.Dependency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DependencyRepository extends JpaRepository<Dependency, Long> {
    Page<Dependency> findByOrganisationId(Long organisationId, Pageable pageable);
    List<Dependency> findByOrganisationId(Long organisationId);
    List<Dependency> findByOrganisationIdAndVendorId(Long organisationId, Long vendorId);
    long countByOrganisationId(Long organisationId);
    long countByOrganisationIdAndDependencyLevel(Long organisationId, String level);
}
