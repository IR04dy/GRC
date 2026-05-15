package com.tss.grc.module.sust.ghg.repository;

import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GhgEmissionRecordRepository extends JpaRepository<GhgEmissionRecord, Long> {

    Page<GhgEmissionRecord> findByOrganisationIdOrderByReportingYearDescScopeAscIdAsc(Long org, Pageable p);

    Page<GhgEmissionRecord> findByOrganisationIdAndReportingYearOrderByScopeAscIdAsc(Long org, Short year, Pageable p);

    Page<GhgEmissionRecord> findByOrganisationIdAndScopeOrderByReportingYearDescIdAsc(Long org, Short scope, Pageable p);

    Optional<GhgEmissionRecord> findByIdAndOrganisationId(Long id, Long org);

    /** All records for one year — used by the aggregate / YoY computation. */
    List<GhgEmissionRecord> findByOrganisationIdAndReportingYear(Long org, Short year);
}
