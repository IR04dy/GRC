package com.tss.grc.module.compliance.scheduler;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.obligation.repository.ObligationAttestationRepository;
import com.tss.grc.module.compliance.obligation.service.ObligationAttestationService;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
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
 * C-3 — compliance-deadline scheduler (remediation plan §19). Completes the
 * scheduler suite alongside {@code PolicyComplianceScheduler}:
 *
 * <ol>
 *   <li><b>Obligation attestations</b> — flips every DUE / IN_PROGRESS cycle
 *       past its due date to OVERDUE and emits {@code OBLIGATION_OVERDUE}
 *       (previously {@code markOverdueForCampaign} only ran when a campaign
 *       service invoked it, and only for campaign-materialised rows).</li>
 *   <li><b>CAPA / audit findings</b> — emits {@code FINDING_OVERDUE} for open
 *       (not-CLOSED) findings past {@code dueDate}; {@code AuditFinding.dueDate}
 *       was stored but never enforced. Re-armed daily so a finding that stays
 *       overdue keeps signalling, deduped per calendar day via an audit marker.</li>
 * </ol>
 *
 * <p>Both feed the F8 automation engine (NOTIFY / ESCALATE), mirroring
 * {@code BcmAnnualCycleScheduledSweep}. Scheduling is enabled globally on
 * {@code GrcApplication}.
 */
@Component
@RequiredArgsConstructor
public class ComplianceDeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(ComplianceDeadlineScheduler.class);

    static final String AUDIT_PREFIX  = "CDL_FIRE:";
    static final String T_FIND_OVERDUE = "FINDING_OVERDUE";

    private final ObligationAttestationRepository attestationRepo;
    private final ObligationAttestationService     attestationService;
    private final AuditFindingRepository           findingRepo;
    private final AuditLogRepository               auditRepo;
    private final AuditLogService                  audit;
    private final ApplicationEventPublisher        publisher;

    /** 01:55 daily — just after the policy scheduler (01:50). */
    @Scheduled(cron = "0 55 1 * * *")
    public void tick() {
        LocalDate today = LocalDate.now();

        // 1. Obligation attestation overdue (org-wide).
        for (Long orgId : attestationRepo.findDistinctOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                int n = attestationService.sweepOverdueForOrg(today);
                if (n > 0) log.info("[compliance-deadline] org={} obligationsOverdue={}", orgId, n);
            } catch (Exception ex) {
                log.warn("[compliance-deadline] org={} obligation sweep failed: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }

        // 2. CAPA / audit findings overdue.
        for (Long orgId : findingRepo.findDistinctOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                int n = sweepFindingsOverdue(orgId, today);
                if (n > 0) log.info("[compliance-deadline] org={} findingsOverdue={}", orgId, n);
            } catch (Exception ex) {
                log.warn("[compliance-deadline] org={} finding sweep failed: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    private int sweepFindingsOverdue(Long org, LocalDate today) {
        List<AuditFinding> overdue = findingRepo.findOverdueOpen(org, today);
        int fired = 0;
        String todayKey = today.toString();
        for (AuditFinding f : overdue) {
            // Dedup per calendar day so a persistently-overdue finding keeps
            // signalling once a day (not once ever, not every run).
            String dedup = T_FIND_OVERDUE + ":" + todayKey;
            if (alreadyFired(org, "AuditFinding", f.getId(), dedup)) continue;

            publisher.publishEvent(new DomainEvent(
                org, T_FIND_OVERDUE, "AuditFinding", f.getId(), null,
                Map.of(
                    "title",       safe(f.getTitle()),
                    "severity",    safe(f.getSeverity()),
                    "owner",       safe(f.getOwner()),
                    "status",      safe(f.getStatus()),
                    "capaStage",   safe(f.getCapaStage()),
                    "clauseRef",   safe(f.getClauseRef()),
                    "dueDate",     f.getDueDate() == null ? "" : f.getDueDate().toString(),
                    "daysOverdue", f.getDueDate() == null ? "0"
                                     : String.valueOf(ChronoUnit.DAYS.between(f.getDueDate(), today))
                )));
            markFired(org, "AuditFinding", f.getId(), dedup);
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
        audit.log(AUDIT_PREFIX + trigger, entityType, entityId, "Deadline scheduler fired " + trigger);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
