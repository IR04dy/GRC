package com.tss.grc.module.iam.governance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.Role;
import com.tss.grc.module.iam.governance.entity.BreakGlassEvent;
import com.tss.grc.module.iam.governance.repository.BreakGlassEventRepository;
import com.tss.grc.module.iam.repository.RoleRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService.AuditEventBuilder;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * RBAC Access-Governance P3 (plan §6.6) — break-glass emergency access.
 * Activation (gated by the dedicated {@code IAM/BREAK_GLASS} permission — CISO,
 * IR_LEAD, GRC_ADMINISTRATOR) grants the pre-provisioned {@code EMERGENCY_RESPONDER}
 * role to the <i>activator themselves</i>, time-bound to at most 24 hours, tied to a
 * mandatory incident reference. It fires a CRITICAL-severity audit event, auto-expires
 * (the P1 hourly sweep removes the {@code iam.user_roles} row; a companion sweep here
 * flips the event status), and mandatorily awaits a post-hoc review by someone other
 * than the activator.
 */
@Service
@RequiredArgsConstructor
public class BreakGlassService {

    private static final Logger log = LoggerFactory.getLogger(BreakGlassService.class);
    private static final int MAX_VALIDITY_HOURS = 24;

    private final BreakGlassEventRepository events;
    private final RoleRepository roles;
    private final AuditLogService audit;
    private final com.tss.grc.module.platform.service.NotificationService notifications;
    private final PlatformTransactionManager txManager;

    @Transactional(readOnly = true)
    public List<BreakGlassEvent> list() {
        return events.findByOrganisationIdAndIsDeletedFalseOrderByActivatedAtDesc(org());
    }

    @Transactional
    public BreakGlassEvent activate(String incidentRef, String reason, Integer validityHours,
                                    Long activator, String activatorUsername) {
        Long org = org();
        int hours = validityHours == null ? MAX_VALIDITY_HOURS : validityHours;
        if (hours < 1 || hours > MAX_VALIDITY_HOURS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "validityHours must be 1–" + MAX_VALIDITY_HOURS);
        }
        Role emergency = roles.findByOrganisationIdOrOrganisationIdIsNull(org).stream()
            .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
            .filter(r -> "EMERGENCY_RESPONDER".equals(r.getRoleName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "EMERGENCY_RESPONDER role not provisioned (V284 missing?)"));
        // F2-62: break-glass is the one path where silently granting nothing is worst — an
        // operator would be told the emergency access is ACTIVE, mid-incident, while holding no
        // authority at all. Checked separately from the lookup above so the message names the
        // real cause instead of claiming the role is missing.
        if (!emergency.grantsAuthority()) {
            throw new IllegalStateException(
                "EMERGENCY_RESPONDER role is deactivated — break-glass would grant no access. "
                    + "Reactivate the role before activating break-glass.");
        }

