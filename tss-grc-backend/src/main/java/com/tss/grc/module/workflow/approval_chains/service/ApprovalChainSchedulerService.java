package com.tss.grc.module.workflow.approval_chains.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.workflow.approval_chains.entity.ApprovalChainInstance;
import com.tss.grc.module.workflow.approval_chains.repository.ApprovalChainInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sprint 13 — Background sweeper for approval-chain SLA breaches.
 *
 * <p>Off by default in dev — gated by
 * {@code grc.approval-chain.scheduler-enabled} so a developer running
 * the UI doesn't get a chatty DB. Operators flip it on per-env.
 *
 * <p>Algorithm per tick (every 15 min):
 * <ol>
 *   <li>Load in-flight instances whose {@code updatedAt} (or
 *       {@code startedAt} if never updated) is more than the minimum
 *       reasonable SLA window in the past (4 hours — anything sooner
 *       than that can't possibly have breached a sane SLA).</li>
 *   <li>For each candidate, call
 *       {@link ApprovalChainInstanceService#escalate(Long)} which
 *       walks the current step's row(s) and decides if any breach.</li>
 * </ol>
 *
 * <p>Tenant context is set per-instance for the duration of the
 * escalate call, then cleared in a finally block.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalChainSchedulerService {

    private final ApprovalChainInstanceRepository instanceRepo;
    private final ApprovalChainInstanceService instanceService;

    @Value("${grc.approval-chain.scheduler-enabled:false}")
    private boolean schedulerEnabled;

    /**
     * Tick every 15 minutes (default Spring fixedDelay value). Fixed
     * delay so a slow batch doesn't pile up overlapping runs.
     */
    @Scheduled(fixedDelay = 900_000L)
    public void tick() {
        if (!schedulerEnabled) {
            log.trace("Approval-chain SLA scheduler disabled (grc.approval-chain.scheduler-enabled=false). Skipping.");
            return;
        }
        // 4-hour buffer — anything younger than that can't have breached
        // a real SLA (even the shortest CAB-Lead 24h SLA on the material
        // change chain leaves a clear gap).
        LocalDateTime cutoff = LocalDateTime.now().minusHours(4);
        List<ApprovalChainInstance> candidates = instanceRepo.findStaleInstances(cutoff);
        if (candidates.isEmpty()) {
            return;
        }
        int escalated = 0;
        for (ApprovalChainInstance i : candidates) {
            try {
                TenantContext.setCurrentTenant(i.getOrganisationId());
                if (instanceService.escalate(i.getId())) {
                    escalated++;
                }
            } catch (RuntimeException ex) {
                log.warn("Approval-chain SLA escalate failed for instance {} (orgId={}): {}",
                    i.getId(), i.getOrganisationId(), ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        if (escalated > 0) {
            log.info("Approval-chain SLA scheduler escalated {} instance(s) on this tick", escalated);
        }
    }
}
