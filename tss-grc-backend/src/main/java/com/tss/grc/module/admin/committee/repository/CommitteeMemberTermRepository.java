package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommitteeMemberTermRepository extends JpaRepository<CommitteeMemberTerm, Long> {

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
     */
    @Query("""
        SELECT COUNT(t) FROM CommitteeMemberTerm t
        WHERE t.organisationId = :organisationId
          AND t.committeeId IN :committeeIds
          AND t.memberUserId = :userId
          AND t.renewalStatus = 'ACTIVE'
          AND t.endedAt IS NULL
          AND t.termStartsAt <= :on
          AND (t.termEndsAt IS NULL OR t.termEndsAt >= :on)
    """)
    long countCurrentSeatsForUser(Long organisationId, Collection<Long> committeeIds,
                                 Long userId, LocalDate on);

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
