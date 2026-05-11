package com.tss.grc.module.scm.resilience.repository;

import com.tss.grc.module.scm.resilience.entity.AlternateSupplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlternateSupplierRepository extends JpaRepository<AlternateSupplier, Long> {

    Page<AlternateSupplier> findByOrganisationIdOrderByPrimaryVendorNameAscAlternateVendorRoleAsc(
            Long organisationId, Pageable pageable);

    Optional<AlternateSupplier> findByIdAndOrganisationId(Long id, Long organisationId);

    List<AlternateSupplier> findByOrganisationIdAndPrimaryVendorIdOrderByAlternateVendorRoleAsc(
            Long organisationId, Long primaryVendorId);

    Page<AlternateSupplier> findByOrganisationIdAndStatusOrderByCreatedAtDesc(
            Long organisationId, String status, Pageable pageable);
}
