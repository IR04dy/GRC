package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.Risk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RiskRepository extends JpaRepository<Risk, Long> {
    Page<Risk> findByOrganisationId(Long organisationId, Pageable pageable);

    /**
     * The same population, UNCAPPED -- F2-422.
     *
     * <p>AuditCommitteeDashboardService#riskSection and ThreeLinesService#openRisksFor both took
     * this register through {@code Pageable.ofSize(1000)} and then derived every published
     * figure from the window -- the open-risk count, the four risk-level counts, the
     * above-appetite total and the CMA Art. 54 "top 5 above appetite" ranking.
     *
     * <p>Derived, so it inherits {@code Risk}'s {@code @Where(is_deleted = 0)} and agrees
     * with the paged overload on soft-delete scoping (checked, not assumed). Deliberately NOT
     * a SQL {@code COUNT} beside the paged fetch: that leaves two populations and re-creates
     * F2-365's defect. The paged overload stays -- the list endpoints are genuine paged reads.
     */
    List<Risk> findByOrganisationId(Long organisationId);

    @Query("SELECT r FROM Risk r WHERE r.organisationId = :orgId AND (:status IS NULL OR r.status = :status)")
    Page<Risk> findByOrgAndStatus(@Param("orgId") Long orgId, @Param("status") String status, Pageable pageable);

    long countByOrganisationId(Long organisationId);
    long countByOrganisationIdAndRiskLevel(Long organisationId, String riskLevel);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    // QUAL-BCMP-005: "open <level> risks" = that level AND not in a terminal
    // (CLOSED) status — distinct from the unfiltered countByRiskLevel total.
    long countByOrganisationIdAndRiskLevelAndStatusNot(Long organisationId, String riskLevel, String status);
    // F2-407: the dashboard's aggregate "open risks" = not in a terminal (CLOSED)
    // status — the same single-terminal "open" the QUAL-BCMP-005 level tiles above
    // use. The old countByStatus(org, "OPEN") dropped every live non-OPEN state
    // (UNDER_REVIEW / MITIGATING / ACCEPTED / TRANSFERRED), so openCriticalRisks
    // could exceed openRisks and open + closed did not partition the register.
    long countByOrganisationIdAndStatusNot(Long organisationId, String status);

    @Query("SELECT r FROM Risk r WHERE r.organisationId = :orgId ORDER BY r.createdAt DESC")
    List<Risk> findTop5ByOrganisationIdOrderByCreatedAtDesc(@Param("orgId") Long orgId, Pageable pageable);

    @Query("SELECT r.inherentLikelihood, r.inherentImpact, COUNT(r) FROM Risk r WHERE r.organisationId = :orgId AND r.inherentLikelihood IS NOT NULL GROUP BY r.inherentLikelihood, r.inherentImpact")
    List<Object[]> countByHeatmapCell(@Param("orgId") Long orgId);

    @Query("SELECT rc.nameEn, COUNT(r) FROM Risk r JOIN RiskCategory rc ON r.categoryId = rc.id WHERE r.organisationId = :orgId GROUP BY rc.nameEn ORDER BY COUNT(r) DESC")
    List<Object[]> countByCategory(@Param("orgId") Long orgId);

    /** §5.1 #6 (V115) — risks past their attestation due date and still open. */
    @Query("SELECT r FROM Risk r WHERE r.organisationId = :orgId "
         + "AND r.status NOT IN ('CLOSED','CANCELLED') "
         + "AND r.nextAttestationDate IS NOT NULL "
         + "AND r.nextAttestationDate < :today "
         + "ORDER BY r.nextAttestationDate ASC")
    List<Risk> findAttestationOverdue(@Param("orgId") Long orgId,
                                      @Param("today") java.time.LocalDate today);

    /** E-J — risks past their scheduled review date and still active (tenant-scoped). */
    @Query("SELECT r FROM Risk r WHERE r.organisationId = :orgId "
         + "AND r.status NOT IN ('CLOSED','CANCELLED','ARCHIVED') "
         + "AND r.reviewDate IS NOT NULL "
         + "AND r.reviewDate < :today "
         + "ORDER BY r.reviewDate ASC")
    List<Risk> findReviewOverdue(@Param("orgId") Long orgId,
                                 @Param("today") java.time.LocalDate today);

    /** E-J — system-wide stale sweep across all tenants (scheduler). */
    @Query("SELECT r FROM Risk r WHERE r.status NOT IN ('CLOSED','CANCELLED','ARCHIVED') "
         + "AND r.reviewDate IS NOT NULL "
         + "AND r.reviewDate < :today "
         + "ORDER BY r.organisationId ASC, r.reviewDate ASC")
    List<Risk> findReviewOverdueAllOrgs(@Param("today") java.time.LocalDate today);

    /** E-K — top active risks by residual (board / committee / audit-package report). */
    @Query("SELECT r FROM Risk r WHERE r.organisationId = :orgId "
         + "AND r.status NOT IN ('CLOSED','CANCELLED','ARCHIVED') "
         + "ORDER BY r.residualScore DESC, r.id ASC")
    List<Risk> findTopActiveByResidual(@Param("orgId") Long orgId, Pageable pageable);

    /** E-K — register stats: live count grouped by risk_level. */
    @Query("SELECT COALESCE(r.riskLevel,'UNSCORED'), COUNT(r) FROM Risk r WHERE r.organisationId = :orgId GROUP BY r.riskLevel")
    List<Object[]> countGroupedByLevel(@Param("orgId") Long orgId);

    /** E-K — register stats: live count grouped by workflow status. */
    @Query("SELECT r.status, COUNT(r) FROM Risk r WHERE r.organisationId = :orgId GROUP BY r.status")
    List<Object[]> countGroupedByStatus(@Param("orgId") Long orgId);

    /** E-B — "uncontrolled exposure": open risks with no linked control, worst residual first. */
    @Query("SELECT r FROM Risk r WHERE r.organisationId = :orgId "
         + "AND r.status NOT IN ('CLOSED','CANCELLED') "
         + "AND NOT EXISTS (SELECT 1 FROM RiskControl rc "
         + "                WHERE rc.riskId = r.id AND rc.organisationId = r.organisationId) "
         + "ORDER BY r.residualScore DESC")
    List<Risk> findUncontrolled(@Param("orgId") Long orgId);

    /** E-H — live register exposure for an appetite statement: the worst open
     *  residual score in a category (null if the category has no open risks). */
    @Query("SELECT MAX(r.residualScore) FROM Risk r WHERE r.organisationId = :orgId "
         + "AND r.categoryId = :catId AND r.status NOT IN ('CLOSED','CANCELLED')")
    Integer findMaxResidualScoreByCategory(@Param("orgId") Long orgId, @Param("catId") Long catId);

    // Framework anchor (IA-5)
    List<Risk> findByOrganisationIdAndRiskFrameworkId(Long organisationId, Long riskFrameworkId);
    List<Risk> findByOrganisationIdAndComplianceFrameworkId(Long organisationId, Long complianceFrameworkId);
    long countByOrganisationIdAndRiskFrameworkId(Long organisationId, Long riskFrameworkId);
    long countByOrganisationIdAndComplianceFrameworkId(Long organisationId, Long complianceFrameworkId);
}
