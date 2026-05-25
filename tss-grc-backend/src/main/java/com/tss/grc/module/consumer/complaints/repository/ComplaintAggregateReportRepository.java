package com.tss.grc.module.consumer.complaints.repository;

import com.tss.grc.module.consumer.complaints.entity.ComplaintAggregateReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintAggregateReportRepository
        extends JpaRepository<ComplaintAggregateReport, Long> {

    Optional<ComplaintAggregateReport> findByOrganisationIdAndReportPeriod(
            Long organisationId, String reportPeriod);

    List<ComplaintAggregateReport> findByOrganisationIdOrderByReportPeriodDesc(Long organisationId);
}
