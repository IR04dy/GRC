package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommitteeMemberTermRepository extends JpaRepository<CommitteeMemberTerm, Long> {

    /**
     * F2-178 — the <b>one</b> predicate that decides whether a seat may authorise anything, as a
     * compile-time constant so every query that needs it is built from this text rather than from a
     * copy of it.
     *
     * <p><b>Why a constant and not a second identical clause.</b> The tree already records the lesson
     * this exists to obey, written for a different pair: {@code ChangeGovernanceHealthService} says
     * <em>"a report describing access must share its predicate with the enforcement point"</em>, because
     * F2-62 cost exactly that — an access report whose predicate had drifted from the gate it described.
     * F2-26's {@code SeatLinkageReport} and F2-177's seat guard are now that same pair one table over:
     * both read {@code member_user_id}, and until this constant existed they decided "this seat counts"
     * by <b>different rules</b>. {@link #findActiveByCommittee} — the report's source — filters on
     * {@code renewal_status} alone; the guard also requires {@code ended_at IS NULL} and that today falls
     * inside the term window. So the report could say a roster was fully linked while the guard admitted
     * nobody on it, and nothing would have disagreed.
     *
     * <p>⚠ <b>That divergence is 0 rows today and it is dated, not hypothetical.</b> Measured 2026-08-09:
     * no ACTIVE term in dev or production has an expired window or a set {@code ended_at}. But
     * <b>nothing in this platform ever transitions an ACTIVE term to EXPIRED</b> — {@code
     * CommitteeScheduledSweep} fires 90/60/30-day reminders and {@code endTerm} is a manual act — so the
     * moment a term passes {@code term_ends_at} the row keeps reading {@code ACTIVE} for ever. The
     * earliest production term ends <b>2027-03-01</b>. On that date the report starts overstating and
     * the guard starts refusing, by calendar arithmetic alone and with no code change.
     *
     * <p>{@code :on} must be bound by every query that concatenates this. Interface fields are
     * implicitly {@code public static final}, and concatenation of final String constants is a constant
     * expression, so this is legal inside {@code @Query} — the annotation sees one string, and there is
     * only one place to edit it.
     *
     * <p>It is split in two because the two halves are needed in different <em>syntactic positions</em>:
     * {@link #CURRENT_SEAT_WINDOW} is a bare boolean expression, so it can sit in a {@code WHERE} clause
     * and inside a {@code CASE WHEN} arm, which is what the coverage aggregate needs in order to count
     * both rules over one pass. Splitting it is what makes the sharing real rather than claimed: had the
     * aggregate re-typed the window into its {@code CASE} arms, this javadoc would have described a
     * single predicate over two copies of one — the exact defect it exists to prevent.
     *
     * <p>⚠ <b>Both constants deliberately begin with a newline, and removing it breaks every caller
     * silently.</b> Java strips incidental <em>trailing</em> whitespace from each line of a text block, so
     * a caller writing {@code SUM(CASE WHEN """ + CURRENT_SEAT_WINDOW} loses the space it thought it had
     * and emits {@code WHENt.endedAt} — which fails at <b>context startup</b> with a
     * {@code QueryCreationException}, taking the whole application down rather than one endpoint. Leading
     * the constant with its own separator makes it impossible for a caller to get that wrong. (Measured:
     * this is exactly how the first draft of the aggregate below failed.)
     */
    String CURRENT_SEAT_WINDOW = """

        t.endedAt IS NULL
              AND t.termStartsAt <= :on
              AND (t.termEndsAt IS NULL OR t.termEndsAt >= :on)""";

    /** {@link #CURRENT_SEAT_WINDOW} plus the {@code renewal_status} half, as a {@code WHERE} fragment. */
    String CURRENT_SEAT_PREDICATE = """

              AND t.renewalStatus = 'ACTIVE'
              AND""" + CURRENT_SEAT_WINDOW;

    Optional<CommitteeMemberTerm> findByIdAndOrganisationId(Long id, Long organisationId);

    /** All term rows for a committee — active + historical. Sorted by
     *  appointed_at desc so the most recent appointments come first. */
    List<CommitteeMemberTerm> findByOrganisationIdAndCommitteeIdOrderByAppointedAtDesc(
        Long organisationId, Long committeeId);

    /** Active members only — drives the quorum check, independence-ratio
     *  KPI, and current-membership card on the committee detail page. */
    @Query("""
        SELECT t FROM CommitteeMemberTerm t
        WHERE t.organisationId = :organisationId
          AND t.committeeId = :committeeId
          AND t.renewalStatus = 'ACTIVE'
        ORDER BY t.role ASC, t.memberName ASC
    """)
    List<CommitteeMemberTerm> findActiveByCommittee(Long organisationId, Long committeeId);

    /** Terms expiring within a window — used by the scheduled sweep to
     *  fire 90/60/30-day renewal reminders. */
    @Query("""
        SELECT t FROM CommitteeMemberTerm t
        WHERE t.organisationId = :organisationId
          AND t.renewalStatus = 'ACTIVE'
          AND t.termEndsAt IS NOT NULL
          AND t.termEndsAt BETWEEN :from AND :to
        ORDER BY t.termEndsAt ASC
    """)
    List<CommitteeMemberTerm> findExpiringBetween(Long organisationId, LocalDate from, LocalDate to);

    /** Committees where this user currently sits (active term with a linked
     *  platform user) — drives workflow-request COMMITTEE recipient matching
     *  (inbox + actionable-recipient checks). */
    @Query("""
        SELECT DISTINCT t.committeeId FROM CommitteeMemberTerm t
        WHERE t.organisationId = :organisationId
          AND t.memberUserId = :userId
          AND t.renewalStatus = 'ACTIVE'
    """)
    List<Long> findActiveCommitteeIdsForUser(Long organisationId, Long userId);

    /**
     * F2-26 — how many ACTIVE seats this user already holds on this committee.
     *
     * <p>Guards the invariant that one person occupies at most one seat per committee.
     * Two ACTIVE seats for the same user would count that person <b>twice</b> in the
     * quorum denominator F2-28 is about to derive from this table, and would let them cast
     * two counted votes on one resolution — the same manufacture-a-quorum outcome DF-3
     * closed from the other direction. {@code excludeTermId} lets a re-link of the seat
     * being edited pass without matching itself.
     */
    @Query("""
        SELECT COUNT(t) FROM CommitteeMemberTerm t
        WHERE t.organisationId = :organisationId
          AND t.committeeId = :committeeId
          AND t.memberUserId = :userId
          AND t.renewalStatus = 'ACTIVE'
          AND (:excludeTermId IS NULL OR t.id <> :excludeTermId)
    """)
    long countActiveSeatsForUser(Long organisationId, Long committeeId, Long userId,
                                Long excludeTermId);

    /**
     * F2-177 — does this user hold a <b>current</b> seat on any of these committees?
     *
     * <p>⚠ <b>Why this is not {@link #countActiveSeatsForUser}.</b> That query answers "does this user
     * already <em>occupy</em> a seat here", which F2-26 needs to stop one person holding two seats on one
     * committee, and it deliberately ignores dates — a future-dated appointment still occupies the seat it
     * was made to. Authorisation is the opposite question, and the two differ on exactly the rows that
     * matter: a term that has not started yet, and a term whose {@code term_ends_at} has passed while
     * {@code renewal_status} still reads {@code ACTIVE} because the renewal sweep has not run. Neither may
     * authorise anything, and both would have passed the other query.
     *
     * <p>{@code endedAt} is checked as well as the dates: ending a term mid-window is a distinct act
     * (resignation, removal) recorded in {@code end_reason}, and it revokes the seat immediately rather
     * than at {@code term_ends_at}.
     *
     * <p>{@code on} is a parameter rather than {@code CURRENT_DATE} so a test can stand on either side of
     * a term boundary — F2-160's lesson, where a suite failed on Saturdays and nothing in the repository
     * said the clock was an input.
     *
     * <p>{@code memberUserId} is only ever non-null for a seat that has been linked to a platform user,
     * so external directors and unlinked seats can never match: no {@code no_platform_user} clause is
     * needed, and adding one would read as though it were load-bearing.
     *
     * <p><b>F2-178.</b> The window clause is no longer written here — it comes from
     * {@link #CURRENT_SEAT_PREDICATE}, shared with the coverage report so the report cannot describe an
     * access rule the guard does not apply. Read that constant's javadoc before changing either.
     */
    @Query("""
        SELECT COUNT(t) FROM CommitteeMemberTerm t
        WHERE t.organisationId = :organisationId
          AND t.committeeId IN :committeeIds
          AND t.memberUserId = :userId
    """ + CURRENT_SEAT_PREDICATE)
    long countCurrentSeatsForUser(Long organisationId, Collection<Long> committeeIds,
                                 Long userId, LocalDate on);

    /**
     * F2-178 — per-committee roster coverage for the whole tenant, in one query.
     *
     * <p>Returns {@code [committeeId, seats, linked, declaredExternal, votingSeats, votingLinked,
     * currentSeats, currentLinked]} for every committee that has at least one ACTIVE term row.
     * Committees with <b>no</b> roster produce no row at all, which is why the service joins this against
     * the committee list rather than reading the shape off this result: a committee with an empty roster
     * is a distinct and worse state than one with unlinked seats — it can never be quorate, which is what
     * {@code ChangeGovernanceHealthService.ROSTER_EMPTY} already refuses CAB approvals for — and a report
     * built only from term rows cannot see it. Measured 2026-08-09: <b>9 of 17</b> dev committees and
     * <b>11 of 17</b> production committees have no roster whatsoever.
     *
     * <p><b>The two pairs of seat counts are the point of this query.</b> {@code seats}/{@code linked}
     * (and the voting split) answer F2-26's question — "is this roster maintained, and can these members
     * cast their own votes?" — counting by {@code renewal_status} alone, exactly as
     * {@link #findActiveByCommittee} does, so the existing per-committee report's numbers are reproduced
     * rather than quietly redefined. {@code currentSeats}/{@code currentLinked} answer F2-177's different
     * question — "does the seat guard admit anybody here?" — counting by {@link #CURRENT_SEAT_WINDOW},
     * the guard's own text. Where the pairs differ, a roster is maintained on paper and confers no
     * access, and the report says so rather than averaging the two into one number that is true of
     * neither.
     *
     * <p>⚠ {@code SUM} over an empty group returns {@code NULL}, not 0. This query groups only non-empty
     * groups so it cannot happen here, but the service coalesces anyway: adding a {@code LEFT JOIN} later
     * would reintroduce exactly that NULL, and it stays invisible until a tenant has an empty committee.
     */
    @Query("""
        SELECT t.committeeId,
               COUNT(t),
               SUM(CASE WHEN t.memberUserId IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN t.memberUserId IS NULL AND t.noPlatformUser = true THEN 1 ELSE 0 END),
               SUM(CASE WHEN t.isVotingMember = 1 THEN 1 ELSE 0 END),
               SUM(CASE WHEN t.isVotingMember = 1 AND t.memberUserId IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN """ + CURRENT_SEAT_WINDOW + """
                        THEN 1 ELSE 0 END),
               SUM(CASE WHEN t.memberUserId IS NOT NULL AND """ + CURRENT_SEAT_WINDOW + """
                        THEN 1 ELSE 0 END)
          FROM CommitteeMemberTerm t
         WHERE t.organisationId = :organisationId
           AND t.renewalStatus = 'ACTIVE'
         GROUP BY t.committeeId
    """)
    List<Object[]> rosterCoverageByCommittee(Long organisationId, LocalDate on);

    /** Independence ratio numerator — count of independent active members. */
    @Query("""
        SELECT COUNT(t) FROM CommitteeMemberTerm t
        WHERE t.organisationId = :organisationId
          AND t.committeeId = :committeeId
          AND t.renewalStatus = 'ACTIVE'
          AND t.isIndependent = 1
    """)
    long countActiveIndependents(Long organisationId, Long committeeId);

    @Query("SELECT DISTINCT t.organisationId FROM CommitteeMemberTerm t")
    List<Long> findDistinctOrganisationIds();
}
