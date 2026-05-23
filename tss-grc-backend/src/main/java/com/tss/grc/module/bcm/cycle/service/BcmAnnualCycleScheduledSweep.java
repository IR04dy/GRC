package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmAssessmentCycle;
import com.tss.grc.module.bcm.cycle.repository.BcmAssessmentCycleRepository;
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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Annual BCM cycle automation — Slice 7-C (Sprint-7).
 *
 * <p>Runs hourly. For every tenant with at least one
 * {@link BcmAssessmentCycle}, fires the time-based triggers the
 * BCM Manager needs to stay on top of the annual ISO 22301
 * cadence (without anyone needing to check a calendar):
 *
 * <ul>
 *   <li>{@code BCM_CYCLE_CLOSEOUT_DUE_90D} — cycle endDate within
 *       90 days, status not yet APPROVED/ARCHIVED.</li>
 *   <li>{@code BCM_CYCLE_CLOSEOUT_DUE_60D} — 60 day lead.</li>
 *   <li>{@code BCM_CYCLE_CLOSEOUT_DUE_30D} — 30 day lead — usually
 *       triggers Board-pack drafting + audit-pack assembly.</li>
 *   <li>{@code BCM_CYCLE_CLOSEOUT_DUE_7D} — 7 day lead — last-call,
 *       usually triggers the executive escalation rule.</li>
 *   <li>{@code BCM_CYCLE_OVERDUE} — endDate already past + still
 *       open. Fires daily until the cycle is APPROVED / ARCHIVED.</li>
 * </ul>
 *
 * <p>Idempotency via audit-log marker {@code BCY_FIRE:<TRIGGER>}
 * mirroring the {@code COM_FIRE} convention in
 * {@code admin/committee/service/CommitteeScheduledSweep}. Each
 * (cycle, trigger) pair fires at most once — re-running after a
 * restart never double-fires. {@code OVERDUE} is the only trigger
 * that can fire more than once (re-armed each calendar day) because
 * it is the genuine "still red" signal.
 *
 * <p>Configurable via:
 * <pre>
 *   grc.bcm.cycle-sweep.period-ms           default 3600000   (1h)
 *   grc.bcm.cycle-sweep.initial-delay-ms    default 120000    (2 min)
 * </pre>
 *
 * <p>Pairs cleanly with the existing F8 automation rule engine —
 * a downstream {@code AR-160+} rule pack can listen for these
 * {@link DomainEvent}s and fan out NOTIFY / ESCALATE / CREATE_RECORD
 * actions per tenant playbook (no per-tenant config code needed).
 */
@Component
@RequiredArgsConstructor
public class BcmAnnualCycleScheduledSweep {

    private static final Logger log = LoggerFactory.getLogger(BcmAnnualCycleScheduledSweep.class);

    /** Audit action_type used for per-trigger-per-entity dedup. {@code action_type}
     *  is VARCHAR(40) — prefix (9) + longest trigger {@code BCM_CYCLE_CLOSEOUT_DUE_90D} (28) = 37. */
    static final String AUDIT_PREFIX = "BCY_FIRE:";

    /** Trigger codes published as {@link DomainEvent#trigger}. */
    static final String T_CLOSEOUT_90D = "BCM_CYCLE_CLOSEOUT_DUE_90D";
    static final String T_CLOSEOUT_60D = "BCM_CYCLE_CLOSEOUT_DUE_60D";
    static final String T_CLOSEOUT_30D = "BCM_CYCLE_CLOSEOUT_DUE_30D";
    static final String T_CLOSEOUT_7D  = "BCM_CYCLE_CLOSEOUT_DUE_7D";
    static final String T_OVERDUE      = "BCM_CYCLE_OVERDUE";

    private final BcmAssessmentCycleRepository cycles;
    private final AuditLogRepository            auditRepo;
    private final AuditLogService               audit;
    private final ApplicationEventPublisher     publisher;

