package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeActionItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CommitteeActionItemRepository extends JpaRepository<CommitteeActionItem, Long> {

    Optional<CommitteeActionItem> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<CommitteeActionItem> findByOrganisationIdAndActionCode(Long organisationId, String actionCode);

    Page<CommitteeActionItem> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    List<CommitteeActionItem> findByOrganisationIdAndMeetingIdOrderBySeqAsc(Long organisationId, Long meetingId);

    List<CommitteeActionItem> findByOrganisationIdAndCommitteeIdOrderByDueDateAsc(Long organisationId, Long committeeId);

    /** Open + in-progress + overdue actions for one committee — drives the
     *  "matters arising" section that the agenda auto-populator pre-fills
     *  into the next meeting's agenda. */
    @Query("""
        SELECT a FROM CommitteeActionItem a
        WHERE a.organisationId = :organisationId
          AND a.committeeId = :committeeId
          AND a.status IN ('OPEN', 'IN_PROGRESS', 'OVERDUE')
        ORDER BY a.dueDate ASC
    """)
    List<CommitteeActionItem> findOpenByCommittee(Long organisationId, Long committeeId);

    /** Actions where due_date has passed but the status is still one that {@code markOverdue}
     *  accepts as an OVERDUE source — OPEN, IN_PROGRESS or DEFERRED. The scheduled sweep flips
     *  them to OVERDUE and fires the F8 escalation.
     *
     *  <p>F2-361: DEFERRED was missing. {@code defer(id, newDueDate)} reschedules the deadline
     *  ("revisit by newDueDate"), and {@code markOverdue} bars only COMPLETED/CANCELLED/OVERDUE —
     *  so it accepts DEFERRED. But this query excluded it, so a deferred committee action whose
     *  revised deadline passed was never swept to OVERDUE, never escalated, and never returned to
     *  matters-arising (startProgress cannot resume a DEFERRED item either). Deferral became a
     *  permanent escape from accountability. The query's set must equal markOverdue's accepted set
     *  (F2-196 / F2-177): the sweep is pinned to that agreement by CommitteeOverdueSweepScopeTest. */
    @Query("""
        SELECT a FROM CommitteeActionItem a
        WHERE a.organisationId = :organisationId
          AND a.dueDate < :today
          AND a.status IN ('OPEN', 'IN_PROGRESS', 'DEFERRED')
        ORDER BY a.dueDate ASC
    """)
    List<CommitteeActionItem> findShouldBeOverdue(Long organisationId, LocalDate today);

    /** Distinct organisations with at least one action item — used by the
     *  scheduled sweep to know which tenants to scan. */
    @Query("SELECT DISTINCT a.organisationId FROM CommitteeActionItem a")
    List<Long> findDistinctOrganisationIds();

    /** All actions owned by a user, regardless of committee — drives the
     *  "my open actions" view in the user's inbox. */
    @Query("""
        SELECT a FROM CommitteeActionItem a
        WHERE a.organisationId = :organisationId
          AND a.ownerUserId = :ownerUserId
          AND a.status IN ('OPEN', 'IN_PROGRESS', 'OVERDUE')
        ORDER BY a.dueDate ASC
    """)
    List<CommitteeActionItem> findOpenByOwner(Long organisationId, Long ownerUserId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
