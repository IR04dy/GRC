package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.CrisisAar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CrisisAarRepository extends JpaRepository<CrisisAar, Long> {
    List<CrisisAar> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    List<CrisisAar> findByOrganisationIdAndCrisisEventIdOrderByCreatedAtDesc(
        Long organisationId, Long crisisEventId);
    Optional<CrisisAar> findByOrganisationIdAndReportCode(Long organisationId, String reportCode);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
