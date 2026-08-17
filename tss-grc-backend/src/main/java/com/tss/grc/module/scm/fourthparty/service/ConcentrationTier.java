package com.tss.grc.module.scm.fourthparty.service;

import com.tss.grc.common.validation.ClosedVocabulary;
import com.tss.grc.common.validation.OrderedVocabulary;

import java.util.List;

/**
 * F2-229 — the concentration ladder for {@code scm.fourth_party_exposures.concentration_risk}.
 *
 * <p><b>Why a ladder type rather than four string literals.</b> Three separate things need to agree
 * about the order of these tokens and, until this class existed, none of them consulted the others:
 *
 * <ul>
 *   <li>{@link FourthPartyExposureService} decides whether a newly-derived tier should replace the
 *       stored one — a comparison, which needs an order;</li>
 *   <li>the auto-CAPA hook fires on the <em>rising edge</em> into HIGH — also a comparison;</li>
 *   <li>the register's default ordering ranks the rows an operator reads top-down.</li>
 * </ul>
 *
 * <p>🔑 <b>The measured defect this replaces: the service ASSIGNED where its own two comments said
 * ELEVATE.</b> {@code FourthPartyExposureService}'s class javadoc said the tier is
 * <i>"auto-elevated to HIGH"</i>, {@code FourthPartyExposureRepository#concentrationCounts}'s
 * javadoc said the service uses it to <i>"elevate concentration_risk to HIGH"</i>, and the CAPA hook
 * was written as a rising edge — which only makes sense if the tier is monotone. The code then did
 * {@code ex.setConcentrationRisk(tier)}, unconditionally, across the whole tenant, on every create
 * and every update. Measured 2026-08-17, identical in dev and prod: all 13 rows carry
 * <b>13 distinct</b> fourth-party names, so the multiplicity rule matches nothing and computes LOW
 * for every row — and any write <b>lowered 9 of the 13</b>, including all three HIGH rows and the
 * sole CRITICAL one. Same family as F2-195's {@code || N}: the correct rule was written down in
 * prose beside the place it was not applied.
 *
 * <p>🔑 <b>The range of the derivation is NARROWER than the range of the column, which is what made
 * the flattening irreversible.</b> Multiplicity can only ever justify LOW, MEDIUM or HIGH — there is
 * no row count that produces CRITICAL — so a CRITICAL assessment overwritten by the derivation could
 * never be restored by it. That is the reusable shape: <b>a recompute whose output range is a strict
 * subset of its column's vocabulary destroys the values it cannot express.</b>
 *
 * <p>So the multiplicity rule is a <b>floor</b>, not the answer: {@code multiplicityFloor} says what
 * the row count alone justifies, and {@link #outranks} decides whether that floor is worth raising a
 * stored assessment to. Nothing here may lower one — a concentration tier is a judgement about how
 * systemically important a fourth party is (the sole CRITICAL row is an underlying hyperscaler
 * region), and a row count is not evidence against a judgement.
 *
 * <p>⚙ {@code CRITICAL} is declared here and had no declaration anywhere before: the entity's field
 * javadoc said {@code LOW | MEDIUM | HIGH}, the frontend's {@code FpConcentration} said the same, and
 * the column held CRITICAL in both databases. It is the vocabulary that was wrong, not the data.
 */
public final class ConcentrationTier {

    /**
     * The ladder, least to most concentrated, naming the {@code CHECK} V377 adds. Before V377 this
     * table had <b>zero</b> check constraints of any kind, so the vocabulary lived only in the heads
     * of its readers — F2-196's sentence, on a different column.
     *
     * <p><b>The order of these arguments is the entire point of the class</b> — it is what
     * {@code ORDER BY concentration_risk DESC} was silently substituting an alphabetical sort for,
     * and alphabetically DESC these four read MEDIUM, LOW, HIGH, CRITICAL.
     *
     * <p>⚙ F2-230 moved the mechanism into {@link OrderedVocabulary}, where three other ladders in
     * this platform now share it. The accessors below are kept as a thin facade because the register
     * ordering, the recompute and the CAPA edge all read them; what is deliberately <em>not</em> kept
     * is a second implementation of rank/outranks/atLeast, since an unread second opinion is how the
     * next reader decides it is the authority (F2-190).
     */
    public static final OrderedVocabulary LADDER = OrderedVocabulary.of(
            "concentrationRisk", "CK_fpe_concentration_risk",
            "LOW", "MEDIUM", "HIGH", "CRITICAL");

    /** The underlying set, for {@code require} and for {@code ClosedVocabularyPinTest}. */
    public static final ClosedVocabulary VOCABULARY = LADDER.vocabulary();

    /** Rank of a token this class does not recognise. Sorts below LOW; see {@link #outranks}. */
    public static final int UNRECOGNISED = OrderedVocabulary.UNRECOGNISED;

    private ConcentrationTier() {}

    /** Least-to-most-concentrated, as declared. */
    public static List<String> ladder() {
        return LADDER.ladder();
    }

    /**
     * Position on the ladder, or {@link #UNRECOGNISED} for anything else.
     *
     * <p>Case-normalising rather than trusting the column: every text column in this database is
     * collated case-insensitively, so {@code 'high'} is storable and {@code "HIGH".equals(…)} would
     * then be false (F2-179).
     */
    public static int rank(String tier) {
        return LADDER.rank(tier);
    }

    /**
     * Is {@code candidate} strictly more concentrated than {@code current}?
     *
     * <p>This is the predicate that makes the recompute an elevation. It is deliberately strict, so
     * an equal tier is not rewritten — that keeps the row un-dirtied and, with it, the auto-CAPA's
     * rising edge honest.
     *
     * <p>⚠ An unrecognised stored value ranks {@link #UNRECOGNISED}, so any real floor outranks it
     * and replaces it. That is the right direction — a token no reader can interpret is not an
     * assessment worth protecting — and after V377 the column cannot hold one anyway.
     */
    public static boolean outranks(String candidate, String current) {
        return LADDER.outranks(candidate, current);
    }

    /** Is {@code tier} at or above {@code threshold}? Throws if the threshold is not a tier. */
    public static boolean atLeast(String tier, String threshold) {
        return LADDER.atLeast(tier, threshold);
    }

    /**
     * The tier the row count <b>alone</b> justifies: three or more live primaries behind the same
     * fourth party is HIGH, two is MEDIUM, anything less is LOW.
     *
     * <p>Note what it cannot return: CRITICAL. There is no number of primaries that makes this rule
     * say CRITICAL, which is precisely why its result may only ever be used as a floor.
     */
    public static String multiplicityFloor(long livePrimariesBehind) {
        if (livePrimariesBehind >= 3) return "HIGH";
        if (livePrimariesBehind == 2) return "MEDIUM";
        return "LOW";
    }
}
