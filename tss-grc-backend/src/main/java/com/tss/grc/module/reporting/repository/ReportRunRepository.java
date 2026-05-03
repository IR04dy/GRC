package com.tss.grc.module.reporting.repository;

import com.tss.grc.module.reporting.entity.ReportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRunRepository extends JpaRepository<ReportRun, Long> {
    List<ReportRun> findTop50ByScheduleIdOrderByTriggeredAtDesc(Long scheduleId);
    List<ReportRun> findTop50ByOrganisationIdOrderByTriggeredAtDesc(Long organisationId);
}
