package com.tss.grc.module.change_mgmt.scheduler;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.util.SaudiWorkdayUtil;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.model.RatificationStates;
import com.tss.grc.module.change_mgmt.repository.ChangeRatificationPolicyRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A17-D3 — the emergency-ratification SLA sweep.
 *
 * <p>ADR-017 D-3: an emergency change implemented on ECAB authority must be
 * retrospectively ratified by the CAB within the tenant's SLA. Breach "escalates
 * to the CISO and lands in the unauthorised-change register" — because "an SLA
 * with no consequence is a report, not a control".
 *
 * <p><b>Interim destination (amendment 1).</b> CE-11's unauthorised-change
 * register does not exist as a table in either database, so the escalation goes
 * to the F8 automation engine (rule {@code CHG-RATIFY-BREACH}, seeded per tenant
 * by V345, escalating to the {@code CISO} role — a role verified to exist before
 * the rule was written) plus a durable audit-ledger event and a timestamp on the
 * change itself. When CE-11 lands, the register becomes an <em>additional</em>
 * sink; nothing here needs replacing.
 *
 * <p><b>Two things this does differently from {@link ChangeTaskSlaScheduler},
 * and why.</b>
 *
 * <p>First, it writes. CH-5's sweep deliberately stored nothing, because the
 * audit marker was a sufficient dedup ledger and writing nothing carries no
 * ROWVERSION risk. D-3 has to record the escalation on the change — the CISO's
 * register view and the KPI both read it, and a governance fact that lives only
 * in a log is the gap F2-93 is about. So it writes exactly once, through a native
 * single-statement UPDATE that never loads the entity: the ROWVERSION landmine is
 * "never save an entity twice around a native {@code @Modifying} on the same
 * table", and not loading it for write is the cleanest way to obey that.
 *
 * <p>Second, the dedup marker is keyed on the <b>due date</b>, not on "escalated
 * once". If the SLA is later shortened and a change's deadline moves, the sweep
 * must be free to escalate again against the new deadline — while a restart, a
 * second run in the same day, or a manually cleared column must not re-notify.
 * The audit ledger is the ledger; the column is the read model.
 *
 * <p><b>What it deliberately does not do:</b> nothing here escalates a
 * {@code GRANDFATHERED} row. V345 labelled the one pre-existing emergency change
 * rather than retro-fitting a clock to it, and the query cannot reach it because
 * only {@code PENDING} rows are considered. Had it been left {@code NOT_REQUIRED}
 * with a null due date instead, SQL's three-valued logic would have filtered it
 * out of the breach query too — and reported it compliant rather than exempt,
 * which is the difference between a control and a lie.
 */
