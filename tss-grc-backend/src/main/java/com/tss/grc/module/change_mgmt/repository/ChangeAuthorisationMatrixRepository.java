package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChangeAuthorisationMatrixRepository extends JpaRepository<ChangeAuthorisationMatrix, Long> {
    Optional<ChangeAuthorisationMatrix> findByOrganisationIdAndChangeTypeAndRiskLevel(
        Long organisationId, String changeType, String riskLevel);
    /**
     * The whole matrix, grouped by change type and then ranked by risk, least risky first — the order
     * a reader scans an escalation in.
     *
     * <p>⚠ <b>Two columns, and only one of them is safe to sort lexically</b> (F2-230).
     * {@code changeType} is CATEGORICAL ({@code EMERGENCY | NORMAL | STANDARD}), so an alphabetical
     * sort of it is a deterministic grouping that claims nothing and is left exactly as it was.
     * {@code riskLevel} is ORDERED ({@code CK_cam_risk}: LOW, MEDIUM, HIGH, CRITICAL) and its
     * alphabetical ascent read CRITICAL, HIGH, LOW, MEDIUM — so the grid could not be read downward as
     * an escalation, which is what a grid like this is for. Presentation rather than control: nothing
     * decides anything from this order, and a reader arriving with a known (type, level) finds their
     * cell either way.
     *
     * <p>The {@code CASE} mirrors {@code ChangeRiskLevel.ladder()};
     * {@code OrderedVocabularySortContractTest} checks the two agree.
     */
    @Query("""
        SELECT m FROM ChangeAuthorisationMatrix m
        WHERE m.organisationId = :organisationId
        ORDER BY m.changeType ASC,
          CASE m.riskLevel
              WHEN 'LOW' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'CRITICAL' THEN 3
              ELSE -1 END ASC
    """)
    List<ChangeAuthorisationMatrix> findMatrixRanked(@Param("organisationId") Long organisationId);
}
