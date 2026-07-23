package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.dto.CommitteeDtos.ResolutionVoteRequest;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.service.CommitteeResolutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PostMapping("/{id}/votes")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeResolution recordVote(@PathVariable Long id, @Valid @RequestBody ResolutionVoteRequest req) {
        return service.recordVote(id, req.memberName(), req.vote(), req.memberRole(), req.rationale());
    }

    @GetMapping("/{id}/votes")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeResolutionVote> votes(@PathVariable Long id) {
        return service.votes(id);
    }
}
