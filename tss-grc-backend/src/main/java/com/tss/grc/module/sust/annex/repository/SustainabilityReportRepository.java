package com.tss.grc.module.sust.annex.repository;

import com.tss.grc.module.sust.annex.entity.SustainabilityReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SustainabilityReportRepository extends JpaRepository<SustainabilityReport, Long> {

    Page<SustainabilityReport> findByOrganisationIdOrderByGeneratedAtDescIdDesc(Long org, Pageable p);

    List<SustainabilityReport> findByOrganisationIdAndReportingYearIdOrderByGeneratedAtDescIdDesc(Long org, Long ryId);

    Optional<SustainabilityReport> findByIdAndOrganisationId(Long id, Long org);
}
