package com.tss.grc.module.policy.scheduler;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.policy.service.PolicyManagementService;
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
 * C-3 — policy-lifecycle scheduler (remediation plan §19).
 *
 * <p>Closes the gap where policy and policy-exception expiry could only be
 * triggered by a human clicking "Run expiry sweep", and where {@code
 * nextReviewDate} was stored but never acted on. Runs once a day, per tenant:
 *
 * <ol>
 *   <li>auto-runs {@link PolicyManagementService#runExpirySweep()} +
 *       {@link PolicyManagementService#runExceptionExpirySweep()} (PUBLISHED →
 *       EXPIRED / APPROVED exception → EXPIRED), and</li>
 *   <li>emits a {@code POLICY_REVIEW_DUE} {@link DomainEvent} for every
 *       PUBLISHED policy whose {@code nextReviewDate} falls within the
 *       {@value #REVIEW_LEAD_DAYS}-day lead window, so the F8 automation engine
 *       can route a NOTIFY / ESCALATE to the owner.</li>
 * </ol>
 *
 * <p>Review-due is deduped by an audit-log marker keyed on the policy's review
 * date ({@code POL_FIRE:POLICY_REVIEW_DUE:<date>}), so a restart never
 * double-notifies, yet a renewed policy (new review date) re-arms next cycle —
 * mirroring {@code BcmAnnualCycleScheduledSweep}. Scheduling is enabled globally
 * on {@code GrcApplication}.
 */
@Component
@RequiredArgsConstructor
public class PolicyComplianceScheduler {

    private static final Logger log = LoggerFactory.getLogger(PolicyComplianceScheduler.class);

    /** Notify the owner this many days before a policy's review falls due. */
    private static final int REVIEW_LEAD_DAYS = 30;

    /** Audit marker prefix for per-policy-per-cycle dedup. action_type is VARCHAR(40);
     *  "POL_FIRE:" (9) + "POLICY_REVIEW_DUE:" (18) + ISO date (10) = 37. */
    static final String AUDIT_PREFIX = "POL_FIRE:";
    static final String T_REVIEW_DUE = "POLICY_REVIEW_DUE";

    private final PolicyDocumentRepository docs;
    private final PolicyManagementService  policyService;
    private final AuditLogRepository        auditRepo;
    private final AuditLogService           audit;
    private final ApplicationEventPublisher publisher;

    /** 01:50 daily — just after the evidence expiry (01:35) and SLA (01:40) sweeps. */
    @Scheduled(cron = "0 50 1 * * *")
    public void tick() {
        LocalDate today = LocalDate.now();
        for (Long orgId : docs.findDistinctOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                int expired    = policyService.runExpirySweep().size();
                int excExpired = policyService.runExceptionExpirySweep().size();
                int reviewDue  = sweepReviewDue(orgId, today);
                if (expired + excExpired + reviewDue > 0) {
                    log.info("[policy scheduler] org={} expired={} exceptionExpired={} reviewDue={}",
                        orgId, expired, excExpired, reviewDue);
                }
            } catch (Exception ex) {
                log.warn("[policy scheduler] org={} scan failed: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    private int sweepReviewDue(Long org, LocalDate today) {
        List<PolicyDocument> due = docs.findReviewDue(org, today, today.plusDays(REVIEW_LEAD_DAYS));
        int fired = 0;
        for (PolicyDocument p : due) {
            // Dedup on the review date so a one-time notice fires per review cycle;
            // renewing the policy (new nextReviewDate) re-arms it for next time.
            String dedup = T_REVIEW_DUE + ":" + p.getNextReviewDate();
            if (alreadyFired(org, "PolicyDocument", p.getId(), dedup)) continue;

            publisher.publishEvent(new DomainEvent(
                org, T_REVIEW_DUE, "PolicyDocument", p.getId(),
                "policy:" + safe(p.getPolicyCode()) + ":v" + safe(p.getVersion()),
                Map.of(
                    "policyCode",      safe(p.getPolicyCode()),
                    "version",         safe(p.getVersion()),
                    "titleEn",         safe(p.getTitleEn()),
                    "category",        safe(p.getCategory()),
                    "ownerEmail",      safe(p.getOwnerEmail()),
                    "ownerName",       safe(p.getOwnerName()),
                    "nextReviewDate",  p.getNextReviewDate() == null ? "" : p.getNextReviewDate().toString(),
                    "daysUntilReview", String.valueOf(ChronoUnit.DAYS.between(today, p.getNextReviewDate()))
                )));
            markFired(org, "PolicyDocument", p.getId(), dedup);
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
        audit.log(AUDIT_PREFIX + trigger, entityType, entityId, "Policy scheduler fired " + trigger);
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
