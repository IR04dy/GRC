package com.tss.grc.module.ndi.gap.repository;

import com.tss.grc.module.ndi.gap.entity.NdiGap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NdiGapRepository extends JpaRepository<NdiGap, Long> {

    /**
     * The gap register for one assessment cycle, <b>most severe first</b>.
     *
     * <p>⚠ These two queries replace {@code …OrderBySeverityAscIdAsc}, a lexical sort of
     * {@code ck_ndi_gap_sev}'s {@code CRITICAL | HIGH | MEDIUM | LOW}. Ascending alphabetically that
     * reads CRITICAL, HIGH, LOW, MEDIUM — 🔑 <b>right on the top two rungs and transposed on the
     * bottom two</b>, which is precisely why nobody noticed: the register opened on the rows a
     * reviewer cares most about, so it looked correct, while MEDIUM and LOW were reversed on every
     * page of every cycle (18 live rows, dev = prod). A sort that is right where you look and wrong
     * where you do not is harder to spot than one that is uniformly wrong (F2-230).
     *
     * <p>The {@code CASE} mirrors {@code NdiGapSeverity.ladder()};
     * {@code OrderedVocabularySortContractTest} checks the two agree token for token.
     */
    @Query(value = """
        SELECT g FROM NdiGap g
        WHERE g.organisationId = :orgId AND g.cycleId = :cycleId
        ORDER BY
          CASE g.severity
              WHEN 'LOW' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'CRITICAL' THEN 3
              ELSE -1 END DESC,
          g.id ASC
        """,
       countQuery = """
        SELECT COUNT(g) FROM NdiGap g
        WHERE g.organisationId = :orgId AND g.cycleId = :cycleId
        """)
    Page<NdiGap> findRegisterBySeverity(@Param("orgId") Long org, @Param("cycleId") Long cycleId, Pageable p);

    /** Same ranking, filtered to one workflow status. */
    @Query(value = """
        SELECT g FROM NdiGap g
        WHERE g.organisationId = :orgId AND g.cycleId = :cycleId AND g.status = :status
        ORDER BY
          CASE g.severity
              WHEN 'LOW' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'CRITICAL' THEN 3
              ELSE -1 END DESC,
          g.id ASC
        """,
       countQuery = """
        SELECT COUNT(g) FROM NdiGap g
        WHERE g.organisationId = :orgId AND g.cycleId = :cycleId AND g.status = :status
        """)
    Page<NdiGap> findRegisterBySeverityAndStatus(
        @Param("orgId") Long org, @Param("cycleId") Long cycleId, @Param("status") String status, Pageable p);

    Page<NdiGap> findByOrganisationIdAndCycleIdAndSeverityOrderByIdAsc(
        Long org, Long cycleId, String severity, Pageable p);

    Optional<NdiGap> findByIdAndOrganisationId(Long id, Long org);

    Optional<NdiGap> findByOrganisationIdAndSourceTypeAndSourceId(Long org, String sourceType, Long sourceId);

    List<NdiGap> findByOrganisationIdAndCycleId(Long org, Long cycleId);
}
