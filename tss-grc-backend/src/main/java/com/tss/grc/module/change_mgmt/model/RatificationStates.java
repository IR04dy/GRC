package com.tss.grc.module.change_mgmt.model;

import java.util.List;
import java.util.Set;

/**
 * A17-D3 — the single vocabulary for emergency-change ratification, plus the
 * bounds on the per-tenant SLA.
 *
 * <p><b>Why a constants class and not an enum.</b> The same reason
 * {@code CommitteeTypes} is one: these values are also a SQL Server CHECK
 * constraint ({@code ck_change_ratification_state}, V345) and a
 * {@code @Pattern} regex, and {@code @Pattern} needs a compile-time constant.
 * An enum cannot supply one. The cost of two definitions is drift, and drift
 * on this exact shape is what produced F2-91 — a 500 instead of a 400, because
 * the Java side accepted a value the database refused. So the vocabulary is
 * declared once here, {@link #REGEX} is derived by hand in lockstep, and
 * {@code RatificationVocabularyTest} fails if the two disagree or if either
 * disagrees with the migration file.
 *
 * <p><b>Why six states.</b> {@link #AWAITING_IMPLEMENTATION} exists because
 * ADR-017 D-3 starts the clock at implementation, not at approval: between ECAB
 * approval and go-live the change is neither compliant nor breached, and
 * collapsing that window into either answer would be a lie in one direction or
 * the other. {@link #GRANDFATHERED} exists because the one pre-existing
 * EMERGENCY row has no {@code implementation_started} at all — measured, in both
 * databases — so it has no clock to be judged against, and SQL's three-valued
 * logic would have quietly filtered it out of every breach query as "fine".
 *
 * <p><b>Breach is not a state.</b> It is derived: {@link #PENDING} and
 * {@code ratification_due_at} in the past. A seventh state would need re-entry
 * logic when a late ratification arrives, and worse, it would let "ratified
 * late" overwrite the fact that it breached. {@code ratification_escalated_at}
 * is the durable record of the breach and survives the ratification.
 */
public final class RatificationStates {

    private RatificationStates() { }

    /** Not an emergency change, or not yet emergency-approved. */
    public static final String NOT_REQUIRED = "NOT_REQUIRED";
    /** ECAB-approved; the clock starts when implementation does. */
    public static final String AWAITING_IMPLEMENTATION = "AWAITING_IMPLEMENTATION";
    /** Clock running. Invariant: {@code ratification_due_at} is never null here. */
    public static final String PENDING = "PENDING";
    /** The CAB retrospectively ratified the emergency change. */
    public static final String RATIFIED = "RATIFIED";
    /** The CAB refused to ratify — the change stands as unauthorised. */
    public static final String REJECTED = "REJECTED";
    /** Pre-dated the clock (V345). No SLA is asserted retrospectively. */
    public static final String GRANDFATHERED = "GRANDFATHERED";

    /** Declaration order matches {@code ck_change_ratification_state} in V345. */
    public static final List<String> VALUES = List.of(
        NOT_REQUIRED, AWAITING_IMPLEMENTATION, PENDING, RATIFIED, REJECTED, GRANDFATHERED);

    /** Compile-time constant for {@code @Pattern} — kept in lockstep with {@link #VALUES}. */
    public static final String REGEX =
        "NOT_REQUIRED|AWAITING_IMPLEMENTATION|PENDING|RATIFIED|REJECTED|GRANDFATHERED";

    public static final String MESSAGE = "must be one of: " + REGEX.replace("|", ", ");

    /** The two decisions a ratification call may carry. */
    public static final String DECISION_RATIFY = "RATIFY";
    public static final String DECISION_REJECT = "REJECT";
    public static final Set<String> DECISIONS = Set.of(DECISION_RATIFY, DECISION_REJECT);

    /**
     * ADR-017 D-3: five business days by default.
     * <p>Business days, not calendar days, and Saudi business days — the working
     * week is Sunday–Thursday, so {@code SaudiWorkdayCalendar} is used rather
     * than plain date arithmetic. A tenant's public holidays must not silently
     * consume the ratification window.
     */
    public static final int DEFAULT_SLA_BUSINESS_DAYS = 5;

    /**
     * Amendment 2's ceiling. Ten business days is two weekly CAB cycles: an
     * emergency change still unratified after two consecutive CABs is an
     * unauthorised change, not a slow one. Mirrored by
     * {@code ck_ratification_sla_bounds} so a direct UPDATE hits it too — a DTO
     * annotation alone does not survive a support script.
     */
    public static final int MIN_SLA_BUSINESS_DAYS = 1;
    public static final int MAX_SLA_BUSINESS_DAYS = 10;

    /** The F8 trigger the breach sweep publishes; consumed by rule CHG-RATIFY-BREACH. */
    public static final String EVENT_BREACHED = "CHANGE_RATIFICATION_BREACHED";

    public static boolean isValid(String state) {
        return state != null && VALUES.contains(state);
    }

    /** True when this state means the tenant still owes a ratification decision. */
    public static boolean isOpen(String state) {
        return PENDING.equals(state) || AWAITING_IMPLEMENTATION.equals(state);
    }
}
