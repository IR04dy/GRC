package com.tss.grc.module.change_adapters;

import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.service.CommitteeGateMapService;
import com.tss.grc.module.admin.committee.service.CommitteeReferralService;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S5 / CE-1 — {@link CommitteePort} over the {@code admin.committee} engine.
 *
 * <p>This is the only class in the platform that imports both sides. Everything it does is
 * translation: no quorum arithmetic, no tally re-derivation, no decision logic. If a method here
 * ever grows a rule, that rule has escaped the engine and the platform is on its way to two
 * answers for "was this decision quorate?" (§6c, and F2-28's surface).
 *
 * <p>The peer services are {@code required = false} because they were optional at the injection
 * sites this replaces — {@code ChangeManagementService} guarded every call with a null check so
 * directly-constructed unit tests could run without them. Preserving that here rather than making
 * them mandatory keeps 49 existing tests constructing the service unchanged; the alternative was to
 * make the null-check the *port's* job and have the adapter's absence look identical to a
 * committee-less deployment, which is a distinction the change module should be able to make.
 */
@Component
public class CommitteeAdapter implements CommitteePort {

    private final CommitteeReferralService referrals;
    private final CommitteeGateMapService gateMap;

    public CommitteeAdapter(@Autowired(required = false) CommitteeReferralService referrals,
                            @Autowired(required = false) CommitteeGateMapService gateMap) {
        this.referrals = referrals;
        this.gateMap = gateMap;
    }

    // ── referrals ────────────────────────────────────────────────────────────

    @Override
    public Optional<ReferralView> activeReferral(String sourceModule, Long sourceEntityId) {
        if (referrals == null) return Optional.empty();
        return referrals.activeReferral(sourceModule, sourceEntityId).map(CommitteeAdapter::view);
    }

    @Override
    public ReferralView refer(String sourceModule, Long sourceEntityId, String gate, String mode,
                              Long committeeId, String referredBy, String recusalNames) {
        requireEngine();
        return view(referrals.refer(sourceModule, sourceEntityId, gate, mode,
                                    committeeId, referredBy, recusalNames));
    }

    @Override
    public ReferralView linkResolution(String sourceModule, Long sourceEntityId, Long resolutionId) {
        requireEngine();
        return view(referrals.linkResolution(sourceModule, sourceEntityId, resolutionId));
    }

    @Override
    public void assertDecisionApproves(String sourceModule, Long sourceEntityId, int minApprovals) {
        requireEngine();
        referrals.assertDecisionApproves(sourceModule, sourceEntityId, minApprovals);
    }

    /**
     * Re-reads the referral before enriching, because the engine's {@code enrich} takes its own
     * entity and the port carries a projection. One extra read on a detail-panel path, and the
     * alternative — widening {@link ReferralView} until it could be converted back — is how a
     * projection turns into a copy of the entity it was meant to decouple from.
     */
    @Override
    public void describe(ReferralView referral, Map<String, Object> out) {
        if (referrals == null || referral == null) return;
        referrals.activeReferral(referral.sourceModule(), referral.sourceEntityId())
                 .ifPresent(entity -> referrals.enrich(entity, out));
    }

    // ── gate map (A17-D2) ────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> gateMap() {
        return gateMap == null ? List.of() : gateMap.current();
    }

    @Override
    public List<Map<String, Object>> eligibleCommittees() {
        return gateMap == null ? List.of() : gateMap.eligibleCommittees();
    }

    @Override
    public Map<String, Object> mapGate(String gate, Long committeeId) {
        requireGateMap();
        return gateMap.upsert(gate, committeeId);
    }

    @Override
    public void unmapGate(String gate) {
        requireGateMap();
        gateMap.clear(gate);
    }

    // ── translation + availability ───────────────────────────────────────────

    private static ReferralView view(CommitteeReferral r) {
        if (r == null) return null;
        return new ReferralView(
            r.getId(), r.getOrganisationId(), r.getSourceModule(), r.getSourceEntityId(),
            r.getCommitteeId(), r.getMeetingId(), r.getResolutionId(), r.getGate(),
            r.getMode(), r.getStatus(), r.getReferredBy(), r.getRecusalNames(), r.getCreatedAt());
    }

    /** A write that needs the engine fails loudly rather than pretending to succeed. */
    private void requireEngine() {
        if (referrals == null)
            throw new IllegalStateException(
                "The committee engine is not available, so this change cannot be referred or decided "
              + "in committee. Referral is how a CAB/ECAB-gated change is authorised (ADR-017 D-2), and "
              + "recording the change as decided without it would assert an authority that never ran.");
    }

    private void requireGateMap() {
        if (gateMap == null)
            throw new IllegalStateException(
                "The committee gate map is not available, so change authority cannot be configured.");
    }
}
