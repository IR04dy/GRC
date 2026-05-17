package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.dto.CommitteeDtos.*;
import com.tss.grc.module.admin.committee.service.CommitteeService;
import com.tss.grc.security.GrcUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/committees")
@RequiredArgsConstructor
@Tag(name = "Committee Workflows")
@SecurityRequirement(name = "bearerAuth")
public class CommitteeController {

    private final CommitteeService service;

    // ── Committee endpoints ────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<CommitteeResponse> listCommittees(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "100") int size) {
        return service.listCommittees(status, PageRequest.of(page, size)).map(CommitteeResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CommitteeResponse getCommittee(@PathVariable Long id) {
        return CommitteeResponse.from(service.getCommittee(id));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommitteeResponse> createCommittee(@Valid @RequestBody CommitteeCreateRequest req) {
        return ResponseEntity.status(201).body(CommitteeResponse.from(service.createCommittee(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CommitteeResponse updateCommittee(@PathVariable Long id,
                                             @Valid @RequestBody CommitteeUpdateRequest req) {
        return CommitteeResponse.from(service.updateCommittee(id, req));
    }

    @PostMapping("/{id}/dissolve")
    @PreAuthorize("isAuthenticated()")
    public CommitteeResponse dissolveCommittee(@PathVariable Long id,
                                               @Valid @RequestBody CommitteeDissolveRequest req) {
        return CommitteeResponse.from(service.dissolveCommittee(id, req));
    }

    // ── Meeting endpoints ──────────────────────────────────────────────
    @GetMapping("/meetings")
    @PreAuthorize("isAuthenticated()")
    public Page<MeetingResponse> listMeetings(@RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "100") int size) {
        return service.listMeetings(status, PageRequest.of(page, size)).map(MeetingResponse::from);
    }

    @GetMapping("/{committeeId}/meetings")
    @PreAuthorize("isAuthenticated()")
    public List<MeetingResponse> meetingsForCommittee(@PathVariable Long committeeId) {
        return service.meetingsForCommittee(committeeId).stream().map(MeetingResponse::from).toList();
    }

    @GetMapping("/meetings/{id}")
    @PreAuthorize("isAuthenticated()")
    public MeetingResponse getMeeting(@PathVariable Long id) {
        return MeetingResponse.from(service.getMeeting(id));
    }

    @PostMapping("/meetings")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MeetingResponse> createMeeting(@Valid @RequestBody MeetingCreateRequest req) {
        return ResponseEntity.status(201).body(MeetingResponse.from(service.createMeeting(req)));
    }

    @PutMapping("/meetings/{id}")
    @PreAuthorize("isAuthenticated()")
    public MeetingResponse updateMeeting(@PathVariable Long id,
                                         @Valid @RequestBody MeetingUpdateRequest req) {
        return MeetingResponse.from(service.updateMeeting(id, req));
    }

    @PostMapping("/meetings/{id}/start")
    @PreAuthorize("isAuthenticated()")
    public MeetingResponse startMeeting(@PathVariable Long id) {
        return MeetingResponse.from(service.startMeeting(id));
    }

    @PostMapping("/meetings/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public MeetingResponse completeMeeting(@PathVariable Long id,
                                           @RequestBody(required = false) MeetingMinutesRequest req) {
        return MeetingResponse.from(service.completeMeeting(id, req));
    }

    @PostMapping("/meetings/{id}/approve-minutes")
    @PreAuthorize("isAuthenticated()")
    public MeetingResponse approveMinutes(@PathVariable Long id, Authentication auth) {
        return MeetingResponse.from(service.approveMinutes(id, usernameOf(auth), userIdOf(auth)));
    }

    @PostMapping("/meetings/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public MeetingResponse cancelMeeting(@PathVariable Long id,
                                         @Valid @RequestBody MeetingCancelRequest req) {
        return MeetingResponse.from(service.cancelMeeting(id, req));
    }

    private static Long userIdOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUserId();
        return null;
    }
    private static String usernameOf(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) return u.getUsername();
        return auth != null ? auth.getName() : null;
    }
}
