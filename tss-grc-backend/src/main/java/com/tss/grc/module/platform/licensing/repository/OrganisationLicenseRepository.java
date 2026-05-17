package com.tss.grc.module.platform.licensing.repository;

import com.tss.grc.module.platform.licensing.entity.OrganisationLicense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationLicenseRepository extends JpaRepository<OrganisationLicense, Long> {

    List<OrganisationLicense> findByOrganisationIdOrderByModuleCodeAsc(Long organisationId);

    Optional<OrganisationLicense> findByOrganisationIdAndModuleCode(Long organisationId, String moduleCode);

    Optional<OrganisationLicense> findByIdAndOrganisationId(Long id, Long organisationId);
}
