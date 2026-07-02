package com.tss.grc.module.sust.evidence.service;

import com.tss.grc.module.sust.evidence.repository.SustainabilityEvidenceRepository;
import com.tss.grc.module.sust.settings.entity.OrganisationSettings;
import com.tss.grc.module.sust.settings.repository.OrganisationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Rule 9 — sustainability evidence expiry sweep, scheduled on a
 * <b>tenant-configurable cron</b> (S-B).
 *
 * <p>Before S-B the {@code evidence_sweep_cron} setting was dead config:
 * {@link SustainabilityEvidenceService#sweepExpired()} existed but nothing
 * ever called it on a timer. This registers a dynamic {@link Trigger} whose
 * cron expression is re-read from {@link OrganisationSettings} on every
 * scheduling decision, so changing the setting changes the next fire time
 * with no redeploy.
 *
 * <p>Multi-tenant note: Spring drives a single trigger, so the <i>cadence</i>
 * is taken from the primary (lowest-id) tenant's {@code evidenceSweepCron};
 * the sweep <i>body</i> then runs for every org that has a settings row or
 * any APPROVED evidence. Each per-org sweep passes the org id explicitly
 * (never {@code TenantContext}, which is unset off-request).
 */
@Component
@RequiredArgsConstructor
public class SustainabilityEvidenceExpiryScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SustainabilityEvidenceExpiryScheduler.class);

    /** Matches the OrganisationSettings schema default (V65). */
    static final String DEFAULT_CRON = "0 0 6 * * *";

    private final OrganisationSettingsRepository settingsRepo;
    private final SustainabilityEvidenceRepository evidenceRepo;
    private final SustainabilityEvidenceService evidenceService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(this::runSweep, this::nextExecution);
    }

    // ── Trigger — recomputes from the live setting each time ───────────
    private Instant nextExecution(TriggerContext ctx) {
        Instant last = ctx.lastCompletion();
        ZonedDateTime from = (last != null ? last : Instant.now()).atZone(ZoneId.systemDefault());
        return nextFire(resolveCron(), from).toInstant();
    }

    /**
     * Next fire time for {@code cron} after {@code from}. Falls back to the
     * schema default when the configured expression is missing or invalid,
     * so a bad setting can never silently disable the sweep. Package-visible
     * + static so it can be unit-tested without a Spring context.
     */
    static ZonedDateTime nextFire(String cron, ZonedDateTime from) {
        try {
            ZonedDateTime next = CronExpression.parse(cron).next(from);
            if (next != null) return next;
        } catch (RuntimeException ex) {
            log.warn("Invalid evidence_sweep_cron '{}' — falling back to default '{}': {}",
                cron, DEFAULT_CRON, ex.getMessage());
        }
        return CronExpression.parse(DEFAULT_CRON).next(from);
    }

    /** Cadence is driven by the primary (lowest-id) tenant's configured cron. */
    private String resolveCron() {
        try {
            return settingsRepo.findAll().stream()
                .min(Comparator.comparing(OrganisationSettings::getOrganisationId))
                .map(OrganisationSettings::getEvidenceSweepCron)
                .filter(c -> c != null && !c.isBlank())
                .orElse(DEFAULT_CRON);
        } catch (RuntimeException ex) {
            log.warn("Could not resolve evidence_sweep_cron — using default: {}", ex.getMessage());
            return DEFAULT_CRON;
        }
    }

    // ── Task — sweep every tenant ──────────────────────────────────────
    private void runSweep() {
        Set<Long> orgs = new LinkedHashSet<>();
        try {
            settingsRepo.findAll().forEach(s -> orgs.add(s.getOrganisationId()));
            orgs.addAll(evidenceRepo.findDistinctOrganisationIdsWithApprovedEvidence());
        } catch (RuntimeException ex) {
            log.warn("Evidence expiry sweep — could not enumerate tenants: {}", ex.getMessage());
            return;
        }
        int totalExpired = 0;
        for (Long orgId : orgs) {
            try {
                totalExpired += evidenceService.sweepExpiredForOrg(orgId).size();
            } catch (RuntimeException ex) {
                log.warn("Evidence expiry sweep failed for org {}: {}", orgId, ex.getMessage());
            }
        }
        if (totalExpired > 0) {
            log.info("Sustainability evidence expiry sweep marked {} row(s) EXPIRED across {} tenant(s).",
                totalExpired, orgs.size());
        }
    }
}
