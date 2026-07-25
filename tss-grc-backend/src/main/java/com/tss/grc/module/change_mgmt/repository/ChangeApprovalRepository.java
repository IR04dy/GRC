package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChangeApprovalRepository extends JpaRepository<ChangeApproval, Long> {
    List<ChangeApproval> findByChangeRequestIdOrderByDecidedAtAsc(Long changeRequestId);

    /** DF-4: one vote per approver per change. Backed by the filtered unique index
     *  ux_change_approval_one_vote_per_approver (V316), so a concurrent double-submit
     *  surfaces as a constraint violation instead of a double-counted CAB majority. */
    Optional<ChangeApproval> findByChangeRequestIdAndApproverId(Long changeRequestId, Long approverId);
}
