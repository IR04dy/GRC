package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.dto.MemberTermRequest;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberSkill;
import com.tss.grc.module.admin.committee.entity.CommitteeMemberTerm;
import com.tss.grc.module.admin.committee.service.CommitteeMemberRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/committees/registry")
@RequiredArgsConstructor
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

    public record EndTermRequest(String renewalStatus, String reason) {}
    public record RenewRequest(LocalDate newStart, LocalDate newEnd, String resolutionCode) {}
}
