package com.tss.grc.common.validation;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A {@link ClosedVocabulary} whose members are <b>ORDERED</b> — a ladder, not a set of labels.
 *
 * <h2>Why this exists</h2>
 * F2-229 found a severity ladder being ranked by {@code ORDER BY <NVARCHAR> DESC}, which is a
 * ranking only where the vocabulary happens to be in alphabetical order. Severity ladders never
 * are: descending alphabetically, {@code LOW|MEDIUM|HIGH|CRITICAL} reads MEDIUM, LOW, HIGH,
 * CRITICAL, so the register's most critical row came back last of thirteen.
 *
 * <p>F2-230 then measured the platform from the values its columns actually store rather than from
 * the words in their names, and the distinction that matters turned out to be narrower than
 * "graded":
 *
 * <ul>
 *   <li><b>CATEGORICAL</b> — {@code [ENVIRONMENTAL, SOCIAL, GOVERNANCE]},
 *       {@code [AGENDA, ATTENDANCE, MOTION]}. An alphabetical sort is a deterministic grouping
 *       that claims nothing. 51 of the 57 vocabulary-shaped sorted columns are this, and a hard
 *       zero over them would fail on correct code — the one failure a gate must never have.</li>
 *   <li><b>ORDERED</b> — a ladder. Sorting it lexically asserts a rank the vocabulary does not
 *       have. This class is for those, and for those only.</li>
 * </ul>
 *
 * <p>🔑 <b>Grading is only ONE way to be ordered.</b> F2-229's work list was keyed on a
 * graded-sounding column NAME, which is the instrument class F2-217 proved measures its own
 * vocabulary. Measuring from stored values instead surfaced an ordered vocabulary whose name
 * sounds like nothing of the kind: {@code consumer.complaint_aggregate_report.report_period}
 * holds {@code Q4-2025}, and a lexical sort of {@code Q<n>-<year>} orders by QUARTER before YEAR,
 * so the most recent SAMA quarterly return is not first. Time and version are ordered too. When
 * you ask "is a lexical sort safe here?", the question is whether the vocabulary is ORDERED — not
 * whether it sounds like a severity.
 *
 * <h2>What it gives you</h2>
 * One declaration per ladder, read by every surface that ranks, thresholds or displays it, so the
 * order cannot be restated differently in two places (F2-196: a threshold over a declared ladder,
 * never a second inclusion list).
 *
 * <p>⚠ It deliberately does <b>not</b> generate the SQL. JPA's {@code @Query} takes a compile-time
 * constant, so a ranked ordering is necessarily a second copy of the ladder written in SQL —
 * that is unavoidable. What stops the two drifting is
 * {@code OrderedVocabularySortContractTest}, which parses the {@code CASE} ladder out of every
 * {@code @Query} annotation by reflection and compares it token for token with the declaration
 * here.
 *
 * @see ClosedVocabulary for the unordered case, and for the casing rules both share
 */
public final class OrderedVocabulary {

    /** Answer of {@link #rank} for a token outside the ladder. Deliberately below every member. */
    public static final int UNRECOGNISED = -1;

    private final ClosedVocabulary vocabulary;
    private final List<String> ladder;

    private OrderedVocabulary(ClosedVocabulary vocabulary, List<String> ladder) {
        this.vocabulary = vocabulary;
        this.ladder = ladder;
    }

    /**
     * Declare an ordered vocabulary.
     *
     * @param field      the API field name, used in the 400 message so a caller knows what to fix
     * @param constraint the {@code CHECK} constraint this ladder mirrors, e.g.
     *                   {@code CK_tpc_residual_risk_tier}. Not decoration: it is what lets
     *                   {@code ClosedVocabularyPinTest} check the tokens against the schema.
     * @param ladderLeastToMost the members <b>in order, least first</b>. The order is the whole
     *                   point of this class, so it is the argument list rather than a set.
     */
    public static OrderedVocabulary of(String field, String constraint, String... ladderLeastToMost) {
        List<String> rungs = List.of(ladderLeastToMost);
        if (rungs.size() < 2) {
            throw new IllegalArgumentException(
                "an ordered vocabulary needs at least two rungs; " + field + " declares " + rungs);
        }
        if (Set.copyOf(rungs).size() != rungs.size()) {
            throw new IllegalArgumentException(
                "the ladder for " + field + " repeats a rung: " + rungs);
        }
        return new OrderedVocabulary(
            ClosedVocabulary.of(field, constraint, ladderLeastToMost), rungs);
    }

