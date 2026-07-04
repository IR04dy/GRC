package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberActionPlan;
import com.tss.grc.module.cyber.repository.CyberActionPlanRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.service.NotificationService;
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

/**
 * CY-E — the cyber CAPA / action-plan SLA clock.
 *
 * <p>Gap→CAPA conversion now routes an owner + a risk-weighted due date onto each
 * {@link CyberActionPlan} (see {@code CyberAssessmentService.convertGapToCapa}).
 * This sweep is the other half: it drives the due date.
 *
 * <ul>
 *   <li><b>Reminder pass</b> — open plans coming due within
 *       {@value #REMINDER_WINDOW_DAYS} days get a throttled reminder to the
 *       {@value #REMIND_ROLE} role.</li>
 *   <li><b>Escalation pass</b> — open plans past their due date are escalated
 *       <em>once</em> ({@code escalatedAt} guards re-fire): a
 *       {@value #ESCALATE_ROLE} broadcast + a {@code CYBER_CAPA_ESCALATED} audit
 *       row + a {@code CYBER_ACTION_PLAN_OVERDUE} F8 event.</li>
 * </ul>
 *
 * <p>Config-gated by {@code grc.cyber-capa-sla.scheduler-enabled} (mirrors the
 * vuln-SLA scheduler, CY-D). Runs system-wide; {@link TenantContext} is set
 * per-row so the audit row lands under the plan's own organisation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CyberActionPlanSlaScheduler {

    /** Terminal workflow states — a plan in one of these is done, never chased. */
    private static final List<String> TERMINAL =
        List.of("COMPLETED", "CANCELLED", "CLOSED", "VALIDATED");
    /** Days-before-due window for the pre-due reminder. */
    private static final int  REMINDER_WINDOW_DAYS   = 7;
    /** Anti-spam gap between reminders for the same plan. */
    private static final long REMINDER_THROTTLE_DAYS = 3;
    /** Role that owns overdue escalations. */
    private static final String ESCALATE_ROLE = "CISO";
    /** Role that receives pre-due reminders. */
    private static final String REMIND_ROLE   = "SECURITY_ANALYST";

    private final CyberActionPlanRepository actionPlanRepo;
    private final NotificationService       notifications;
    private final AuditLogService           audit;
    private final ApplicationEventPublisher publisher;

    @Value("${grc.cyber-capa-sla.scheduler-enabled:false}")
    private boolean enabled;

    /** Poll on the configured interval (default 5 min; prod flips it on). */
    @Scheduled(fixedDelayString = "${grc.cyber-capa-sla.scheduler-interval-ms:300000}")
    public void runSweep() {
        if (!enabled) {
            log.trace("Cyber-CAPA SLA scheduler disabled (grc.cyber-capa-sla.scheduler-enabled=false). Skipping.");
            return;
        }
        try {
            SweepResult r = sweep();
            if (r.reminders() > 0 || r.escalations() > 0) {
                log.info("Cyber-CAPA SLA sweep → {} reminder(s), {} escalation(s)", r.reminders(), r.escalations());
            }
        } catch (Exception e) {
            log.error("Cyber-CAPA SLA sweep failed: {}", e.getMessage(), e);
        }
    }

    /** One reminder + escalation pass over every tenant's open, dated plans. */
    @Transactional
    public SweepResult sweep() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime throttleBefore = now.minusDays(REMINDER_THROTTLE_DAYS);

        int escalations = 0;
        for (CyberActionPlan ap : actionPlanRepo
                .findByWorkflowStatusNotInAndEscalatedAtIsNullAndDueDateBefore(TERMINAL, today)) {
            escalate(ap, today, now);
            escalations++;
        }

        int reminders = 0;
        for (CyberActionPlan ap : actionPlanRepo
                .findByWorkflowStatusNotInAndDueDateBetween(TERMINAL, today, today.plusDays(REMINDER_WINDOW_DAYS))) {
            if (ap.getEscalatedAt() != null) continue;                       // already escalated — no reminder
            if (ap.getLastReminderAt() != null && ap.getLastReminderAt().isAfter(throttleBefore)) continue;
            remind(ap, today, now);
            reminders++;
        }
        return new SweepResult(reminders, escalations);
    }

    private void escalate(CyberActionPlan ap, LocalDate today, LocalDateTime now) {
        long daysOverdue = ChronoUnit.DAYS.between(ap.getDueDate(), today);
        ap.setEscalatedAt(now);
        ap.setLastReminderAt(now);
        ap.setReminderCount(ap.getReminderCount() + 1);
        actionPlanRepo.save(ap);

        Long org = ap.getOrganisationId();
        withTenant(org, () -> audit.log("CYBER_CAPA_ESCALATED", "CyberActionPlan", ap.getId(),
            "Action plan \"" + nz(ap.getTitleEn()) + "\" is " + daysOverdue + " day(s) overdue (due "
                + ap.getDueDate() + ", owner " + nz(ap.getOwnerName()) + ") — escalated"));

        notifications.broadcastToRole(org, ESCALATE_ROLE,
            "Cyber CAPA overdue — escalated",
            "خطة معالجة سيبرانية متأخرة — تم التصعيد",
            "\"" + nz(ap.getTitleEn()) + "\" (owner " + nz(ap.getOwnerName()) + ") is " + daysOverdue
                + " day(s) overdue.",
            "CYBER_CAPA_ESCALATED", "CyberActionPlan", ap.getId());

        publisher.publishEvent(DomainEvent.of(org, "CYBER_ACTION_PLAN_OVERDUE", "CyberActionPlan", ap.getId(),
            Map.of("priority",    nz(ap.getPriority()),
                   "owner",       nz(ap.getOwnerName()),
                   "daysOverdue", daysOverdue,
                   "sourceType",  nz(ap.getSourceType()))));
    }

    private void remind(CyberActionPlan ap, LocalDate today, LocalDateTime now) {
        long daysToDue = ChronoUnit.DAYS.between(today, ap.getDueDate());
        ap.setLastReminderAt(now);
        ap.setReminderCount(ap.getReminderCount() + 1);
        actionPlanRepo.save(ap);

        notifications.broadcastToRole(ap.getOrganisationId(), REMIND_ROLE,
            "Cyber CAPA due soon",
            "خطة معالجة سيبرانية تقترب من الاستحقاق",
            "\"" + nz(ap.getTitleEn()) + "\" (owner " + nz(ap.getOwnerName()) + ") is due in " + daysToDue
                + " day(s) — " + ap.getDueDate() + ".",
            "CYBER_CAPA_REMINDER", "CyberActionPlan", ap.getId());
    }

    /** Run an audit write under the row's own tenant (AuditLogService reads org
     *  from TenantContext), then restore whatever was bound before. */
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

    private static String nz(String s) { return s == null ? "" : s; }

    public record SweepResult(int reminders, int escalations) {}
}
