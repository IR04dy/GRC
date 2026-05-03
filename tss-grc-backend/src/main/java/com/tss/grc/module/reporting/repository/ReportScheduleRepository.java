package com.tss.grc.module.reporting.repository;

import com.tss.grc.module.reporting.entity.ReportSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {
    Page<ReportSchedule> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    @Query("""
        SELECT s FROM ReportSchedule s
        WHERE s.isActive = true
          AND s.nextRunAt <= :now
        """)
    List<ReportSchedule> findDue(@Param("now") LocalDateTime now);
}
