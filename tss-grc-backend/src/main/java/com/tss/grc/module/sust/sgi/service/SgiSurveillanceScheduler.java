package com.tss.grc.module.sust.sgi.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.sgi.repository.SgiCommitmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SGI-B — daily surveillance sweep of the Saudi Green Initiative register.
 *
 * <p>Runs at 03:00 UTC — after the SoD detective scan window (02:30 UTC) — and,
 * for every tenant that owns any commitment, raises the behind-plan and
 * overdue-milestone auto-CAPAs (see {@link SgiSurveillanceService#sweep()}).
 * Idempotent: a re-run raises nothing new.
 *
 * <p>Off-request note: the sweep body runs on a scheduler thread where
 * {@code TenantContext} is unset, so this establishes the tenant explicitly per
 * org before delegating (and clears it in a finally) — the same pattern as the
 * vendor-ESG escalation and evidence-expiry schedulers. {@code AuditLogService}
 * already guards its request-scoped lookups, so the CAPA writes audit cleanly
 * off-request.
 */
@Component
@RequiredArgsConstructor
public class SgiSurveillanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(SgiSurveillanceScheduler.class);

    private final SgiCommitmentRepository repo;
    private final SgiSurveillanceService service;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void runSweep() {
        List<Long> orgs;
        try {
            orgs = repo.findDistinctOrganisationIds();
        } catch (RuntimeException ex) {
            log.warn("SGI surveillance sweep — could not enumerate tenants: {}", ex.getMessage());
            return;
        }
        int delayed = 0;
        int milestone = 0;
        for (Long orgId : orgs) {
            try {
                TenantContext.setCurrentTenant(orgId);
                SgiSurveillanceService.SweepResult r = service.sweep();
                delayed += r.delayedCapas();
                milestone += r.milestoneCapas();
            } catch (RuntimeException ex) {
                log.warn("SGI surveillance sweep failed for org {}: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        if (delayed > 0 || milestone > 0) {
            log.info("SGI surveillance sweep raised {} behind-plan and {} overdue-milestone auto-CAPA(s) across {} tenant(s).",
                delayed, milestone, orgs.size());
        }
    }
}
