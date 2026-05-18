package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.CrisisSituationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CrisisSituationReportRepository extends JpaRepository<CrisisSituationReport, Long> {

    List<CrisisSituationReport> findByOrganisationIdAndCrisisEventIdOrderByReportedAtAsc(
        Long organisationId, Long crisisEventId);

    long countByOrganisationIdAndCrisisEventId(Long organisationId, Long crisisEventId);
}
