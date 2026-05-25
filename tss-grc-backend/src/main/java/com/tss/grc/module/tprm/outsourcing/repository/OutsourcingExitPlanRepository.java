package com.tss.grc.module.tprm.outsourcing.repository;

import com.tss.grc.module.tprm.outsourcing.entity.OutsourcingExitPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OutsourcingExitPlanRepository extends JpaRepository<OutsourcingExitPlan, Long> {

    Optional<OutsourcingExitPlan> findByOrganisationIdAndArrangementIdAndIsDeletedFalse(
        Long organisationId, Long arrangementId);

    Optional<OutsourcingExitPlan> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    List<OutsourcingExitPlan> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    /**
     * "Overdue for test" = test cadence requires a test sooner than
     * {@code lastTestedDate} can satisfy. ANNUAL requires within 365 days,
     * BIANNUAL within 730 days. NEVER_TESTED and NEVER are excluded —
     * NEVER_TESTED is the bootstrap state and NEVER is an explicit
     * board-accepted decision (e.g. non-material printing arrangements).
     */
    @Query("""
        SELECT p FROM OutsourcingExitPlan p
         WHERE p.organisationId = :orgId
           AND (
               (p.testFrequency = 'ANNUAL'   AND (p.lastTestedDate IS NULL OR p.lastTestedDate < :annualCutoff))
            OR (p.testFrequency = 'BIANNUAL' AND (p.lastTestedDate IS NULL OR p.lastTestedDate < :biannualCutoff))
           )
        ORDER BY p.lastTestedDate ASC NULLS FIRST
        """)
    List<OutsourcingExitPlan> findOverdueTest(@Param("orgId") Long orgId,
                                                @Param("annualCutoff") LocalDate annualCutoff,
                                                @Param("biannualCutoff") LocalDate biannualCutoff);
}
