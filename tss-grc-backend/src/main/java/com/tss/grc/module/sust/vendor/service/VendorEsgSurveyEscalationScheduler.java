package com.tss.grc.module.sust.vendor.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.vendor.repository.VendorEsgSurveyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * S-O — vendor ESG survey non-response escalation sweep.
 *
 * <p>Runs daily (just after the {@code 06:00} evidence-expiry sweep) and, for
 * every tenant with surveys, escalates any SENT survey whose response deadline
 * has passed — moving it to ESCALATED and opening a VENDOR_ESG corrective
 * action (see {@link VendorEsgSurveyService#sweepOverdue()}). Idempotent: the
 * S-D auto-CAPA dedup means a re-run never doubles CAPAs.
 *
 * <p>Off-request note: the sweep body runs on a scheduler thread where
 * {@code TenantContext} is unset, so this establishes the tenant explicitly per
 * org before delegating (and clears it in a finally) — mirroring how the
 * evidence-expiry scheduler passes the org id rather than trusting the thread
 * local. {@code AuditLogService} already guards its request-scoped lookups, so
 * the CAPA writes audit cleanly off-request.
 */
@Component
@RequiredArgsConstructor
public class VendorEsgSurveyEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(VendorEsgSurveyEscalationScheduler.class);

    private final VendorEsgSurveyRepository repo;
    private final VendorEsgSurveyService    service;

    /** Daily at 06:15 local — one pass per tenant that has any survey. */
    @Scheduled(cron = "0 15 6 * * *")
    public void runSweep() {
        List<Long> orgs;
        try {
            orgs = repo.findDistinctOrganisationIds();
        } catch (RuntimeException ex) {
            log.warn("Vendor ESG escalation sweep — could not enumerate tenants: {}", ex.getMessage());
            return;
        }
        int totalEscalated = 0;
        for (Long orgId : orgs) {
            try {
                TenantContext.setCurrentTenant(orgId);
                totalEscalated += service.sweepOverdue().size();
            } catch (RuntimeException ex) {
                log.warn("Vendor ESG escalation sweep failed for org {}: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        if (totalEscalated > 0) {
            log.info("Vendor ESG escalation sweep escalated {} non-responding survey(s) across {} tenant(s).",
                totalEscalated, orgs.size());
        }
    }
}
