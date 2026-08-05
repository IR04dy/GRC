package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChangeApprovalRepository extends JpaRepository<ChangeApproval, Long> {
    List<ChangeApproval> findByChangeRequestIdOrderByDecidedAtAsc(Long changeRequestId);

    /** DF-4: one vote per approver per change. Backed by the filtered unique index
     *  ux_change_approval_one_vote_per_approver (V316), so a concurrent double-submit
     *  surfaces as a constraint violation instead of a double-counted CAB majority. */
    Optional<ChangeApproval> findByChangeRequestIdAndApproverId(Long changeRequestId, Long approverId);

    /**
     * S8f / CE-11 — recorded {@code APPROVE} votes per change, for a set of changes.
     *
     * <p>⚠ {@code APPROVE} only. {@code DEFER}, {@code REJECT} and {@code ABSTAIN} are votes cast but
     * they are not authority granted, and the reconciliation turns on that distinction: dev's change 7
     * reached {@code SCHEDULED} carrying one recorded vote, and that vote was a {@code DEFER}. Counting
     * rows rather than approvals would have reported it authorised.
     *
     * <p>Tenancy comes from the caller: {@code changeIds} are the ids of an already
     * organisation-filtered population, and this table has no {@code organisation_id} of its own
     * (it is scoped through its parent change).
     */
    @Query("""
        SELECT a.changeRequestId, COUNT(a)
        FROM ChangeApproval a
        WHERE a.changeRequestId IN :changeIds AND a.decision = 'APPROVE'
        GROUP BY a.changeRequestId
        """)
    List<Object[]> countApprovalsByChange(@Param("changeIds") List<Long> changeIds);
}
