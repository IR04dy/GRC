package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeGateMap;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.repository.CommitteeGateMapRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
// F2-113 — an INBOUND read of the change module's SoD vocabulary, which ChangeModuleBoundaryTest
// permits and by design ("peers may read the change module"); the ban is on change_mgmt reaching
// out. ChangeSoD is a static, bean-free rule holder, so this adds no wiring in either direction —
// and it is the alternative to a fourth private copy of "is this the same person?", which is the
// defect class F2-112/F2-113 belong to.
import com.tss.grc.module.change_mgmt.service.ChangeSoD;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
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
    private final CommitteeMeetingRepository    meetingRepo;

    /** F2-113 — the roster, for {@link #recusalCoverage}: a committee with no seat linked to a
     *  platform user cannot be recused by id however well the referred record captured its people. */
    private final CommitteeMemberTermRepository memberTermRepo;

    /**
     * S7b / V350 — what the committee is being asked to decide, as the referring module states it.
     *
     * <p>Carried on the referral rather than joined at agenda-build time: the agenda is built in
     * this module and the item belongs to a peer, so a join would be a reverse dependency ADR-012
     * has no port for. See {@link CommitteeReferral#getSourceCode()}.
     */
    public record SubjectRef(String code, String titleEn, String titleAr) {
        /** For callers that genuinely have nothing to state — the item stays off the agenda. */
        public static final SubjectRef NONE = new SubjectRef(null, null, null);
        public boolean isPresent() { return code != null && !code.isBlank(); }
    }

    /**
     * F2-113 / V358 — who must not cast the deciding vote on the referred item, as the referring
     * module knows them: platform user ids where it captured identity, labels where it has only text.
     *
     * <p><b>Why a type and not two String parameters.</b> This replaced a positional
     * {@code String recusalNames}, and adding {@code String recusalUserIds} beside it would have put
     * two adjacent same-typed parameters into an eight-argument method — transposable at every call
     * site, and transposing them writes names into the id column, where they parse as nothing and
     * the gate goes quiet again. {@code List<Long>} and {@code List<String>} cannot be swapped by
     * accident, and the compiler now forces every existing caller to say which it has.
     *
     * <p>Ids and names are deliberately <b>unpaired</b> — see
     * {@link CommitteeReferral#getRecusalUserIds()}. Both are read; neither supersedes the other.
     */
    public record Recusal(List<Long> userIds, List<String> names) {

        /** For callers that genuinely bar nobody. */
        public static final Recusal NONE = new Recusal(List.of(), List.of());

        public Recusal {
            userIds = userIds == null ? List.of()
                : userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
            names = names == null ? List.of()
                : names.stream().filter(n -> n != null && !n.isBlank())
                    .map(String::trim).distinct().toList();
        }

        /** A caller that has labels and no captured identity — the pre-CE-15 world. */
        public static Recusal byName(String... names) {
            return new Recusal(List.of(), java.util.Arrays.asList(names));
        }

        public boolean isPresent() { return !userIds.isEmpty() || !names.isEmpty(); }

        String idCsv()   { return csv(userIds.stream().map(String::valueOf).toList()); }
        String nameCsv() { return csv(names); }

        private static String csv(List<String> parts) {
            return parts.isEmpty() ? null : String.join(",", parts);
        }
    }

    /**
     * Create (or reuse) the active referral of a source item to its CAB/ECAB
     * committee. {@code committeeId} is optional — when null the per-tenant gate
     * map resolves it (user decision #1). {@code recusal} names the requester/author
     * to force-RECUSE from the vote — by user id where the referring module captured
     * one, by label where it did not. Idempotent: one active referral per item.
     *
     * <p><b>{@code subject} is a required parameter, not an overload.</b> A referral with no
     * subject never reaches an agenda (see {@link CommitteeReferral#getSourceCode()}), so an
     * optional variant would let a caller silently produce an invisible item — which is the
     * defect S7b exists to close. Callers with nothing to state pass {@link SubjectRef#NONE}
     * explicitly and the omission is then a decision on the record.
     */
    @Transactional
    public CommitteeReferral refer(String sourceModule, Long sourceEntityId, String gate,
                                   String mode, Long committeeId, String referredBy, Recusal recusal,
                                   SubjectRef subject) {
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
        Recusal rec = recusal == null ? Recusal.NONE : recusal;
        r.setRecusalNames(rec.nameCsv());
        r.setRecusalUserIds(rec.idCsv());
        if (subject != null) {
            r.setSourceCode(blankToNull(subject.code()));
            r.setSubjectEn(blankToNull(subject.titleEn()));
            r.setSubjectAr(blankToNull(subject.titleAr()));
        }
        return referralRepo.save(r);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * S7b / CE-4 — the items this committee has been asked to decide and has not decided yet.
     * This is the CAB queue, as the agenda builder needs it.
     *
     * <p><b>Only referrals carrying a {@code sourceCode} are returned, and that is the control.</b>
     * Measured 2026-08-03: dev's only two {@code PENDING} referrals point at change ids 101 and 102,
     * and no change request with either id exists in <em>any</em> organisation — orphans left when
     * their changes were deleted, and nothing detects or clears them. A queue keyed on
     * {@code status = 'PENDING'} alone would have put two non-existent items on an agenda while
     * omitting all three real ones: wrong in both directions, F2-96's shape. Requiring the snapshot
     * excludes them for a stated reason instead of by luck.
     *
     * <p>{@code DECIDED} and {@code WITHDRAWN} are excluded — the committee has finished with them.
     */
    @Transactional(readOnly = true)
    public List<CommitteeReferral> awaitingDecision(Long committeeId) {
        return queue(committeeId).stated();
    }

    /** The open statuses — a referral in either is still the committee's to decide. */
    public static final List<String> OPEN_STATUSES = List.of("PENDING", "SCHEDULED");

    /**
     * CE-13 — the same query as {@link #awaitingDecision}, partitioned rather than filtered.
     *
     * <p><b>Why this exists.</b> {@code awaitingDecision} drops the referrals with no subject snapshot
     * because putting them on an agenda would table an item nobody can read (see its javadoc). That is
     * the right call for the agenda and the wrong call for a workspace: dropped silently, the two dev
     * orphans are excluded from the agenda for a stated reason and reported to <em>nobody</em>, so the
     * queue looks empty when it is in fact two rows of unfinished business. The CAB workspace shows
     * both partitions with different remedies — table it, or clear it.
     *
     * <p><b>And why the partition, not a second finder.</b> The subject-snapshot predicate is the
     * control; a second query applying "the same" rule is a mirror maintained by assertion, and it
     * lies the first time one side changes. There is one query, one predicate, and
     * {@code awaitingDecision} is now a view over this method rather than a peer of it.
     */
    @Transactional(readOnly = true)
    public Queue queue(Long committeeId) {
        if (committeeId == null) return new Queue(List.of(), List.of());
        List<CommitteeReferral> open = referralRepo
            .findByOrganisationIdAndCommitteeIdAndStatusInOrderByIdAsc(
                TenantContext.getCurrentTenant(), committeeId, OPEN_STATUSES);
        List<CommitteeReferral> stated = open.stream().filter(CommitteeReferralService::states).toList();
        List<CommitteeReferral> unstated = open.stream().filter(r -> !states(r)).toList();
        return new Queue(stated, unstated);
    }

    /** Carries a V350 subject snapshot, and so can be named on an agenda. */
    private static boolean states(CommitteeReferral r) {
        return r.getSourceCode() != null && !r.getSourceCode().isBlank();
    }

    /**
     * A committee's open queue, split by whether the item can be named.
     *
     * @param stated   referrals carrying a V350 subject snapshot — agenda-ready
     * @param unstated referrals with no snapshot: real rows, unnameable, and invisible until CE-13
     */
    public record Queue(List<CommitteeReferral> stated, List<CommitteeReferral> unstated) {
        public int total() { return stated.size() + unstated.size(); }
    }

    /**
     * F2-113 — how much of the force-recusal gate's population the gate can actually decide.
     *
     * <p><b>Why the engine computes this and not the referring module.</b> The gate is blind for two
     * independent reasons, and no single module can see both: the referral may carry no captured
     * identity for the item's people, <em>or</em> the deciding committee may have no seat linked to a
     * platform user. Reporting only the first — the natural thing to do from {@code change_mgmt},
     * which owns {@code requested_by_id} — would have read as full coverage the moment CE-15's
     * changes accumulated, while 29 of dev's 42 active seats stayed uncomparable. That is F2-112's
     * own shape reproduced inside the instrument built to detect it. Both facts live here, so the
     * count is taken here and handed back through {@code CommitteePort}.
     *
     * <p><b>The buckets, precisely.</b> {@link ChangeSoD.Coverage} has three and this gate has four
     * states, so one bucket carries two:
     * <ul>
     *   <li>{@code checkable} — the referral carries at least one user id <b>and</b> its committee has
     *       at least one linked active seat. The id comparison can fire.</li>
     *   <li>{@code labelOnly} — something is recorded and only a <em>name</em> comparison can fire.
     *       This is the bucket's documented meaning ("the gate is UNVERIFIABLE"), so it also holds the
     *       referral that carries ids nobody on its committee can be matched against.</li>
     *   <li>{@code absent} — nothing is recorded to compare. Measured 2026-08-05: <b>4 of 4</b> live
     *       referrals, because the CSV builder produced blank from two NULL labels. The gate was not
     *       weakly enforced; it was unarmed.</li>
     * </ul>
     *
     * <p>The population is {@link #OPEN_STATUSES} — the engine's own definition of "still the
     * committee's to decide", reused rather than restated, because a referral it has finished with
     * cannot be re-gated and counting it would report coverage the gate will never be asked for.
     */
    @Transactional(readOnly = true)
    public ChangeSoD.Coverage recusalCoverage(String sourceModule) {
        Long org = TenantContext.getCurrentTenant();
        List<CommitteeReferral> open = referralRepo
            .findByOrganisationIdAndSourceModuleAndStatusInOrderByIdAsc(org, sourceModule, OPEN_STATUSES);

        Map<Long, Boolean> committeeHasLinkedSeat = new java.util.HashMap<>();
        long checkable = 0, labelOnly = 0, absent = 0;
        for (CommitteeReferral r : open) {
            boolean hasIds   = notBlank(r.getRecusalUserIds());
            boolean recorded = hasIds || notBlank(r.getRecusalNames());
            boolean byId = hasIds && committeeHasLinkedSeat.computeIfAbsent(
                r.getCommitteeId(),
                // findActiveByCommittee, not a COUNT query: the active-term predicate is the
                // roster's and a second copy of it here would be a mirror maintained by assertion.
                cid -> memberTermRepo.findActiveByCommittee(org, cid).stream()
                    .anyMatch(t -> t.getMemberUserId() != null));
            if (byId) checkable++;
            else if (recorded) labelOnly++;
            else absent++;
        }
        return new ChangeSoD.Coverage(ChangeSoD.GATE_COMMITTEE_VOTE_NOT_REQUESTER,
            open.size(), checkable, labelOnly, absent);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

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

    /**
     * S3 gate: assert the committee has APPROVED this referred item — its linked
     * resolution carried {@code outcome=APPROVED} on quorate (MINUTES_APPROVED)
     * minutes with at least {@code minApprovals} FOR votes. Throws (→409)
     * otherwise. Read-only: the caller advances its own state on success.
     */
    @Transactional(readOnly = true)
    public void assertDecisionApproves(String sourceModule, Long sourceEntityId, int minApprovals) {
        Long org = TenantContext.getCurrentTenant();
        CommitteeReferral ref = referralRepo
            .findByOrganisationIdAndSourceModuleAndSourceEntityId(org, sourceModule, sourceEntityId)
            .orElseThrow(() -> new IllegalStateException("No committee referral to gate on"));
        if (ref.getResolutionId() == null)
            throw new IllegalStateException("The committee motion has not been raised and linked yet");
        CommitteeResolution res = resolutionRepo.findByIdAndOrganisationId(ref.getResolutionId(), org)
            .orElseThrow(() -> new IllegalStateException("The linked committee resolution is missing"));
        // S7a — the decision mode this gate enforces is stated in CommitteeDecisionAuthority:
        // meeting-bound minutes, and it is the ONLY representable mode because meeting_id is
        // NOT NULL. ⚠ The `!= null` below therefore cannot be false. It is kept because a
        // missing meeting must fail closed rather than skip the gate — but read quickly it
        // looks like a documented exemption for asynchronous voting, and it is not one. If
        // asynchronous voting is ever built, the exemption has to be argued and gated, not
        // inherited from this null check.
        if (res.getMeetingId() != null) {
            CommitteeMeeting mtg = meetingRepo.findByIdAndOrganisationId(res.getMeetingId(), org)
                .orElseThrow(() -> new IllegalStateException("The linked committee meeting is missing"));
            if (!CommitteeDecisionAuthority.MINUTES_APPROVED.equals(mtg.getStatus()))
                throw new IllegalStateException(
                    "Committee minutes are not approved yet (meeting is " + mtg.getStatus() + ")");
        }
        // F2-89 — "not decided yet" and "decided against" are different answers and the refusal
        // must not conflate them. Before V349 the vocabulary had no undecided value, so a motion
        // awaiting a vote had to carry some verdict and this message reported it as one: a
        // raised motion read as "the committee did not approve this item", sending the requester
        // to argue their case when what was needed was for the committee to vote. Same rule as
        // S6c's quorum refusal — a message naming the wrong remedy costs someone a day.
        if (!"APPROVED".equals(res.getOutcome()))
            throw new IllegalStateException(
                CommitteeDecisionAuthority.isDecided(res)
                    ? "The committee did not approve this item (resolution outcome is "
                      + res.getOutcome() + ")"
                    : "The committee has not decided this motion yet — it is still "
                      + res.getOutcome() + " and no vote has been counted. It needs to be put to "
                      + "the committee and the minutes approved before this can advance.");
        int forVotes = res.getVoteFor() == null ? 0 : res.getVoteFor();
        if (forVotes < minApprovals)
            throw new IllegalStateException("Committee approval is short of the required "
                + minApprovals + " FOR vote(s) — has " + forVotes);
    }

    @Transactional(readOnly = true)
    public Optional<CommitteeReferral> activeReferral(String sourceModule, Long sourceEntityId) {
        return referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(
            TenantContext.getCurrentTenant(), sourceModule, sourceEntityId);
    }

    /**
     * Enrich a referral view with the linked resolution's outcome + tally and the
     * meeting's status + quorum, so the source module's detail (guarded by its own
     * READ permission) can show the committee decision without the viewer needing
     * committee-admin access.
     */
    @Transactional(readOnly = true)
    public void enrich(CommitteeReferral ref, Map<String, Object> out) {
        Long org = TenantContext.getCurrentTenant();
        if (ref.getResolutionId() != null) {
            resolutionRepo.findByIdAndOrganisationId(ref.getResolutionId(), org).ifPresent(r -> {
                out.put("resolutionOutcome", r.getOutcome());
                out.put("voteFor",     r.getVoteFor());
                out.put("voteAgainst", r.getVoteAgainst());
                out.put("voteAbstain", r.getVoteAbstain());
            });
        }
        if (ref.getMeetingId() != null) {
            meetingRepo.findByIdAndOrganisationId(ref.getMeetingId(), org).ifPresent(m -> {
                out.put("meetingStatus", m.getStatus());
                out.put("quorumMet", m.getQuorumMet() != null && m.getQuorumMet() == 1);
            });
        }
    }
}
