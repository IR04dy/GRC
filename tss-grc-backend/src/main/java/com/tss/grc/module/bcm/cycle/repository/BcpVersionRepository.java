package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcpVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcpVersionRepository extends JpaRepository<BcpVersion, Long> {

    List<BcpVersion> findByOrganisationIdAndPlanIdOrderByVersionNumberDesc(Long organisationId, Long planId);

    Optional<BcpVersion> findByOrganisationIdAndPlanIdAndVersionNumber(Long organisationId, Long planId, Integer versionNumber);
}
