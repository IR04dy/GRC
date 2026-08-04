package com.tss.grc.module.change_mgmt.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * S7e / CE-5 — the condition register's vocabulary, published once.
 *
 * <p>Must stay equal to {@code ck_cc_status} (V352). {@code ChangeConditionRegisterTest} reads the
 * migration and this class and fails if they drift — the F2-91 pattern, after that finding measured a
 * vocabulary stated three times and wrong in two of them.
 *
 * <p><b>Three values, and the two closing ones are not interchangeable.</b> {@code SATISFIED} means
 * the obligation was met; {@code WAIVED} means an authority removed it. Collapsing them into "CLOSED"
 * would make a governance breach indistinguishable from compliance in every report — and the waiver
 * is the one an auditor asks about. That is also why waiving demands {@code CHANGE:APPROVE} while
 * satisfying needs only {@code CHANGE:WRITE}: doing the work is operational, forgiving it is not.
 */
public final class ChangeConditionStatus {

    private ChangeConditionStatus() { }

    /** The obligation stands. This is the only state that blocks implementation. */
    public static final String OPEN = "OPEN";

    /** The obligation was met. */
    public static final String SATISFIED = "SATISFIED";

    /** An authority removed the obligation, with a reason (`ck_cc_waiver_reason` demands one). */
    public static final String WAIVED = "WAIVED";

    /** Both ways a condition stops blocking. */
    public static final Set<String> CLOSED = Set.of(SATISFIED, WAIVED);

    /** Insertion-ordered so a UI can render the vocabulary without restating it. */
    public static final Set<String> ALL =
        new LinkedHashSet<>(List.of(OPEN, SATISFIED, WAIVED));

    public static boolean isClosed(String status) {
        return status != null && CLOSED.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * ⚠ There is deliberately no {@code CONDITIONAL} change status and no {@code CONDITIONAL}
     * resolution outcome. An approve-with-conditions change is {@code CAB_APPROVED}; conditionality
     * is <b>derived</b> from whether the change carries any condition at all. A literal stored beside
     * the register can disagree with it — a {@code CONDITIONAL} row whose conditions were never
     * entered reads conditional with nothing to satisfy, and a plain {@code CAB_APPROVED} row that
     * later acquires a condition reads unconditional while being blocked. The derivation cannot lie.
     *
     * <p>V352's self-check 7 THROWs if {@code ck_change_status} ever names it. To change the
     * decision, amend that check, this javadoc and {@code ChangeConditionRegisterTest} together.
     *
     * <p>Note what it counts: <b>all</b> conditions, not open ones. A change whose conditions have
     * all been satisfied <em>was</em> approved conditionally and must keep saying so — a report that
     * forgets it the moment the last condition closes cannot answer "how many conditional approvals
     * did the CAB grant this quarter", which is the question the register exists to answer.
     */
    public static boolean isConditional(long totalConditionCount) {
        return totalConditionCount > 0;
    }
}
