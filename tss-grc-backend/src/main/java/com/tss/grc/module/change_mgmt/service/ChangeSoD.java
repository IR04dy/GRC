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

    /**
     * SAME if the actor matches any party; otherwise DIFFERENT when at least one was checkable.
     *
     * <p><b>F2-113 — an actor that may itself be unlinked.</b> Every caller until now passed an
     * authenticated principal, which always carries an id. The committee force-recusal passes a
     * {@code committee_member_terms} seat, and 29 of dev's 42 active seats carry no
     * {@code member_user_id} — the table has a {@code no_platform_user} column because an external
     * director legitimately never gets one. That does <b>not</b> need a second rule: rule 2 above keys
     * the name fallback on the <em>party</em>, so a party carrying only a name is compared by name
     * whatever the actor holds, and an unlinked seat keeps every refusal it had.
     *
     * <p>⚠ What it does need is that the caller build one party <b>per bar</b> rather than per person
     * — {@code Party(id, null)} and {@code Party(null, label)} as separate entries, never
     * {@code Party(id, label)}. Merging them re-engages rule 1 ("the id decides when the party has
     * one") and silently retires the name comparison for every unlinked seat, which is a refusal
     * removed by a change that looks like a tidy-up.
     * {@code CommitteeVoteRecusalTallyTest.an_unlinked_seat_is_still_recused_by_name_even_though_the_referral_carries_ids}
     * is the test that catches it, and the reason it exists is that CE-15 nearly shipped the same
     * weakening from the other side.
     */
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
     * @param checkable  records where the compared party carries a user id — {@link Strength#BY_ID}
     * @param labelOnly  records where it carries only a free-text label — {@link Strength#BY_LABEL}
     * @param absent     records where the party is not recorded at all — nothing to compare
     *
     * <p>⚠ <b>F2-251 corrected {@code labelOnly}'s description.</b> It read <i>"records where it
     * carries only a free-text label, <b>so the gate is UNVERIFIABLE</b>"</i>, and that is false:
     * {@link #compare}'s rule 2 compares a label-only party <em>by name</em> and returns SAME or
     * DIFFERENT, which is the whole reason a legacy row can still be refused. The two columns are
     * evidential <em>strength</em>, not can-decide versus cannot.
     *
     * <p>🔑 That sentence was not idle. The belief it states — that a label-only party cannot be
     * decided — is exactly the belief that produced the <i>"legacy fallback only"</i> framing in the
     * two hand-written copies of this comparison, where the name branch was gated behind another
     * party's id being absent. <b>A description that answers the question a reader was about to ask
     * is how a wrong belief propagates into code</b> (F2-184's shape, in an instrument's own
     * documentation).
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

    /**
     * F2-113 — Track D's bar on the deciding committee vote: the change's requester/owner may not
     * cast it.
     *
     * <p>The fourth gate on this report and the first that does not live in this module — it is
     * enforced by {@code CommitteeResolutionService.recordVote}, over data <em>this</em> module hands
     * the engine at referral time. Its coverage therefore comes back through {@code CommitteePort},
     * because the reach depends on two facts that no single module holds: whether the referred change
     * recorded who raised it, and whether the deciding committee's seats are linked to platform users.
     * Reporting only the first would have read as full coverage while 29 of 42 dev seats remained
     * uncomparable — F2-112's own shape, one report down.
     */
    public static final String GATE_COMMITTEE_VOTE_NOT_REQUESTER = "COMMITTEE_VOTE_NOT_REQUESTER";

    /**
     * F2-251 — the CAB vote's own bar (CM-C2), and the reason it was not on this list until now.
     *
     * <p>This report exists to answer <i>"which self-review refusals can actually fire?"</i> — the
     * question F2-112 needed asked. It was answered for four gates, and <b>the two refusals in this
     * module that could not fire were not among them</b>: the CAB vote and the retrospective
     * ratification, the two whose comparison was hand-written rather than routed through this class.
     *
     * <p>🔑 <b>The instrument is real; its POPULATION was a list somebody wrote.</b> Both gates were
     * enforced, tested and shipped, and neither appeared in the report built specifically to say
     * whether such a gate can decide anything — so the one artefact that would have surfaced them
     * had them outside its scope by construction. A gate list is exactly the kind of narrowing this
     * codebase has been caught by before (a filename, a three-entry allow-list, an {@code extends}
     * clause, a field-name list inside a fix); {@code ChangeSodGateCoverageContractTest} now derives
     * the population from the refusals themselves rather than from this constant, in both directions.
     */
    public static final String GATE_CAB_VOTE_NOT_REQUESTER = "CAB_VOTE_NOT_REQUESTER";

    /** F2-251 — the retrospective ratification's bar on the raiser/owner (ADR-017 D-3). */
    public static final String GATE_RATIFIER_NOT_REQUESTER = "RATIFIER_NOT_REQUESTER";

    /** F2-251 — and its bar on the emergency approver, which is a different conflict. */
    public static final String GATE_RATIFIER_NOT_APPROVER = "RATIFIER_NOT_APPROVER";

    public static final List<String> GATES = List.of(
        GATE_SELF_WAIVER, GATE_PIR_NOT_IMPLEMENTER, GATE_CONDITION_NOT_OWNER,
        GATE_COMMITTEE_VOTE_NOT_REQUESTER,
        GATE_CAB_VOTE_NOT_REQUESTER, GATE_RATIFIER_NOT_REQUESTER, GATE_RATIFIER_NOT_APPROVER);
}
