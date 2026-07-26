package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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
