package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingAttendeeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionVoteRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
// F2-113 — an INBOUND read of the change module's SoD vocabulary. ChangeModuleBoundaryTest bans
// change_mgmt reaching OUT to a peer and deliberately permits the reverse ("peers may read the
// change module"); ChangeSoD is a static, bean-free rule holder, so this adds no wiring. The
// alternative was a fourth private copy of "is this the same person?" — which is the defect class
// F2-112 and F2-113 are both instances of.
import com.tss.grc.module.change_mgmt.service.ChangeSoD;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.tss.grc.common.exception.DomainAccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolution lifecycle service. A resolution is created when a motion
 * is carried at a committee meeting — once the meeting closes, the
 * resolution becomes an immutable record.
 *
 * <p>The {@code regulatorNotify} flag drives an F8 rule that pre-fills
 * the regulator-notification queue (CMA disclosure, SAMA material
 * decision, SDAIA personal-data event, etc.).
 *
 * <p>Audit-log + DomainEvent on create. Resolutions don't have a
 * mutable status — the outcome (APPROVED/REJECTED/DEFERRED/WITHDRAWN)
 * is set at creation and is part of the immutable record.
 */
@Service
@RequiredArgsConstructor
public class CommitteeResolutionService {

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(CommitteeResolutionService.class);

    private final CommitteeResolutionRepository repo;
    private final AuditLogService               audit;
    private final ApplicationEventPublisher     publisher;
    private final CommitteeResolutionVoteRepository  voteRepo;      // CH-14
    private final CommitteeMeetingAttendeeRepository attendeeRepo;  // CH-14

    /** DF-3: the committee's active roster — the only source of eligible voters.
     *  A **constructor** dependency on purpose: the sibling {@code referralRepo} below
     *  is {@code @Autowired(required = false)} field injection, and Mockito's
     *  {@code @InjectMocks} selects the generated constructor and then skips property
     *  injection entirely — so a field-injected repository stays null in unit tests and
     *  the branch that uses it silently no-ops. That is exactly how the Track-D recusal
     *  path went untested. A security control must not be able to disappear that way. */
    private final CommitteeMemberTermRepository      memberTermRepo;

    /** F2-100: a decision is binding on approved minutes, so publishing needs the meeting.
     *  A <b>constructor</b> dependency for the reason spelled out on {@code memberTermRepo}
     *  above — a field-injected collaborator stays null under {@code @InjectMocks} and the
     *  branch that uses it silently no-ops, which is precisely how the missing publish went
     *  unnoticed for as long as it did. */
    private final CommitteeMeetingRepository         meetingRepo;

    /** Track D (S2): optional — force-RECUSE a referred item's requester/author
     *  from the deciding vote. Field-injected so directly-constructed unit tests
     *  are unaffected (the recusal simply no-ops when absent). */
    @Autowired(required = false)
    private CommitteeReferralRepository referralRepo;

