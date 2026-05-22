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
