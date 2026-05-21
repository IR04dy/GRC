package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmSettings;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpPlanReviewAttestation;
import com.tss.grc.module.bcm.cycle.repository.BcmSettingsRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanReviewAttestationRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * BCP/DRP plan review attestation service — audit Tier-2 #10.
 *
 * <p>Plan owners attest periodically that the plan is still accurate.
 * Each attestation advances {@code next_review_date} by the configured
 * frequency (from {@link BcmSettings}) and writes a history row.
 *
 * <h3>Frequencies</h3>
 * <p>Critical-tier plans (a BCP/DRP whose source BIA's overall_criticality is
 * {@code CRITICAL} or {@code SOVEREIGN}) follow
 * {@link BcmSettings#getExerciseFrequencyMonthsCritical} (default 6 months);
 * others follow {@link BcmSettings#getExerciseFrequencyMonthsDefault} (12).
 * The same setting drives exercise cadence — review cadence intentionally
 * mirrors it so the regulator sees one consistent rhythm.
 *
 * <h3>States</h3>
 * <p>{@code reviewState} on the plan row is denormalised off
 * {@code next_review_date} relative to today, using a 30-day "due soon"
 * window. It's recomputed on every attestation and by {@link #sweep()},
 * which an admin or scheduler can call to bulk-refresh.
 *
 * <h3>F8 events</h3>
 * <ul>
 *   <li>{@code BCM_PLAN_ATTESTED}        — emitted on every attestation</li>
 *   <li>{@code BCM_PLAN_REVIEW_OVERDUE}  — emitted by sweep() when a plan
 *                                          flips into OVERDUE</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PlanReviewService {

    private static final int DUE_SOON_DAYS  = 30;
    private static final Set<String> OUTCOMES =
        Set.of("STILL_CURRENT", "CHANGES_REQUIRED", "REOPENED_TO_EDIT");

    private final BcpPlanRepository plans;
    private final BcpPlanReviewAttestationRepository attestations;
    private final BcmSettingsRepository settings;
    private final AuditLogService audit;
    private final ApplicationEventPublisher eventPublisher;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── Attestation ───────────────────────────────────────────────

    @Transactional
    public BcpPlanReviewAttestation attest(Long planId, String outcome,
                                           String notes, String byName,
                                           boolean isCritical) {
        Long o = org();
        BcpPlan p = plans.findByIdAndOrganisationId(planId, o)
            .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + planId));
        String out = (outcome == null || outcome.isBlank()) ? "STILL_CURRENT" : outcome.toUpperCase();
        if (!OUTCOMES.contains(out))
            throw new IllegalArgumentException("Invalid outcome: " + out);

        // Resolve the next review window. Default + critical periods come
        // from BcmSettings; if the row hasn't been created yet for this
        // tenant, fall back to (12 / 6).
        int months = monthsForFrequency(o, isCritical);

        LocalDate previousDate = p.getNextReviewDate();
        LocalDate newDate = "STILL_CURRENT".equals(out)
            ? LocalDate.now().plusMonths(months)
            : previousDate;  // keep the existing date — plan needs work first

        // Write history row
        BcpPlanReviewAttestation row = new BcpPlanReviewAttestation();
        row.setOrganisationId(o);
        row.setPlanId(planId);
        row.setPlanVersion(p.getCurrentVersion() == null ? 1 : p.getCurrentVersion());
        row.setAttestedByName(byName == null ? "system" : byName);
        row.setAttestationOutcome(out);
        row.setNotes(notes);
        row.setPreviousNextReviewDate(previousDate);
        row.setNewNextReviewDate(newDate);
        BcpPlanReviewAttestation saved = attestations.save(row);

        // Update the parent
        p.setLastAttestedAt(LocalDateTime.now());
        p.setLastAttestedByName(saved.getAttestedByName());
        p.setNextReviewDate(newDate);
        p.setReviewState(classify(newDate));
        plans.save(p);

        audit.log("ATTEST", "BcpPlan", planId,
            "Plan " + p.getPlanCode() + " attestation: " + out
                + " (next review " + newDate + ")");

        eventPublisher.publishEvent(new DomainEvent(
            o, "BCM_PLAN_ATTESTED", "BcpPlan", planId, null,
            Map.of("planCode", p.getPlanCode(), "outcome", out,
                   "nextReviewDate", String.valueOf(newDate))));

        return saved;
    }

    private int monthsForFrequency(Long organisation, boolean isCritical) {
        return settings.findByOrganisationId(organisation)
            .map(s -> isCritical
                ? defaultIfNull(s.getExerciseFrequencyMonthsCritical(), 6)
                : defaultIfNull(s.getExerciseFrequencyMonthsDefault(),  12))
            .orElse(isCritical ? 6 : 12);
    }

    private static int defaultIfNull(Integer v, int fallback) { return v == null ? fallback : v; }

    // ── History ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BcpPlanReviewAttestation> history(Long planId) {
        return attestations.findByOrganisationIdAndPlanIdOrderByAttestedAtDesc(org(), planId);
    }

    // ── Review queue + sweep ──────────────────────────────────────

    /**
     * Plans whose next_review_date falls in the [today, today+daysAhead] window,
     * or that are already OVERDUE. Used by the UI to drive a "Review queue"
     * KPI / table on the plans page.
     */
    @Transactional(readOnly = true)
    public List<BcpPlan> reviewQueue(int daysAhead) {
        Long o = org();
        LocalDate now = LocalDate.now();
        LocalDate horizon = now.plusDays(Math.max(0, daysAhead));
        return plans.findByOrganisationIdOrderByIdDesc(o, org.springframework.data.domain.PageRequest.of(0, 1000))
            .getContent().stream()
            .filter(p -> p.getNextReviewDate() != null)
            .filter(p -> !p.getNextReviewDate().isAfter(horizon))
            .filter(p -> !"ARCHIVED".equals(p.getStatus())
                       && !"CANCELLED".equals(p.getStatus())
                       && !"SUPERSEDED".equals(p.getStatus()))
            .sorted(Comparator.comparing(BcpPlan::getNextReviewDate))
            .toList();
    }

    /**
     * Bulk-recompute reviewState. Any plan that transitions into OVERDUE for
     * the first time emits a BCM_PLAN_REVIEW_OVERDUE event so the F8 engine
     * can fan out the configured escalation. Returns counts per bucket.
     */
    @Transactional
    public SweepResult sweep() {
        Long o = org();
        long current = 0, dueSoon = 0, overdue = 0, escalated = 0;
        for (BcpPlan p : plans.findByOrganisationIdOrderByIdDesc(
                o, org.springframework.data.domain.PageRequest.of(0, 10_000)).getContent()) {
            String next = classify(p.getNextReviewDate());
            String prev = p.getReviewState();
            if (!Objects.equals(next, prev)) {
                p.setReviewState(next);
                plans.save(p);
                if ("OVERDUE".equals(next)) {
                    escalated++;
                    eventPublisher.publishEvent(new DomainEvent(
                        o, "BCM_PLAN_REVIEW_OVERDUE", "BcpPlan", p.getId(), null,
                        Map.of("planCode", p.getPlanCode(),
                               "nextReviewDate", String.valueOf(p.getNextReviewDate()))));
                }
            }
            switch (next) {
                case "CURRENT":  current++;  break;
                case "DUE_SOON": dueSoon++;  break;
                case "OVERDUE":  overdue++;  break;
            }
        }
        return new SweepResult(current, dueSoon, overdue, escalated);
    }

    private static String classify(LocalDate next) {
        if (next == null) return "CURRENT";
        LocalDate today = LocalDate.now();
        if (next.isBefore(today)) return "OVERDUE";
        if (next.isBefore(today.plusDays(DUE_SOON_DAYS))) return "DUE_SOON";
        return "CURRENT";
    }

    public record SweepResult(long current, long dueSoon, long overdue, long flippedToOverdue) {}
}
