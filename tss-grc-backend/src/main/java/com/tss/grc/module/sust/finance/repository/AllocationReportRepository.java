package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.AllocationReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Green-Finance G2 — org-scoped finders for post-issuance report snapshots. */
public interface AllocationReportRepository extends JpaRepository<AllocationReport, Long> {

    List<AllocationReport> findByOrganisationIdAndInstrumentIdAndIsDeletedFalseOrderByPeriodYearDesc(
        Long organisationId, Long instrumentId);

    List<AllocationReport> findByOrganisationIdAndIsDeletedFalseOrderByPeriodYearDesc(Long organisationId);

    Optional<AllocationReport> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    boolean existsByOrganisationIdAndInstrumentIdAndPeriodYearAndReportTypeAndIsDeletedFalse(
        Long organisationId, Long instrumentId, Integer periodYear, String reportType);
}
