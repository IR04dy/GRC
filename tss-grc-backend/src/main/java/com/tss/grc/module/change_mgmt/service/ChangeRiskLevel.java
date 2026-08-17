package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.validation.OrderedVocabulary;

import java.util.List;

/**
 * F2-230 — the risk ladder for {@code change_mgmt.change_authorisation_matrix.risk_level}.
 *
 * <h2>What this one is, and what it is not</h2>
 * {@code findByOrganisationIdOrderByChangeTypeAscRiskLevelAsc} feeds the authorisation matrix
 * {@code GovernancePanel} renders as a flat grid of 36 cells. {@code change_type} is
 * <b>CATEGORICAL</b> ({@code EMERGENCY | NORMAL | STANDARD}) — sorting it alphabetically is a
 * deterministic grouping that claims nothing, and it is deliberately left alone. {@code risk_level}
 * is <b>ORDERED</b> ({@code CK_cam_risk}: LOW, MEDIUM, HIGH, CRITICAL), and sorting it alphabetically
 * ascending reads CRITICAL, HIGH, LOW, MEDIUM within each change type.
 *
 * <p>That distinction — two columns in one {@code ORDER BY}, one safe to sort lexically and one not —
 * is why the F2-230 gate classifies per column rather than per query.
 *
 * <p>⚠ <b>Graded honestly: this is a presentation defect, not a control defect.</b> Nothing
 * <em>decides</em> anything from this order. An authorisation matrix is a lookup: a reader arrives
 * knowing the change type and the risk level and wants the authoriser, so the cell they need is the
 * cell they find whatever order the rows arrive in. What the wrong order costs is the reading a grid
 * is for — scanning down the escalation to see who signs off as risk rises, which in a grid ordered
 * CRITICAL, HIGH, LOW, MEDIUM is not a scan anybody can do. It is fixed here because it is the same
 * one-line shape as the three live defects beside it, and because leaving it would mean the gate had
 * to tolerate a member indistinguishable from an unreviewed one (house rule 3).
 */
public final class ChangeRiskLevel {

    /** Least to most risky, naming V306's existing {@code CHECK}. */
    public static final OrderedVocabulary LADDER = OrderedVocabulary.of(
            "riskLevel", "CK_cam_risk",
            "LOW", "MEDIUM", "HIGH", "CRITICAL");

    private ChangeRiskLevel() {}

    /** Least-to-most-risky, as declared. */
    public static List<String> ladder() {
        return LADDER.ladder();
    }

    /** Position on the ladder, or {@link OrderedVocabulary#UNRECOGNISED}. */
    public static int rank(String level) {
        return LADDER.rank(level);
    }

    /** Is {@code level} at or above {@code threshold}? */
    public static boolean atLeast(String level, String threshold) {
        return LADDER.atLeast(level, threshold);
    }
}
