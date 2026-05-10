package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChangeApprovalRepository extends JpaRepository<ChangeApproval, Long> {
    List<ChangeApproval> findByChangeRequestIdOrderByDecidedAtAsc(Long changeRequestId);
}
