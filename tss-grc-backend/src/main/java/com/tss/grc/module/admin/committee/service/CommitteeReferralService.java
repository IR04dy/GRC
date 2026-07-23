package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeGateMap;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.repository.CommitteeGateMapRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Track D: shared referral logic used by both Change (CAB/ECAB) and Policy
 * (policy committee) to route an approval THROUGH the committee engine. Keeps
 * the referral model in one committee-domain place so both callers reuse it.
 *
 * <p>S1 is link-only — creating/reading a referral does NOT yet gate anything;
 * the CAB_APPROVED / REVIEW→APPROVED gates are added in later slices, guarded on
 * "a referral exists", so nothing changes until an item is actually referred.
 */
@Service
@RequiredArgsConstructor
public class CommitteeReferralService {

    private final CommitteeReferralRepository referralRepo;
    private final CommitteeGateMapRepository  gateMapRepo;
    private final CommitteeRepository         committeeRepo;

    /**
     * Create (or reuse) the active referral of a source item to its CAB/ECAB
     * committee. {@code committeeId} is optional — when null the per-tenant gate
     * map resolves it (user decision #1). Idempotent: one active referral per item.
     */
    @Transactional
    public CommitteeReferral refer(String sourceModule, Long sourceEntityId, String gate,
                                   String mode, Long committeeId, String referredBy) {
        Long org = TenantContext.getCurrentTenant();

        Optional<CommitteeReferral> existing = referralRepo
            .findByOrganisationIdAndSourceModuleAndSourceEntityId(org, sourceModule, sourceEntityId);
        if (existing.isPresent()) return existing.get();

        Long resolved = committeeId != null ? committeeId
            : gateMapRepo.findByOrganisationIdAndGate(org, gate)
                .map(CommitteeGateMap::getCommitteeId)
                .orElseThrow(() -> new IllegalStateException(
                    "No committee is configured for gate " + gate
                        + " — set the gate map (admin) or pass committeeId explicitly"));

        Committee c = committeeRepo.findByIdAndOrganisationId(resolved, org)
            .orElseThrow(() -> new EntityNotFoundException("Committee " + resolved + " not found in this org"));
        if ("DISSOLVED".equals(c.getStatus()))
            throw new IllegalStateException("Committee " + resolved + " is dissolved");

        CommitteeReferral r = new CommitteeReferral();
        r.setOrganisationId(org);
        r.setSourceModule(sourceModule);
        r.setSourceEntityId(sourceEntityId);
        r.setCommitteeId(resolved);
        r.setGate(gate);
        r.setMode(mode == null || mode.isBlank() ? "APPROVAL" : mode);
        r.setStatus("PENDING");
        r.setReferredBy(referredBy);
        return referralRepo.save(r);
    }

    @Transactional(readOnly = true)
    public Optional<CommitteeReferral> activeReferral(String sourceModule, Long sourceEntityId) {
        return referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(
            TenantContext.getCurrentTenant(), sourceModule, sourceEntityId);
    }
}
