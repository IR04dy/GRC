package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmComplianceMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcmComplianceMappingRepository extends JpaRepository<BcmComplianceMapping, Long> {

    List<BcmComplianceMapping> findByOrganisationIdOrderBySourceFrameworkAscRequirementCodeAsc(Long organisationId);

    List<BcmComplianceMapping> findByOrganisationIdAndSourceFrameworkOrderByRequirementCodeAsc(
        Long organisationId, String sourceFramework);

    Optional<BcmComplianceMapping> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<BcmComplianceMapping> findByOrganisationIdAndSourceFrameworkAndRequirementCode(
        Long organisationId, String sourceFramework, String requirementCode);

    long countByOrganisationIdAndComplianceStatus(Long organisationId, String complianceStatus);

    long countByOrganisationIdAndSourceFrameworkAndComplianceStatus(
        Long organisationId, String sourceFramework, String complianceStatus);
}
