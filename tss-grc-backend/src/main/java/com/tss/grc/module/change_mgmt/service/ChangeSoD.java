package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.security.GrcUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * CE-15 — the platform's one answer to "is the acting principal the same person as this named party?",
 * and an honest account of when it cannot tell.
 *
 * <h2>Why this exists as one class</h2>
 * There are now three self-review refusals over a change's people: the condition waiver (S7e), the PIR
 * review and the condition closure (both CE-15). {@code assertNotSelfWaiver} had the comparison written
 * inline; a second and third copy of it is the mirror maintained by assertion that S8h and CE-13 each
 * refused — it lies the first time one copy learns something the others do not.
 *
 * <h2>⚠ What the measurement said before any gate was written (F2-112)</h2>
 * A change's people are stored as <b>free-text labels</b>, and in dev they are <b>teams, not people</b>:
 * <pre>
 *   requested_by_id  NULL on 17 of 17     change_owner_id  NULL on 17 of 17
 *   requester labels resolving to a user ....  0 of 13   'AppSec', 'CIO Office', 'SRE', 'FCC Head' …
 *   owner labels resolving to a user ........  0 of 15   'IAM Team', 'CISO', 'CloudOps' …
 *   implementer labels resolving to a user ..  0 of  4   'AppSec on-call', 'HR Comms' …
 * </pre>
 * So the shipped waiver refusal always took its name branch and compared a username ({@code admin})
 * against a team name ({@code CloudOps}) — it could not fire for any change in either database. A gate
 * whose comparison cannot match is not a weak control; it is an absent one that reports as present.
 *
 * <h2>The rule this class encodes</h2>
 * <ol>
 *   <li><b>The id decides when the <em>party</em> has one.</b> Real identity, and what records created
 *       from CE-15 onwards carry.</li>
 *   <li><b>Otherwise the name decides</b>, case-insensitively — preserved from
 *       {@code assertNotSelfWaiver}, and keyed on the <b>party</b> lacking an id rather than on both
 *       sides, so nothing that used to be refused stops being refused. See {@link #compare} for the
 *       regression an existing S7e test caught when the first draft got this backwards.</li>
 *   <li><b>"Cannot tell" is a third answer and it is never a silent pass.</b>
 *       {@link Verdict#UNVERIFIABLE} is for the cases where nothing comparable exists at all.</li>
 *   <li><b>How much an answer rested on is reported separately</b> ({@link #strength}), because a
 *       {@code DIFFERENT} between a username and a team label is a real answer to a weak question, and
 *       a gate must not be weakened in order to describe its own weakness. {@code
 *       GET /api/v1/changes/sod-coverage} publishes that population per gate — S6b-i's rule applied to
 *       an instrument rather than to a verdict: {@code satisfied} must not read true over something
 *       unverifiable, and here the honest move is to say how much is unverifiable.</li>
 * </ol>
 *
 * <p>The gates <b>refuse only on a positive match</b>. Refusing on a weak comparison would block every
 * change that exists today — the mistake S6c avoided for {@code quorum_requires_chair} (default-on would
 * have refused 17 of 18 quorate meetings) and S8e avoided by shipping its flag inert. Coverage is the
 * instrument for the rest, and it is a number an operator can drive down.
 */
public final class ChangeSoD {

    private ChangeSoD() { }

    /** What a comparison between the acting principal and a named party could establish. */
    public enum Verdict {
        /** Same person — the gate refuses. */
        SAME,
        /** Demonstrably different: both sides carried an id, or both carried comparable names. */
        DIFFERENT,
        /**
         * The comparison could not be made. Either side missing, or a label that is not a person.
         *
         * <p>Never treated as DIFFERENT by a caller that reports coverage, and never treated as SAME by
         * a gate: refusing on it would trap every pre-existing record.
         */
        UNVERIFIABLE;

        public boolean refuses() { return this == SAME; }
        public boolean verified() { return this != UNVERIFIABLE; }
    }

    /** The acting principal, or empty when there is none (a scheduler, an unauthenticated test). */
    public record Actor(Long id, String name) {
        public boolean isPresent() { return id != null || (name != null && !name.isBlank()); }
    }

    /** One named party on a record: an id when the platform captured one, and the label as typed. */
    public record Party(String field, Long id, String name) { }

    /** Read the principal once. Mirrors {@code stampDecisionAuthor}'s extraction exactly. */
    public static Actor actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return new Actor(null, null);
        if (auth.getPrincipal() instanceof GrcUserDetails u) {
            return new Actor(u.getUserId(), u.getUsername());
        }
        return new Actor(null, auth.getName());
    }

    /**
     * Is {@code party} the same person as {@code actor}?
     *
     * <p><b>Ids decide when the party has one. Otherwise the name decides.</b> The fallback is keyed on
     * the <em>party</em> having no id — not on both sides lacking one.
     *
     * <p>⚠ This method's first draft keyed the fallback on <b>both</b> sides, and
     * {@code ChangeConditionLifecycleTest.theLegacyFallbackIsAFallbackNotThePrimaryTest} — S7e's own
     * test, written for DF-4/F2-24 — caught it as a regression, correctly. Its scenario is a legacy
     * change with no captured ids and a signed-in user whose <em>id exists</em> and whose name matches
     * the requester: the old inline rule refused (the record has no ids, so names are all there is), and
     * the draft answered UNVERIFIABLE and let the waiver through. A refactor that stops refusing
     * something is not a refactor. The worry that drove the draft — a name match against a team label
     * being read as a clean pass — is real, but it belongs in {@link #strength} and the coverage report,
     * <b>never</b> in the verdict: weakening a refusal to describe its own weakness is the worst of both.
     */
    public static Verdict compare(Actor actor, Party party) {
        if (actor == null || !actor.isPresent() || party == null) return Verdict.UNVERIFIABLE;

        if (party.id() != null) {
            // The party is a known user. Only another id can answer; a name could belong to anyone.
            if (actor.id() == null) return Verdict.UNVERIFIABLE;
            return actor.id().equals(party.id()) ? Verdict.SAME : Verdict.DIFFERENT;
        }
        if (isBlank(actor.name()) || isBlank(party.name())) return Verdict.UNVERIFIABLE;
        return actor.name().trim().equalsIgnoreCase(party.name().trim())
            ? Verdict.SAME : Verdict.DIFFERENT;
    }

    /** How much a comparison rests on. Reported, never allowed to change a {@link Verdict}. */
    public enum Strength {
        /** Both sides carried a user id. */
        BY_ID,
        /** The party is a free-text label, so only a name match could have refused. */
        BY_LABEL,
        /** Nothing comparable. */
        NONE
    }

    /**
     * The evidential strength behind {@link #compare}'s answer.
     *
     * <p>This is the F2-112 instrument. A {@code DIFFERENT} at {@link Strength#BY_LABEL} is a real
     * answer to a weak question — 0 of 28 dev people-labels resolve to any user, so the gate is
     * technically deciding and practically blind. Coverage counts these; the gate still refuses on a
     * name match, because refusing more is never the wrong side to err on.
     */
    public static Strength strength(Actor actor, Party party) {
        if (actor == null || !actor.isPresent() || party == null) return Strength.NONE;
        if (party.id() != null) return actor.id() != null ? Strength.BY_ID : Strength.NONE;
        if (isBlank(actor.name()) || isBlank(party.name())) return Strength.NONE;
        return Strength.BY_LABEL;
    }

    /** SAME if the actor matches any party; otherwise DIFFERENT when at least one was checkable. */
    public static Verdict compareAny(Actor actor, List<Party> parties) {
        boolean anyVerified = false;
        for (Party p : parties) {
            Verdict v = compare(actor, p);
            if (v == Verdict.SAME) return Verdict.SAME;
            anyVerified |= v.verified();
        }
        return anyVerified ? Verdict.DIFFERENT : Verdict.UNVERIFIABLE;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    // ── coverage ────────────────────────────────────────────────────────────────────────────────

    /**
     * How much of one gate's population the gate can actually check.
     *
     * @param gate       the rule, by name
     * @param population records the gate applies to
     * @param checkable  records where the compared party carries a user id — the gate can decide
     * @param labelOnly  records where it carries only a free-text label, so the gate is UNVERIFIABLE
     * @param absent     records where the party is not recorded at all — nothing to compare
     */
    public record Coverage(String gate, long population, long checkable, long labelOnly, long absent) {
        /**
         * Percent of the population this gate can decide, 0 when the population is empty.
         *
         * <p>{@code @JsonProperty} deliberately: a record's non-component accessors are absent from the
         * payload by default, and a consumer left to re-derive "is this gate blind?" from the raw counts
         * is the mirror-maintained-by-assertion problem again — the screen and the server would each
         * hold a copy of the rule.
         */
        @com.fasterxml.jackson.annotation.JsonProperty
        public int coveragePct() {
            return population == 0 ? 0 : (int) Math.round(100.0 * checkable / population);
        }

        /**
         * True when the gate is enabled over a population it cannot decide for a single record.
         *
         * <p>The state worth publishing: the control is on, the tests pass, and it has never been able
         * to fire. F2-112 is exactly this for the waiver refusal, and it went unnoticed because nothing
         * asked the question.
         */
        @com.fasterxml.jackson.annotation.JsonProperty
        public boolean enabledButBlind() { return population > 0 && checkable == 0; }
    }

    /** Gate names, so the report and the refusals cannot drift apart. */
    public static final String GATE_SELF_WAIVER = "CONDITION_WAIVER_NOT_SELF";
    public static final String GATE_PIR_NOT_IMPLEMENTER = "PIR_REVIEWER_NOT_IMPLEMENTER";
    public static final String GATE_CONDITION_NOT_OWNER = "CONDITION_SATISFIER_NOT_OWNER";
    public static final List<String> GATES =
        List.of(GATE_SELF_WAIVER, GATE_PIR_NOT_IMPLEMENTER, GATE_CONDITION_NOT_OWNER);
}
