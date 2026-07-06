package com.tss.grc.module.workflow.requests.controller;

import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.ActionRequest;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.CreateRequest;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.EventResponse;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.RecipientSuggestion;
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
 * <p>Gated {@code hasPermission('WORKFLOW','READ'|'WRITE')} — V271 grants both to every role
 * (the engine is an inbox primitive: any user can be addressed and must be able to answer), so
 * the module gate satisfies the WS2 authorization ratchet while staying universally available.
 * The fine actor authorization (who may respond at the current step, who reviews, who approves,
 * initiator-only close/reopen) is enforced in {@link WorkflowRequestService}, because it depends
 * on the request's own recipient rows, not on a module permission.
 */
@RestController
@RequestMapping("/api/v1/workflows/requests")
@RequiredArgsConstructor
@Tag(name = "Workflow Engine — Requests")
public class WorkflowRequestController {

    private final WorkflowRequestService svc;
    private final WorkflowRequestSlaScheduler slaScheduler;   // WF-2 — manual sweep trigger

    @PostMapping
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse create(@Valid @RequestBody CreateRequest body) { return svc.create(body); }

    @GetMapping
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
    public Page<RequestResponse> list(@RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return svc.list(status, PageRequest.of(page, Math.min(size, 200)));
    }

    /** Inbox — requests addressed to me (directly, via a role I hold, or via my department). */
    @GetMapping("/inbox")
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
    public List<RequestResponse> inbox() { return svc.myInbox(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
    public RequestResponse get(@PathVariable Long id) { return svc.getDetail(id); }

    /** WF-9 — edit a DRAFT request (the §6 PUT). Once sent, use the lifecycle actions → 409. */
    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse update(@PathVariable Long id, @Valid @RequestBody CreateRequest body) {
        return svc.update(id, body);
    }

    /** WF-8 — owner/role/department suggestions for composing a request against a source record. */
    @GetMapping("/recipient-suggestions")
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
    public List<RecipientSuggestion> recipientSuggestions(
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String sourceRecordType,
            @RequestParam(required = false) Long sourceRecordId) {
        return svc.recipientSuggestions(sourceModule, sourceRecordType, sourceRecordId);
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse send(@PathVariable Long id) { return svc.send(id); }

    /** WF-6 — body.outcome (optional) can trigger a conditional branch rule (§7 #5). */
    @PostMapping("/{id}/respond")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse respond(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.respond(id, body == null ? null : body.note(), body == null ? null : body.outcome());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse submit(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.submit(id, body == null ? null : body.note());
    }

    /** Reviewer gate — body.outcome = APPROVE | RETURN. */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse review(@PathVariable Long id, @RequestBody ActionRequest body) {
        return svc.review(id, body.outcome(), body.note());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse approve(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.approve(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse reject(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.reject(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse resubmit(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.resubmit(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse escalate(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.escalate(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse close(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.close(id, body == null ? null : body.note());
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasPermission('WORKFLOW','WRITE')")
    public RequestResponse reopen(@PathVariable Long id, @RequestBody(required = false) ActionRequest body) {
        return svc.reopen(id, body == null ? null : body.note());
    }

    /** The request's append-only audit timeline. */
    @GetMapping("/{id}/audit-log")
    @PreAuthorize("hasPermission('WORKFLOW','READ')")
    public List<EventResponse> auditLog(@PathVariable Long id) { return svc.auditLog(id); }

    /** WF-2 — run the SLA sweep on demand (the scheduler also runs it on a timer):
     *  pre-due reminders + overdue → OVERDUE + once-only escalation. Admin-only ops action. */
    @PostMapping("/run-sla-sweep")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN')")
    public WorkflowRequestSlaScheduler.SweepResult runSlaSweep() { return slaScheduler.sweep(); }
}
