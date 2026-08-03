package com.tss.grc.module.admin.committee.service;

import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * S7 — <b>which decision mode is authoritative, stated once.</b>
 *
 * <p>The S7 row requires the platform to "state which decision mode is authoritative per change
 * type (meeting-bound minutes vs asynchronous electronic vote — leaving it ambiguous recreates
 * F-014's two-answers defect)". This class is that statement, and it is a <em>measurement</em>
 * rather than a design choice:
 *
 * <p><b>Meeting-bound minutes is authoritative, for every change type, because it is the only
 * mode the schema can represent.</b> {@code admin.committee_resolutions.meeting_id} is
 * {@code NOT NULL} (V154) and {@link CommitteeResolution#getMeetingId()} is mapped
 * {@code nullable = false}, so a resolution cannot exist without a meeting. An asynchronous
 * electronic vote — a motion decided with no meeting to minute — has no representable form.
 *
 * <p>⚠ <b>And there is a dead branch that makes it look like there are two modes.</b>
 * {@code CommitteeReferralService.assertDecisionApproves} reads
 * {@code if (res.getMeetingId() != null) { ...require MINUTES_APPROVED... }} — a condition that
 * can never be false. Read quickly, that guard says "meeting-bound resolutions need approved
 * minutes; asynchronous ones do not", i.e. it looks like a deliberate two-mode policy with a
 * documented exemption. It is not: it is one mode plus an unreachable else. The distinction
 * matters because the unreachable branch is the permissive one, so anyone who later makes
 * {@code meeting_id} nullable to add asynchronous voting would silently ship a decision path
 * with <em>no</em> minutes gate at all. If asynchronous voting is ever built, the exemption has
 * to be argued on its merits and gated on something — not inherited from a null check.
 *
 * <p><b>So the binding rule is:</b> a committee decision is binding when the outcome is decided
 * <em>and</em> the meeting's minutes are {@code MINUTES_APPROVED}. Those minutes already carry
 * four gates (COMPLETED, quorate, quorum COMPUTED not asserted, and F2-39's separation from
 * whoever recorded attendance), an e-signature over a content hash, and — since F2-90 — a
 * ledger entry. Nothing else in the platform attests a committee decision that strongly, which
 * is why {@link #isBinding} is the single predicate and both consumers use it: the gate that
 * lets a change advance, and the event that tells the outside world.
 *
 * <p>The emergency path is <b>not</b> a second decision mode. An ECAB emergency change is
 * authorised outside a meeting and then <em>ratified</em> — A17-D3 modelled ratification as
 * columns orthogonal to {@code status} precisely so it would not need a resolution, and it has
 * its own SLA, breach sweep and escalation. It does not reach this class.
 */
public final class CommitteeDecisionAuthority {

    private CommitteeDecisionAuthority() { }

    /** A motion raised and not yet decided. F2-89 / V349 — the value that did not exist. */
    public static final String PROPOSED = "PROPOSED";

    /**
     * The outcomes that mean the committee has decided. Order is the V154 declaration order so
     * the migration ratchet can compare literally.
     */
    public static final Set<String> DECIDED = Set.of("APPROVED", "REJECTED", "DEFERRED", "WITHDRAWN");

    /**
     * Every value {@code CK_resolution_outcome} permits after V349.
     *
     * <p>Insertion-ordered and asserted against the migration text by
     * {@code CommitteeOutcomeVocabularyTest}. F2-91 is the reason: the committee-type vocabulary
     * was stated three times and wrong twice, and the constant that disagreed with the CHECK
     * constraint turned a create into a 500. One constant, one ratchet.
     */
    public static final Set<String> ALL = java.util.Collections.unmodifiableSet(
        new LinkedHashSet<>(java.util.List.of(PROPOSED, "APPROVED", "REJECTED", "DEFERRED", "WITHDRAWN")));

    /** The meeting status at which minutes — and therefore the decisions in them — become binding. */
    public static final String MINUTES_APPROVED = "MINUTES_APPROVED";

    /**
     * Has the committee decided this motion?
     *
     * <p>{@code null} is undecided, not decided: a row whose outcome could not be read must not
     * be treated as carrying a verdict. Unknown values are also undecided — the same stance
     * {@link CommitteeSeatOffice#UNCLASSIFIED} takes on a title the platform cannot read, and
     * for the same reason: guessing in the permissive direction is how a control disappears.
     */
    public static boolean isDecided(String outcome) {
        return outcome != null && DECIDED.contains(outcome.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** Convenience for a resolution row. */
    public static boolean isDecided(CommitteeResolution r) {
        return r != null && isDecided(r.getOutcome());
    }

    /**
     * Is this decision <b>binding</b> — decided, on minutes that have been approved?
     *
     * <p>A null meeting is <em>not</em> binding. See the class javadoc: today it cannot happen
     * ({@code meeting_id NOT NULL}), and if it ever can, the answer must be argued rather than
     * defaulted. Failing closed here means the worst case is a decision that is not announced,
     * never one announced without approved minutes.
     */
    public static boolean isBinding(CommitteeResolution r, CommitteeMeeting meeting) {
        return isDecided(r) && meeting != null && MINUTES_APPROVED.equals(meeting.getStatus());
    }
}
