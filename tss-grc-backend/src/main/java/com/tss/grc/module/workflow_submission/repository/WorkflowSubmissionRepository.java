package com.tss.grc.module.workflow_submission.repository;

import com.tss.grc.module.workflow_submission.entity.WorkflowSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Per-wizard aggregate counts in a single tenant-scoped query.
     *
     * <p>Returns one row per {@code workflowId} the tenant has at least
     * one submission for. Each row carries:
     *   [0] String  workflowId
     *   [1] Long    total            (all statuses)
     *   [2] Long    open             (SUBMITTED + UNDER_REVIEW)
     *   [3] Long    approved         (APPROVED)
     *
     * <p>Used by the Workflow Hub to render per-card "N submitted · M open"
     * badges without loading every row. Replaces the client-side aggregation
     * that scaled O(rows) per Hub visit.
     */
    @Query("""
        SELECT s.workflowId,
               COUNT(s),
               SUM(CASE WHEN s.status IN ('SUBMITTED','UNDER_REVIEW') THEN 1 ELSE 0 END),
               SUM(CASE WHEN s.status = 'APPROVED' THEN 1 ELSE 0 END)
          FROM WorkflowSubmission s
         WHERE s.organisationId = :orgId
         GROUP BY s.workflowId
        """)
    List<Object[]> countsByWorkflowId(@Param("orgId") Long organisationId);
}
