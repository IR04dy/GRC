package com.tss.grc.module.sust.annex.repository;

import com.tss.grc.module.sust.annex.entity.ReportingYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportingYearRepository extends JpaRepository<ReportingYear, Long> {

    List<ReportingYear> findByOrganisationIdOrderByYearDesc(Long org);

    Optional<ReportingYear> findByIdAndOrganisationId(Long id, Long org);

    Optional<ReportingYear> findByOrganisationIdAndYear(Long org, Short year);
}
