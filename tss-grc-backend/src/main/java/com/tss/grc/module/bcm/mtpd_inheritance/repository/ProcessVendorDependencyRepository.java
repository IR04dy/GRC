package com.tss.grc.module.bcm.mtpd_inheritance.repository;

import com.tss.grc.module.bcm.mtpd_inheritance.entity.ProcessVendorDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped repository for {@link ProcessVendorDependency} (Slice 10-E).
 *
 * <p>Soft-deleted rows are hidden by the entity's {@code @Where} clause.
 */
public interface ProcessVendorDependencyRepository extends JpaRepository<ProcessVendorDependency, Long> {

    List<ProcessVendorDependency> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    List<ProcessVendorDependency> findByOrganisationIdAndBusinessProcessIdAndIsDeletedFalse(
        Long organisationId, Long businessProcessId);

    List<ProcessVendorDependency> findByOrganisationIdAndVendorIdAndIsDeletedFalse(
        Long organisationId, Long vendorId);

    Optional<ProcessVendorDependency> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    /** Drives the compute engine — only REQUIRED + RECOMMENDED count. */
    List<ProcessVendorDependency> findByOrganisationIdAndDependencyTypeInAndIsDeletedFalse(
        Long organisationId, List<String> dependencyTypes);
}