    @Scheduled(fixedDelayString = "${grc.bcm.cycle-sweep.period-ms:3600000}",
               initialDelayString = "${grc.bcm.cycle-sweep.initial-delay-ms:120000}")
    public void tick() {
        LocalDate today = LocalDate.now();
        for (Long orgId : cycles.findDistinctOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                int fired = 0;
                fired += sweepCloseoutDue(orgId, today, 90, T_CLOSEOUT_90D);
                fired += sweepCloseoutDue(orgId, today, 60, T_CLOSEOUT_60D);
                fired += sweepCloseoutDue(orgId, today, 30, T_CLOSEOUT_30D);
                fired += sweepCloseoutDue(orgId, today,  7, T_CLOSEOUT_7D);
                fired += sweepOverdue(orgId, today);
                if (fired > 0) {
                    log.info("[bcm cycle sweep] org={} fired {} trigger(s)", orgId, fired);
                }
            } catch (Exception ex) {
                log.warn("[bcm cycle sweep] org={} scan failed: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    // ── Lead-time close-out sweep (one-shot per cycle) ───────────────

    private int sweepCloseoutDue(Long org, LocalDate today, int leadDays, String trigger) {
        // Window: [today, today + leadDays] inclusive. Repeated calls don't
        // refire because alreadyFired() checks the audit marker.
        List<BcmAssessmentCycle> rows = cycles.findEndingBetween(org, today, today.plusDays(leadDays));
        int fired = 0;
        for (BcmAssessmentCycle c : rows) {
            // The 90-day finder also returns cycles inside the 60/30/7 window.
            // Skip rows that are closer than this lead time — they'll be picked
            // up by the tighter sweep on this same tick.
            long daysToEnd = ChronoUnit.DAYS.between(today, c.getEndDate());
            int prevLead = previousLead(leadDays);
            if (prevLead > 0 && daysToEnd <= prevLead) continue;

            if (alreadyFired(org, "BcmAssessmentCycle", c.getId(), trigger)) continue;

            publisher.publishEvent(DomainEvent.of(
                org, trigger, "BcmAssessmentCycle", c.getId(),
                Map.of(
                    "cycleNameEn",       safe(c.getCycleNameEn()),
                    "cycleNameAr",       safe(c.getCycleNameAr()),
                    "reportingYear",     String.valueOf(c.getReportingYear()),
                    "endDate",           c.getEndDate() == null ? "" : c.getEndDate().toString(),
                    "status",            safe(c.getStatus()),
                    "bcmManagerId",      c.getBcmManagerId() == null ? "" : c.getBcmManagerId().toString(),
                    "executiveSponsor",  c.getExecutiveSponsorId() == null ? "" : c.getExecutiveSponsorId().toString(),
                    "leadDays",          String.valueOf(leadDays),
                    "criticalGapCount",  String.valueOf(c.getCriticalGapCount()),
                    "openGapCount",      String.valueOf(c.getOpenGapCount())
                )));
            markFired(org, "BcmAssessmentCycle", c.getId(), trigger);
            fired++;
        }
        return fired;
    }

    // ── Overdue sweep (re-arms daily) ────────────────────────────────

    private int sweepOverdue(Long org, LocalDate today) {
        List<BcmAssessmentCycle> rows = cycles.findOverdue(org, today);
        int fired = 0;
        // For OVERDUE we dedupe per-day, not per-cycle — the audit-log
        // marker is "BCY_FIRE:BCM_CYCLE_OVERDUE:<yyyy-MM-dd>". That way
        // a cycle that stays overdue keeps firing one event per day (so
        // Saudi-native daily-stand-up dashboards remain accurate) without
        // flooding the inbox every hour.
        String todayKey = today.toString();
        for (BcmAssessmentCycle c : rows) {
            String dedupKey = T_OVERDUE + ":" + todayKey;
            if (alreadyFired(org, "BcmAssessmentCycle", c.getId(), dedupKey)) continue;

            publisher.publishEvent(DomainEvent.of(
                org, T_OVERDUE, "BcmAssessmentCycle", c.getId(),
                Map.of(
                    "cycleNameEn",       safe(c.getCycleNameEn()),
                    "cycleNameAr",       safe(c.getCycleNameAr()),
                    "reportingYear",     String.valueOf(c.getReportingYear()),
                    "endDate",           c.getEndDate() == null ? "" : c.getEndDate().toString(),
                    "status",            safe(c.getStatus()),
                    "daysOverdue",       String.valueOf(
                                            ChronoUnit.DAYS.between(c.getEndDate(), today)),
                    "criticalGapCount",  String.valueOf(c.getCriticalGapCount()),
                    "openGapCount",      String.valueOf(c.getOpenGapCount())
                )));
            markFired(org, "BcmAssessmentCycle", c.getId(), dedupKey);
            fired++;
        }
        return fired;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Inverse of the lead ladder — what's the next-tighter window? */
    private static int previousLead(int leadDays) {
        return switch (leadDays) {
            case 90 -> 60;
            case 60 -> 30;
            case 30 ->  7;
            case  7 ->  0;
            default ->  0;
        };
    }

    private static String safe(String s) { return s == null ? "" : s; }

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
        audit.log(AUDIT_PREFIX + trigger, entityType, entityId, "Sweep fired " + trigger);
    }
}
