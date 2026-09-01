package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.dto.LinkUserRequest;
import com.tss.grc.module.admin.committee.dto.LinkableUser;
import com.tss.grc.module.admin.committee.dto.MemberTermRequest;
import com.tss.grc.module.admin.committee.dto.RosterCoverageReport;
import com.tss.grc.module.admin.committee.dto.SeatLinkageReport;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberSkill;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberSkillRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeMemberTermRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.iam.service.TenantUserDirectory;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Member-registry service: appoint members, end terms, renew terms,
 * maintain the skills matrix.
 *
 * <p>Independence-ratio is computed on the fly so the committee detail
 * page can show "5/7 independent (71%)" against the CMA Article 54
 * threshold ("majority of members must be independent" for Audit
 * Committee).
 *
 * <p>F8 events:
 * <ul>
 *   <li>{@code COMMITTEE_MEMBER_APPOINTED} — fires AR-156 (welcome packet,
 *       skills-matrix prompt)</li>
 *   <li>{@code COMMITTEE_MEMBER_TERM_ENDED} — fires AR-157 (offboarding
 *       checklist, succession reminder)</li>
 *   <li>{@code COMMITTEE_TERM_EXPIRING_60D} — fired by sweep when
 *       termEndsAt within 60 days</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CommitteeMemberRegistryService {

    private final CommitteeMemberTermRepository  terms;
    private final CommitteeRepository            committees;
    private final CommitteeMemberSkillRepository skills;
    private final UserRepository                 users;
    private final TenantUserDirectory            directory;
    private final AuditLogService                audit;
    private final ApplicationEventPublisher      publisher;

    /**
     * F2-178 — the date the coverage reports judge term windows against, injectable for the same reason
     * {@code CommitteeSeatAuthority} carries one: F2-160 cost a suite that failed on Saturdays because
     * nothing said the clock was an input. Not a constructor parameter, so Lombok's
     * {@code @RequiredArgsConstructor} and every existing caller are untouched.
     */
    private Clock clock = Clock.system(ZoneId.of("UTC"));

    void setClock(Clock clock) { this.clock = clock; }

    // ── Member terms ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommitteeMemberTerm> listTerms(Long committeeId) {
        return terms.findByOrganisationIdAndCommitteeIdOrderByAppointedAtDesc(
            TenantContext.getCurrentTenant(), committeeId);
    }

    @Transactional(readOnly = true)
    public List<CommitteeMemberTerm> listActiveMembers(Long committeeId) {
        return terms.findActiveByCommittee(
            TenantContext.getCurrentTenant(), committeeId);
    }

    @Transactional(readOnly = true)
    public CommitteeMemberTerm getTerm(Long id) {
        return terms.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CommitteeMemberTerm " + id));
    }

    /**
     * Appoint a member. Creates a new term row with {@code renewalStatus=ACTIVE}.
     *
     * <p><b>F2-25.</b> Takes a validated {@link MemberTermRequest} rather than the JPA
     * entity, so the mandatory fields are stated at the boundary instead of being discovered
     * as a {@code DataIntegrityViolation} → 500. The tenant and {@code id} handling below is
     * unchanged and was already correct; it is kept (belt and braces) because the DTO now
     * makes those fields unreachable from the request anyway.
     */
    @Transactional
    public CommitteeMemberTerm appoint(MemberTermRequest req) {
        Long org = TenantContext.getCurrentTenant();

        // F2-25: the committee must exist in THIS tenant. Previously any committeeId was
        // accepted blind, so a typo produced an orphan roster row that silently counted
        // toward nothing — and a foreign id produced a row pointing across tenants.
        CommitteeMemberTerm in = new CommitteeMemberTerm();
        in.setCommitteeId(requireCommitteeInTenant(req.committeeId(), org));
        in.setMemberName(req.memberName().trim());
        // F2-26: the user link was previously copied straight from the body with no check
        // at all — so an appointment could point a governance seat at a user id that does
        // not exist, or that belongs to another tenant. Unlike the id/organisationId pair
        // this one had no server-side defence, because nothing was populating it.
        if (req.memberUserId() != null)
            requireUserInTenant(req.memberUserId(), org, req.committeeId(), null);
        in.setMemberUserId(req.memberUserId());
        in.setRole(req.role().trim());
        if (req.isVotingMember() != null) in.setIsVotingMember(req.isVotingMember());
        if (req.isIndependent()  != null) in.setIsIndependent(req.isIndependent());
        in.setIndependenceBasisEn(req.independenceBasisEn());
        in.setAppointedAt(req.appointedAt());
        in.setAppointedByReso(req.appointedByReso());
        in.setTermStartsAt(req.termStartsAt());
        in.setTermEndsAt(req.termEndsAt());
        if (req.termNumber() != null) in.setTermNumber(req.termNumber());

        // Retained from the pre-DTO implementation: null id forces save() into INSERT
        // instead of merge(), and the tenant is stamped server-side, never taken from the
        // caller. A review-council live probe confirmed this defence works.
        in.setId(null);
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        if (in.getRenewalStatus() == null) in.setRenewalStatus("ACTIVE");
        if (in.getTermNumber() == null) in.setTermNumber(1);
        CommitteeMemberTerm saved = terms.save(in);

        audit.log("MEMBER_APPOINT", "CommitteeMemberTerm", saved.getId(),
            saved.getMemberName() + " · " + saved.getRole() + " · committee=" + saved.getCommitteeId()
                + " · term#" + saved.getTermNumber());

        publisher.publishEvent(DomainEvent.of(
            org, "COMMITTEE_MEMBER_APPOINTED", "CommitteeMemberTerm", saved.getId(),
            payload(saved)));

        return saved;
    }

    /** End a term — sets {@code renewalStatus} (RESIGNED/REMOVED/EXPIRED)
     *  and records the reason. */
    @Transactional
    public CommitteeMemberTerm endTerm(Long id, String renewalStatus, String reason) {
        CommitteeMemberTerm t = getTerm(id);
        String prev = t.getRenewalStatus();
        if (!"ACTIVE".equals(prev)) {
            throw new IllegalStateException("Cannot end term from status " + prev);
        }
        if (!List.of("EXPIRED", "RESIGNED", "REMOVED", "RENEWED").contains(renewalStatus)) {
            throw new IllegalArgumentException("renewalStatus must be EXPIRED|RESIGNED|REMOVED|RENEWED");
        }
        t.setRenewalStatus(renewalStatus);
        t.setEndReason(reason);
        t.setEndedAt(LocalDateTime.now());
        CommitteeMemberTerm saved = terms.save(t);

        audit.log("MEMBER_TERM_END", "CommitteeMemberTerm", id,
            "Status ACTIVE → " + renewalStatus + " · reason=" + (reason == null ? "—" : reason));

        publisher.publishEvent(DomainEvent.of(
            saved.getOrganisationId(), "COMMITTEE_MEMBER_TERM_ENDED",
            "CommitteeMemberTerm", saved.getId(),
            payload(saved, Map.of("renewalStatus", renewalStatus,
                                  "endReason",     reason == null ? "" : reason))));

        return saved;
    }

    /** Renew a term — closes the current row as RENEWED and creates a
     *  new ACTIVE row with {@code termNumber + 1} and the new dates. */
    @Transactional
    public CommitteeMemberTerm renew(Long oldTermId, LocalDate newStart, LocalDate newEnd,
                                       String resolutionCode) {
        CommitteeMemberTerm old = getTerm(oldTermId);
        if (!"ACTIVE".equals(old.getRenewalStatus())) {
            throw new IllegalStateException("Cannot renew from status " + old.getRenewalStatus());
        }
        // Close old row.
        old.setRenewalStatus("RENEWED");
        old.setEndedAt(LocalDateTime.now());
        terms.save(old);

        // Create new row.
        CommitteeMemberTerm next = new CommitteeMemberTerm();
        next.setOrganisationId(old.getOrganisationId());
        next.setCommitteeId(old.getCommitteeId());
        next.setMemberName(old.getMemberName());
        next.setMemberUserId(old.getMemberUserId());
        next.setRole(old.getRole());
        next.setIsVotingMember(old.getIsVotingMember());
        next.setIsIndependent(old.getIsIndependent());
        next.setIndependenceBasisEn(old.getIndependenceBasisEn());
        next.setAppointedAt(LocalDate.now());
        next.setAppointedByReso(resolutionCode);
        next.setTermStartsAt(newStart);
        next.setTermEndsAt(newEnd);
        next.setTermNumber(old.getTermNumber() + 1);
        next.setRenewalStatus("ACTIVE");
        next.setCreatedAt(LocalDateTime.now());
        CommitteeMemberTerm saved = terms.save(next);

        audit.log("MEMBER_RENEW", "CommitteeMemberTerm", saved.getId(),
            old.getMemberName() + " · term#" + saved.getTermNumber()
                + " · resolution=" + (resolutionCode == null ? "—" : resolutionCode));

        publisher.publishEvent(DomainEvent.of(
            saved.getOrganisationId(), "COMMITTEE_MEMBER_RENEWED",
            "CommitteeMemberTerm", saved.getId(),
            payload(saved, Map.of("oldTermId", String.valueOf(oldTermId),
                                  "termNumber", String.valueOf(saved.getTermNumber())))));

        return saved;
    }

    // ── Platform-user linkage (F2-26) ────────────────────────────────

    /**
     * Set a seat's platform-user state: link it, declare it external, or clear it.
     *
     * <p><b>F2-26.</b> Every one of the 31 seeded roster rows had {@code member_user_id
     * NULL}, so {@code recordVote}'s roster match always fell through and no member could
     * cast their own vote — every vote had to be secretariat-entered and stamped
     * {@code on_behalf = 1}, which is the secretary-authors-the-facts model F2-21
     * identified as the root defect. This is the act that closes that: a deliberate,
     * audited statement of who holds a seat.
     *
     * <p><b>Guarded by {@code ADMIN:WRITE}, never {@code ADMIN:APPROVE}</b> (ADR-014).
     * Linking is what <em>confers</em> the ability to self-vote, so if the vote grant also
     * conferred linking, any voter could seat themselves and manufacture a quorum — the
     * escalation V317 decomposed {@code ADMIN:WRITE} to prevent.
     *
     * <p><b>Self-linking is permitted, and marked.</b> A secretary linking their own
     * account to a voting seat is a legitimate case (an ORG_ADMIN who genuinely chairs the
     * risk committee) and blocking it would need a second approver on routine roster
     * maintenance — which strands single-administrator tenants on exactly the kind of SoD
     * gate that has tripped them before. It also grants no new capability: an
     * {@code ADMIN:WRITE} holder can already record a vote for any member on their behalf.
     * What self-linking changes is the <em>provenance stamp</em> — a vote that reads as a
     * personal member vote rather than an on-behalf entry. So the ledger names it
     * explicitly ({@code SELF}) and the linkage report surfaces who holds each seat, making
     * the case reviewable instead of invisible. Residual risk, stated in the register
     * rather than papered over: a secretary could self-link, vote, and unlink. The recorded
     * vote keeps its own {@code voter_user_id} (V315) and both link transitions are in the
     * ledger, so the sequence is reconstructible after the fact — deterrence, not
     * prevention.
     *
     * <p>Closed terms are refused (409). A term that has ended is history, and history is
     * not edited — the same stance V315 and V316 take on pre-fix votes and approvals.
     */
    @Transactional
    public CommitteeMemberTerm linkUser(Long termId, LinkUserRequest req, Long actorUserId) {
        CommitteeMemberTerm t = getTerm(termId);
        if (!"ACTIVE".equals(t.getRenewalStatus()))
            throw new IllegalStateException(
                "Term " + termId + " is " + t.getRenewalStatus() + ". The platform-user link "
              + "can only be changed on an ACTIVE term — a closed term is a historical record.");

        Long org = TenantContext.getCurrentTenant();
        Long wanted = req.memberUserId();

        if (req.declaresExternal()) {
            if (wanted != null)
                throw new IllegalArgumentException(
                    "A seat cannot both be linked to user " + wanted + " and be declared to "
                  + "have no platform user. Send either memberUserId or noPlatformUser.");
            if (req.basisEn() == null || req.basisEn().isBlank())
                throw new IllegalArgumentException(
                    "basisEn is required when declaring that a seat has no platform user — "
                  + "an external seat must say why, or the declaration is an unevidenced "
                  + "assertion (e.g. \"External auditor — attends under CMA Article 54, no "
                  + "platform account\").");
        }
        if (wanted != null) requireUserInTenant(wanted, org, t.getCommitteeId(), termId);

        // Before → after, captured before mutation so the ledger records the transition
        // rather than just the outcome.
        String before = describeLinkage(t);

        t.setMemberUserId(wanted);
        t.setNoPlatformUser(req.declaresExternal());
        t.setNoPlatformUserBasisEn(req.declaresExternal() ? req.basisEn().trim() : null);
        CommitteeMemberTerm saved = terms.save(t);

        String after = describeLinkage(saved);
        boolean self = wanted != null && wanted.equals(actorUserId);
        audit.log("MEMBER_LINK_USER", "CommitteeMemberTerm", termId,
            saved.getMemberName() + " · committee=" + saved.getCommitteeId()
                + " · " + before + " → " + after
                + (self ? " · SELF (the actor linked their own account to this seat)" : "")
                + (req.reason() == null || req.reason().isBlank()
                    ? "" : " · reason=" + req.reason().trim()));

        return saved;
    }

    /**
     * Users the secretariat may attach to a seat — the picker source.
     *
     * <p>Served here, under {@code ADMIN:WRITE}, rather than from {@code IAM}'s user list:
     * measured on the live grant table, {@code BOARD_SECRETARY} holds {@code ADMIN:WRITE}
     * but <b>not</b> {@code IAM:READ}, so a picker built on {@code /api/v1/admin/users}
     * would 403 for the one persona that maintains committee rosters. See
     * {@link LinkableUser} for why the projection is deliberately minimal.
     *
     * <p>Filtered to {@code isActive} users. Deliberately <b>not</b> filtered on
     * {@code isLocked}, which departs from the platform's usual "who can act right now"
     * filter: a lockout is transient auth state, and letting it change who may hold a
     * governance seat would make the linkage report flap as people get locked out. A
     * deactivated account, by contrast, is a departed person, and seating one is the
     * fabricated-identity problem F2-26 is about.
     *
     * @param committeeId optional — when given, {@code heldSeats} reports how many ACTIVE
     *                    seats each user already holds on that committee, so the UI can
     *                    grey out a choice that would 409
     */
    @Transactional(readOnly = true)
    public List<LinkableUser> linkableUsers(Long committeeId, String query) {
        Long org = TenantContext.getCurrentTenant();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return users.findByOrganisationId(org, Pageable.unpaged()).getContent().stream()
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
            .filter(u -> q.isEmpty()
                || contains(u.getUsername(), q) || contains(u.getFullNameEn(), q)
                || contains(u.getFullNameAr(), q) || contains(u.getJobTitleEn(), q))
            .sorted(Comparator.comparing(u -> u.getFullNameEn() == null ? "" : u.getFullNameEn(),
                                         String.CASE_INSENSITIVE_ORDER))
            .map(u -> new LinkableUser(u.getId(), u.getUsername(), u.getFullNameEn(),
                u.getFullNameAr(), u.getJobTitleEn(),
                committeeId == null ? 0L
                    : terms.countActiveSeatsForUser(org, committeeId, u.getId(), null)))
            .toList();
    }

    /**
     * Linkage coverage for one committee — the control that stops F2-26 recurring.
     *
     * <p>F2-26 was a data gap, not a code defect: the mechanism worked and nothing
     * surfaced that it had nothing to work on, for 31 seats, until a live probe hit
     * <em>"you are not an active member of this committee"</em> on a legitimate member.
     * A number on the screen the secretariat already uses is what converts that from a
     * discovery made in a meeting into a task on a page.
     *
     * <p>Guarded by {@code ADMIN:READ}: it is a report, and the 21 roles that can see a
     * committee should be able to see whether its roster is complete. It discloses which
     * platform user holds each seat — which is deliberate, because a mis-link ("CRO"
     * pointing at the auditor's account) is only visible if the resolved name is shown.
     */
    @Transactional(readOnly = true)
    public SeatLinkageReport linkage(Long committeeId) {
        Long org = TenantContext.getCurrentTenant();
        List<CommitteeMemberTerm> active = listActiveMembers(committeeId);

        // One lookup for every linked user on the roster, rather than per seat.
        Map<Long, User> byId = new HashMap<>();
        List<Long> ids = active.stream().map(CommitteeMemberTerm::getMemberUserId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        if (!ids.isEmpty())
            for (User u : users.findAllById(ids))
                if (org.equals(u.getOrganisationId())) byId.put(u.getId(), u);

        List<SeatLinkageReport.Seat> seats = new java.util.ArrayList<>();
        long linked = 0, external = 0, unlinked = 0;
        long votingSeats = 0, votingLinked = 0;
        long currentSeats = 0, currentLinked = 0;
        LocalDate today = LocalDate.now(clock);

        for (CommitteeMemberTerm t : active) {
            SeatLinkageReport.State state = t.getMemberUserId() != null
                ? SeatLinkageReport.State.LINKED
                : Boolean.TRUE.equals(t.getNoPlatformUser())
                    ? SeatLinkageReport.State.EXTERNAL
                    : SeatLinkageReport.State.UNLINKED;
            boolean voting = t.getIsVotingMember() != null && t.getIsVotingMember() == 1;
            // F2-178 — the authorisation rule, not the roster rule. Same expression the seat guard's
            // query applies; see CommitteeMemberTerm#isCurrentOn.
            boolean current = t.isCurrentOn(today);

            switch (state) {
                case LINKED   -> linked++;
                case EXTERNAL -> external++;
                case UNLINKED -> unlinked++;
            }
            if (voting) {
                votingSeats++;
                if (state == SeatLinkageReport.State.LINKED) votingLinked++;
            }
            if (current) {
                currentSeats++;
                if (state == SeatLinkageReport.State.LINKED) currentLinked++;
            }

            User u = t.getMemberUserId() == null ? null : byId.get(t.getMemberUserId());
            seats.add(new SeatLinkageReport.Seat(
                t.getId(), t.getMemberName(), t.getRole(), voting, state,
                t.getMemberUserId(),
                u == null ? null : u.getUsername(),
                u == null ? null : u.getFullNameEn(),
                state == SeatLinkageReport.State.EXTERNAL ? t.getNoPlatformUserBasisEn() : null,
                current));
        }

        // Declared-external seats leave the denominator: a report that can never reach 100%
        // because a charter seats an external auditor is a report nobody reads.
        long linkable = active.size() - external;
        int pct = linkable <= 0 ? 0 : (int) Math.round(100.0 * linked / linkable);

        return new SeatLinkageReport(committeeId, active.size(), linked, external, unlinked,
            votingSeats, votingLinked, votingSeats - votingLinked,
            currentSeats, currentLinked, pct, seats);
    }

    /**
     * F2-178 — tenant-wide roster coverage: every live committee's linkage in one call.
     *
     * <p><b>Why this is a separate method and not {@code linkage()} in a loop.</b> Seventeen calls to
     * {@link #linkage(Long)} would issue seventeen term queries and seventeen user look-ups to compute a
     * screen that needs neither the per-seat rows nor the resolved usernames. The aggregate is one query;
     * the committee list is a second. More importantly the loop cannot answer the question this report
     * exists for: {@code linkage()} takes a committee id, so a committee with an empty roster is
     * indistinguishable from one that is 0% linked, and 9 of 17 dev committees are in exactly that state.
     *
     * <p>Rows are ordered worst-first — committees the seat guard admits nobody on, then by how many seats
     * are unlinked — because a coverage report sorted alphabetically is a list, not a work queue.
     */
    @Transactional(readOnly = true)
    public RosterCoverageReport rosterCoverage() {
        Long org = TenantContext.getCurrentTenant();
        LocalDate today = LocalDate.now(clock);

        List<Committee> all = committees.findByOrganisationId(org);
        // DISSOLVED bodies confer no seat (CommitteeSeatAuthority), so they are excluded rather than
        // listed as permanently red rows nobody may act on. The count is reported, not inferred.
        List<Committee> live = all.stream()
            .filter(c -> !"DISSOLVED".equalsIgnoreCase(String.valueOf(c.getStatus())))
            .toList();
        long dissolved = all.size() - live.size();

        Map<Long, long[]> agg = new HashMap<>();
        for (Object[] r : terms.rosterCoverageByCommittee(org, today))
            agg.put(((Number) r[0]).longValue(), new long[] {
                num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5]), num(r[6]), num(r[7]) });

        List<RosterCoverageReport.Row> rows = new java.util.ArrayList<>();
        long tSeats = 0, tLinked = 0, tExternal = 0, tUnlinked = 0, tCurrentLinked = 0;
        long withRoster = 0, fullyLinked = 0, admitsNobody = 0;

        for (Committee c : live) {
            long[] a = agg.getOrDefault(c.getId(), new long[7]);
            long seats = a[0], linked = a[1], external = a[2];
            long votingSeats = a[3], votingLinked = a[4], currentSeats = a[5], currentLinked = a[6];
            long unlinked = seats - linked - external;
            long linkable = seats - external;
            int pct = linkable <= 0 ? 0 : (int) Math.round(100.0 * linked / linkable);

            rows.add(new RosterCoverageReport.Row(
                c.getId(), c.getCode(), c.getNameEn(), c.getNameAr(),
                c.getCommitteeType(), String.valueOf(c.getStatus()),
                seats > 0, seats, linked, external, unlinked,
                votingSeats, votingLinked, currentSeats, currentLinked, pct));

            tSeats += seats; tLinked += linked; tExternal += external;
            tUnlinked += unlinked; tCurrentLinked += currentLinked;
            if (seats > 0) withRoster++;
            if (linkable > 0 && linked == linkable) fullyLinked++;
            if (currentLinked == 0) admitsNobody++;
        }

        rows.sort(Comparator
            .comparing((RosterCoverageReport.Row r) -> !r.admitsNobody())   // admits nobody first
            .thenComparing(r -> r.hasRoster())                              // no roster at all before 0%
            .thenComparing(Comparator.comparingLong(RosterCoverageReport.Row::unlinked).reversed())
            .thenComparing(r -> String.valueOf(r.code())));

        return new RosterCoverageReport(rows, live.size(), withRoster, fullyLinked, admitsNobody,
            tSeats, tLinked, tExternal, tUnlinked, tCurrentLinked, dissolved);
    }

    /** SUM over an empty group is NULL in SQL Server, not 0 — see the query's javadoc. */
    private static long num(Object o) { return o == null ? 0L : ((Number) o).longValue(); }

    /** Independence stats for a committee: numerator + denominator. */
    @Transactional(readOnly = true)
    public Map<String, Object> independenceStats(Long committeeId) {
        Long org = TenantContext.getCurrentTenant();
        long active = listActiveMembers(committeeId).size();
        long independent = terms.countActiveIndependents(org, committeeId);
        return Map.of(
            "activeMembers",        active,
            "independentMembers",   independent,
            "independenceRatioPct", active == 0 ? 0 : Math.round(100.0 * independent / active));
    }

    // ── Skills matrix ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommitteeMemberSkill> listSkills(Long committeeId) {
        return skills.findByOrganisationIdAndCommitteeIdOrderByMemberNameAsc(
            TenantContext.getCurrentTenant(), committeeId);
    }

    @Transactional(readOnly = true)
    public List<CommitteeMemberSkill> listSkillsByDimension(Long committeeId, String dimension) {
        return skills.findByDimension(
            TenantContext.getCurrentTenant(), committeeId, dimension);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> skillCoverage(Long committeeId) {
        Map<String, Long> out = new HashMap<>();
        for (Object[] row : skills.countByDimension(
                TenantContext.getCurrentTenant(), committeeId)) {
            out.put((String) row[0], ((Number) row[1]).longValue());
        }
        return out;
    }

    @Transactional
    public CommitteeMemberSkill addSkill(CommitteeMemberSkill in) {
        // Mass-assignment defense — see appoint() above.
        in.setId(null);
        Long org = TenantContext.getCurrentTenant();
        in.setOrganisationId(org);
        in.setCreatedAt(LocalDateTime.now());
        if (in.getLastReviewedAt() == null) {
            in.setLastReviewedAt(LocalDate.now());
        }
        CommitteeMemberSkill saved = skills.save(in);

        audit.log("SKILL_ADD", "CommitteeMemberSkill", saved.getId(),
            saved.getMemberName() + " · " + saved.getSkillDimension() + " · " + saved.getSkillLevel());

        return saved;
    }

    @Transactional
    public void removeSkill(Long id) {
        CommitteeMemberSkill s = skills.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("CommitteeMemberSkill " + id));
        skills.delete(s);
        audit.log("SKILL_REMOVE", "CommitteeMemberSkill", id,
            s.getMemberName() + " · " + s.getSkillDimension());
    }

    // ── helpers ──────────────────────────────────────────────────────

    private Map<String, Object> payload(CommitteeMemberTerm t) {
        return payload(t, Map.of());
    }

    private Map<String, Object> payload(CommitteeMemberTerm t, Map<String, String> extra) {
        Map<String, Object> m = new HashMap<>();
        m.put("memberName",   t.getMemberName() == null ? "" : t.getMemberName());
        m.put("role",         t.getRole() == null ? "" : t.getRole());
        m.put("committeeId",  t.getCommitteeId() == null ? "" : String.valueOf(t.getCommitteeId()));
        m.put("termNumber",   t.getTermNumber() == null ? "" : String.valueOf(t.getTermNumber()));
        m.put("isIndependent", t.getIsIndependent() != null && t.getIsIndependent() == 1 ? "true" : "false");
        m.putAll(extra);
        return m;
    }

    /**
     * F2-25: resolve a committee id within the caller's tenant, or reject with a message
     * that names the problem. Returns the id so callers can use it inline.
     *
     * <p>400 rather than 404 deliberately: the id arrived in a request body as a reference,
     * not as the addressed resource, and a 404 here would let a caller probe which committee
     * ids exist in other tenants by distinguishing "not found" from "not yours". Both cases
     * return the same 400.
     */
    private Long requireCommitteeInTenant(Long committeeId, Long org) {
        if (committees.findByIdAndOrganisationId(committeeId, org).isEmpty())
            throw new IllegalArgumentException(
                "Committee " + committeeId + " does not exist in this organisation.");
        return committeeId;
    }

    /**
     * F2-26: resolve a platform user within the caller's tenant and refuse a second ACTIVE
     * seat for them on the same committee.
     *
     * <p>400 for "no such user in this organisation" — one message for both "does not
     * exist" and "belongs to another tenant", for the same reason
     * {@link #requireCommitteeInTenant} does: distinguishing them would let a caller
     * enumerate other tenants' user ids.
     *
     * <p>409 for the duplicate seat, because it is a state conflict rather than a malformed
     * request: the user is real and the seat is real, but the combination would count one
     * person twice in the quorum denominator and let them cast two counted votes.
     */
    private void requireUserInTenant(Long userId, Long org, Long committeeId, Long excludeTermId) {
        // F2-249 — the resolve-in-tenant half is TenantUserDirectory's; the duplicate-seat check
        // below is genuinely this register's own and stays here. The label in that message used
        // to be a null-only fallback, so a user whose full_name_en was the empty string rendered
        // as "" — the primitive is blank-safe, which is why sharing it is a fix and not a tidy-up.
        User u = directory.require(userId, "a committee seat");
        if (committeeId != null
                && terms.countActiveSeatsForUser(org, committeeId, u.getId(), excludeTermId) > 0)
            throw new IllegalStateException(
                directory.labelFor(u)
              + " already holds an active seat on this committee. One person cannot hold two "
              + "seats — it would count them twice toward quorum and let them cast two votes. "
              + "End the existing term first, or link a different user.");
    }

    /** Human-readable linkage state for the audit ledger: what the seat was, what it became. */
    private String describeLinkage(CommitteeMemberTerm t) {
        if (t.getMemberUserId() != null) return "user=" + t.getMemberUserId();
        if (Boolean.TRUE.equals(t.getNoPlatformUser())) return "EXTERNAL (no platform user)";
        return "UNLINKED";
    }

    private static boolean contains(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }
}
