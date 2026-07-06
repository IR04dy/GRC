package com.tss.grc.module.workflow.requests.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.workflow.requests.dto.WorkflowRequestDtos.EscalationTierDto;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequest;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequestEvent;
import com.tss.grc.module.workflow.requests.entity.WorkflowRequestRecipient;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestEventRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRecipientRepository;
import com.tss.grc.module.workflow.requests.repository.WorkflowRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WF-2 — the workflow-request SLA clock (roadmap items 4 SLA countdown, 5 Reminder engine,
 * 6 Escalation engine).
 *
 * <p>WF-1 gave every request a due date, a reminder policy and an escalation path — this sweep
 * makes them tick (mirrors the CY-D/CY-E/CY-J scheduler pattern):
 *
 * <ul>
 *   <li><b>Reminder pass</b> — open requests coming due within their own
 *       {@code reminderDaysBefore} window get a reminder to the pending assignee recipients
 *       (user / role / department fan-out), throttled via {@code lastReminderAt}
 *       ({@value #REMINDER_THROTTLE_HOURS}h), with a {@code REMINDED} timeline event.</li>
 *   <li><b>Overdue pass</b> — open requests past their due date flip to {@code OVERDUE} and are
 *       escalated <em>once</em> ({@code escalatedAt} guards re-fire): the escalation path
 *       (escalation role / user + any ESCALATION-role recipients) is notified, an
 *       {@code ESCALATED} timeline event + audit row are written, and an F8
 *       {@code WORKFLOW_REQUEST_OVERDUE} event fires.</li>
 * </ul>
 *
 * <p>Config-gated by {@code grc.workflow-request-sla.scheduler-enabled} (dev 60s / prod 5m).
 * Runs system-wide; {@link TenantContext} is bound per row so audit rows land under the
 * request's own organisation. ROWVERSION rule: recipients are loaded BEFORE the request row is
 * mutated, and notifications (whose role-broadcast queries flush) run after the single save.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowRequestSlaScheduler {

    /** Statuses the clock chases. DRAFT isn't sent; review/approval stages have their own actors;
     *  OVERDUE/ESCALATED are already flagged. */
    private static final List<String> OPEN_STATUSES =
        List.of("SENT", "ASSIGNED", "IN_PROGRESS", "WAITING_RESPONSE", "REOPENED");
    /** WF-10 — the overdue pass also re-scans already-OVERDUE requests so a multi-tier ladder can
     *  keep climbing on later sweeps (a plain OVERDUE request with no new tier is a no-op). */
    private static final List<String> OVERDUE_SCAN_STATUSES =
        List.of("SENT", "ASSIGNED", "IN_PROGRESS", "WAITING_RESPONSE", "REOPENED", "OVERDUE");
    /** Anti-spam gap between reminders for the same request (~once a day). */
    private static final long REMINDER_THROTTLE_HOURS = 20;
    /** Upper bound for the reminder-window scan; per-request windows are filtered in memory. */
    private static final int MAX_REMINDER_WINDOW_DAYS = 30;

    private final WorkflowRequestRepository          requestRepo;
    private final WorkflowRequestRecipientRepository recipientRepo;
    private final WorkflowRequestEventRepository     eventRepo;
    private final UserRepository                     userRepo;
    private final CommitteeMemberTermRepository      committeeMembers;
    private final NotificationService                notifications;
    private final AuditLogService                    audit;
    private final ApplicationEventPublisher          publisher;

    @Value("${grc.workflow-request-sla.scheduler-enabled:false}")
    private boolean enabled;

    /** Poll on the configured interval (default 5 min; dev flips it to 1 min). */
    @Scheduled(fixedDelayString = "${grc.workflow-request-sla.scheduler-interval-ms:300000}")
    public void runSweep() {
        if (!enabled) {
            log.trace("Workflow-request SLA scheduler disabled (grc.workflow-request-sla.scheduler-enabled=false).");
            return;
        }
        try {
            SweepResult r = sweep();
            if (r.reminders() > 0 || r.escalations() > 0) {
                log.info("Workflow-request SLA sweep → {} reminder(s), {} escalation(s)",
                    r.reminders(), r.escalations());
            }
        } catch (Exception e) {
            log.error("Workflow-request SLA sweep failed: {}", e.getMessage(), e);
        }
    }

    /** One reminder + overdue/escalation pass over every tenant's open, dated requests. */
    @Transactional
    public SweepResult sweep() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime throttleBefore = now.minusHours(REMINDER_THROTTLE_HOURS);

        int escalations = 0;
        for (WorkflowRequest r : requestRepo.findByStatusInAndDueDateBefore(OVERDUE_SCAN_STATUSES, today)) {
            if (escalateOverdue(r, today, now)) escalations++;
        }

        int reminders = 0;
        for (WorkflowRequest r : requestRepo.findByStatusInAndDueDateBetween(
                OPEN_STATUSES, today, today.plusDays(MAX_REMINDER_WINDOW_DAYS))) {
            int window = r.getReminderDaysBefore() == null ? 3 : r.getReminderDaysBefore();
            if (r.getDueDate().isAfter(today.plusDays(window))) continue;         // outside its own window
            if (r.getEscalatedAt() != null) continue;                              // already escalated — no nagging
            if (r.getLastReminderAt() != null && r.getLastReminderAt().isAfter(throttleBefore)) continue;
            remind(r, today, now);
            reminders++;
        }
        return new SweepResult(reminders, escalations);
    }

    /**
     * Flip an overdue request to OVERDUE and escalate it. Without a tier ladder this is the
     * original once-only single-path escalation. With a WF-10 ladder, the sweep advances to the
     * highest tier whose {@code afterDays} threshold has elapsed, notifying each newly-reached
     * rung once — climbing further on later sweeps as more days pass. Returns true if it acted
     * (so the sweep counts real escalations, not idle re-scans of already-OVERDUE requests).
     */
    private boolean escalateOverdue(WorkflowRequest r, LocalDate today, LocalDateTime now) {
        List<WorkflowRequestRecipient> recipients = recipientRepo
            .findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId());
        List<EscalationTierDto> tiers = WorkflowRequestService.readTiers(r.getEscalationTiers());

        long daysOverdue = ChronoUnit.DAYS.between(r.getDueDate(), today);
        boolean alreadyOverdue = "OVERDUE".equals(r.getStatus());
        boolean firstEscalation = r.getEscalatedAt() == null;
        int curLevel = r.getEscalationLevel() == null ? 0 : r.getEscalationLevel();

        int targetLevel = curLevel;
        if (!tiers.isEmpty()) {
            int reached = 0;
            for (EscalationTierDto t : tiers)
                if (t.afterDays() == null || t.afterDays() <= daysOverdue) reached++;
            targetLevel = Math.max(curLevel, Math.min(reached, tiers.size()));
        }
        boolean newTier = targetLevel > curLevel;

        // Idle re-scan of an already-OVERDUE request with no new tier reached → nothing to do.
        if (alreadyOverdue && !newTier) return false;

        String from = r.getStatus();
        r.setStatus("OVERDUE");
        if (firstEscalation) r.setEscalatedAt(now);
        r.setLastReminderAt(now);
        r.setReminderCount(r.getReminderCount() == null ? 1 : r.getReminderCount() + 1);
        if (newTier) r.setEscalationLevel(targetLevel);
        requestRepo.save(r);

        if (!alreadyOverdue)
            appendEvent(r, "OVERDUE", from, "OVERDUE", daysOverdue + " day(s) past due (" + r.getDueDate() + ")");

        String title = "Workflow request overdue — escalated";
        String titleAr = "طلب سير عمل متأخر — تم التصعيد";

        if (tiers.isEmpty()) {
            // Single-path escalation, once only (unchanged behaviour).
            if (!firstEscalation) return true;
            appendEvent(r, "ESCALATED", "OVERDUE", "OVERDUE",
                "Auto-escalated by the SLA sweep to the escalation path");
            withTenant(r.getOrganisationId(), () ->
                audit.log("WORKFLOW_REQUEST_OVERDUE", "WorkflowRequest", r.getId(),
                    code(r) + " \"" + r.getTitleEn() + "\" is " + daysOverdue
                        + " day(s) overdue (due " + r.getDueDate() + ") — escalated"));
            String msg = code(r) + " \"" + r.getTitleEn() + "\" is " + daysOverdue
                + " day(s) overdue (due " + r.getDueDate() + ", priority " + r.getPriority() + ").";
            if (r.getEscalationUserId() != null)
                notifications.notifyUser(r.getOrganisationId(), r.getEscalationUserId(),
                    title, titleAr, msg, "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
            if (r.getEscalationRole() != null && !r.getEscalationRole().isBlank())
                notifications.broadcastToRole(r.getOrganisationId(), r.getEscalationRole(),
                    title, titleAr, msg, "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
            for (WorkflowRequestRecipient rec : recipients) {
                if (!"ESCALATION".equals(rec.getActorRole())) continue;
                if ("USER".equals(rec.getRecipientType()) && rec.getRecipientId() != null)
                    notifications.notifyUser(r.getOrganisationId(), rec.getRecipientId(),
                        title, titleAr, msg, "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
                else if ("ROLE".equals(rec.getRecipientType()) && rec.getRecipientKey() != null)
                    notifications.broadcastToRole(r.getOrganisationId(), rec.getRecipientKey(),
                        title, titleAr, msg, "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
            }
            publisher.publishEvent(DomainEvent.of(r.getOrganisationId(), "WORKFLOW_REQUEST_OVERDUE",
                "WorkflowRequest", r.getId(),
                Map.of("priority",    r.getPriority() == null ? "" : r.getPriority(),
                       "requestType", r.getRequestType() == null ? "" : r.getRequestType(),
                       "daysOverdue", daysOverdue)));
            return true;
        }

        // WF-10 — climb the ladder: notify each rung from curLevel up to the newly-reached tier.
        if (!newTier) return true;
        final int tgt = targetLevel;
        withTenant(r.getOrganisationId(), () ->
            audit.log("WORKFLOW_REQUEST_OVERDUE", "WorkflowRequest", r.getId(),
                code(r) + " \"" + r.getTitleEn() + "\" is " + daysOverdue + " day(s) overdue — escalated to tier " + tgt));
        for (int idx = curLevel; idx < targetLevel; idx++) {
            EscalationTierDto t = tiers.get(idx);
            appendEvent(r, "ESCALATED", "OVERDUE", "OVERDUE",
                "Auto-escalated to tier " + (t.level() != null ? t.level() : idx + 1)
                    + (t.role() != null ? " (" + t.role() + ")" : ""));
            String msg = code(r) + " \"" + r.getTitleEn() + "\" is " + daysOverdue
                + " day(s) overdue — escalation tier " + (t.level() != null ? t.level() : idx + 1) + ".";
            if (t.userId() != null)
                notifications.notifyUser(r.getOrganisationId(), t.userId(),
                    title, titleAr, msg, "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
            if (t.role() != null && !t.role().isBlank())
                notifications.broadcastToRole(r.getOrganisationId(), t.role(),
                    title, titleAr, msg, "WORKFLOW_REQUEST_ESCALATED", "WorkflowRequest", r.getId());
        }
        publisher.publishEvent(DomainEvent.of(r.getOrganisationId(), "WORKFLOW_REQUEST_OVERDUE",
            "WorkflowRequest", r.getId(),
            Map.of("priority",    r.getPriority() == null ? "" : r.getPriority(),
                   "requestType", r.getRequestType() == null ? "" : r.getRequestType(),
                   "daysOverdue", daysOverdue, "escalationLevel", targetLevel)));
        return true;
    }

    /** Send a pre-due reminder to the pending assignee recipients of the actionable step. */
    private void remind(WorkflowRequest r, LocalDate today, LocalDateTime now) {
        List<WorkflowRequestRecipient> recipients = recipientRepo
            .findByOrganisationIdAndRequestIdOrderByStepNoAscIdAsc(r.getOrganisationId(), r.getId());

        long daysToDue = ChronoUnit.DAYS.between(today, r.getDueDate());
        r.setLastReminderAt(now);
        r.setReminderCount(r.getReminderCount() == null ? 1 : r.getReminderCount() + 1);
        requestRepo.save(r);

        appendEvent(r, "REMINDED", r.getStatus(), r.getStatus(),
            "Reminder sent — due in " + daysToDue + " day(s) (" + r.getDueDate() + ")");

        String title = "Workflow request due soon";
        String titleAr = "طلب سير عمل يقترب من الاستحقاق";
        String msg = code(r) + " \"" + r.getTitleEn() + "\" is due in " + daysToDue
            + " day(s) — " + r.getDueDate() + ".";
        boolean sequential = "SEQUENTIAL".equals(r.getRoutingMode());
        for (WorkflowRequestRecipient rec : recipients) {
            if (!"ASSIGNEE".equals(rec.getActorRole())) continue;
            if (!Set.of("PENDING", "NOTIFIED", "IN_PROGRESS").contains(rec.getStatus())) continue;
            if (sequential && !rec.getStepNo().equals(r.getCurrentStep())) continue;
            switch (rec.getRecipientType()) {
                case "USER" -> notifications.notifyUser(r.getOrganisationId(), rec.getRecipientId(),
                    title, titleAr, msg, "WORKFLOW_REQUEST_REMINDER", "WorkflowRequest", r.getId());
                case "ROLE" -> notifications.broadcastToRole(r.getOrganisationId(), rec.getRecipientKey(),
                    title, titleAr, msg, "WORKFLOW_REQUEST_REMINDER", "WorkflowRequest", r.getId());
                case "DEPARTMENT" -> userRepo.findByOrganisationIdAndDepartmentIdAndIsActiveTrue(
                        r.getOrganisationId(), rec.getRecipientId())
                    .forEach(u -> notifications.notifyUser(r.getOrganisationId(), u.getId(),
                        title, titleAr, msg, "WORKFLOW_REQUEST_REMINDER", "WorkflowRequest", r.getId()));
                case "COMMITTEE" -> committeeMembers.findActiveByCommittee(
                        r.getOrganisationId(), rec.getRecipientId()).stream()
                    .map(CommitteeMemberTerm::getMemberUserId).filter(java.util.Objects::nonNull).distinct()
                    .forEach(uid -> notifications.notifyUser(r.getOrganisationId(), uid,
                        title, titleAr, msg, "WORKFLOW_REQUEST_REMINDER", "WorkflowRequest", r.getId()));
                default -> { /* EXTERNAL — no outbound-mail portal in the platform yet */ }
            }
        }
    }

    private void appendEvent(WorkflowRequest r, String type, String from, String to, String note) {
        WorkflowRequestEvent e = new WorkflowRequestEvent();
        e.setOrganisationId(r.getOrganisationId());
        e.setRequestId(r.getId());
        e.setEventType(type);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setActor("sla-sweep");
        e.setNote(note);
        eventRepo.save(e);
    }

    /** Run an audit write under the row's own tenant, then restore whatever was bound before. */
    private void withTenant(Long org, Runnable r) {
        Long previous = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(org);
            r.run();
        } finally {
            if (previous != null) TenantContext.setCurrentTenant(previous);
            else TenantContext.clear();
        }
    }

    private static String code(WorkflowRequest r) { return "WR-" + String.format("%05d", r.getId()); }

    public record SweepResult(int reminders, int escalations) {}
}
