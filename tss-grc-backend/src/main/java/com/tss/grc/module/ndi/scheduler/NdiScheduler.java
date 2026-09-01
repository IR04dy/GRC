package com.tss.grc.module.ndi.scheduler;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.evidence.service.NdiEvidenceItemService;
import com.tss.grc.module.ndi.reports.entity.NdiReportDefinition;
import com.tss.grc.module.ndi.reports.repository.NdiReportDefinitionRepository;
import com.tss.grc.module.ndi.reports.service.NdiReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * NDI background scheduler.
 *
 * <p>Two timed jobs:
 * <ul>
 *   <li><b>Evidence expiry sweep</b> — every 6 hours flips ACTIVE
 *       evidence rows past their expiry_date to EXPIRED (slice 7
 *       Rule 6). The mass-flip endpoint is also still callable on
 *       demand from the Evidence tab.</li>
 *   <li><b>Report scheduler</b> — every minute scans saved report
 *       definitions whose {@code scheduleCron} expression's next-fire
 *       window has passed since the last_run_at, runs them, and
 *       stamps the new last_run_*. This is the bare-bones loop that
 *       slice 11 reserved as future-work.</li>
 * </ul>
 *
 * <p>The platform is multi-tenant. The expiry sweep iterates over
 * organisations by reading the distinct {@code organisation_id}s in
 * the report-definition table — same set scopes the expiry job, which
 * is good enough for the demo (and avoids a separate cross-tenant
 * registry). In production a proper org-iteration service would
 * replace this.
 */
@Component
public class NdiScheduler {

    private static final Logger log = LoggerFactory.getLogger(NdiScheduler.class);

    private final NdiEvidenceItemService evidence;
    private final NdiReportService reports;
    private final NdiReportDefinitionRepository reportDefs;

    public NdiScheduler(NdiEvidenceItemService evidence,
                        NdiReportService reports,
                        NdiReportDefinitionRepository reportDefs) {
        this.evidence = evidence;
        this.reports = reports;
        this.reportDefs = reportDefs;
    }

    /** Evidence expiry sweep — runs every 6 hours. */
    @Scheduled(fixedDelayString = "${grc.ndi.evidence-expiry-period-ms:21600000}",
               initialDelayString = "${grc.ndi.evidence-expiry-initial-delay-ms:60000}")
    public void evidenceExpirySweep() {
        for (Long orgId : distinctOrgIds()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                List<Long> flipped = evidence.runExpiryCheck();
                if (!flipped.isEmpty()) {
                    log.info("[NDI scheduler] evidence expiry sweep: org={} flipped {} row(s) to EXPIRED",
                        orgId, flipped.size());
                }
            } catch (Exception ex) {
                log.warn("[NDI scheduler] evidence expiry sweep failed for org={}: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** Report schedule loop — runs every minute. */
    @Scheduled(fixedDelayString = "${grc.ndi.report-scheduler-period-ms:60000}",
               initialDelayString = "${grc.ndi.report-scheduler-initial-delay-ms:30000}")
    public void reportScheduleSweep() {
        LocalDateTime now = LocalDateTime.now();
        for (NdiReportDefinition def : reportDefs.findAll()) {
            if (def.getScheduleCron() == null || def.getScheduleCron().isBlank()) continue;
            if (!"ACTIVE".equals(def.getStatus())) continue;
            if (Boolean.TRUE.equals(def.getIsDeleted())) continue;
            if (!isDue(def, now)) continue;

            try {
                TenantContext.setCurrentTenant(def.getOrganisationId());
                NdiReportService.ReportResult r = reports.run(def.getId(), null);
                log.info("[NDI scheduler] ran report {} ({}): {} rows", def.getReportCode(), def.getReportType(), r.rows().size());
            } catch (Exception ex) {
                log.warn("[NDI scheduler] report {} failed: {}", def.getReportCode(), ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    private boolean isDue(NdiReportDefinition def, LocalDateTime now) {
        try {
            CronExpression cron = CronExpression.parse(def.getScheduleCron());
            // Anchor on last_run_at; if never run, anchor at "an hour ago" so the
            // first matching window catches us.
            LocalDateTime anchor = def.getLastRunAt() == null
                ? now.minus(Duration.ofHours(1))
                : def.getLastRunAt();
            LocalDateTime nextFire = cron.next(anchor);
            return nextFire != null && !nextFire.isAfter(now);
        } catch (IllegalArgumentException ex) {
            log.warn("[NDI scheduler] report {} has invalid cron '{}': {}",
                def.getReportCode(), def.getScheduleCron(), ex.getMessage());
            return false;
        }
    }

    /** Distinct organisations that have an NDI footprint. */
    protected List<Long> distinctOrgIds() {
        return reportDefs.findAll().stream()
            .map(NdiReportDefinition::getOrganisationId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    }
}