    private static final java.util.Set<String> VOTE_VALUES =
        java.util.Set.of("FOR", "AGAINST", "ABSTAIN", "RECUSED");

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public Page<CommitteeResolution> list(Pageable pageable) {
        try {
            return repo.findByOrganisationIdOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), pageable);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) {
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public CommitteeResolution get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CommitteeResolution " + id));
    }

    // ── CH-14: per-member resolution voting + recusal/COI ────────────────────

    /**
     * Who is really recording a vote (DF-3 / finding F2-21).
     *
     * <p>The identity comes from the authenticated principal, never from the request
     * body. {@code mayRecordOnBehalf} is true only for a committee secretary/chair
     * authority — the legitimate case where one person minutes a show of hands in the
     * room. It is the difference between "voting" and "recording someone's vote", and
     * the distinction is persisted rather than lost.
     */
    public record VoteActor(Long userId, String username, boolean mayRecordOnBehalf) {
        /** An ordinary member: may cast their own vote and nobody else's. */
        public static VoteActor self(Long userId, String username) {
            return new VoteActor(userId, username, false);
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean sameName(String a, String b) {
        return !norm(a).isEmpty() && norm(a).equalsIgnoreCase(norm(b));
    }

    /**
     * F2-113 — the parties a referral bars from voting, as {@link ChangeSoD} compares them.
     *
     * <p><b>The defect this replaced.</b> The Track-D recusal read {@code recusal_names} only and
     * matched it with {@code sameName} against {@code member_name}. A change's people are team labels
     * ('CloudOps', 'IAM Team', 'AppSec'); a committee's members are people ('Khalid Al-Rashidi').
     * Measured 2026-08-05 in dev: 15 of 15 owner labels and 13 of 13 requester labels resolved to no
     * user, and <b>0 of 4</b> live referrals carried a name at all — so the deciding-vote bar had never
     * engaged for any change in either database. Its tests were green throughout, because they supplied
     * the CSV the production path never produces. The third instance of F2-112's shape.
     *
     * <p><b>Why {@code ChangeSoD} and not a fourth comparison here.</b> There were three copies of
     * "is the acting principal this named party?" before CE-15 extracted them; a fourth, in a
     * different module, is the mirror-maintained-by-assertion problem at its worst — it would lie the
     * first time one copy learned something the others did not, and the lie would be a security
     * control silently disagreeing with itself.
     *
     * <p>⚠ <b>Ids and names are separate parties, one per BAR rather than one per person, and that is
     * load-bearing.</b> A referral is a list of bars — "user 8 may not vote", "anyone called
     * 'CloudOps' may not vote" — not a list of people with two attributes each, which is also all
     * V358's two unpaired CSVs can express. Merging them into {@code Party(id, label)} would look
     * tidier and would re-engage {@code ChangeSoD}'s rule that a party's id supersedes its name,
     * silently retiring the name comparison for the 29 of 42 active seats that carry no
     * {@code member_user_id} — a refusal removed by a change that reads as a cleanup. CE-15 nearly
     * shipped that weakening from the other side and an existing test caught it; here the test is
     * {@code CommitteeVoteRecusalTallyTest.an_unlinked_seat_is_still_recused_by_name_even_though_the_referral_carries_ids}.
     *
     * <p>Unparsable ids are skipped rather than thrown: a malformed CSV must not stop a committee
     * voting, and a gate's inability to compare belongs in the coverage report rather than in a
     * member's face (S6b-i — weakness belongs in the instrument, not in the verdict).
     */
    private static List<ChangeSoD.Party> recusedParties(CommitteeReferral r) {
        List<ChangeSoD.Party> parties = new java.util.ArrayList<>();
        if (r.getRecusalUserIds() != null)
            for (String raw : r.getRecusalUserIds().split(",")) {
                try {
                    parties.add(new ChangeSoD.Party("recusalUserId", Long.valueOf(norm(raw)), null));
                } catch (NumberFormatException notAnId) {
                    log.warn("Referral {} carries an unparsable recusal user id \"{}\" — that party "
                           + "cannot be barred by identity", r.getId(), norm(raw));
                }
            }
        if (r.getRecusalNames() != null)
            for (String nm : r.getRecusalNames().split(","))
                if (!norm(nm).isEmpty())
                    parties.add(new ChangeSoD.Party("recusalName", null, norm(nm)));
        return parties;
    }

    /**
     * Record (or update) one member's vote on a resolution, then re-derive the
     * aggregate tally + outcome.
     *
     * <p><b>DF-3 / F2-21 — actor provenance.</b> This method previously took
     * {@code memberName} straight from the request body and never checked it against
     * the caller or against the committee roster. That single gap defeated four
     * controls at once: COI recusal and the Track-D SoD recusal both compared the
     * <em>claimed</em> name (so a different name evaded them), quorum could be
     * manufactured from one actor by submitting N names, the upsert-by-name allowed
     * overwriting another member's cast vote, and {@code audit.log} recorded the
     * claimed name so the ledger corroborated the forgery. Because minutes are
     * SHA-256 e-signed, the signature then attested to forgeable content.
     *
     * <p>The order of the checks below is the control. Tenant scope first, then
     * identity, then roster membership, then recusal <em>on the resolved identity</em>,
     * then overwrite protection — anything earlier leaking into a later step is how
     * this class of defect returns. {@code ADR-006} requires "immutable authenticated
     * user IDs" and "forged-actor tests"; {@link CommitteeVoteProvenanceTest} is that
     * test, and it fails if any of these guards is removed.
     *
     * <p><b>⚠ F2-113 — and provenance was not enough, because the Track-D recusal had nothing
     * comparable to compare.</b> DF-3 closed the evasion route (the claimed name) and left the
     * comparison itself intact: a CSV of the change's people matched against
     * {@code member_name}. Nobody asked whether those two vocabularies overlap. They do not — a
     * change's people are team labels, a committee's are people — so the bar was unevadable and
     * also unable to fire, for every change in both databases. The recusal below now resolves on
     * the seat's {@code member_user_id} against the referral's {@code recusal_user_ids}, with the
     * name comparison kept as the fallback for the seats that carry no user id. See
     * {@link #recusedParties}, and {@code GET /api/v1/changes/sod-coverage} for how much of the
     * population it can still not decide.
     */
    @Transactional
    public CommitteeResolution recordVote(Long resolutionId, String requestedMember, String voteRaw,
                                          String memberRole, String rationale, VoteActor actor) {
        CommitteeResolution r = get(resolutionId);   // org-scoped — must stay first
        if (actor == null || (actor.userId() == null && norm(actor.username()).isEmpty()))
            throw new DomainAccessDeniedException(
                "A committee vote must be recorded by an identified user.");
        String vote = norm(voteRaw).toUpperCase();
        if (!VOTE_VALUES.contains(vote))
            throw new IllegalArgumentException("vote must be one of " + VOTE_VALUES);

        Long org = TenantContext.getCurrentTenant();

        // ── F2-40: a recorded tally is not a starting point for counted votes ───
        // recomputeTally() below rewrites vote_for/against/abstain from the vote rows, so the
        // first vote cast on a resolution whose tally was asserted from the minutes would
        // replace it — 5/0/0 APPROVED becomes 1/0/0, or 0/0/0 DEFERRED on a RECUSED vote. There
        // is no correct arithmetic that merges the two: the asserted figures count members who
        // have no vote row and never will. Refuse instead of converting, the same stance V319
        // took on an asserted quorum. All 20 pre-V322 resolutions sit behind this guard.
        // The test is "would this vote overwrite something a person recorded", not the label
        // alone: an unlabelled row with a zero tally has nothing to lose and stays votable, which
        // is also how a resolution created empty behaves before its first vote.
        String tallySrc = r.getTallySource();
        boolean asserted = "ASSERTED".equals(tallySrc) || "ASSERTED_LEGACY".equals(tallySrc);
        boolean unlabelledButRecorded = tallySrc == null
            && (zero(r.getVoteFor()) + zero(r.getVoteAgainst()) + zero(r.getVoteAbstain())) > 0;
        if (asserted || unlabelledButRecorded)
            throw new IllegalStateException(
                "Resolution " + r.getResolutionCode() + " carries a tally that was "
              + ("ASSERTED".equals(tallySrc)
                    ? "recorded from the minutes by "
                      + (r.getTallyRecordedByName() == null
                            ? "user#" + r.getTallyRecordedByUserId() : r.getTallyRecordedByName())
                    : "recorded before vote provenance existed")
              + " (" + r.getVoteFor() + "/" + r.getVoteAgainst() + "/" + r.getVoteAbstain()
              + " · " + r.getOutcome() + "), so counting a vote here would overwrite it. "
              + "To decide a motion by in-platform voting, create the resolution with no tally.");

        // ── the committee's active roster is the only source of eligible voters ──
        List<CommitteeMemberTerm> roster = r.getCommitteeId() == null
            ? List.of()
            : memberTermRepo.findActiveByCommittee(org, r.getCommitteeId());

        // The actor's own seat: by linked platform user first (authoritative), then
        // by username. Name matching is the fallback only — memberUserId is the field
        // that cannot be spoofed.
        CommitteeMemberTerm actorSeat = roster.stream()
            .filter(t -> actor.userId() != null && actor.userId().equals(t.getMemberUserId()))
            .findFirst()
            .or(() -> roster.stream()
                .filter(t -> sameName(t.getMemberName(), actor.username()))
                .findFirst())
            .orElse(null);

        // ── resolve WHOSE vote this is ──────────────────────────────────────────
        CommitteeMemberTerm seat;
        if (norm(requestedMember).isEmpty()) {
            // No name supplied = "my own vote". This is the common, safe path.
            seat = actorSeat;
            if (seat == null)
                throw new DomainAccessDeniedException(
                    "You are not an active member of this committee, so you cannot cast a vote. "
                  + "A committee secretary or chair may record a vote on a member's behalf by "
                  + "naming that member.");
        } else {
            String want = norm(requestedMember);
            seat = roster.stream().filter(t -> sameName(t.getMemberName(), want)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "\"" + want + "\" is not an active member of this committee. A vote can only "
                  + "be recorded for a member on the committee's active roster."));
            if (!(actorSeat != null && sameName(actorSeat.getMemberName(), seat.getMemberName()))
                    && !actor.mayRecordOnBehalf())
                // F2-80: this message is now DELIVERED to the caller, so it must not name the
                // member. It used to interpolate seat.getMemberName(), which was harmless only
                // because handleAccessDenied threw the whole string away — an unauthorised caller
                // would otherwise learn a colleague's name from a 403. Name the authority required,
                // never the person holding it.
                throw new DomainAccessDeniedException(
                    "You may only cast your own vote. Recording a vote on another member's "
                  + "behalf requires committee secretary or chair authority.");
        }
        if (seat.getIsVotingMember() != null && seat.getIsVotingMember() == 0)
            throw new IllegalArgumentException(seat.getMemberName()
                + " is a non-voting member of this committee and cannot cast a counted vote.");

        // From here on, `member` is the CANONICAL roster name — never the raw input.
        // This is what closes the recusal-evasion-by-name-variant hole.
        String member = norm(seat.getMemberName());
        boolean onBehalf = actorSeat == null || !sameName(actorSeat.getMemberName(), member);

        // COI forces recusal: a member who declared a conflict on the meeting
        // attendance cannot cast a counted vote.
        if (r.getMeetingId() != null && !"RECUSED".equals(vote)) {
            boolean conflicted = attendeeRepo.findByCommitteeMeetingId(r.getMeetingId()).stream()
                .anyMatch(a -> sameName(member, a.getMemberName())
                    && Boolean.TRUE.equals(a.getCoiDeclared()));
            if (conflicted) vote = "RECUSED";
        }

        // Track D SoD: force-RECUSE the referred item's requester/author (same
        // mechanic as COI) — they cannot cast the deciding committee vote.
        if (!"RECUSED".equals(vote) && referralRepo != null) {
            var refOpt = referralRepo.findByResolutionId(resolutionId);
            if (refOpt.isPresent()
                    && ChangeSoD.compareAny(
                            new ChangeSoD.Actor(seat.getMemberUserId(), member),
                            recusedParties(refOpt.get())).refuses())
                vote = "RECUSED";
        }

        // Vote ownership is already settled above: reaching this line means the actor is
        // either the member themselves or a secretary/chair with on-behalf authority, so
        // the upsert cannot touch a row that is not theirs to change.
        //
        // An earlier revision added a second check here — "refuse if a DIFFERENT user
        // recorded this row" — and mutation testing killed it: removing it broke no test,
        // because the on-behalf gate already denies every harmful path. Worse, it was
        // actively wrong in the one path it *did* reach. When a secretary records Ali's
        // vote on his behalf and Ali then votes himself, the recorder differs from the
        // actor and Ali lacks on-behalf authority — so Ali would have been blocked from
        // correcting his own vote. The member is the authority on their own vote; the
        // change is audited and stamped either way.
        CommitteeResolutionVote v = voteRepo.findByResolutionIdAndMemberName(resolutionId, member)
            .orElseGet(CommitteeResolutionVote::new);

        v.setOrganisationId(org);
        v.setResolutionId(resolutionId);
        v.setMeetingId(r.getMeetingId());
        v.setMemberName(member);
        v.setMemberRole(memberRole);
        v.setVote(vote);
        v.setRationale(rationale);
        v.setRecordedByUserId(actor.userId());
        v.setRecordedByName(actor.username());
        v.setOnBehalf(onBehalf);
        voteRepo.save(v);

        recomputeTally(r);
        String actorLabel = norm(actor.username()).isEmpty()
            ? ("user#" + actor.userId()) : actor.username();
        audit.log("RESOLUTION_VOTE", "CommitteeResolution", resolutionId,
            member + " voted " + vote
                + (onBehalf ? " (recorded by " + actorLabel + " ON BEHALF OF " + member + ")"
                            : " (recorded by " + actorLabel + ")")
                + " -> tally " + r.getVoteFor() + "/" + r.getVoteAgainst()
                + "/" + r.getVoteAbstain() + " (" + r.getOutcome() + ")");
        return r;
    }

    @Transactional(readOnly = true)
    public List<CommitteeResolutionVote> votes(Long resolutionId) {
        get(resolutionId);   // org-scope guard
        return voteRepo.findByResolutionId(resolutionId);
    }

    /** Re-derive vote_for/against/abstain (RECUSED excluded) + outcome from the
     *  per-member votes and persist onto the resolution.
     *
     *  <p>F2-40 — this method <b>overwrites</b> the tally, and with zero vote rows that means
     *  {@code 0/0/0 -> DEFERRED}. Before V322 it could therefore rewrite a recorded 5/0/0
     *  APPROVED board decision to 1/0/0 on the first vote cast, or to 0/0/0 DEFERRED on a
     *  RECUSED one, silently. {@link #recordVote} now refuses to reach here on an asserted
     *  resolution; the stamp below is what makes a computed tally identifiable as such. */
    private void recomputeTally(CommitteeResolution r) {
        List<CommitteeResolutionVote> vs = voteRepo.findByResolutionId(r.getId());
        int forC = (int) vs.stream().filter(x -> "FOR".equals(x.getVote())).count();
        int agC  = (int) vs.stream().filter(x -> "AGAINST".equals(x.getVote())).count();
        int abC  = (int) vs.stream().filter(x -> "ABSTAIN".equals(x.getVote())).count();
        r.setVoteFor(forC);
        r.setVoteAgainst(agC);
        r.setVoteAbstain(abC);
        r.setOutcome(outcomeFor(vs.size(), forC, agC));
        r.setTallySource("COMPUTED");
        r.setTallyRecordedByUserId(null);   // the vote rows carry their own attribution
        r.setTallyRecordedByName(null);
        r.setTallyStampedAt(LocalDateTime.now());
        repo.save(r);
        // F2-100 — the vote that decides a motion is one of the three moments a decision can
        // become binding. Before this line, deciding a motion by counted votes published
        // nothing at all.
        publishIfBinding(r);
    }

    private static int zero(Integer i) { return i == null ? 0 : i; }

    /**
     * Map a counted tally onto the outcome vocabulary
     * ({@code CK_resolution_outcome}: PROPOSED | APPROVED | REJECTED | DEFERRED | WITHDRAWN).
     *
     * <p>A tie among cast FOR/AGAINST votes fails the motion (REJECTED); only abstentions and
     * recusals leave it undecided-by-the-meeting (DEFERRED).
     *
     * <p><b>F2-89 — "nobody has voted" and "the committee could not decide" are different facts.</b>
     * Before V349 this returned DEFERRED for both, because DEFERRED was the closest thing to
     * undecided the vocabulary had. They have different consequences: AR-152 re-tables a DEFERRED
     * motion at the next meeting, which is the right response to a committee that considered a
     * motion and did not carry it, and the wrong response to one that has not been put to a vote.
     *
     * <p>⚠ <b>{@code voteRows == 0} cannot happen from the one caller</b> —
     * {@link #recomputeTally} runs after {@code voteRepo.save}, and no path in the module deletes a
     * resolution vote (checked: the repository has no delete, and the controller exposes none). It
     * is a <b>pure static function</b> rather than a branch inside {@code recomputeTally} precisely
     * so that case is stated and tested instead of being an unreachable line that reads like a
     * fix. If a withdraw-vote path is ever added, this already answers correctly rather than
     * silently deferring a motion nobody voted on — and the test says so, so the decision is
     * inherited deliberately and not by accident.
     */
    static String outcomeFor(int voteRows, int forCount, int againstCount) {
        if (voteRows <= 0)                          return CommitteeDecisionAuthority.PROPOSED;
        if (forCount > againstCount)                return "APPROVED";
        if (forCount > 0 || againstCount > 0)       return "REJECTED";
        return "DEFERRED";
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeResolution> listByMeeting(Long meetingId) {
        try {
            return repo.findByOrganisationIdAndMeetingIdOrderBySeqAsc(
                TenantContext.getCurrentTenant(), meetingId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public List<CommitteeResolution> listByCommittee(Long committeeId) {
        try {
            return repo.findByOrganisationIdAndCommitteeIdOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), committeeId);
        } catch (RuntimeException e) {
            if (CommitteeSqlGuards.isMissingObjectError(e)) return Collections.emptyList();
            throw e;
        }
    }

    /**
     * F2-40 — a tally supplied at creation is <b>asserted</b>, and is now recorded as such.
     *
     * <p>The secretariat form on {@code CommitteesPage} posts {@code voteFor}/{@code voteAgainst}/
     * {@code voteAbstain}/{@code outcome} straight from the minutes, which is the normal path for a
     * committee that votes in the room. That stays legal. What changes is that the row says so:
     * {@code ASSERTED} with the authenticated recorder stamped on it, versus {@code COMPUTED} for a
     * resolution created empty and decided through {@link #recordVote}. Provenance is taken from
     * {@code Authentication}, never from the body — the DF-3 rule.
     */
    @Transactional
    public CommitteeResolution create(CommitteeResolution in, VoteActor actor) {
        // Mass-assignment defense — see CommitteeActionItemService.create()
        // for the full attack path. Force id=null so save() persists a new
        // row instead of merge()-ing into a potentially cross-tenant row.
        in.setId(null);
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        // F2-100: server-assigned, never accepted from the body — a caller who could stamp this
        // would suppress the announcement of their own committee's decision.
        in.setDecisionPublishedAt(null);

        // ── F2-89: a motion may now be RAISED ────────────────────────────────────────────
        // Absent or blank outcome means "raised, not yet decided" instead of a 500 on a NOT NULL
        // column. Before V349 there was no such value, so every caller had to name a verdict:
        // the golden-path IT picked DEFERRED (and tripped AR-152's re-table rule 38 times), and
        // the only UI that creates a resolution defaulted the field to APPROVED.
        if (norm(in.getOutcome()).isEmpty()) {
            in.setOutcome(CommitteeDecisionAuthority.PROPOSED);
        } else {
            String want = norm(in.getOutcome()).toUpperCase(java.util.Locale.ROOT);
            if (!CommitteeDecisionAuthority.ALL.contains(want))
                throw new IllegalArgumentException(
                    "outcome must be one of " + CommitteeDecisionAuthority.ALL + " — got \""
                  + in.getOutcome() + "\". Use " + CommitteeDecisionAuthority.PROPOSED
                  + " to raise a motion that has not been decided yet.");
            in.setOutcome(want);
        }

        int assertedVotes = zero(in.getVoteFor()) + zero(in.getVoteAgainst()) + zero(in.getVoteAbstain());
        in.setTallyRecordedByUserId(null);
        in.setTallyRecordedByName(null);
        if (assertedVotes > 0) {
            in.setTallySource("ASSERTED");
            if (actor != null) {
                in.setTallyRecordedByUserId(actor.userId());
                in.setTallyRecordedByName(norm(actor.username()).isEmpty() ? null : actor.username());
            }
        } else {
            // No numbers asserted, so the tally is whatever the votes say — including nothing yet.
            in.setTallySource("COMPUTED");
        }
        in.setTallyStampedAt(LocalDateTime.now());

        CommitteeResolution saved = repo.save(in);

        audit.log("RESOLUTION_CREATE", "CommitteeResolution", saved.getId(),
            saved.getResolutionCode() + " · " + saved.getOutcome()
                + " (" + saved.getVoteFor() + "/" + saved.getVoteAgainst() + "/" + saved.getVoteAbstain() + ")"
                + " tally=" + saved.getTallySource()
                + ("ASSERTED".equals(saved.getTallySource())
                    ? " recorded by " + (saved.getTallyRecordedByName() == null
                        ? "user#" + saved.getTallyRecordedByUserId() : saved.getTallyRecordedByName())
                    : ""));

        // ── F2-89/F2-100: raising a motion is not deciding it ────────────────────────────
        // This publish used to be unconditional, so creating a resolution announced its
        // "decision" whether or not one had been taken. A raised motion now gets an event that
        // says so, and the decision event is published when the decision becomes BINDING —
        // which for a resolution created on already-approved minutes is right here, and
        // otherwise is at the deciding vote or at minutes approval.
        if (!CommitteeDecisionAuthority.isDecided(saved)) {
            // No seeded rule listens to this: nothing has been decided, so nobody should be
            // paged. It exists so tenants have a correct hook for "a motion was tabled" instead
            // of reaching for a decision event, and so the audit story is not create-only.
            publisher.publishEvent(DomainEvent.of(
                org, "COMMITTEE_MOTION_RAISED", "CommitteeResolution", saved.getId(),
                Map.of("resolutionCode", saved.getResolutionCode(),
                       "committeeId",    saved.getCommitteeId(),
                       "meetingId",      saved.getMeetingId(),
                       "titleEn",        saved.getTitleEn() == null ? "" : saved.getTitleEn(),
                       "confidentiality", saved.getConfidentiality() == null ? "" : saved.getConfidentiality())));
        } else {
            publishIfBinding(saved);
        }

        return saved;
    }

    // ── F2-100: the single site that announces a committee decision ──────────────────────

    /**
     * Publish {@code COMMITTEE_RESOLUTION_<outcome>} if — and only once — this decision has
     * become binding.
     *
     * <p><b>Why this is not simply "publish on the deciding vote".</b> A decision is binding on
     * approved minutes, not on a tally (see {@link CommitteeDecisionAuthority}), and the two
     * orders both happen in real data: measured 2026-08-03, <b>19 of 22</b> resolutions in dev
     * belong to meetings that were already {@code MINUTES_APPROVED} when the resolution was
     * recorded — the secretariat approves the minutes, then types up what was resolved. Publishing
     * only at minutes approval would therefore have announced nothing for the dominant real-world
     * pattern, which is the mirror image of the bug being fixed. So all three moments try, and
     * {@code decision_published_at} makes exactly one of them win.
     *
     * <p>Never throws. An announcement failure must not roll back a recorded vote or a recorded
     * resolution — the decision is the fact, the notification is a consequence of it. This is the
     * F2-90 lesson inverted: the ledger entry and the row are what an auditor reads, and neither
     * should be lost because an event listener was unhappy.
     */
    void publishIfBinding(CommitteeResolution r) {
        try {
            if (r == null || r.getId() == null) return;
            if (r.getDecisionPublishedAt() != null) return;          // already announced
            if (!CommitteeDecisionAuthority.isDecided(r)) return;    // nothing decided yet
            Long org = r.getOrganisationId() != null ? r.getOrganisationId() : TenantContext.getCurrentTenant();
            CommitteeMeeting meeting = r.getMeetingId() == null ? null
                : meetingRepo.findByIdAndOrganisationId(r.getMeetingId(), org).orElse(null);
            if (!CommitteeDecisionAuthority.isBinding(r, meeting)) return;

            r.setDecisionPublishedAt(LocalDateTime.now());
            repo.save(r);

            // F8 — broadcast resolution outcomes so downstream automation can act (regulator
            // notification, board-register filing, risk-register update for accepted risks,
            // policy publication for approved policies, …). The trigger event encodes the
            // outcome so rule conditions can dispatch.
            publisher.publishEvent(DomainEvent.of(
                org, "COMMITTEE_RESOLUTION_" + r.getOutcome(),
                "CommitteeResolution", r.getId(),
                Map.of("resolutionCode",  r.getResolutionCode(),
                       "committeeId",     r.getCommitteeId(),
                       "meetingId",       r.getMeetingId(),
                       "outcome",         r.getOutcome(),
                       "regulatorNotify", Boolean.valueOf(r.getRegulatorNotify() != null && r.getRegulatorNotify() == 1),
                       "confidentiality", r.getConfidentiality() == null ? "" : r.getConfidentiality())));
        } catch (RuntimeException ex) {
            log.warn("[committee] could not announce the decision on resolution {}: {}",
                r == null ? null : r.getId(), ex.getMessage());
        }
    }

    /**
     * Approving the minutes is the act that makes every decision in them binding — so it is the
     * third publish moment. Called by {@code CommitteeService.approveMinutes} after the status
     * flip; each resolution is {@code decision_published_at}-guarded, and a motion still
     * {@code PROPOSED} when the minutes are approved is simply not announced (the committee
     * recorded no verdict for it).
     *
     * @return how many decisions were announced — logged by the caller, and asserted by the
     *         golden-path IT so a silent zero cannot pass for success.
     */
    @Transactional
    public int publishBindingDecisionsForMeeting(Long meetingId) {
        if (meetingId == null) return 0;
        int published = 0;
        for (CommitteeResolution r : listByMeeting(meetingId)) {
            if (r.getDecisionPublishedAt() != null) continue;
            publishIfBinding(r);
            if (r.getDecisionPublishedAt() != null) published++;
        }
        return published;
    }
}
