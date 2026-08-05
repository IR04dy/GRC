package com.tss.grc.module.change_adapters;

import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.service.CommitteeAgendaJson;
import com.tss.grc.module.admin.committee.service.CommitteeGateMapService;
import com.tss.grc.module.admin.committee.service.CommitteeReferralService;
import com.tss.grc.module.admin.committee.service.CommitteeService;
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
    /** CE-13 — meeting reads only (the next sitting). Optional for the same reason as the other two. */
    private final CommitteeService committees;

    public CommitteeAdapter(@Autowired(required = false) CommitteeReferralService referrals,
                            @Autowired(required = false) CommitteeGateMapService gateMap,
                            @Autowired(required = false) CommitteeService committees) {
        this.referrals = referrals;
        this.gateMap = gateMap;
        this.committees = committees;
    }

    // ── referrals ────────────────────────────────────────────────────────────

    @Override
    public Optional<ReferralView> activeReferral(String sourceModule, Long sourceEntityId) {
        if (referrals == null) return Optional.empty();
        return referrals.activeReferral(sourceModule, sourceEntityId).map(CommitteeAdapter::view);
    }

    @Override
    public ReferralView refer(String sourceModule, Long sourceEntityId, String gate, String mode,
                              Long committeeId, String referredBy, String recusalNames,
                              SubjectRef subject) {
        requireEngine();
        // S7b — translate the change-owned SubjectRef into the committee-owned one. The two are
        // field-for-field on purpose and this line is the only place they meet, which is the whole
        // point of the adapter living outside both modules.
        var engineSubject = subject == null
            ? CommitteeReferralService.SubjectRef.NONE
            : new CommitteeReferralService.SubjectRef(
                subject.code(), subject.titleEn(), subject.titleAr());
        return view(referrals.refer(sourceModule, sourceEntityId, gate, mode,
                                    committeeId, referredBy, recusalNames, engineSubject));
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
     * S8f / CE-11 — the non-throwing form, and deliberately implemented by <b>calling the assertion</b>.
     *
     * <p>The obvious alternative is to read the resolution tally here and compare it to
     * {@code minApprovals}. That is precisely the second, weaker answer to "was this quorate?" that
     * this port's javadoc warns about: the engine's assertion also consults the meeting's quorum
     * source and the minutes' approval state, and a local tally would silently disagree with it the
     * first time either changes.
     *
     * <p>The narrow catch is the cost of reusing one answer. {@code IllegalStateException} is the
     * engine-unavailable signal from {@link #requireEngine()} and is <b>not</b> swallowed — a
     * reconciliation that treated "the engine is missing" as "the committee did not approve" would
     * invent findings, which is the opposite failure to the one this method fixes.
     */
    @Override
    public boolean decisionApproves(String sourceModule, Long sourceEntityId, int minApprovals) {
        requireEngine();
        try {
            referrals.assertDecisionApproves(sourceModule, sourceEntityId, minApprovals);
            return true;
        } catch (IllegalStateException engineUnavailable) {
            throw engineUnavailable;
        } catch (RuntimeException notApproved) {
            return false;
        }
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

    // ── CE-13: the queue, and where it will be heard ─────────────────────────

    /**
     * Translation only — the partition is {@code CommitteeReferralService.queue}'s, not this class's.
     *
     * <p>Writing {@code filter(r -> r.sourceCode() != null)} here instead would have been shorter and
     * wrong for the reason the port's javadoc gives: the subject-snapshot rule is a control living in
     * the engine, and a copy of it in the adapter is the mirror that eventually disagrees.
     */
    @Override
    public CommitteeQueue queue(Long committeeId) {
        if (referrals == null) return CommitteeQueue.empty();
        var q = referrals.queue(committeeId);
        return new CommitteeQueue(
            q.stated().stream().map(CommitteeAdapter::view).toList(),
            q.unstated().stream().map(CommitteeAdapter::view).toList());
    }

    @Override
    public Optional<Sitting> nextSitting(Long committeeId) {
        if (committees == null) return Optional.empty();
        return committees.nextSitting(committeeId).map(m -> new Sitting(
            m.getId(), m.getMeetingCode(), m.getTitleEn(), m.getTitleAr(),
            m.getScheduledAt(), m.getStatus(),
            CommitteeAgendaJson.items(m.getAgendaJson()).size(),
            // The references THIS module has tabled — not every line carrying its source module, which
            // counted a charter standing item as a tabled change. Read through the shared agenda reader
            // so the adapter cannot drift from what the builder writes or the agenda pack prints; that
            // drift left 24 of 34 dev agendas blank in one reader and dashes in the other.
            CommitteeAgendaJson.tabledRefs(m.getAgendaJson(), "CHANGE")));
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
            r.getMode(), r.getStatus(), r.getReferredBy(), r.getRecusalNames(), r.getCreatedAt(),
            r.getSourceCode(), r.getSubjectEn(), r.getSubjectAr());
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
