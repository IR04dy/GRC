package com.tss.grc.module.sust.climate.repository;

import com.tss.grc.module.sust.climate.entity.ClimateReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClimateReportRepository extends JpaRepository<ClimateReport, Long> {

    List<ClimateReport> findByOrganisationIdOrderByReportingYearDescVersionDesc(Long org);

    Optional<ClimateReport> findByIdAndOrganisationId(Long id, Long org);

    /** Latest version for a year — used to bump the version on re-generation. */
    Optional<ClimateReport> findFirstByOrganisationIdAndReportingYearOrderByVersionDesc(Long org, Integer reportingYear);
}
