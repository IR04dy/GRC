package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.CrisisCommsAudience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrisisCommsAudienceRepository extends JpaRepository<CrisisCommsAudience, Long> {
    List<CrisisCommsAudience> findByOrganisationIdAndIsActiveTrueOrderByName(Long organisationId);
}
