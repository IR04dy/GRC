package com.tss.grc.module.change_mgmt.scheduler;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeTask;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * CH-5 — change-task SLA scheduler.
 *
 * <p>{@code change_tasks.due_at} was stored but never acted on: an overdue
 * implementation / test / rollback task raised no reminder or escalation. This
 * runs daily, per tenant, and emits a {@code CHANGE_TASK_OVERDUE}
 * {@link DomainEvent} for every still-open task whose {@code dueAt} has passed,
 * so the F8 automation engine can route a NOTIFY / ESCALATE to the owner.
 *
 * <p>Dedup mirrors {@code PolicyComplianceScheduler}: an audit-log marker keyed on
 * the task's due date ({@code CHG_FIRE:TASK_OVERDUE:<dueDate>}), so a restart never
 * double-notifies, yet rescheduling the task (a new {@code dueAt}) re-arms it next
 * cycle. No SLA-tracking columns are added — the audit trail is the dedup ledger.
 * The sweep only reads + emits (no entity save), so it carries no ROWVERSION risk.
 */
@Component
@RequiredArgsConstructor
public class ChangeTaskSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChangeTaskSlaScheduler.class);

    /** action_type is VARCHAR(40): "CHG_FIRE:" (9) + "TASK_OVERDUE:" (13) + ISO date (10) = 32. */
    static final String AUDIT_PREFIX  = "CHG_FIRE:";
    static final String T_OVERDUE     = "TASK_OVERDUE";
    /** F8 trigger name emitted for the automation engine. */
    static final String EVENT_OVERDUE = "CHANGE_TASK_OVERDUE";

    private final ChangeTaskRepository      taskRepo;
    private final ChangeRequestRepository   changeRepo;
    private final AuditLogRepository         auditRepo;
    private final AuditLogService            audit;
    private final ApplicationEventPublisher  publisher;

    /** 01:45 daily — between the evidence (01:35) and policy (01:50) sweeps. */
    @Scheduled(cron = "0 45 1 * * *")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (Long orgId : changeRepo.findDistinctOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                int fired = sweep(orgId, now);
                if (fired > 0) log.info("[change-task SLA] org={} overdue-notified={}", orgId, fired);
            } catch (Exception ex) {
                log.warn("[change-task SLA] org={} sweep failed: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Emit {@code CHANGE_TASK_OVERDUE} for every overdue open task not already
     * notified for its current due date. Returns how many events fired.
     * Package-visible for direct unit testing.
     */
    int sweep(Long org, LocalDateTime now) {
        int fired = 0;
        for (ChangeTask t : taskRepo.findOverdueOpen(org, now)) {
            String dueDate = t.getDueAt() == null ? "" : t.getDueAt().toLocalDate().toString();
            String dedup   = T_OVERDUE + ":" + dueDate;
            if (alreadyFired(org, "ChangeTask", t.getId(), dedup)) continue;

            long daysOverdue = t.getDueAt() == null ? 0
                : ChronoUnit.DAYS.between(t.getDueAt().toLocalDate(), now.toLocalDate());
            publisher.publishEvent(new DomainEvent(
                org, EVENT_OVERDUE, "ChangeTask", t.getId(),
                "change-task:" + t.getId(),
                Map.of(
                    "taskId",          String.valueOf(t.getId()),
                    "changeRequestId", String.valueOf(t.getChangeRequestId()),
                    "titleEn",         safe(t.getTitleEn()),
                    "ownerName",       safe(t.getOwnerName()),
                    "status",          safe(t.getStatus()),
                    "dueAt",           t.getDueAt() == null ? "" : t.getDueAt().toString(),
                    "daysOverdue",     String.valueOf(daysOverdue)
                )));
            markFired(org, "ChangeTask", t.getId(), dedup);
            fired++;
        }
        return fired;
    }

    private boolean alreadyFired(Long org, String entityType, Long entityId, String trigger) {
        String marker = AUDIT_PREFIX + trigger;
        List<AuditLogEntry> rows = auditRepo
            .findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(org, entityType, entityId);
        for (AuditLogEntry row : rows) {
            if (marker.equals(row.getActionType())) return true;
        }
        return false;
    }

    private void markFired(Long org, String entityType, Long entityId, String trigger) {
        audit.log(AUDIT_PREFIX + trigger, entityType, entityId, "Change-task SLA scheduler fired " + trigger);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
