package com.tss.grc.module.ndi.gap.service;

import com.tss.grc.common.validation.OrderedVocabulary;

import java.util.List;

/**
 * F2-230 — the severity ladder for {@code ndi.gap.severity}.
 *
 * <h2>The defect this closes</h2>
 * Both paged gap listings ended {@code OrderBySeverityAscIdAsc}, a lexical sort of
 * {@code CRITICAL | HIGH | MEDIUM | LOW} (V73's {@code ck_ndi_gap_sev}). Ascending alphabetically
 * that reads:
 *
 * <pre>  CRITICAL, HIGH, LOW, MEDIUM  </pre>
 *
 * <p>🔑 <b>Which is why this one survived: the top two rungs are accidentally right.</b> CRITICAL and
 * HIGH really are the alphabetically-first two, so the gap register opened on the rows a reviewer
 * cares most about and looked correct — while <b>MEDIUM and LOW were silently transposed</b>, every
 * time, on 18 live rows in both dev and prod. A sort that is right where you look and wrong where you
 * do not is harder to notice than one that is uniformly wrong, and it is the same accident that makes
 * {@code ai_governance.ml_model.risk_tier}'s lexical sort genuinely correct — there the query narrows
 * to {@code IN ('CRITICAL','HIGH')}, so the accident is total rather than partial. The difference
 * between those two cases is measurable, not a matter of judgement, which is what
 * {@link OrderedVocabulary#isAlphabetical} exists to express.
 */
public final class NdiGapSeverity {

    /**
     * Least to most severe, naming V73's existing {@code CHECK}. The vocabulary was already closed;
     * the order is what had never been written down.
     */
    public static final OrderedVocabulary LADDER = OrderedVocabulary.of(
            "severity", "ck_ndi_gap_sev",
            "LOW", "MEDIUM", "HIGH", "CRITICAL");

    private NdiGapSeverity() {}

    /** Least-to-most-severe, as declared. */
    public static List<String> ladder() {
        return LADDER.ladder();
    }

    /** Position on the ladder, or {@link OrderedVocabulary#UNRECOGNISED}. */
    public static int rank(String severity) {
        return LADDER.rank(severity);
    }

    /** Is {@code severity} at or above {@code threshold}? */
    public static boolean atLeast(String severity, String threshold) {
        return LADDER.atLeast(severity, threshold);
    }
}
