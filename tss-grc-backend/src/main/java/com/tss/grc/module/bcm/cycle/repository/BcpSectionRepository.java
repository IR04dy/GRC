package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcpSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BcpSectionRepository extends JpaRepository<BcpSection, Long> {

    List<BcpSection> findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(Long organisationId, Long planId);

    Optional<BcpSection> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<BcpSection> findByOrganisationIdAndPlanIdAndSectionKey(Long organisationId, Long planId, String sectionKey);
}
