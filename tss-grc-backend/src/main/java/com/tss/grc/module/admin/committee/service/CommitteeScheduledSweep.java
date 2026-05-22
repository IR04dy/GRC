package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeActionItem;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.repository.CommitteeActionItemRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
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
import java.util.List;
import java.util.Map;

/**
 * Time-based committee-trigger sweep. Closes the loop on the V158
 * automation rules whose triggers fire from elapsed-time conditions
 * rather than user-driven state changes.
 *
 * <p>Sweeps covered (hourly):
 * <ul>
 *   <li>{@code COMMITTEE_ACTION_OVERDUE} — action whose dueDate is past
 *       and status is still OPEN/IN_PROGRESS gets flipped to OVERDUE,
 *       firing AR-148 (owner + line-manager escalation).</li>
 *   <li>{@code COMMITTEE_RESOLUTION_REVIEW_DUE_30D} — resolutions with
 *       reviewRequiredBy &le; 30 days fire AR-154 reminder.</li>
 *   <li>{@code COMMITTEE_CHARTER_REVIEW_DUE_30D} — committees whose
 *       nextCharterReviewAt &le; 30 days fire AR-155 reminder.</li>
 * </ul>
 *
 * <p>Idempotency via audit-log marker {@code COM_FIRE:<TRIGGER>} —
 * mirrors the SocOpsScheduledSweep pattern so re-running after a
 * restart never double-fires.
 */
@Component
@RequiredArgsConstructor
public class CommitteeScheduledSweep {

    private static final Logger log = LoggerFactory.getLogger(CommitteeScheduledSweep.class);

    /** Audit action_type used for per-trigger-per-entity dedup. Kept
     *  short because action_type is VARCHAR(40) — prefix (9) plus the
     *  longest trigger {@code COMMITTEE_CHARTER_REVIEW_DUE_30D} (31) = 40. */
    static final String AUDIT_PREFIX = "COM_FIRE:";

    private final CommitteeActionItemRepository actions;
    private final CommitteeResolutionRepository resolutions;
    private final CommitteeRepository           committees;
    private final AuditLogRepository            auditRepo;
    private final AuditLogService               audit;
    private final ApplicationEventPublisher     publisher;

    @Scheduled(fixedDelayString = "${grc.committee.sweep-period-ms:3600000}",
               initialDelayString = "${grc.committee.sweep-initial-delay-ms:120000}")
    public void tick() {
        LocalDate today = LocalDate.now();
        for (Long orgId : actions.findDistinctOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                int fired = 0;
                fired += sweepOverdueActions(orgId, today);
                fired += sweepResolutionReviewDue(orgId, today);
                fired += sweepCharterReviewDue(orgId, today);
                if (fired > 0) {
                    log.info("[committee sweep] org={} fired {} trigger(s)", orgId, fired);
                }
            } catch (Exception ex) {
                log.warn("[committee sweep] org={} scan failed: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    // ── Overdue action items ──────────────────────────────────────────

    private int sweepOverdueActions(Long org, LocalDate today) {
        List<CommitteeActionItem> rows = actions.findShouldBeOverdue(org, today);
        int fired = 0;
        for (CommitteeActionItem a : rows) {
            if (alreadyFired(org, "CommitteeActionItem", a.getId(), "COMMITTEE_ACTION_OVERDUE")) continue;
            // Flip status to OVERDUE in-place so the action's own state
            // tracks the sweep result.
            a.setStatus("OVERDUE");
            actions.save(a);

            publisher.publishEvent(DomainEvent.of(
                org, "COMMITTEE_ACTION_OVERDUE", "CommitteeActionItem", a.getId(),
                Map.of("actionCode",   a.getActionCode(),
                       "committeeId",  a.getCommitteeId(),
                       "meetingId",    a.getMeetingId(),
                       "ownerName",    a.getOwnerName() == null ? "" : a.getOwnerName(),
                       "dueDate",      a.getDueDate() == null ? "" : a.getDueDate().toString(),
                       "priority",     a.getPriority(),
                       "linkedModule", a.getLinkedModule() == null ? "" : a.getLinkedModule(),
                       "linkedEntityCode", a.getLinkedEntityCode() == null ? "" : a.getLinkedEntityCode())));
            markFired(org, "CommitteeActionItem", a.getId(), "COMMITTEE_ACTION_OVERDUE");
            fired++;
        }
        return fired;
    }

    // ── Resolution review due (30-day lead time) ──────────────────────

    private int sweepResolutionReviewDue(Long org, LocalDate today) {
        List<CommitteeResolution> rows = resolutions.findReviewDueBetween(org, today, today.plusDays(30));
        int fired = 0;
        for (CommitteeResolution r : rows) {
            if (alreadyFired(org, "CommitteeResolution", r.getId(), "COMMITTEE_RESOLUTION_REVIEW_DUE_30D")) continue;
            publisher.publishEvent(DomainEvent.of(
                org, "COMMITTEE_RESOLUTION_REVIEW_DUE_30D", "CommitteeResolution", r.getId(),
                Map.of("resolutionCode",   r.getResolutionCode(),
                       "committeeId",      r.getCommitteeId(),
                       "meetingId",        r.getMeetingId(),
                       "titleEn",          r.getTitleEn() == null ? "" : r.getTitleEn(),
                       "reviewRequiredBy", r.getReviewRequiredBy() == null ? "" : r.getReviewRequiredBy().toString())));
            markFired(org, "CommitteeResolution", r.getId(), "COMMITTEE_RESOLUTION_REVIEW_DUE_30D");
            fired++;
        }
        return fired;
    }

    // ── Charter review due (30-day lead time) ─────────────────────────

    private int sweepCharterReviewDue(Long org, LocalDate today) {
        // Scope to this org at the DB layer (replaces findAll().filter(byOrg) —
        // that pattern is O(N×M) over N tenants and M committees; the sweep
        // calls this method per-tenant inside a TenantContext loop already).
        List<Committee> all = committees.findByOrganisationId(org);
        int fired = 0;
        for (Committee c : all) {
            if (c.getNextCharterReviewAt() == null) continue;
            LocalDate dueDate = c.getNextCharterReviewAt();
            if (dueDate.isBefore(today) || dueDate.isAfter(today.plusDays(30))) continue;
            if (alreadyFired(org, "Committee", c.getId(), "COMMITTEE_CHARTER_REVIEW_DUE_30D")) continue;

            publisher.publishEvent(DomainEvent.of(
                org, "COMMITTEE_CHARTER_REVIEW_DUE_30D", "Committee", c.getId(),
                Map.of("committeeCode",          c.getCode() == null ? "" : c.getCode(),
                       "nameEn",                 c.getNameEn() == null ? "" : c.getNameEn(),
                       "nextCharterReviewAt",    dueDate.toString(),
                       "charterTemplateCode",    c.getCharterTemplateCode() == null ? "" : c.getCharterTemplateCode())));
            markFired(org, "Committee", c.getId(), "COMMITTEE_CHARTER_REVIEW_DUE_30D");
            fired++;
        }
        return fired;
    }

    // ── Audit-log dedup ───────────────────────────────────────────────

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
        audit.log(AUDIT_PREFIX + trigger, entityType, entityId,
            "Sweep fired " + trigger);
    }
}
