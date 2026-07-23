package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeGateMap;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.repository.CommitteeGateMapRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
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
 * <p>S1 is link-only. S2 adds recusal-name capture + linking the deciding
 * resolution; the CAB_APPROVED / REVIEW→APPROVED gates (S3/S5) then read this,
 * guarded on "a referral exists" so non-referred items are unaffected.
 */
@Service
@RequiredArgsConstructor
public class CommitteeReferralService {

    private final CommitteeReferralRepository  referralRepo;
    private final CommitteeGateMapRepository   gateMapRepo;
    private final CommitteeRepository          committeeRepo;
    private final CommitteeResolutionRepository resolutionRepo;

    /**
     * Create (or reuse) the active referral of a source item to its CAB/ECAB
     * committee. {@code committeeId} is optional — when null the per-tenant gate
     * map resolves it (user decision #1). {@code recusalNames} is a CSV of the
     * requester/author names to force-RECUSE from the vote. Idempotent: one
     * active referral per item.
     */
    @Transactional
    public CommitteeReferral refer(String sourceModule, Long sourceEntityId, String gate,
                                   String mode, Long committeeId, String referredBy, String recusalNames) {
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
        r.setRecusalNames(recusalNames);
        return referralRepo.save(r);
    }

    /**
     * Bind the committee resolution that will decide this referred item. Sets the
     * referral's resolution + meeting and marks it SCHEDULED. The resolution must
     * belong to the referral's committee.
     */
    @Transactional
    public CommitteeReferral linkResolution(String sourceModule, Long sourceEntityId, Long resolutionId) {
        Long org = TenantContext.getCurrentTenant();
        CommitteeReferral ref = referralRepo
            .findByOrganisationIdAndSourceModuleAndSourceEntityId(org, sourceModule, sourceEntityId)
            .orElseThrow(() -> new EntityNotFoundException(
                "No committee referral for " + sourceModule + " " + sourceEntityId));
        CommitteeResolution res = resolutionRepo.findByIdAndOrganisationId(resolutionId, org)
            .orElseThrow(() -> new EntityNotFoundException("Resolution " + resolutionId + " not found in this org"));
        if (ref.getCommitteeId() != null && !ref.getCommitteeId().equals(res.getCommitteeId()))
            throw new IllegalArgumentException("Resolution " + resolutionId + " is not from the referral's committee");
        ref.setResolutionId(resolutionId);
        ref.setMeetingId(res.getMeetingId());
        ref.setStatus("SCHEDULED");
        return referralRepo.save(ref);
    }

    @Transactional(readOnly = true)
    public Optional<CommitteeReferral> activeReferral(String sourceModule, Long sourceEntityId) {
        return referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(
            TenantContext.getCurrentTenant(), sourceModule, sourceEntityId);
    }
}
