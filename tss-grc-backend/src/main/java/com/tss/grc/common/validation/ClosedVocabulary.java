package com.tss.grc.common.validation;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * F2-179 — a caller-supplied value destined for a column whose {@code CHECK} constraint restricts
 * it to a closed set of tokens.
 *
 * <p><b>Why this type exists.</b> Every text column in this database is collated
 * {@code Latin1_General_100_CI_AS_SC_UTF8} (or {@code SQL_Latin1_General_CP1_CI_AS}) — 4,460 of
 * 4,511 collated columns, all <b>case-insensitive</b>. So a {@code CHECK (severity IN (N'CRITICAL',
 * …))} <em>accepts</em> {@code 'critical'} and stores it verbatim, and the Java that reads it back
 * with {@code "CRITICAL".equals(severity)} is asking a case-<b>sensitive</b> question of a
 * case-<b>insensitive</b> column. The database's own answer to
 * {@code SELECT CASE WHEN N'critical' = N'CRITICAL'} is <b>equal</b>, which is exactly what makes
 * the constraint useless as a guard for the comparison.
 *
 * <p>🔑 <b>A closed column needs a closed writer</b> — F2-180's lesson, arrived at from the mirror
 * direction (a literal the column <em>refuses</em>). This is the other half: a value the column
 * <em>accepts</em> and the code then fails to recognise. Normalising at the boundary makes the
 * constraint, the Java comparison and the frontend's label map agree, which is why it is preferred
 * over {@code equalsIgnoreCase} at each comparison site: that leaves the odd casing in the column,
 * where the next reader — including the frontend's {@code Record<Token, Label>} lookup — misses it.
 *
 * <p><b>Measured population</b> (2026-08-11, dev): 347 closed upper-case vocabularies across 389
 * single-column CHECKs; 207 reachable through an entity field; <b>79 with an open writer</b> (a
 * caller's casing reaches the column); 25 of those also read back by a case-sensitive Java
 * comparison. Zero non-canonical values are stored today, so this is a latent defect rather than a
 * data-corruption event — the trap is reachable only through the API, which is precisely where
 * machine-to-machine callers live and nobody eyeballs the payload.
 *
 * <p><b>⚠ This is not new code, it is code that was in the wrong package.</b>
 * {@code module/cyber}'s {@code CyberInputValidation#checkEnum} has done exactly this since CY-H
 * — trim, upper-case, validate, throw a 400 naming the set. It was used 6 times, all inside
 * {@code module/cyber}, and ADR-012/ADR-018 forbid a peer module importing it. So every other
 * module either hand-rolled a weaker version or did nothing. Two of the hand-rolled ones
 * ({@code WhistleblowerCaseService#safeChannel}, {@code #safeReporterType}) return their argument
 * unchanged — the {@code safeRole(String role) { return role; }} decoy shape F2-175 deleted.
 *
 * <p><b>The one thing this adds over {@code checkEnum}: the vocabulary names the constraint it
 * mirrors.</b> A bare {@code Set.of(…)} is a claim about the schema that nothing checks, and a
 * claim nothing checks is how {@code AttachmentAccessPolicy}'s role list drifted three times
 * (F2-52, F2-177). Because {@link #constraint()} carries the constraint name,
 * {@code ClosedVocabularyPinTest} can read the migration SQL and fail the build when the two
 * disagree — mechanically, for every vocabulary declared this way, without a hand-maintained
 * registry to forget to update.
 */
public final class ClosedVocabulary {

    private final String field;
    private final String constraint;
    private final Set<String> tokens;

    private ClosedVocabulary(String field, String constraint, Set<String> tokens) {
        this.field = field;
        this.constraint = constraint;
        this.tokens = tokens;
    }

    /**
     * Declare a vocabulary.
     *
     * @param field      the API field name, used in the 400 message so the caller knows what to fix
     * @param constraint the {@code CHECK} constraint this set mirrors, e.g. {@code CK_ml_drift_sev}.
     *                   Not decoration: it is what makes the set checkable against the schema.
     * @param tokens     the allowed tokens, in the constraint's own casing
     */
    public static ClosedVocabulary of(String field, String constraint, String... tokens) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("a ClosedVocabulary must name its field");
        }
        if (constraint == null || constraint.isBlank()) {
            throw new IllegalArgumentException(
                "a ClosedVocabulary must name the CHECK constraint it mirrors — an unnamed set is a "
                + "claim about the schema that nothing can check");
        }
        if (tokens == null || tokens.length == 0) {
            throw new IllegalArgumentException("a ClosedVocabulary with no tokens accepts nothing");
        }
        Set<String> canonical = new LinkedHashSet<>();
        for (String t : tokens) {
            if (t == null || !t.equals(t.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                    "vocabulary tokens must be declared in upper case (got '" + t + "') — the whole "
                    + "point is that upper case is the canonical form");
            }
            canonical.add(t);
        }
        return new ClosedVocabulary(field, constraint, Set.copyOf(canonical));
    }

    /**
     * Normalise a required value: trimmed, upper-cased, and a member of the vocabulary.
     *
     * @throws IllegalArgumentException (→ 400) if blank or outside the set. A 400 naming the
     *         vocabulary beats the 409 the CHECK would raise: before F2-180 that 409 said
     *         "something it refers to does not exist", which sent three investigations hunting a
     *         foreign key that did not exist.
     */
    public String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required and must be one of " + tokens);
        }
        return check(value);
    }

    /** Normalise, or fall back to {@code fallback} when the caller said nothing at all. */
    public String orDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            if (!tokens.contains(fallback)) {
                throw new IllegalArgumentException(
                    "the fallback for " + field + " must itself be in the vocabulary (got '"
                    + fallback + "', allowed " + tokens + ")");
            }
            return fallback;
        }
        return check(value);
    }

    /**
     * Normalise, falling back to {@code fallback} for a value <b>outside</b> the vocabulary as well
     * as for a blank one — without raising anything.
     *
     * <p>⚠ <b>This discards information. Reach for it only where refusing the whole request would
     * lose something worse than a mis-graded field.</b> The motivating case is the public
     * whistleblower portal: {@code safeSeverity} already mapped an unrecognised severity to
     * {@code MEDIUM} and <em>accepted the report</em>, and a report that never arrives is a worse
     * outcome than one triaged at the wrong band. Converting that to a 400 would have made
     * submissions that succeed today start failing, which is the line
     * {@code CyberInputValidation}'s own javadoc draws.
     *
     * <p>What it does fix is the F2-179 defect proper: {@code 'critical'} now lands on
     * {@code CRITICAL} instead of falling through to the fallback. <b>Whether an unrecognised token
     * should be refused rather than coerced is a separate question</b> and deliberately not settled
     * here — it is a product decision about the intake channel, not a casing bug.
     */
    public String coerce(String value, String fallback) {
        if (!tokens.contains(fallback)) {
            throw new IllegalArgumentException(
                "the fallback for " + field + " must itself be in the vocabulary (got '"
                + fallback + "', allowed " + tokens + ")");
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        return tokens.contains(normalised) ? normalised : fallback;
    }

    /**
     * Normalise an <b>optional</b> value, answering {@code null} for blank <em>and</em> for anything
     * outside the vocabulary.
     *
     * <p><b>Why this exists rather than {@link #coerce}.</b> {@code coerce} demands a fallback that is
     * itself a member, because a mis-graded severity is still a severity. A <em>nullable</em> closed
     * column has no such member — {@code consumer.complaint.customer_id_type} is
     * {@code NATIONAL_ID | IQAMA | PASSPORT | CR} and nothing in that set means "not stated", so the
     * absence has to be {@code NULL}. Picking one of the four as a stand-in would assert an identity
     * document the customer never claimed.
     *
     * <p><b>Why not {@link #require}.</b> On the surface that first needed this — the anonymous,
     * SAMA-mandated public complaint form — a 400 loses the complaint. The whistleblower precedent
     * (F2-179) settles the direction: a report that never arrives is worse than one with a field the
     * platform could not interpret.
     *
     * <p>⚠ <b>This discards a value the caller supplied</b>, so it is only right where the field is a
     * classifier the record can do without. It is the wrong answer for anything a control reads —
     * there, a null is not a smaller version of the value, it is an unanswered question. F2-183 uses
     * it once, on an optional ID-type label that no branch in the platform tests.
     */
    public String coerceOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        return tokens.contains(normalised) ? normalised : null;
    }

    /**
     * Is {@code value} this token, ignoring the casing the column could not distinguish?
     *
     * <p>For reading back a value that may predate the writer being closed. New code should compare
     * normalised values with {@code equals} — needing this at a fresh call site means the writer
     * upstream is still open, which is the defect rather than a case for this method.
     */
    public boolean is(String value, String token) {
        if (!tokens.contains(token)) {
            throw new IllegalArgumentException(
                "'" + token + "' is not in the " + field + " vocabulary " + tokens
                + " — comparing against a token the column cannot hold is always false");
        }
        return value != null && token.equalsIgnoreCase(value.trim());
    }

    private String check(String value) {
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        if (!tokens.contains(normalised)) {
            throw new IllegalArgumentException(
                field + " must be one of " + tokens + " (got '" + value + "')");
        }
        return normalised;
    }

    public Set<String> tokens() {
        return tokens;
    }

    /** The {@code CHECK} constraint this vocabulary mirrors — read by the build-time pin. */
    public String constraint() {
        return constraint;
    }

    public String field() {
        return field;
    }

    @Override
    public String toString() {
        return "ClosedVocabulary[" + field + " ~ " + constraint + " " + tokens + "]";
    }
}
