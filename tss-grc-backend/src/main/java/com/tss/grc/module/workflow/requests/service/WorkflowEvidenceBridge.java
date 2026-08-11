package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequest;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequestEvent;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestEventRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * WF-4 — the feed-back half of the evidence bridge (roadmap item 7).
 *
 * <p>The send-side bridge opens a real {@code evidence.evidence_requests} row anchored
 * {@code entityType='WORKFLOW_REQUEST'}. When the evidence team finishes their lifecycle,
 * {@code EvidenceRequestService.updateStatus} publishes the anchored
 * {@code EVIDENCE_REQUEST_APPROVED} / {@code EVIDENCE_REQUEST_REJECTED} events — this listener
 * closes the loop on the workflow side:
 *
 * <ul>
 *   <li>evidence <b>APPROVED</b> → the workflow request drives to <b>APPROVED</b></li>
 *   <li>evidence <b>REJECTED</b> → the workflow request drives to <b>RETURNED</b>
 *       (the requester can fix and resubmit — which re-enters review)</li>
 * </ul>
 *
 * <p>Intermediate {@code SUBMITTED} hops are inserted where the WF-1 state machine requires
 * them; terminal requests (CLOSED / ARCHIVED / already APPROVED / REJECTED) are left alone.
 * The initiator is notified, a timeline event is appended, and a platform audit row is written
 * under the request's own tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEvidenceBridge {

    /**
     * Statuses where an evidence outcome is moot because the request is already decided.
     *
     * <p>A product decision, not derivable from the lifecycle table: {@code APPROVED} and
     * {@code REJECTED} are not graph-terminal (both can be reopened), but a request that has already
     * been decided must not be re-decided by a late evidence event.
     */
    private static final Set<String> TERMINAL = Set.of("CLOSED", "ARCHIVED", "APPROVED", "REJECTED");

    private final WorkflowRequestRepository      requestRepo;
    private final WorkflowRequestEventRepository eventRepo;
    private final NotificationService            notifications;
    private final AuditLogService                audit;

    @EventListener
    @Transactional
    public void onEvidenceOutcome(DomainEvent event) {
        String trigger = event.triggerEvent();
        boolean approved = "EVIDENCE_REQUEST_APPROVED".equals(trigger);
        boolean rejected = "EVIDENCE_REQUEST_REJECTED".equals(trigger);
        if (!approved && !rejected) return;
        Object anchorType = event.payload() == null ? null : event.payload().get("anchorType");
        if (!"WORKFLOW_REQUEST".equals(anchorType)) return;

        Long requestId = asLong(event.payload().get("anchorId"));
        if (requestId == null) return;
        WorkflowRequest r = requestRepo.findByIdAndOrganisationId(requestId, event.organisationId())
            .orElse(null);
        if (r == null) {
            log.warn("[wf-evidence-bridge] anchored workflow request {} not found in org {}",
                requestId, event.organisationId());
            return;
        }
        if (TERMINAL.contains(r.getStatus())) {
            log.info("[wf-evidence-bridge] request {} already terminal ({}) — evidence {} ignored",
                requestId, r.getStatus(), trigger);
            return;
        }

        String from = r.getStatus();
        String requestCode = String.valueOf(event.payload().get("requestCode"));
        String target = approved ? "APPROVED" : "RETURNED";

        // F2-190 — ask the lifecycle table instead of a hard-coded hop list.
        //
        // What was wrong: this said "Drive through the WF-1 machine: pre-submit states hop via
        // SUBMITTED first" and then drove around it. PRE_SUBMIT held 7 states and TERMINAL 4, of the
        // table's 16 — so for the five in neither (DRAFT, SUBMITTED, UNDER_REVIEW, RETURNED,
        // RESUBMITTED) the target was written directly, and two of those moves the table forbids:
        // DRAFT -> APPROVED (a request nobody ever sent, marked approved) and RETURNED -> APPROVED
        // (whose only legal continuations are RESUBMITTED and CLOSED). The hop itself was also
        // decorative: two consecutive setStatus calls collapse, so the row never rested in SUBMITTED
        // and no event recorded it — a control the comment described that did not run.
        //
        // Now: direct if legal; otherwise one declared intermediate, recorded as its own event so the
        // trail matches the machine; otherwise refuse and leave the row alone. DRAFT refuses on
        // purpose — reaching APPROVED from there needs SENT, and an evidence approval must not send a
        // request on the requester's behalf.
        String hop = null;
        if (!WorkflowRequestService.mayTransition(from, target)) {
            hop = WorkflowRequestService.hopVia(from, target).orElse(null);
            if (hop == null) {
                log.warn("[wf-evidence-bridge] request {} is {} and the lifecycle offers no route to {}"
                        + " — evidence {} recorded against the evidence request, workflow left alone",
                    requestId, from, target, trigger);
                return;
            }
        }
        if (hop != null) {
            r.setStatus(hop);
            requestRepo.save(r);
            appendEvent(r, hop, from, hop, "Evidence outcome for " + requestCode
                + " requires the lifecycle step " + from + " → " + hop + " before " + target);
        }
        r.setStatus(target);
        requestRepo.save(r);

        String note = (approved ? "Evidence request " : "Evidence request ")
            + requestCode + (approved ? " APPROVED in the evidence subsystem" : " REJECTED in the evidence subsystem — returned for changes");
        // from-status of the DECIDING event is the hop when there was one, so the two events read as a
        // walk of the machine rather than two competing accounts of the same change.
        appendEvent(r, approved ? "APPROVED" : "RETURNED",
            hop == null ? from : hop, r.getStatus(), note);
        withTenant(r.getOrganisationId(), () ->
            audit.log(approved ? "WORKFLOW_REQUEST_APPROVED" : "WORKFLOW_REQUEST_RETURNED",
                "WorkflowRequest", r.getId(), code(r) + " · via evidence bridge · " + note));
        notifications.notifyUser(r.getOrganisationId(), r.getInitiatedBy(),
            approved ? "Workflow request approved (evidence accepted)" : "Workflow request returned (evidence rejected)",
            approved ? "اعتُمد طلب سير العمل (قُبل الدليل)" : "أُعيد طلب سير العمل (رُفض الدليل)",
            code(r) + " \"" + r.getTitleEn() + "\" — " + note,
            approved ? "WORKFLOW_REQUEST_APPROVED" : "WORKFLOW_REQUEST_RETURNED",
            "WorkflowRequest", r.getId());
        log.info("[wf-evidence-bridge] {} → {} (evidence {})", from, r.getStatus(), trigger);
    }

    private void appendEvent(WorkflowRequest r, String type, String from, String to, String note) {
        WorkflowRequestEvent e = new WorkflowRequestEvent();
        e.setOrganisationId(r.getOrganisationId());
        e.setRequestId(r.getId());
        e.setEventType(type);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setActor("evidence-bridge");
        e.setNote(note);
        eventRepo.save(e);
    }

    private void withTenant(Long org, Runnable run) {
        Long previous = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(org);
            run.run();
        } finally {
            if (previous != null) TenantContext.setCurrentTenant(previous);
            else TenantContext.clear();
        }
    }

    private static Long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return v == null ? null : Long.parseLong(v.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String code(WorkflowRequest r) { return "WR-" + String.format("%05d", r.getId()); }
}
