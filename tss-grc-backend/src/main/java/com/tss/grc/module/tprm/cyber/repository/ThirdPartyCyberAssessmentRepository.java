package com.tss.grc.module.tprm.cyber.repository;

import com.tss.grc.module.tprm.cyber.entity.ThirdPartyCyberAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ThirdPartyCyberAssessmentRepository extends JpaRepository<ThirdPartyCyberAssessment, Long> {

    /**
     * The register, most exposed first.
     *
     * <p>⚠ <b>This replaces {@code findByOrganisationIdOrderByResidualRiskTierDescIdDesc}</b>, whose
     * derived {@code OrderBy} was a lexical sort of an NVARCHAR column over the five-rung ladder
     * {@code ResidualRiskTier} declares. Descending alphabetically, those five read MODERATE, LOW,
     * HIGH, ELEVATED, CRITICAL — so the most exposed third party in the register sorted <b>last</b>,
     * on a PAGED endpoint, behind an index that made the wrong answer fast (F2-230).
     *
     * <p>The {@code CASE} below is necessarily a <b>second copy</b> of that ladder: {@code @Query}
     * takes a compile-time constant, so no Java constant can be interpolated into it. What stops the
     * copy drifting is {@code OrderedVocabularySortContractTest}, which parses this annotation by
     * reflection and compares it token for token, in order, with {@code ResidualRiskTier.ladder()} —
     * and fails the build if the two disagree or if either ladder stops descending.
     */
    @Query(value = """
        SELECT a FROM ThirdPartyCyberAssessment a
        WHERE a.organisationId = :orgId
        ORDER BY
          CASE a.residualRiskTier
              WHEN 'LOW' THEN 0 WHEN 'MODERATE' THEN 1 WHEN 'ELEVATED' THEN 2
              WHEN 'HIGH' THEN 3 WHEN 'CRITICAL' THEN 4 ELSE -1 END DESC,
          a.id DESC
        """,
       countQuery = """
        SELECT COUNT(a) FROM ThirdPartyCyberAssessment a WHERE a.organisationId = :orgId
        """)
    Page<ThirdPartyCyberAssessment> findRegisterByResidualRisk(@Param("orgId") Long orgId, Pageable p);

    /** Same ranking, filtered to one lifecycle status. */
    @Query(value = """
        SELECT a FROM ThirdPartyCyberAssessment a
        WHERE a.organisationId = :orgId AND a.status = :status
        ORDER BY
          CASE a.residualRiskTier
              WHEN 'LOW' THEN 0 WHEN 'MODERATE' THEN 1 WHEN 'ELEVATED' THEN 2
              WHEN 'HIGH' THEN 3 WHEN 'CRITICAL' THEN 4 ELSE -1 END DESC,
          a.id DESC
        """,
       countQuery = """
        SELECT COUNT(a) FROM ThirdPartyCyberAssessment a
        WHERE a.organisationId = :orgId AND a.status = :status
        """)
    Page<ThirdPartyCyberAssessment> findRegisterByResidualRiskAndStatus(
        @Param("orgId") Long orgId, @Param("status") String status, Pageable p);

    Optional<ThirdPartyCyberAssessment> findByIdAndOrganisationId(Long id, Long org);
}
