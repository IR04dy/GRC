package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.dto.LinkUserRequest;
import com.tss.grc.module.admin.committee.dto.LinkableUser;
import com.tss.grc.module.admin.committee.dto.MemberTermRequest;
import com.tss.grc.module.admin.committee.dto.SeatLinkageReport;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberSkill;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.service.CommitteeMemberRegistryService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import com.tss.grc.security.GrcUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/committees/registry")
@RequiredArgsConstructor
@RequireModule("BOARD")
public class CommitteeMemberRegistryController {

    private final CommitteeMemberRegistryService service;

    // ── Member terms ─────────────────────────────────────────────────

    @GetMapping("/committees/{committeeId}/terms")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeMemberTerm> listTerms(@PathVariable Long committeeId) {
        return service.listTerms(committeeId);
    }

    @GetMapping("/committees/{committeeId}/active-members")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeMemberTerm> listActive(@PathVariable Long committeeId) {
        return service.listActiveMembers(committeeId);
    }

    @GetMapping("/committees/{committeeId}/independence")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public Map<String, Object> independenceStats(@PathVariable Long committeeId) {
        return service.independenceStats(committeeId);
    }

    /**
     * Appoint a member to a committee's roster.
     *
     * <p><b>F2-25.</b> Binds {@link MemberTermRequest}, not the JPA entity. The entity's
     * {@code nullable = false} columns used to be the only statement of what a caller had
     * to supply, enforced at the database rather than at the boundary — so a body missing
     * {@code appointedAt} or {@code termStartsAt} (neither of which has a default) died as
     * a {@code DataIntegrityViolation} and surfaced as a **500**. An authorised secretary
     * could not appoint a member and the API could not say which field was wrong.
     *
     * <p>Guarded by {@code ADMIN:WRITE} — the **secretariat** right, held by 3 roles. This
     * is deliberately *not* {@code ADMIN:APPROVE} (the vote capability, 15 roles): if one
     * grant did both, any voter could appoint themselves to a roster and manufacture a
     * quorum. See ADR-014.
     */
    @PostMapping("/terms")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeMemberTerm appoint(@Valid @RequestBody MemberTermRequest req) {
        return service.appoint(req);
    }

    /**
     * Set which platform user holds this seat — link, re-link, declare external, or clear.
     *
     * <p><b>F2-26.</b> Every seeded roster row had {@code member_user_id NULL}, so
     * {@code recordVote}'s roster match always fell through to the name fallback and no
     * member could cast their own vote: every vote had to be entered by the secretariat
     * and stamped {@code on_behalf = 1}. This endpoint did not exist — the column was
     * writable only at appointment time, and nothing populated it. Without it the ADR-014
     * self-vote path is unreachable no matter how the grants are configured.
     *
     * <p>Guarded by {@code ADMIN:WRITE} (the secretariat right, 3 roles) and deliberately
     * <b>not</b> {@code ADMIN:APPROVE} (the vote capability, 15 roles): linking is what
     * confers the ability to self-vote, so a voter who could also link would be able to
     * seat themselves and manufacture a quorum. That is the escalation V317 split
     * {@code ADMIN:WRITE} apart to prevent — re-merging it here would undo ADR-014 through
     * the back door.
     */
    @PostMapping("/terms/{id}/link-user")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeMemberTerm linkUser(@PathVariable Long id,
                                        @Valid @RequestBody LinkUserRequest req,
                                        Authentication auth) {
        return service.linkUser(id, req, userIdOf(auth));
    }

    /**
     * Users who may be attached to a seat — the picker source for the appointment and
     * link-user forms.
     *
     * <p>Guarded by {@code ADMIN:WRITE} rather than {@code IAM:READ} on purpose:
     * {@code BOARD_SECRETARY} holds the former and not the latter, so sourcing the picker
     * from {@code /api/v1/admin/users} would 403 for the persona that maintains rosters
     * while still letting them perform the link. See {@link LinkableUser} for why the
     * projection excludes email, roles and lock state.
     */
    @GetMapping("/linkable-users")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public List<LinkableUser> linkableUsers(@RequestParam(required = false) Long committeeId,
                                            @RequestParam(required = false) String q) {
        return service.linkableUsers(committeeId, q);
    }

    /**
     * Roster-linkage coverage — how many seats can cast their own vote, and which cannot.
     *
     * <p>{@code ADMIN:READ}, because it is a report; the 21 roles that can see a committee
     * should be able to see whether its roster is complete rather than finding out during
     * a meeting.
     */
    @GetMapping("/committees/{committeeId}/linkage")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public SeatLinkageReport linkage(@PathVariable Long committeeId) {
        return service.linkage(committeeId);
    }

    @PostMapping("/terms/{id}/end")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeMemberTerm endTerm(@PathVariable Long id, @RequestBody EndTermRequest req) {
        return service.endTerm(id, req.renewalStatus(), req.reason());
    }

    @PostMapping("/terms/{id}/renew")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeMemberTerm renew(@PathVariable Long id, @RequestBody RenewRequest req) {
        return service.renew(id, req.newStart(), req.newEnd(), req.resolutionCode());
    }

    // ── Skills matrix ───────────────────────────────────────────────

    @GetMapping("/committees/{committeeId}/skills")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeMemberSkill> listSkills(@PathVariable Long committeeId) {
        return service.listSkills(committeeId);
    }

    @GetMapping("/committees/{committeeId}/skills/coverage")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public Map<String, Long> coverage(@PathVariable Long committeeId) {
        return service.skillCoverage(committeeId);
    }

    @GetMapping("/committees/{committeeId}/skills/by-dimension/{dimension}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeMemberSkill> byDimension(@PathVariable Long committeeId,
                                                    @PathVariable String dimension) {
        return service.listSkillsByDimension(committeeId, dimension);
    }

    @PostMapping("/skills")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeMemberSkill addSkill(@RequestBody CommitteeMemberSkill in) {
        return service.addSkill(in);
    }

    @DeleteMapping("/skills/{id}")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public void removeSkill(@PathVariable Long id) {
        service.removeSkill(id);
    }

    /** F2-26 — the acting user, for the SELF marker on a self-link. Mirrors
     *  {@code CommitteeResolutionController}; null when the principal is not a
     *  {@code GrcUserDetails} (never the case on an authenticated request, but the
     *  linkage code treats null as "not self" rather than assuming). */
    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }

    public record EndTermRequest(String renewalStatus, String reason) {}
    public record RenewRequest(LocalDate newStart, LocalDate newEnd, String resolutionCode) {}
}
