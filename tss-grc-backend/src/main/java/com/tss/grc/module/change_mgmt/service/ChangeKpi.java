package com.tss.grc.module.change_mgmt.service;

/**
 * S8b / CE-12 — one ITIL/ISO KPI, reported with the population it was computed over.
 *
 * <p><b>The record shape IS the slice.</b> §12 of the uplift plan lists twenty metrics as bare
 * percentages ("success rate", "failed-change rate", "quorum-met rate"), and a bare percentage is
 * exactly how this module already misleads: {@code successRatePct} divides SUCCESS by *rows that have
 * an outcome*, and since 15 of 17 changes in dev carry no outcome at all it renders <b>100%</b> on the
 * dashboard — {@link #COMPUTED_PARTIAL} made invisible by the absence of a denominator (F2-107, and
 * F2-42's shape one module over).
 *
 * <p>So no number leaves this class without saying what it is a number <em>of</em>:
 *
 * <pre>
 *   COMPUTED         the metric covers the whole relevant population
 *   COMPUTED_PARTIAL the metric is real but the denominator is a subset — `denominator` &lt; `population`
 *   NO_DATA          the population is measurable and empty; 0 is the honest answer, not a gap
 *   NOT_MEASURABLE   there is no source for this metric at all — `blockedBy` names what is missing
 * </pre>
 *
 * <p><b>{@link #NOT_MEASURABLE} is not an omission, it is the finding.</b> Seven of §12's metrics have
 * no source in either database — implementation timestamps are populated on <b>0 of 17</b> changes, the
 * CE-11 register does not exist, {@code CommitteeMeeting} has no publication act (S7b measured and
 * pinned that), and attendance is recorded on 4 of 34 meetings in dev and <b>0 of 32</b> in prod.
 * Silently dropping them would leave a KPI page that looks complete; returning them with a reason is
 * what tells an operator which control to start recording.
 *
 * <p><b>⚠ {@code provenance} is a second, orthogonal axis, and measurement is what forced it.</b> A
 * figure can cover its whole population and <em>still</em> not be a measurement. The CAB's quorum-met
 * rate is 2 of 2 — complete coverage, {@link #COMPUTED} — but both meetings carry
 * {@code quorum_source = ASSERTED_LEGACY}, meaning a human typed "we were quorate" and nothing
 * computed it from the roster. Reporting "Quorum met: 100%" would upgrade two assertions into a
 * measured control, which is precisely what S7f refused to let the minutes PDF do. So {@code basis}
 * answers <em>how much of the population is covered</em> and {@code provenance} answers <em>whether
 * the covered rows were measured or asserted</em>. Collapsing them loses one of the two.
 *
 * <p>No free text crosses the wire. {@code blockedBy} is a stable code the frontend translates, so the
 * bilingual requirement (house rule 7) is met without inventing a second prose channel that only ever
 * gets written in English.
 *
 * @param key         stable identifier, also the i18n key suffix
 * @param value       the metric, or {@code null} when not measurable
 * @param unit        {@code PCT} · {@code DAYS} · {@code COUNT} · {@code RATIO_PER_MEETING}
 * @param numerator   what was counted, or {@code null}
 * @param denominator what it was counted against — the figure a bare percentage hides
 * @param population  the full population the metric ideally covers; equals {@code denominator} when
 *                    {@code basis} is {@link #COMPUTED}
 * @param basis       coverage: one of the four constants below
 * @param provenance  whether the covered rows were computed or asserted — {@link #MEASURED},
 *                    {@link #ASSERTED}, {@link #MIXED}, or {@code null} when nothing was counted
 * @param blockedBy   why not measurable, as a stable code; {@code null} otherwise
 */
