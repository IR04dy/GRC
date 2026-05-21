package com.tss.grc.module.workflow_submission.repository;

import com.tss.grc.module.workflow_submission.entity.WorkflowSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowSubmissionRepository extends JpaRepository<WorkflowSubmission, Long> {

    /** All submissions for one tenant, newest first — page-bound at the service. */
    List<WorkflowSubmission> findByOrganisationIdOrderBySubmittedAtDesc(Long organisationId);

    /** Filter by catalog id (e.g. 'governance-board-pack'). */
    List<WorkflowSubmission> findByOrganisationIdAndWorkflowIdOrderBySubmittedAtDesc(
        Long organisationId, String workflowId);

    /** Filter by module bucket (e.g. 'GOVERNANCE'). */
    List<WorkflowSubmission> findByOrganisationIdAndWorkflowModuleOrderBySubmittedAtDesc(
        Long organisationId, String workflowModule);

    Optional<WorkflowSubmission> findByOrganisationIdAndId(Long organisationId, Long id);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
