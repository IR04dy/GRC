package com.tss.grc.module.workflow.requests.controller;

import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.ActionRequest;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.CreateRequest;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.EventResponse;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RequestResponse;
import com.tss.grc.module.workflow.requests.service.WorkflowRequestService;
import com.tss.grc.module.workflow.requests.service.WorkflowRequestSlaScheduler;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * WF-1 — the §6 API surface of the platform workflow request engine.
 *
 * <p>Gated {@code isAuthenticated()}: any module user can raise or answer a request — the fine
 * actor authorization (who may respond at the current step, who reviews, who approves, initiator-
 * only close/reopen) is enforced in {@link WorkflowRequestService}, because it depends on the
 * request's own recipient rows, not on a module permission.
 */
@RestController
@RequestMapping("/api/v1/workflows/requests")
@RequiredArgsConstructor
@Tag(name = "Workflow Engine — Requests")
public class WorkflowRequestController {

    private final WorkflowRequestService svc;
    private final WorkflowRequestSlaScheduler slaScheduler;   // WF-2 — manual sweep trigger

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public RequestResponse create(@Valid @RequestBody CreateRequest body) { return svc.create(body); }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<RequestResponse> list(@RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return svc.list(status, PageRequest.of(page, Math.min(size, 200)));
    }

    /** Inbox — requests addressed to me (directly, via a role I hold, or via my department). */
    @GetMapping("/inbox")
    @PreAuthorize("isAuthenticated()")
    public List<RequestResponse> inbox() { return svc.myInbox(); }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse get(@PathVariable Long id) { return svc.getDetail(id); }

    @PostMapping("/{id}/send")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse send(@PathVariable Long id) { return svc.send(id); }

    @PostMapping("/{id}/respond")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse respond(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.respond(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse submit(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.submit(id, body == null ? null : body.note());
    }

    /** Reviewer gate — body.outcome = APPROVE | RETURN. */
    @PostMapping("/{id}/review")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse review(@PathVariable Long id, @RequestBody ActionRequest body) {
        return svc.review(id, body.outcome(), body.note());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse approve(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.approve(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse reject(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.reject(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse resubmit(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.resubmit(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse escalate(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.escalate(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse close(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.close(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("isAuthenticated()")
    public RequestResponse reopen(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.reopen(id, body == null ? null : body.note());
    }

    /** The request's append-only audit timeline. */
    @GetMapping("/{id}/audit-log")
    @PreAuthorize("isAuthenticated()")
    public List<EventResponse> auditLog(@PathVariable Long id) { return svc.auditLog(id); }

    /** WF-2 — run the SLA sweep on demand (the scheduler also runs it on a timer):
     *  pre-due reminders + overdue → OVERDUE + once-only escalation. Admin-only ops action. */
    @PostMapping("/run-sla-sweep")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN')")
    public WorkflowRequestSlaScheduler.SweepResult runSlaSweep() { return slaScheduler.sweep(); }
}
