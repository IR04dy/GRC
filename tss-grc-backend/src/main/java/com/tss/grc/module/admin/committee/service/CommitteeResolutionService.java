package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingAttendeeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionVoteRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
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
     */
    @Transactional
    public CommitteeResolution recordVote(Long resolutionId, String requestedMember, String voteRaw,
                                          String memberRole, String rationale, VoteActor actor) {
        CommitteeResolution r = get(resolutionId);   // org-scoped — must stay first
        if (actor == null || (actor.userId() == null && norm(actor.username()).isEmpty()))
            throw new AccessDeniedException(
                "A committee vote must be recorded by an identified user.");
        String vote = norm(voteRaw).toUpperCase();
        if (!VOTE_VALUES.contains(vote))
            throw new IllegalArgumentException("vote must be one of " + VOTE_VALUES);

        Long org = TenantContext.getCurrentTenant();

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
                throw new AccessDeniedException(
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
                throw new AccessDeniedException(
                    "You may only cast your own vote. Recording a vote on behalf of "
                  + seat.getMemberName() + " requires committee secretary or chair authority.");
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
            if (refOpt.isPresent() && refOpt.get().getRecusalNames() != null) {
                for (String nm : refOpt.get().getRecusalNames().split(",")) {
                    if (sameName(member, nm)) { vote = "RECUSED"; break; }
                }
            }
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
     *  per-member votes and persist onto the resolution. */
    private void recomputeTally(CommitteeResolution r) {
        List<CommitteeResolutionVote> vs = voteRepo.findByResolutionId(r.getId());
        int forC = (int) vs.stream().filter(x -> "FOR".equals(x.getVote())).count();
        int agC  = (int) vs.stream().filter(x -> "AGAINST".equals(x.getVote())).count();
        int abC  = (int) vs.stream().filter(x -> "ABSTAIN".equals(x.getVote())).count();
        r.setVoteFor(forC);
        r.setVoteAgainst(agC);
        r.setVoteAbstain(abC);
        // Map the tally onto the resolution's permitted outcome vocabulary
        // (CK_resolution_outcome: APPROVED | REJECTED | DEFERRED | WITHDRAWN).
        // A tie among cast FOR/AGAINST votes fails the motion (REJECTED); only
        // abstentions/recusals leave it undecided (DEFERRED).
        String outcome;
        if (forC > agC)               outcome = "APPROVED";
        else if (forC > 0 || agC > 0) outcome = "REJECTED";
        else                          outcome = "DEFERRED";
        r.setOutcome(outcome);
        repo.save(r);
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

    @Transactional
    public CommitteeResolution create(CommitteeResolution in) {
        // Mass-assignment defense — see CommitteeActionItemService.create()
        // for the full attack path. Force id=null so save() persists a new
        // row instead of merge()-ing into a potentially cross-tenant row.
        in.setId(null);
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        CommitteeResolution saved = repo.save(in);

        audit.log("RESOLUTION_CREATE", "CommitteeResolution", saved.getId(),
            saved.getResolutionCode() + " · " + saved.getOutcome()
                + " (" + saved.getVoteFor() + "/" + saved.getVoteAgainst() + "/" + saved.getVoteAbstain() + ")");

        // F8 — broadcast resolution outcomes so downstream automation can
        // act (regulator notification, board notification, risk-register
        // update for accepted risks, policy publication for approved
        // policies, etc.). The trigger event encodes the outcome so rule
        // conditions can dispatch.
        publisher.publishEvent(DomainEvent.of(
            org, "COMMITTEE_RESOLUTION_" + saved.getOutcome(),
            "CommitteeResolution", saved.getId(),
            Map.of("resolutionCode",  saved.getResolutionCode(),
                   "committeeId",     saved.getCommitteeId(),
                   "meetingId",       saved.getMeetingId(),
                   "outcome",         saved.getOutcome(),
                   "regulatorNotify", Boolean.valueOf(saved.getRegulatorNotify() != null && saved.getRegulatorNotify() == 1),
                   "confidentiality", saved.getConfidentiality() == null ? "" : saved.getConfidentiality())));

        return saved;
    }
}
