package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.CrisisCommsDistribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrisisCommsDistributionRepository extends JpaRepository<CrisisCommsDistribution, Long> {
    List<CrisisCommsDistribution> findByOrganisationIdOrderBySentAtDesc(Long organisationId);
}