@Component
@RequiredArgsConstructor
public class EmergencyRatificationSlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmergencyRatificationSlaScheduler.class);

    /** action_type is VARCHAR(40): "CHG_FIRE:" (9) + "RATIFY_BREACH:" (14) + ISO date (10) = 33. */
    static final String AUDIT_PREFIX = "CHG_FIRE:";
    static final String T_BREACH     = "RATIFY_BREACH";

    private final ChangeRequestRepository   changeRepo;
    private final AuditLogRepository        auditRepo;
    private final AuditLogService           audit;
    private final ApplicationEventPublisher publisher;

    @Autowired(required = false)
    private ChangeRatificationPolicyRepository policyRepo;

    /**
     * Per-escalation {@code REQUIRES_NEW} transaction — the platform's documented
     * engine-wiring pattern, and here it is load-bearing twice over.
     *
     * <p><b>It fixes a defect this slice's own live probe caught.</b> The first version put
     * {@code @Transactional} on {@code sweep()} and called it from {@code tick()}, which is
     * self-invocation: the Spring proxy is bypassed, so the native {@code @Modifying}
     * escalation stamp ran with no transaction and threw
     * <em>"Executing an update/delete query"</em> on every single run. The six unit tests
     * stayed green throughout, because a mocked repository needs no transaction — the mock
     * could not fail the way the real one does. Only the live fire against a real database
     * showed it, which is the argument for house rule 4 in one sentence.
     *
     * <p><b>And {@code REQUIRES_NEW} per change, not one transaction for the sweep</b>, so
     * one unwritable change cannot roll back the escalations already recorded for the
     * others. A batch control that is all-or-nothing fails closed on the whole tenant the
     * first time one row misbehaves.
     */
    @Autowired(required = false)
    private org.springframework.transaction.PlatformTransactionManager txManager;

    /**
     * 01:55 daily — after the change-task sweep (01:45) and the policy sweep (01:50).
     *
     * <p>Configurable, unlike its CH-5 sibling, for two reasons. Operationally, a
     * customer whose CAB meets Sunday morning wants the escalation to land before it,
     * not at 01:55 on an arbitrary day. And for verification: a hard-coded daily cron
     * means the escalation path — the consequence half of this control, the half ADR-017
     * D-3 says makes it a control rather than a report — can only ever be exercised by
     * unit tests. Being able to run it on a one-minute cron in dev is how it was proven
     * to fire against a real database.
     */
    @Scheduled(cron = "${grc.change.ratification-sweep-cron:0 55 1 * * *}")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        for (Long orgId : changeRepo.findDistinctOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                int fired = sweep(orgId, now);
                if (fired > 0)
                    log.warn("[ratification SLA] org={} unratified-emergency-changes-escalated={}", orgId, fired);
            } catch (Exception ex) {
                // ERROR with the stack trace, not WARN with a message. A17-D2/F2-87 shipped a
                // broken control behind an accurate WARN that ran for a week and nobody read;
                // the first version of THIS class did the same for three sweeps in a row. A
                // sweep that threw must not look like a sweep with nothing to do — so the line
                // says out loud that the escalation did not happen.
                log.error("[ratification SLA] org={} sweep FAILED — no breach escalation was recorded "
                        + "for this tenant on this run. Unratified emergency changes remain unreported.",
                        orgId, ex);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Escalate every emergency change past its ratification deadline that has not
     * already been escalated for that deadline. Returns how many escalations fired.
     * Package-visible for direct unit testing.
     */
    int sweep(Long org, LocalDateTime now) {
        int fired = 0;
        String escalateTo = escalationRole(org);
        for (ChangeRequest c : changeRepo.findRatificationBreaches(org, now)) {
            // PENDING implies a non-null due date (ck_change_ratification_clock), so this
            // is total — but read it from the row rather than assuming, because the
            // constraint is what makes it true and constraints can be dropped.
            LocalDateTime due = c.getRatificationDueAt();
            if (due == null) continue;
            String dedup = T_BREACH + ":" + due.toLocalDate();
            if (alreadyFired(org, c.getId(), dedup)) continue;

            long overdue = SaudiWorkdayUtil.businessDaysBetween(due, now);
            publisher.publishEvent(new DomainEvent(
                org, RatificationStates.EVENT_BREACHED, "ChangeRequest", c.getId(),
                "change:" + c.getId(),
                Map.of(
                    "changeId",             String.valueOf(c.getId()),
                    "changeCode",           safe(c.getChangeCode()),
                    "titleEn",              safe(c.getTitleEn()),
                    "riskLevel",            safe(c.getRiskLevel()),
                    "approvedByName",       safe(c.getCabDecisionByName()),
                    "implementationStarted", c.getImplementationStarted() == null
                                                ? "" : c.getImplementationStarted().toString(),
                    "ratificationDueAt",    due.toString(),
                    "businessDaysOverdue",  String.valueOf(overdue),
                    "escalateTo",           escalateTo
                )));

            // One native UPDATE, no entity load — see the class javadoc on ROWVERSION — inside
            // its own REQUIRES_NEW transaction, because a @Modifying query needs one and
            // self-invocation from tick() means @Transactional on this method would not
            // supply it. The ledger marker rides the same transaction as the stamp, so the
            // dedup record and the escalation record cannot diverge.
            inNewTransaction(() -> {
                changeRepo.stampRatificationEscalation(c.getId(), now);
                audit.log(AUDIT_PREFIX + dedup, "ChangeRequest", c.getId(),
                    "Emergency change " + safe(c.getChangeCode()) + " is UNRATIFIED " + overdue
                        + " business day(s) past its CAB ratification deadline of " + due
                        + " — escalated to " + escalateTo + " (ADR-017 D-3). Until ratified it stands as an "
                        + "unauthorised production change.");
            });
            fired++;
        }
        return fired;
    }

    /**
     * Run {@code body} in its own transaction, or inline when no transaction manager is
     * present (directly-constructed unit tests). The inline path is why the six unit tests
     * could not catch the missing transaction — recorded here rather than quietly relied on.
     */
    private void inNewTransaction(Runnable body) {
        if (txManager == null) { body.run(); return; }
        org.springframework.transaction.support.TransactionTemplate tpl =
            new org.springframework.transaction.support.TransactionTemplate(txManager);
        tpl.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tpl.executeWithoutResult(status -> body.run());
    }

    /** The tenant's configured escalation role, defaulting to the ADR's CISO. */
    private String escalationRole(Long org) {
        if (policyRepo == null) return "CISO";
        return policyRepo.findByOrganisationId(org)
            .map(p -> p.getEscalationRole() == null || p.getEscalationRole().isBlank()
                        ? "CISO" : p.getEscalationRole())
            .orElse("CISO");
    }

    private boolean alreadyFired(Long org, Long changeId, String trigger) {
        String marker = AUDIT_PREFIX + trigger;
        List<AuditLogEntry> rows = auditRepo
            .findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(org, "ChangeRequest", changeId);
        for (AuditLogEntry row : rows) {
            if (marker.equals(row.getActionType())) return true;
        }
        return false;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
