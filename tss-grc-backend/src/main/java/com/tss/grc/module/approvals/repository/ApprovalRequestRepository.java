package com.tss.grc.module.approvals.repository;

import com.tss.grc.module.approvals.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    List<ApprovalRequest> findByOrganisationIdOrderByRequestedAtDesc(Long organisationId);
    Optional<ApprovalRequest> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    List<ApprovalRequest> findByOrganisationIdAndAssignedToAndStatus(Long organisationId, String assignedTo, String status);
    List<ApprovalRequest> findByOrganisationIdAndStatus(Long organisationId, String status);
    long countByOrganisationIdAndAssignedToAndStatus(Long organisationId, String assignedTo, String status);
}
