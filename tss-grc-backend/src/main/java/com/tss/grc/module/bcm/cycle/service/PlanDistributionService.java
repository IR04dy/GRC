package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpPlanAcknowledgement;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanAcknowledgementRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import com.tss.grc.common.exception.DomainAccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Plan distribution + acknowledgement service — audit Tier-2 #11.
 *
 * <p>Tracks which recipients received which version of a BCP/DRP, and whether
 * they acknowledged it. The audit trail (F7 row per state change) is the
 * compliance artefact ISO 22301 §7.4 and SAMA BCM §6.4 expect.
 *
 * <h3>Workflow</h3>
 * <ol>
 *   <li>{@link #distribute} — author lists recipients. Service creates PENDING
 *       rows for the plan's current version. If a recipient already has a
 *       PENDING/DECLINED row for this same version, that row is reset rather
 *       than duplicated.</li>
 *   <li>{@link #acknowledge} — recipient confirms; row → ACKNOWLEDGED.</li>
 *   <li>{@link #decline}   — recipient pushes back; row → DECLINED.</li>
 *   <li>{@link #markStale} — invoked when a plan's currentVersion advances;
 *       all rows pointing at lower versions are flipped to STALE so the
 *       UI can prompt for re-distribution.</li>
 * </ol>
 *
 * <p>Stats are computed inline (no separate snapshot table) — the row count is
 * always small enough (recipients per plan typically &lt; 50) that aggregation
 * at request time is cheap.
 */
@Service
@RequiredArgsConstructor
public class PlanDistributionService {

    private static final Set<String> CHANNELS =
        Set.of("EMAIL", "PORTAL", "IN_PERSON", "PRINT", "OTHER");
    private static final Set<String> ACK_TARGETS =
        Set.of("ACKNOWLEDGED", "DECLINED");

    private final BcpPlanAcknowledgementRepository acks;
    private final BcpPlanRepository plans;
    private final AuditLogService audit;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Distribution ──────────────────────────────────────────────

    /**
     * Distribute {@code planId} to the supplied recipients at the plan's
     * current version. Existing rows for the same (plan, version, recipient)
     * are reset to PENDING; new recipients get a fresh row.
     */
    @Transactional
    public List<BcpPlanAcknowledgement> distribute(Long planId, List<Recipient> recipients,
                                                   String channel, String distributedByName,
                                                   String notes) {
        Long o = org();
        BcpPlan p = plans.findByIdAndOrganisationId(planId, o)
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planId));
        if (recipients == null || recipients.isEmpty())
            throw new IllegalArgumentException("recipients required");
        String ch = (channel == null || channel.isBlank()) ? "EMAIL" : channel.toUpperCase();
        if (!CHANNELS.contains(ch))
            throw new IllegalArgumentException("Invalid distribution channel: " + ch);

        int version = p.getCurrentVersion() == null ? 1 : p.getCurrentVersion();
        List<BcpPlanAcknowledgement> out = new ArrayList<>(recipients.size());
        LocalDateTime now = LocalDateTime.now();

        for (Recipient r : recipients) {
            if (r.name == null || r.name.isBlank())
                throw new IllegalArgumentException("recipient name required");
            // Find an existing row for this (plan, version, recipient) — match
            // on user_id first, falling back to email so external recipients
            // without a user account still dedupe correctly.
            BcpPlanAcknowledgement existing = acks
                .findByOrganisationIdAndPlanIdAndPlanVersionOrderByDistributedAtDesc(o, planId, version)
                .stream()
                .filter(a -> sameRecipient(a, r))
                .findFirst()
                .orElseGet(BcpPlanAcknowledgement::new);

            existing.setOrganisationId(o);
            existing.setPlanId(planId);
            existing.setPlanVersion(version);
            existing.setRecipientUserId(r.userId);
            existing.setRecipientName(r.name);
            existing.setRecipientEmail(r.email);
            existing.setRecipientRole(r.role);
            existing.setDistributedAt(now);
            existing.setDistributedByName(distributedByName);
            existing.setDistributionChannel(ch);
            existing.setDistributionNotes(notes);
            existing.setStatus("PENDING");
            existing.setAcknowledgedAt(null);
            existing.setAcknowledgementNotes(null);
            existing.setUpdatedAt(now);

            BcpPlanAcknowledgement saved = acks.save(existing);
            audit.log("DISTRIBUTE", "BcpPlanAcknowledgement", saved.getId(),
                "Plan " + p.getPlanCode() + " v" + version + " distributed to "
                    + r.name + " via " + ch);
            out.add(saved);
        }
        return out;
    }

    // ── Acknowledgement ───────────────────────────────────────────

    /** Recipient confirms receipt + acceptance of the version they were sent. */
    @Transactional
    public BcpPlanAcknowledgement acknowledge(Long ackId, String notes, String byName,
                                              Long actorUserId, boolean canActOnBehalf) {
        return transition(ackId, "ACKNOWLEDGED", notes, byName, actorUserId, canActOnBehalf);
    }

    /** Recipient pushes back — still evidence-worthy, ties up the row. */
    @Transactional
    public BcpPlanAcknowledgement decline(Long ackId, String notes, String byName,
                                          Long actorUserId, boolean canActOnBehalf) {
        return transition(ackId, "DECLINED", notes, byName, actorUserId, canActOnBehalf);
    }

    private BcpPlanAcknowledgement transition(Long ackId, String to, String notes, String byName,
                                              Long actorUserId, boolean canActOnBehalf) {
        if (!ACK_TARGETS.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        BcpPlanAcknowledgement row = acks.findByIdAndOrganisationId(ackId, org())
            .orElseThrow(() -> new EntityNotFoundException("Acknowledgement not found: " + ackId));

        // OWNERSHIP: an acknowledgement is BCM compliance evidence that a NAMED
        // recipient received and accepted a specific plan version. Previously this
        // method scoped by organisation only, so any authenticated user in the org
        // could acknowledge or decline somebody else's distribution and the row would
        // read as that recipient's acceptance (only the audit line named the real
        // clicker). A permission cannot express this — BCM_READ is held by 17 of 57
        // roles while plan recipients are ordinary staff — so it is an identity check:
        // you may only act on your own row, unless you hold BCM WRITE, in which case
        // it is recorded explicitly as acting on behalf of the recipient.
        if (!canActOnBehalf && !isDesignatedRecipient(row, actorUserId, byName))
            // F2-80: this message is now DELIVERED, so the recipient's name comes out. It used to
            // interpolate row.getRecipientName(), safe only while handleAccessDenied discarded it.
            // The caller learns the rule and the remedy without learning who the recipient is.
            throw new DomainAccessDeniedException(
                "Only the designated recipient may " + to.toLowerCase()
                    + " this plan distribution. Someone holding BCM write access can record the "
                    + "action on their behalf.");
        boolean onBehalf = !isDesignatedRecipient(row, actorUserId, byName);

        // Pre-existing rule, kept: a superseded distribution cannot be acted on.
        // Ordered AFTER the ownership check so an unauthorised caller learns nothing
        // about the row's state.
        if ("STALE".equals(row.getStatus()))
            throw new IllegalStateException("This distribution is stale — request a fresh copy.");

        row.setStatus(to);
        row.setAcknowledgedAt(LocalDateTime.now());
        row.setAcknowledgementNotes(notes);
        row.setUpdatedAt(LocalDateTime.now());
        BcpPlanAcknowledgement saved = acks.save(row);
        audit.log(to, "BcpPlanAcknowledgement", saved.getId(),
            "Plan v" + saved.getPlanVersion() + " " + to.toLowerCase()
                + " by " + (byName == null ? saved.getRecipientName() : byName)
                + (onBehalf ? " ON BEHALF OF " + saved.getRecipientName() : ""));
        return saved;
    }

    /**
     * True when the caller is the row's designated recipient. Prefers the FK
     * ({@code recipientUserId}); falls back to a name/e-mail match for rows
     * distributed before the user link was captured, so legacy distributions stay
     * actionable by the person actually named on them.
     */
    private boolean isDesignatedRecipient(BcpPlanAcknowledgement row, Long actorUserId, String byName) {
        if (row.getRecipientUserId() != null && actorUserId != null)
            return row.getRecipientUserId().equals(actorUserId);
        if (byName == null || byName.isBlank()) return false;
        return byName.equalsIgnoreCase(row.getRecipientName())
            || byName.equalsIgnoreCase(row.getRecipientEmail());
    }

    // ── List + stats ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BcpPlanAcknowledgement> listForPlan(Long planId) {
        return acks.findByOrganisationIdAndPlanIdOrderByDistributedAtDesc(org(), planId);
    }

    @Transactional(readOnly = true)
    public DistributionStats statsFor(Long planId) {
        BcpPlan p = plans.findByIdAndOrganisationId(planId, org())
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planId));
        int v = p.getCurrentVersion() == null ? 1 : p.getCurrentVersion();
        Long o = org();
        long pending      = acks.countByOrganisationIdAndPlanIdAndPlanVersionAndStatus(o, planId, v, "PENDING");
        long acknowledged = acks.countByOrganisationIdAndPlanIdAndPlanVersionAndStatus(o, planId, v, "ACKNOWLEDGED");
        long declined     = acks.countByOrganisationIdAndPlanIdAndPlanVersionAndStatus(o, planId, v, "DECLINED");
        long stale        = acks.countByOrganisationIdAndPlanIdAndPlanVersionAndStatus(o, planId, v, "STALE");
        long total = pending + acknowledged + declined + stale;
        double ackPct = total == 0 ? 0 : Math.round((acknowledged * 1000.0) / total) / 10.0;
        return new DistributionStats(v, total, pending, acknowledged, declined, stale, ackPct);
    }

    /**
     * Mark all open acknowledgements pointing at lower-than-current versions as STALE.
     * Call from the plan lifecycle whenever {@code currentVersion} bumps.
     */
    @Transactional
    public int markStale(Long planId) {
        Long o = org();
        BcpPlan p = plans.findByIdAndOrganisationId(planId, o)
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planId));
        int v = p.getCurrentVersion() == null ? 1 : p.getCurrentVersion();
        int n = acks.markStaleBelow(o, planId, v);
        if (n > 0) {
            audit.log("STALE", "BcpPlanAcknowledgement", null,
                "Marked " + n + " acks stale on plan " + p.getPlanCode() + " (now v" + v + ")");
        }
        return n;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static boolean sameRecipient(BcpPlanAcknowledgement a, Recipient r) {
        if (r.userId != null && Objects.equals(a.getRecipientUserId(), r.userId)) return true;
        if (r.email != null && r.email.equalsIgnoreCase(a.getRecipientEmail())) return true;
        return false;
    }

    public record Recipient(Long userId, String name, String email, String role) {}

    public record DistributionStats(
        int    planVersion,
        long   total,
        long   pending,
        long   acknowledged,
        long   declined,
        long   stale,
        double acknowledgedPercent
    ) {}
}
