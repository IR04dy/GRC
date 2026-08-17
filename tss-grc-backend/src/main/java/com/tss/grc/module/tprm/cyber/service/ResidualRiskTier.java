package com.tss.grc.module.tprm.cyber.service;

import com.tss.grc.common.validation.OrderedVocabulary;

import java.util.List;

/**
 * F2-230 — the residual-risk ladder for {@code tprm.third_party_cyber_assessments.residual_risk_tier}.
 *
 * <h2>The defect this closes</h2>
 * The register's default ordering was the derived method
 * {@code findByOrganisationIdOrderByResidualRiskTierDescIdDesc} — a lexical sort of an NVARCHAR
 * column presented as a severity ranking. {@link ThirdPartyCyberAssessmentService#recompute} derives
 * a <b>five</b>-rung ladder, and alphabetically DESC those five read:
 *
 * <pre>  MODERATE &gt; LOW &gt; HIGH &gt; ELEVATED &gt; CRITICAL  </pre>
 *
 * <p>So <b>CRITICAL sorted last and LOW sorted second</b>. Measured 2026-08-17, identical in dev and
 * prod: 16 rows holding ELEVATED, LOW and MODERATE, where DESC returns MODERATE first and ELEVATED —
 * the highest tier present — last. And the register is <b>paged</b>
 * ({@code Page<ThirdPartyCyberAssessment>}), so on any tenant with more assessments than one page the
 * most exposed vendors are not merely mis-ordered, they are on a page nobody opens.
 *
 * <p>⚠ There is an index on {@code (organisation_id, residual_risk_tier)} (V53), which is what made
 * the wrong ordering fast. A lexical sort backed by an index looks like a considered decision.
 *
 * <h2>Why the ladder had no declaration to consult</h2>
 * The five rungs existed <b>only</b> as an inline ternary chain inside {@code recompute}. The column
 * has no CHECK before V378, the entity declares nothing but a {@code "MODERATE"} default, and the
 * service's own {@code INHERENT} set is a <em>different</em> four-token risk vocabulary
 * ({@code LOW|MEDIUM|HIGH|CRITICAL}) on the same entity, overlapping this one only at LOW. F2-196's
 * shape: the vocabulary lived in the heads of its readers, and the readers disagreed.
 *
 * <p>🔑 The frontend had it <b>right</b> all along — {@code TpcResidual} declares all five rungs in
 * order and {@code TPC_RESIDUAL_TONE} covers every one. The half that was wrong is the half that
 * decides what an operator sees first. F2-205's follow-up question applies: when two copies of a
 * vocabulary disagree, ask which of them is the server.
 *
 * @see OrderedVocabulary for why "ordered" rather than "graded" is the property that matters
 */
public final class ResidualRiskTier {

    /**
     * Least to most exposed, naming the {@code CHECK} V378 adds.
     *
     * <p>These are exactly the five rungs {@code recompute}'s score bands produce, and that is not a
     * coincidence to be maintained by hand: {@code recompute} reads this ladder, so the derivation and
     * the ordering cannot drift.
     */
    public static final OrderedVocabulary LADDER = OrderedVocabulary.of(
            "residualRiskTier", "CK_tpc_residual_risk_tier",
            "LOW", "MODERATE", "ELEVATED", "HIGH", "CRITICAL");

    private ResidualRiskTier() {}

    /** Least-to-most-exposed, as declared. */
    public static List<String> ladder() {
        return LADDER.ladder();
    }

    /** Position on the ladder, or {@link OrderedVocabulary#UNRECOGNISED}. */
    public static int rank(String tier) {
        return LADDER.rank(tier);
    }

    /**
     * Is {@code tier} at or above {@code threshold}?
     *
     * <p>This replaces the inclusion lists. Six readers each spelled the "is this vendor a concern?"
     * question for themselves and the two on {@code TprmConsolidatedOverview} between them omitted
     * ELEVATED entirely — {@code tpcHigh} counted {@code HIGH|CRITICAL} and {@code tpcOk} counted
     * {@code LOW|MODERATE}, so every ELEVATED assessment was in <b>neither</b> bucket. That is
     * F2-196's rule at a tier: elevated must be a threshold over a declared ladder, never a second
     * inclusion list.
     */
    public static boolean atLeast(String tier, String threshold) {
        return LADDER.atLeast(tier, threshold);
    }
}