        LocalDateTime expires = LocalDateTime.now().plusHours(hours);
        int inserted = events.insertBreakGlassAssignment(activator, emergency.getId(), expires,
            "BREAK-GLASS " + incidentRef);
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Break-glass already active for this user (EMERGENCY_RESPONDER still assigned)");
        }

        BreakGlassEvent e = new BreakGlassEvent();
        e.setOrganisationId(org);
        e.setActivatedBy(activator);
        e.setActivatedByUsername(activatorUsername);
        e.setRoleId(emergency.getId());
        e.setIncidentRef(incidentRef);
        e.setReason(reason);
        e.setExpiresAt(expires);
        BreakGlassEvent saved = events.save(e);

        // CRITICAL trail: highest severity the audit ledger supports, wired for the
        // CISO/IAM_LEAD notification feed (severity-filtered) — plan §6.6.
        audit.recordEvent(new AuditEventBuilder()
            .actionType("BREAK_GLASS").sourceModule("IAM")
            .entityType("BreakGlassEvent").entityId(saved.getId())
            .entityCode(incidentRef)
            .severity("ERROR")
            .newValueJson("{\"userId\":" + activator + ",\"role\":\"EMERGENCY_RESPONDER\""
                + ",\"expiresAt\":\"" + expires + "\",\"incidentRef\":\"" + incidentRef + "\"}")
            .message("CRITICAL: break-glass activated by " + activatorUsername
                + " for incident " + incidentRef + " (expires " + expires + ", post-hoc review required)"));
        // V294 (plan §6.6) — in-app notification to the CISO + IAM_LEAD populations.
        for (String role : List.of("CISO", "IAM_LEAD")) {
            try {
                notifications.broadcastToRole(org, role,
                    "Break-glass activated: " + incidentRef,
                    "تفعيل وصول الطوارئ: " + incidentRef,
                    activatorUsername + " activated EMERGENCY_RESPONDER until " + expires
                        + ". Post-hoc review is mandatory.",
                    "BREAK_GLASS", "BreakGlassEvent", saved.getId());
            } catch (RuntimeException ex) {
                log.warn("break-glass notification to {} failed: {}", role, ex.getMessage());
            }
        }
        return saved;
    }

    /** Post-hoc review (mandatory, plan §6.6): reviewer ≠ activator; note required. */
    @Transactional
    public BreakGlassEvent review(Long id, String note, Long reviewer) {
        BreakGlassEvent e = events.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
            .orElseThrow(() -> new EntityNotFoundException("Break-glass event not found: " + id));
        if ("REVIEWED".equals(e.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event already reviewed");
        }
        if (reviewer != null && reviewer.equals(e.getActivatedBy())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The activator cannot perform their own post-hoc review");
        }
        e.setReviewStatus("REVIEWED");
        e.setReviewedBy(reviewer);
        e.setReviewedAt(LocalDateTime.now());
        e.setReviewNote(note);
        BreakGlassEvent saved = events.save(e);
        audit.log("UPDATE", "BreakGlassEvent", saved.getId(),
            "Break-glass post-hoc review completed for incident " + saved.getIncidentRef());
        return saved;
    }

    /** Flip the caller's past-due events to EXPIRED (assignment removal is the P1 hourly sweep). */
    @Transactional
    public int expirySweep() {
        return expireAndAudit(org());
    }

    /**
     * DF-1/BL-C1 — scheduler threads carry no ambient transaction, so the {@code @Modifying}
     * expiry update must run inside a per-call REQUIRES_NEW {@link TransactionTemplate}
     * (§16 engine-wiring rule; without it prod threw {@code TransactionRequiredException}
     * every run and expired grants outlived their window). Self-invoking the
     * {@code @Transactional} {@link #expirySweep()} would bypass the proxy — same failure.
     * Org-explicit: each tenant is swept + audited under its own {@link TenantContext} binding.
     */
    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT5M")
    public void scheduledExpirySweep() {
        for (Long org : events.distinctActiveOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(org);
                int n = inNewTransaction(() -> expireAndAudit(org));
                if (n > 0) log.info("break-glass sweep: org {} — {} event(s) expired", org, n);
            } catch (RuntimeException e) {
                log.warn("break-glass expiry sweep failed for org {}: {}", org, e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** One org's expiry pass: the native flip + the CRITICAL-trail audit row (same TX). */
    private int expireAndAudit(Long org) {
        int n = events.expireEventsForOrg(org);
        if (n > 0) {
            audit.recordEvent(new AuditEventBuilder()
                .actionType("ACCESS_REVOKE").sourceModule("IAM").entityType("BreakGlassEvent")
                .message("Break-glass expiry sweep: " + n + " event(s) marked EXPIRED"));
        }
        return n;
    }

    /** New-transaction wrapper for scheduler work (there is no transaction to join off-request). */
    private <T> T inNewTransaction(Supplier<T> work) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tt.execute(status -> work.get());
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }
}
