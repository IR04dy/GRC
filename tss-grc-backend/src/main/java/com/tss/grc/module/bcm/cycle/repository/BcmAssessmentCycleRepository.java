package com.tss.grc.module.bcm.cycle.repository;

import com.tss.grc.module.bcm.cycle.entity.BcmAssessmentCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BcmAssessmentCycleRepository extends JpaRepository<BcmAssessmentCycle, Long> {

    List<BcmAssessmentCycle> findByOrganisationIdOrderByReportingYearDesc(Long organisationId);

    Optional<BcmAssessmentCycle> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<BcmAssessmentCycle> findByOrganisationIdAndReportingYear(Long organisationId, Short reportingYear);

    List<BcmAssessmentCycle> findByOrganisationIdAndStatusOrderByReportingYearDesc(Long organisationId, String status);

    /** Distinct tenants with at least one assessment cycle — drives the scheduled annual-cycle sweep. */
    @Query("SELECT DISTINCT c.organisationId FROM BcmAssessmentCycle c")
    List<Long> findDistinctOrganisationIds();

    /**
     * Cycles whose {@code endDate} falls inside the inclusive window and which are
     * not yet APPROVED / ARCHIVED — used by the annual-cycle scheduler to push
     * close-out reminders at T-90 / T-60 / T-30 / T-7 days.
     */
    @Query("""
        SELECT c FROM BcmAssessmentCycle c
        WHERE c.organisationId = :org
          AND c.endDate IS NOT NULL
          AND c.endDate >= :from AND c.endDate <= :to
          AND c.status NOT IN ('APPROVED', 'ARCHIVED')
        """)
    List<BcmAssessmentCycle> findEndingBetween(Long org, LocalDate from, LocalDate to);

    /** Currently-in-flight cycles whose end_date is already in the past. */
    @Query("""
        SELECT c FROM BcmAssessmentCycle c
        WHERE c.organisationId = :org
          AND c.endDate IS NOT NULL
          AND c.endDate < :today
          AND c.status NOT IN ('APPROVED', 'ARCHIVED')
        """)
    List<BcmAssessmentCycle> findOverdue(Long org, LocalDate today);
}
