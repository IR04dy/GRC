package com.tss.grc.module.sust.sgi.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.sust.capa.repository.CorrectiveActionRepository;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import com.tss.grc.module.sust.sgi.repository.SgiCommitmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * SGI-B — time-based surveillance over the Saudi Green Initiative register.
 *
 * <p>Progress tier is computed on read and the S-D auto-CAPA fires only inside a
 * create/update, so an <em>untouched</em> commitment silently drifts DELAYED as
 * its {@code expectedPct} grows on the calendar and its next milestone slips —
 * with nothing raising a corrective action or reminding the owner. This sweep
 * closes that gap for every ACTIVE commitment of the tenant:
 * <ul>
 *   <li><b>Behind plan</b> — tier {@code DELAYED} raises an idempotent HIGH
 *       auto-CAPA under {@code (SGI_COMMITMENT, "{id}")} — the <em>same</em>
 *       source marker the on-write path uses, so the two never double up.</li>
 *   <li><b>Milestone overdue</b> — {@code next_milestone_date} is in the past and
 *       the current value has not been refreshed since raises a MEDIUM auto-CAPA
 *       under {@code (SGI_COMMITMENT, "{id}:milestone")} (a ref suffix, so no
 *       {@code ck_ca_source} change) and pings the owner in-app.</li>
 * </ul>
 *
 * <p>Idempotent: a re-run raises nothing new (existence-guarded per source ref),
 * mirroring the G4 trajectory / permit-expiry sweeps. Reused both by the manual
 * endpoint (request thread carries the tenant) and by {@link SgiSurveillanceScheduler}
 * (which establishes the tenant explicitly per org).
 */
@Service
@RequiredArgsConstructor
public class SgiSurveillanceService {

    private static final String SOURCE = "SGI_COMMITMENT";

    private final SgiCommitmentRepository repo;
    private final SgiCommitmentService sgiService;
    private final CorrectiveActionService capaService;
    private final CorrectiveActionRepository capas;
    private final NotificationService notifications;
    private final UserRepository users;

    /** Counts of auto-CAPAs newly raised this pass. */
    public record SweepResult(int delayedCapas, int milestoneCapas) {}

    /**
     * Sweep every ACTIVE commitment of the current tenant. The tenant must be
     * bound to the thread (request-scoped or set by the scheduler).
     */
    @Transactional
    public SweepResult sweep() {
        Long org = TenantContext.getCurrentTenant();
        LocalDate today = LocalDate.now();
        int delayed = 0;
        int milestone = 0;

        for (SgiCommitment c : repo.findByOrganisationIdAndStatus(org, "ACTIVE")) {
            // ── behind plan (DELAYED) ────────────────────────────────────
            String tier = String.valueOf(sgiService.progress(c).get("tier"));
            if ("DELAYED".equals(tier)) {
                String ref = String.valueOf(c.getId());
                if (!capas.existsByOrganisationIdAndSourceTypeAndSourceRef(org, SOURCE, ref)) {
                    capaService.autoOpen(SOURCE, ref,
                        "SGI commitment behind plan: " + c.getCommitmentCode(),
                        "التزام المبادرة الخضراء متأخر عن الخطة: " + c.getCommitmentCode(),
                        "Surveillance sweep (SGI-B): commitment " + c.getCommitmentCode()
                            + " (#" + c.getId() + ") is tracking DELAYED against its linear plan.",
                        "HIGH");
                    delayed++;
                }
            }

            // ── milestone overdue ────────────────────────────────────────
            LocalDate ms = c.getNextMilestoneDate();
            boolean overdue = ms != null && ms.isBefore(today)
                && (c.getCurrentValueAsOf() == null || c.getCurrentValueAsOf().isBefore(ms));
            if (overdue) {
                String ref = c.getId() + ":milestone";
                if (!capas.existsByOrganisationIdAndSourceTypeAndSourceRef(org, SOURCE, ref)) {
                    capaService.autoOpen(SOURCE, ref,
                        "SGI milestone overdue: " + c.getCommitmentCode(),
                        "مرحلة التزام المبادرة الخضراء متأخرة: " + c.getCommitmentCode(),
                        "Surveillance sweep (SGI-B): the " + ms + " milestone for commitment "
                            + c.getCommitmentCode() + " (#" + c.getId()
                            + ") has passed without a refreshed current value.",
                        "MEDIUM");
                    notifyOwner(org, c, ms);
                    milestone++;
                }
            }
        }
        return new SweepResult(delayed, milestone);
    }

    /**
     * Ping the commitment owner in-app about an overdue milestone. The owner is
     * held as a free-text e-mail, so resolve it to an active tenant user; if it
     * maps to none, the auto-CAPA's SUSTAINABILITY_OFFICER role broadcast still
     * covers it. (SMTP delivery is out of scope until a relay exists.)
     */
    private void notifyOwner(Long org, SgiCommitment c, LocalDate ms) {
        if (c.getOwnerEmail() == null || c.getOwnerEmail().isBlank()) return;
        List<User> owners = users.findActiveByOrganisationIdAndEmail(org, c.getOwnerEmail().trim());
        for (User u : owners) {
            notifications.notifyUser(org, u.getId(),
                "SGI milestone overdue",
                "مرحلة التزام المبادرة الخضراء متأخرة",
                "The " + ms + " milestone for your commitment " + c.getCommitmentCode()
                    + " has passed — refresh the current value or reschedule.",
                "SGI_MILESTONE", "SgiCommitment", c.getId());
        }
    }
}
