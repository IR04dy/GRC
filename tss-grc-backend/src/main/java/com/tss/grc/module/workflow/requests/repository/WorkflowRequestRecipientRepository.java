package com.tss.grc.module.workflow.requests.repository;

import com.tss.grc.module.workflow.requests.entity.WorkflowRequestRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowRequestRecipientRepository extends JpaRepository<WorkflowRequestRecipient, Long> {

    List<WorkflowRequestRecipient> findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(
        Long organisationId, Long requestId);

    /** WF-7 — resolve an EXTERNAL recipient by its opaque public-tracker token (the ONLY key). */
    Optional<WorkflowRequestRecipient> findByAccessToken(String accessToken);

    /** Inbox — recipient rows directly addressed to a user id. Role/department membership is
     *  resolved in the service (a user's inbox = USER rows + ROLE rows they hold + their dept). */
    List<WorkflowRequestRecipient> findByOrganisationIdAndRecipientTypeAndRecipientId(
        Long organisationId, String recipientType, Long recipientId);

    List<WorkflowRequestRecipient> findByOrganisationIdAndRecipientTypeAndRecipientKeyIn(
        Long organisationId, String recipientType, List<String> recipientKeys);
}
