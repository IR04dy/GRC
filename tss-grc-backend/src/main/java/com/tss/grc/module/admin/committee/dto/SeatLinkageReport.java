package com.tss.grc.module.admin.committee.dto;

import java.util.List;

/**
 * Roster-linkage coverage for one committee (finding F2-26).
 *
 * <p><b>What this report is for.</b> F2-26 was not a code defect — the mechanism worked and
 * the data did not exist, on all 31 seeded seats, unnoticed until a live probe found a
 * legitimate member getting <em>"you are not an active member of this committee"</em>. The
 * report is the control that stops that recurring: the gap becomes a number on the screen
 * the secretariat already uses, instead of a discovery made in a meeting.
 *
 * <p><b>Why voting coverage is reported separately, and is the number that matters.</b>
 * Non-voting seats — standing invitees, the DPO attending the risk committee, the external
 * auditor at the audit committee — never cast a counted vote, so linking them changes
 * nothing about whether a quorate meeting can be held with members voting as themselves.
 * A single blended percentage lets a committee read 80% while every voting chair is
 * unlinked. {@code votingUnlinked} is the count that answers the operational question.
 *
 * <p><b>Why {@code declaredExternal} is not counted as a gap.</b> A charter legitimately
 * seats an external auditor who will never hold a login. Counting that seat as missing
 * would make the report permanently red and therefore ignored — so a declared external
 * seat is subtracted from the denominator of {@code coveragePct} and reported in its own
 * right, with its basis, so the declaration itself stays auditable.
 *
 * <p><b>Why there are now two counts of "linked", and why that is not redundancy (F2-178).</b> This
 * report was written when {@code member_user_id} had exactly one consumer: {@code recordVote}'s roster
 * match, which asks <em>"is this person on the roster?"</em> and is content with {@code renewal_status =
 * 'ACTIVE'}. F2-177 added a second consumer — a seat is now an authorisation subject — and that one also
 * requires {@code ended_at IS NULL} and that today fall inside the term window. So {@code linked} and
 * {@code currentLinked} are two different true statements about the same column, and reporting only the
 * first would let a fully green roster sit on top of a guard that admits nobody. The tree already carries
 * the rule this obeys, written for a different pair: <em>"a report describing access must share its
 * predicate with the enforcement point"</em> ({@code ChangeGovernanceHealthService}, after F2-62 cost
 * exactly that drift).
 *
 * <p>⚠ On 2026-08-09 the two agree on every row in dev and production, and that is <b>not</b> reassurance:
 * nothing in the platform ever moves an ACTIVE term to EXPIRED, so the divergence arrives by calendar
 * arithmetic. The earliest production term ends <b>2027-03-01</b>.
 *
 * @param committeeId       the committee
 * @param activeSeats       ACTIVE terms — the roster as it stands today
 * @param linked            seats with a platform user
 * @param declaredExternal  seats declared to have no platform user, with a basis
 * @param unlinked          seats that are neither — the real gap
 * @param votingSeats       ACTIVE seats that carry a counted vote
 * @param votingLinked      of those, how many can cast their own vote
 * @param votingUnlinked    of those, how many still cannot
 * @param currentSeats      ACTIVE seats whose term window contains today and which have not been ended —
 *                          the seats F2-177's guard is willing to consider at all
 * @param currentLinked     of those, how many name a platform user. <b>This is the number that says
 *                          whether the seat guard admits anybody on this committee.</b> Lower than
 *                          {@code linked} means a linked seat has lapsed and confers nothing
 * @param coveragePct       linked / (activeSeats − declaredExternal), 0 when that is 0.
 *                          100 means every seat that can be linked is linked
 * @param seats             per-seat detail so the UI can act on the specific gap
 */
public record SeatLinkageReport(
        Long committeeId,
        long activeSeats,
        long linked,
        long declaredExternal,
        long unlinked,
        long votingSeats,
        long votingLinked,
        long votingUnlinked,
        long currentSeats,
        long currentLinked,
        int  coveragePct,
        List<Seat> seats
) {

    /** LINKED = holds a platform user · EXTERNAL = declared to have none · UNLINKED = gap. */
    public enum State { LINKED, EXTERNAL, UNLINKED }

    /**
     * @param termId       the roster row, which is what {@code link-user} addresses
     * @param memberName   name as it appears on the charter and in the minutes
     * @param role         Chair | Vice-Chair | Member | Secretary | Standing Invitee | …
     * @param votingMember whether this seat casts a counted vote
     * @param state        which of the three states this seat is in
     * @param memberUserId the linked user, or NULL
     * @param username     the linked user's login handle, resolved for display, or NULL
     * @param fullNameEn   the linked user's name — deliberately surfaced so a mis-link
     *                     ("CRO" pointing at the auditor's account) is visible on the
     *                     report rather than discovered when the vote is attributed
     * @param basisEn      why the seat is external; NULL unless state is EXTERNAL
     * @param currentlySeated whether this seat's term window contains today and it has not been ended —
     *                     F2-178. Deliberately a flag beside {@code state} rather than a fourth
     *                     {@code State} value: currency and linkage are independent axes, and a
     *                     {@code LAPSED} enum member would force a lapsed-and-unlinked seat to report
     *                     only one of its two problems
     */
    public record Seat(
            Long    termId,
            String  memberName,
            String  role,
            boolean votingMember,
            State   state,
            Long    memberUserId,
            String  username,
            String  fullNameEn,
            String  basisEn,
            boolean currentlySeated
    ) {}
}