public record ChangeKpi(
    String key,
    Double value,
    String unit,
    Long numerator,
    Long denominator,
    Long population,
    String basis,
    String provenance,
    String blockedBy
) {

    public static final String COMPUTED         = "COMPUTED";
    public static final String COMPUTED_PARTIAL = "COMPUTED_PARTIAL";
    public static final String NO_DATA          = "NO_DATA";
    public static final String NOT_MEASURABLE   = "NOT_MEASURABLE";

    /** Derived from records the platform computed itself. */
    public static final String MEASURED = "MEASURED";
    /** Derived from values a human entered — true unless someone mistyped, but not a measurement. */
    public static final String ASSERTED = "ASSERTED";
    /** Some of each. Reported as its own state rather than rounded to the flattering one. */
    public static final String MIXED = "MIXED";

    public static final String PCT               = "PCT";
    public static final String DAYS              = "DAYS";
    public static final String COUNT             = "COUNT";
    public static final String RATIO_PER_MEETING = "RATIO_PER_MEETING";

    /** Every {@code basis} value, so a test can assert the vocabulary is closed. */
    public static final java.util.List<String> BASES =
        java.util.List.of(COMPUTED, COMPUTED_PARTIAL, NO_DATA, NOT_MEASURABLE);

    /** Every {@code provenance} value. */
    public static final java.util.List<String> PROVENANCES =
        java.util.List.of(MEASURED, ASSERTED, MIXED);

    /** Every {@code unit}, same reason. */
    public static final java.util.List<String> UNITS =
        java.util.List.of(PCT, DAYS, COUNT, RATIO_PER_MEETING);

    /**
     * Stable reasons a metric has no source. Each is a measured fact about this platform, not a
     * placeholder — and each names the control that would have to start being recorded.
     */
    public static final class Blocked {
        /** {@code implementation_started} / {@code implementation_ended}: 0 of 17 changes in dev. */
        public static final String NO_IMPLEMENTATION_TIMESTAMPS = "NO_IMPLEMENTATION_TIMESTAMPS";
        /** No {@code submitted_at} column exists; only {@code created_at}, which is not the same act. */
        public static final String NO_SUBMISSION_TIMESTAMP = "NO_SUBMISSION_TIMESTAMP";
        /** {@code pir_reviewed_at}: 0 of 17 — and F2-105 is why (the card 400'd on every save). */
        public static final String NO_PIR_TIMESTAMPS = "NO_PIR_TIMESTAMPS";
        /** CE-11's unauthorised-change register is not built; A17-D3's interim sink is scoped narrower. */
        public static final String CE11_REGISTER_ABSENT = "CE11_REGISTER_ABSENT";
        /** S7b measured it and pinned it: {@code CommitteeMeeting} has no publication act at all. */
        public static final String NO_PUBLICATION_ACT = "NO_PUBLICATION_ACT";
        /** Attendance rows: 4 of 34 meetings in dev, 0 of 32 in prod. */
        public static final String NO_ATTENDANCE_RECORDS = "NO_ATTENDANCE_RECORDS";
        /** No referral has ever reached a DECIDED state, so time-in-queue has no closing bracket. */
        public static final String NO_DECIDED_REFERRALS = "NO_DECIDED_REFERRALS";
        /** The committee engine is not wired on this deployment (the port is absent). */
        public static final String COMMITTEE_ENGINE_UNAVAILABLE = "COMMITTEE_ENGINE_UNAVAILABLE";

        public static final java.util.List<String> VALUES = java.util.List.of(
            NO_IMPLEMENTATION_TIMESTAMPS, NO_SUBMISSION_TIMESTAMP, NO_PIR_TIMESTAMPS,
            CE11_REGISTER_ABSENT, NO_PUBLICATION_ACT, NO_ATTENDANCE_RECORDS,
            NO_DECIDED_REFERRALS, COMMITTEE_ENGINE_UNAVAILABLE);

        private Blocked() { }
    }

    // ── factories: the only way to build one, so `basis` can never contradict the numbers ────────

    /**
     * A rate over its full population.
     *
     * <p>⚠ Downgrades itself to {@link #NO_DATA} when the denominator is zero rather than reporting
     * {@code 0%}. "No changes have closed yet" and "no closed change succeeded" are different facts
     * and a zero would merge them.
     */
    public static ChangeKpi pct(String key, long numerator, long denominator) {
        if (denominator <= 0) return noData(key, PCT, 0L);
        return new ChangeKpi(key, round1(numerator * 100.0 / denominator), PCT,
                             numerator, denominator, denominator, COMPUTED, MEASURED, null);
    }

    /**
     * A rate whose denominator is a <b>subset</b> of the population it appears to describe.
     *
     * <p>This is the constructor {@code successRatePct} should always have used. It stays
     * {@link #COMPUTED} when coverage is complete, so a tenant that records outcomes properly is not
     * permanently labelled partial.
     */
    public static ChangeKpi pctOfSubset(String key, long numerator, long denominator, long population) {
        if (denominator <= 0) return noData(key, PCT, population);
        return new ChangeKpi(key, round1(numerator * 100.0 / denominator), PCT,
                             numerator, denominator, population,
                             denominator >= population ? COMPUTED : COMPUTED_PARTIAL, MEASURED, null);
    }

    public static ChangeKpi count(String key, long n, long population) {
        return new ChangeKpi(key, (double) n, COUNT, n, population, population, COMPUTED, MEASURED, null);
    }

    public static ChangeKpi days(String key, double days, long sampleSize, long population) {
        if (sampleSize <= 0) return noData(key, DAYS, population);
        return new ChangeKpi(key, round1(days), DAYS, null, sampleSize, population,
                             sampleSize >= population ? COMPUTED : COMPUTED_PARTIAL, MEASURED, null);
    }

    public static ChangeKpi perMeeting(String key, long items, long meetings) {
        if (meetings <= 0) return noData(key, RATIO_PER_MEETING, 0L);
        return new ChangeKpi(key, round1((double) items / meetings), RATIO_PER_MEETING,
                             items, meetings, meetings, COMPUTED, MEASURED, null);
    }

    /** The population is measurable and empty. A real answer, not a gap. */
    public static ChangeKpi noData(String key, String unit, Long population) {
        return new ChangeKpi(key, null, unit, 0L, 0L, population, NO_DATA, null, null);
    }

    /** There is no source for this metric. {@code blockedBy} says what is missing. */
    public static ChangeKpi notMeasurable(String key, String unit, String blockedBy) {
        return new ChangeKpi(key, null, unit, null, null, null, NOT_MEASURABLE, null, blockedBy);
    }

    /**
     * Restate this KPI's provenance.
     *
     * <p>Used where the rows counted were <em>asserted</em> rather than computed — the CAB quorum-met
     * rate over meetings whose {@code quorum_source} is {@code ASSERTED_LEGACY}. Deliberately a
     * separate step from the factories: provenance is a property of the peer's records, not of the
     * arithmetic, so the arithmetic has no business guessing it.
     */
    public ChangeKpi withProvenance(String p) {
        return new ChangeKpi(key, value, unit, numerator, denominator, population, basis, p, blockedBy);
    }

    /** True when the number shown covers less than the population it looks like it describes. */
    public boolean isPartial() { return COMPUTED_PARTIAL.equals(basis); }

    /** True when the figure is real but rests on what somebody typed. */
    public boolean isAsserted() { return ASSERTED.equals(provenance) || MIXED.equals(provenance); }

    private static double round1(double d) { return Math.round(d * 10.0) / 10.0; }
}
