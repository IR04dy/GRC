package com.tss.grc.module.risk.kri.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.risk.kri.entity.KriDefinition;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Daily KRI evaluation tick — Sprint 13.
 *
 * <p>OFF in every packaged profile, gated by {@code grc.kri.scheduler-enabled}.
 * Declared in {@code application.yml} as a placeholder and named in
 * {@code external.properties.template}, so an operator can turn it on per environment.
 *
 * <p>⚠ This paragraph read "Off by default in dev ... operators flip it on per-env"
 * until F2-462, and both clauses were false: the property was declared in NO packaged yml,
 * so it was off in production too, and there was no placeholder and no template entry for an
 * operator to flip. Enabling it writes to live tenant data, so it stays OFF by default and
 * switching it on is an operator decision taken per environment and recorded.
 *
 * <p>The tick fires at 06:00 daily ({@code 0 0 6 * * *}). For every
 * tenant that has at least one active non-MANUAL KRI, the scheduler
 * sets the tenant context and delegates to
 * {@link KriMeasurementService#evaluateAll(Long)}. Tenant context is
 * cleared in a {@code finally} so the next iteration starts clean.
 *
 * <p>The implementation walks every active KRI cross-tenant once,
 * collects the unique organisation_id set, and processes each
 * tenant in a fresh transactional boundary. For Sprint-13 the
 * evaluation is an orchestration shell — auto-fetchers (SQL runner,
 * metric-API client) ship in a follow-up slice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KriSchedulerService {

    private final KriDefinitionRepository defRepo;
    private final KriMeasurementService measurementService;

    @Value("${grc.kri.scheduler-enabled:false}")
    private boolean schedulerEnabled;

    /**
     * Daily 06:00 server-local time. Matches the CCM engine cadence so
     * an audit committee meeting at 08:00 sees fresh numbers.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void tick() {
        if (!schedulerEnabled) return;
        runEvaluationCycle();
    }

    /**
     * Visible for tests + operators ("run now") — the same loop the
     * scheduled tick invokes.
     */
    int runEvaluationCycle() {
        List<KriDefinition> all = defRepo.findByIsActiveTrueAndIsDeletedFalse();
        if (all.isEmpty()) return 0;

        Set<Long> tenants = new HashSet<>();
        for (KriDefinition d : all) {
            if (d.getOrganisationId() != null) tenants.add(d.getOrganisationId());
        }

        int totalDispatched = 0;
        for (Long orgId : tenants) {
            try {
                TenantContext.setCurrentTenant(orgId);
                totalDispatched += measurementService.evaluateAll(orgId);
            } catch (RuntimeException ex) {
                log.warn("KRI scheduler cycle failed for org {}: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        if (totalDispatched > 0) {
            log.info("KRI scheduler considered {} KRI(s) across {} tenant(s)",
                    totalDispatched, tenants.size());
        }
        return totalDispatched;
    }
}
