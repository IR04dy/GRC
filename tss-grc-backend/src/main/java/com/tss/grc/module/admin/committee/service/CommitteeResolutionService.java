package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.repository.CommitteeMeetingAttendeeRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionVoteRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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

    /** Record (or update) one member's vote on a resolution, then re-derive the
     *  aggregate tally + outcome. A member who declared a conflict of interest on
     *  the meeting attendance is forced to RECUSED (excluded from the tally). */
    @Transactional
    public CommitteeResolution recordVote(Long resolutionId, String memberName, String voteRaw,
                                          String memberRole, String rationale) {
        CommitteeResolution r = get(resolutionId);   // org-scoped
        if (memberName == null || memberName.isBlank())
            throw new IllegalArgumentException("memberName is required");
        String vote = voteRaw == null ? "" : voteRaw.trim().toUpperCase();
        if (!VOTE_VALUES.contains(vote))
            throw new IllegalArgumentException("vote must be one of " + VOTE_VALUES);
        String member = memberName.trim();

        // COI forces recusal: a member who declared a conflict on the meeting
        // attendance cannot cast a counted vote.
        if (r.getMeetingId() != null && !"RECUSED".equals(vote)) {
            boolean conflicted = attendeeRepo.findByCommitteeMeetingId(r.getMeetingId()).stream()
                .anyMatch(a -> member.equalsIgnoreCase(a.getMemberName())
                    && Boolean.TRUE.equals(a.getCoiDeclared()));
            if (conflicted) vote = "RECUSED";
        }

        // Track D SoD: force-RECUSE the referred item's requester/author (same
        // mechanic as COI) — they cannot cast the deciding committee vote.
        if (!"RECUSED".equals(vote) && referralRepo != null) {
            var refOpt = referralRepo.findByResolutionId(resolutionId);
            if (refOpt.isPresent() && refOpt.get().getRecusalNames() != null) {
                for (String nm : refOpt.get().getRecusalNames().split(",")) {
                    if (member.equalsIgnoreCase(nm.trim())) { vote = "RECUSED"; break; }
                }
            }
        }

        CommitteeResolutionVote v = voteRepo.findByResolutionIdAndMemberName(resolutionId, member)
            .orElseGet(CommitteeResolutionVote::new);
        v.setOrganisationId(TenantContext.getCurrentTenant());
        v.setResolutionId(resolutionId);
        v.setMeetingId(r.getMeetingId());
        v.setMemberName(member);
        v.setMemberRole(memberRole);
        v.setVote(vote);
        v.setRationale(rationale);
        voteRepo.save(v);

        recomputeTally(r);
        audit.log("RESOLUTION_VOTE", "CommitteeResolution", resolutionId,
            member + " voted " + vote + " -> tally " + r.getVoteFor() + "/" + r.getVoteAgainst()
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
