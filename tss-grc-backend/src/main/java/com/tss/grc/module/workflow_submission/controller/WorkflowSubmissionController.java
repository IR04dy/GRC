package com.tss.grc.module.workflow_submission.controller;

import com.tss.grc.module.workflow_submission.entity.WorkflowSubmission;
import com.tss.grc.module.workflow_submission.service.WorkflowSubmissionService;
import com.tss.grc.module.workflow_submission.service.WorkflowSubmissionService.CreateBody;
import com.tss.grc.module.workflow_submission.service.WorkflowSubmissionService.ReviewBody;
import com.tss.grc.module.workflow_submission.service.WorkflowSubmissionService.WorkflowCount;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public WorkflowSubmission create(@Valid @RequestBody CreateBody body, Authentication auth) {
        return service.create(body, userIdOf(auth));
    }

    @GetMapping
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
    public List<WorkflowSubmission> list(
        @RequestParam(required = false) String workflowId,
        @RequestParam(required = false) String module
    ) {
        if (workflowId != null && !workflowId.isBlank()) return service.listByWorkflowId(workflowId);
        if (module     != null && !module.isBlank())     return service.listByModule(module);
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
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
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
    public List<WorkflowCount> countsByWorkflow() {
        return service.countsByWorkflowId();
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasPermission('WORKFLOW','APPROVE') or hasRole('PLATFORM_ADMIN')")
    public WorkflowSubmission review(@PathVariable Long id,
                                     @Valid @RequestBody ReviewBody body,
                                     Authentication auth) {
        // Segregation of duties: the acting reviewer is derived from the
        // authenticated principal (never the request body); the service
        // rejects self-review (reviewer == submitter).
        return service.review(id, body, userIdOf(auth));
    }

    /** Forms remediation (G1) — persist the engine request a submission was routed as. */
    @PutMapping("/{id}/spawned-request/{requestId}")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public WorkflowSubmission linkSpawnedRequest(@PathVariable Long id, @PathVariable Long requestId) {
        return service.linkSpawnedRequest(id, requestId);
    }

    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
}