    /** The members, least first. The single declaration of the order. */
    public List<String> ladder() {
        return ladder;
    }

    /** The underlying set, for {@code require}/{@code coerce} and for the schema pin. */
    public ClosedVocabulary vocabulary() {
        return vocabulary;
    }

    /**
     * Position on the ladder, 0 for the lowest rung. {@link #UNRECOGNISED} for anything else —
     * <b>including null</b>, because a column with no value has no rank, and answering 0 would
     * silently file it at the bottom of a severity ladder as though that had been measured.
     *
     * <p>Casing is normalised: the collation is case-INSENSITIVE, so {@code 'critical'} can be
     * stored where {@code CRITICAL} was meant and a case-sensitive Java comparison then reads it
     * as a token nobody declared (F2-179).
     */
    public int rank(String token) {
        if (token == null || token.isBlank()) return UNRECOGNISED;
        return ladder.indexOf(token.trim().toUpperCase(Locale.ROOT));
    }

    /** True when {@code candidate} sits strictly higher than {@code current}. */
    public boolean outranks(String candidate, String current) {
        return rank(candidate) > rank(current);
    }

    /**
     * The threshold question — {@code token} at or above {@code threshold}.
     *
     * <p>This is what replaces an inclusion list. {@code status == 'HIGH' || status == 'CRITICAL'}
     * is a second copy of the top of the ladder, and every reader who writes one drops whatever
     * rung they did not think of: F2-229 found six such readers and all six dropped CRITICAL,
     * which the column held.
     *
     * @throws IllegalArgumentException if the THRESHOLD is not a member — that is a programming
     *         error in the caller, and answering {@code false} would hide it. An unrecognised
     *         {@code token} is data and answers {@code false}.
     */
    public boolean atLeast(String token, String threshold) {
        int floor = rank(threshold);
        if (floor == UNRECOGNISED) {
            throw new IllegalArgumentException(
                "'" + threshold + "' is not a rung of " + vocabulary.field() + "; expected one of " + ladder);
        }
        return rank(token) >= floor;
    }

    /** The lowest rung — the state a record starts in where the ladder is also a progression. */
    public String bottom() {
        return ladder.get(0);
    }

    /** The highest rung. */
    public String top() {
        return ladder.get(ladder.size() - 1);
    }

    /**
     * Whether this ladder happens to coincide with its own alphabetical order — i.e. whether a
     * lexical sort would be correct for it by accident.
     *
     * <p>Not a curiosity. F2-229 measured {@code ai_governance.ml_model.risk_tier} being sorted
     * lexically and the sort is <b>correct</b>, because the query narrows to
     * {@code IN ('CRITICAL','HIGH')} and those two tokens' alphabetical order is their severity
     * order. A gate that flagged it would be a gate failing on working code, so the accident has
     * to be expressible rather than argued about in a comment.
     */
    public boolean isAlphabetical() {
        return ladder.equals(ladder.stream().sorted().toList());
    }

    /** Normalise a required value — delegates, so a ladder needs no second copy of the tokens. */
    public String require(String value) {
        return vocabulary.require(value);
    }

    /** Normalise an optional value, defaulting to a named rung. */
    public String orDefault(String value, String fallback) {
        return vocabulary.orDefault(value, fallback);
    }

    /**
     * Membership, casing-normalised.
     *
     * <p>Expressed through {@link #rank} rather than through the underlying set, so a ladder has
     * exactly one notion of "is this a member" and it is the one the ordering uses. A second
     * membership test that could disagree with the rank is how the vocabularies in this programme
     * keep drifting apart (F2-190: an unread second opinion is how the next reader decides it is
     * the authority).
     */
    public boolean is(String value) {
        return rank(value) != UNRECOGNISED;
    }
}
