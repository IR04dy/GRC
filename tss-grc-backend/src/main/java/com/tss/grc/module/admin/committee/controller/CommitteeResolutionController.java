package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.dto.CommitteeDtos.ResolutionVoteRequest;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.service.CommitteeResolutionService;
import com.tss.grc.security.GrcUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/committees/resolutions")
@RequiredArgsConstructor
public class CommitteeResolutionController {

    private final CommitteeResolutionService service;

    @GetMapping
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public Page<CommitteeResolution> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public CommitteeResolution get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/by-meeting/{meetingId}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeResolution> byMeeting(@PathVariable Long meetingId) {
        return service.listByMeeting(meetingId);
    }

    @GetMapping("/by-committee/{committeeId}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeResolution> byCommittee(@PathVariable Long committeeId) {
        return service.listByCommittee(committeeId);
    }

    @PostMapping
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeResolution create(@RequestBody CommitteeResolution in) {
        return service.create(in);
    }

    // ── CH-14: per-member voting + recusal/COI ───────────────────────────────

    /**
     * Record the caller's vote on a resolution.
     *
     * <p><b>DF-3 / F2-21.</b> The voter is the <b>authenticated principal</b>, not a
     * name in the request body. Omit {@code memberName} to vote as yourself — that is
     * the normal path. Supplying {@code memberName} for someone else is only honoured
     * for a committee secretary/chair (see {@link #mayRecordOnBehalf}), and the row is
     * then stamped {@code on_behalf = 1} with the real actor recorded, so an on-behalf
     * vote is permitted but never silent.
     *
     * <p>Previously this passed {@code req.memberName()} straight through, so any
     * principal with {@code ADMIN:WRITE} could vote as any name — defeating recusal,
     * quorum and vote ownership. The sibling {@code CommitteeController#approveMinutes}
     * already derived its actor from {@code Authentication}; this now matches it.
     *
     * <p><b>D-7 / ADR-014.</b> Guarded by {@code ADMIN:APPROVE} — the vote capability —
     * not by the {@code ADMIN:WRITE} that administers committees. The two were the same
     * grant, so anyone who could vote could appoint themselves to the roster and
     * manufacture a quorum. Membership of {@code committee_member_terms} still confers
     * the vote; this permission only opens the door.
     */
    @PostMapping("/{id}/votes")
    @PreAuthorize("hasPermission('ADMIN','APPROVE')")
    public CommitteeResolution recordVote(@PathVariable Long id,
                                          @Valid @RequestBody ResolutionVoteRequest req,
                                          Authentication auth) {
        return service.recordVote(id, req.memberName(), req.vote(), req.memberRole(),
            req.rationale(),
            new CommitteeResolutionService.VoteActor(
                userIdOf(auth), usernameOf(auth), mayRecordOnBehalf(auth)));
    }

    @GetMapping("/{id}/votes")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeResolutionVote> votes(@PathVariable Long id) {
        return service.votes(id);
    }

    // ── DF-3: actor resolution (mirrors CommitteeController) ─────────────────

    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }

    private static String usernameOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUsername();
        return auth != null ? auth.getName() : null;
    }

    /**
     * May this principal record a vote for a <em>different</em> member — the committee
     * secretary/chair minuting a show of hands?
     *
     * <p>Matched against granted authorities the same way {@code ModulePermissionEvaluator}
     * does it: an exact {@code MODULE_ACTION} authority, plus the {@code ROLE_PLATFORM_ADMIN}
     * bypass.
     *
     * <p><b>Corrected under D-7 / ADR-014.</b> This originally tested for {@code ADMIN_APPROVE}
     * at a time when {@code ADMIN:APPROVE} <em>was not in the permission catalogue at all</em>,
     * so no tenant could ever grant it and the on-behalf path was in practice
     * {@code PLATFORM_ADMIN}-only — the Javadoc claimed a tenant-controlled split the data did
     * not support. The capability now tested is {@code ADMIN_WRITE}, the <b>secretariat</b>
     * right (held by 3 roles: board secretary, GRC administrator, organisation administrator),
     * which is the persona that legitimately minutes a show of hands. An ordinary committee
     * member holds {@code ADMIN:APPROVE} only and can therefore vote solely as themselves.
     *
     * <p>An on-behalf vote is permitted but never silent: the row is stamped
     * {@code on_behalf = 1} with {@code recorded_by_user_id} set to the real actor (V315).
     */
    private static boolean mayRecordOnBehalf(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String s = a.getAuthority();
            if ("ROLE_PLATFORM_ADMIN".equals(s) || "ADMIN_WRITE".equals(s)) return true;
        }
        return false;
    }
}
