package com.tss.grc.module.scm.fourthparty.repository;

import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FourthPartyExposureRepository extends JpaRepository<FourthPartyExposure, Long> {

    /**
     * The register, most concentrated first.
     *
     * <p><b>F2-229 — this replaces {@code findByOrganisationIdOrderByConcentrationRiskDesc
     * DownstreamCriticalityDesc}, whose name asserted a ranking it did not perform.</b> Both columns
     * hold LOW|MEDIUM|HIGH|CRITICAL, and a derived {@code OrderBy…Desc} on an NVARCHAR column is a
     * <em>lexical</em> sort: descending alphabetically those four read MEDIUM, LOW, HIGH, CRITICAL.
     * Measured against the live register (13 rows, identical in dev and prod) the row that came back
     * <b>last of 13 was the CRITICAL/CRITICAL one</b> — an underlying hyperscaler region — with the
     * three HIGH/CRITICAL rows 10th, 11th and 12th. The frontend requests {@code size=200} and
     * renders the server's order, so the most concentrated fourth party in the register was the last
     * row on the page.
     *
     * <p>🔑 <b>A lexical sort is a ranking only where the vocabulary happens to be in alphabetical
     * order, and severity ladders never are.</b> The {@code CASE} ladders below are checked
     * token-for-token against {@link com.tss.grc.module.scm.fourthparty.service.ConcentrationTier
     * #ladder()} by {@code FourthPartyOrderingContractTest}, which reads this annotation by
     * reflection — so the second copy of the order that a {@code @Query} string necessarily is
     * cannot drift from the first.
     *
     * <p>⚙ {@code ELSE -1} mirrors {@code ConcentrationTier.UNRECOGNISED}: after V377 neither column
     * can hold an unrecognised token, and if one somehow did it sorts below LOW rather than being
     * silently grouped with it.
     *
     * <p>⚙ The {@code countQuery} is supplied explicitly rather than left to Spring Data's
     * derivation, because deriving a count from a query whose {@code ORDER BY} contains {@code CASE}
     * expressions is the kind of thing that works until it does not.
     */
    @Query(value = """
            SELECT f FROM FourthPartyExposure f
            WHERE f.organisationId = :orgId
            ORDER BY
              CASE f.concentrationRisk
                  WHEN 'LOW' THEN 0 WHEN 'MEDIUM' THEN 1
                  WHEN 'HIGH' THEN 2 WHEN 'CRITICAL' THEN 3 ELSE -1 END DESC,
              CASE f.downstreamCriticality
                  WHEN 'LOW' THEN 0 WHEN 'MEDIUM' THEN 1
                  WHEN 'HIGH' THEN 2 WHEN 'CRITICAL' THEN 3 ELSE -1 END DESC,
              f.id ASC
            """,
           countQuery = """
            SELECT COUNT(f) FROM FourthPartyExposure f WHERE f.organisationId = :orgId
            """)
    Page<FourthPartyExposure> findRegisterByConcentration(@Param("orgId") Long orgId,
                                                          Pageable pageable);

    Optional<FourthPartyExposure> findByIdAndOrganisationId(Long id, Long organisationId);

    Page<FourthPartyExposure> findByOrganisationIdAndStatusOrderByCreatedAtDesc(
            Long organisationId, String status, Pageable pageable);

    /**
     * Every live edge in the tenant.
     *
     * <p>Used by the concentration recompute, which counts fourth-party names in Java rather than in
     * SQL. F2-229 deleted the {@code concentrationCounts} aggregate that used to do it: the count has
     * to exclude RETIRED edges, and doing that in JPQL put the rule somewhere no unit test with a
     * mocked repository could reach — while leaving the un-filtered aggregate in place as an unread
     * second opinion is how the next reader decides it is the authority (F2-190).
     */
    List<FourthPartyExposure> findByOrganisationId(Long organisationId);
}
