package com.tss.grc.module.workflow_submission.controller;

import com.tss.grc.module.workflow_submission.entity.WorkflowSubmission;
import com.tss.grc.module.workflow_submission.service.WorkflowSubmissionService;
import com.tss.grc.module.workflow_submission.service.WorkflowSubmissionService.CreateBody;
import com.tss.grc.module.workflow_submission.service.WorkflowSubmissionService.ReviewBody;
import com.tss.grc.module.workflow_submission.service.WorkflowSubmissionService.WorkflowCount;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST surface for the generic wizard ledger.
 *
 * <pre>
 *   POST /api/v1/workflow-submissions                 — create one
 *   GET  /api/v1/workflow-submissions                 — list all (newest first)
 *   GET  /api/v1/workflow-submissions?workflowId=...  — filter by catalog id
 *   GET  /api/v1/workflow-submissions?module=GOVERNANCE — filter by module
 *   GET  /api/v1/workflow-submissions/{id}            — fetch one
 *   POST /api/v1/workflow-submissions/{id}/review     — approve / reject / close
 *   GET  /api/v1/workflow-submissions/counts/by-workflow — per-wizard aggregates
 * </pre>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workflow-submissions")
@Tag(name = "Workflow submissions (generic wizard ledger)")
public class WorkflowSubmissionController {

    private final WorkflowSubmissionService service;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public WorkflowSubmission create(@RequestBody CreateBody body) {
        return service.create(body);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<WorkflowSubmission> list(
        @RequestParam(required = false) String workflowId,
        @RequestParam(required = false) String module
    ) {
        if (workflowId != null && !workflowId.isBlank()) return service.listByWorkflowId(workflowId);
        if (module     != null && !module.isBlank())     return service.listByModule(module);
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public WorkflowSubmission get(@PathVariable Long id) {
        return service.get(id);
    }

    /**
     * Per-wizard aggregate counts for the Workflow Hub.
     *
     * <p>Returns one entry per {@code workflowId} the tenant has at least
     * one submission for. Replaces the client-side aggregation in
     * {@code WorkflowHubPage} that loaded every submission row on every Hub
     * visit. Single tenant-scoped SQL query under the hood.
     */
    @GetMapping("/counts/by-workflow")
    @PreAuthorize("isAuthenticated()")
    public List<WorkflowCount> countsByWorkflow() {
        return service.countsByWorkflowId();
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("isAuthenticated()")
    public WorkflowSubmission review(@PathVariable Long id, @RequestBody ReviewBody body) {
        return service.review(id, body);
    }
}
