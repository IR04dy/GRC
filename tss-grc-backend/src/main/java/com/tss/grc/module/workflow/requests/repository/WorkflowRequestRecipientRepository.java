package com.tss.grc.module.workflow.requests.repository;

import com.tss.grc.module.workflow.requests.entity.WorkflowRequestRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRequestRecipientRepository extends JpaRepository<WorkflowRequestRecipient, Long> {

    List<WorkflowRequestRecipient> findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(
        Long organisationId, Long requestId);

    /** Inbox — recipient rows directly addressed to a user id. Role/department membership is
     *  resolved in the service (a user's inbox = USER rows + ROLE rows they hold + their dept). */
    List<WorkflowRequestRecipient> findByOrganisationIdAndRecipientTypeAndRecipientId(
        Long organisationId, String recipientType, Long recipientId);

    List<WorkflowRequestRecipient> findByOrganisationIdAndRecipientTypeAndRecipientKeyIn(
        Long organisationId, String recipientType, List<String> recipientKeys);
}
