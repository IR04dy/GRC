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
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public Page<CommitteeResponse> listCommittees(@RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "100") int size) {
        return service.listCommittees(status, PageRequest.of(page, Math.min(size, 200))).map(CommitteeResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public CommitteeResponse getCommittee(@PathVariable Long id) {
        return CommitteeResponse.from(service.getCommittee(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public ResponseEntity<CommitteeResponse> createCommittee(@Valid @RequestBody CommitteeCreateRequest req) {
        return ResponseEntity.status(201).body(CommitteeResponse.from(service.createCommittee(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeResponse updateCommittee(@PathVariable Long id,
                                             @Valid @RequestBody CommitteeUpdateRequest req) {
        return CommitteeResponse.from(service.updateCommittee(id, req));
    }

    @PostMapping("/{id}/dissolve")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeResponse dissolveCommittee(@PathVariable Long id,
                                               @Valid @RequestBody CommitteeDissolveRequest req) {
        return CommitteeResponse.from(service.dissolveCommittee(id, req));
    }

    // ── Meeting endpoints ──────────────────────────────────────────────
    @GetMapping("/meetings")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public Page<MeetingResponse> listMeetings(@RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "100") int size) {
        return service.listMeetings(status, PageRequest.of(page, Math.min(size, 200))).map(MeetingResponse::from);
    }

    @GetMapping("/{committeeId}/meetings")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<MeetingResponse> meetingsForCommittee(@PathVariable Long committeeId) {
        return service.meetingsForCommittee(committeeId).stream().map(MeetingResponse::from).toList();
    }

    @GetMapping("/meetings/{id}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public MeetingResponse getMeeting(@PathVariable Long id) {
        return MeetingResponse.from(service.getMeeting(id));
    }

    @PostMapping("/meetings")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public ResponseEntity<MeetingResponse> createMeeting(@Valid @RequestBody MeetingCreateRequest req) {
        return ResponseEntity.status(201).body(MeetingResponse.from(service.createMeeting(req)));
    }

    @PutMapping("/meetings/{id}")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public MeetingResponse updateMeeting(@PathVariable Long id,
                                         @Valid @RequestBody MeetingUpdateRequest req) {
        return MeetingResponse.from(service.updateMeeting(id, req));
    }

    @PostMapping("/meetings/{id}/start")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public MeetingResponse startMeeting(@PathVariable Long id) {
        return MeetingResponse.from(service.startMeeting(id));
    }

    @PostMapping("/meetings/{id}/complete")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public MeetingResponse completeMeeting(@PathVariable Long id,
                                           @RequestBody(required = false) MeetingMinutesRequest req) {
        return MeetingResponse.from(service.completeMeeting(id, req));
    }

    /**
     * Approve the minutes — the act that makes a meeting's record binding, and e-signs it.
     *
     * <p><b>F2-39 — guard corrected from {@code ADMIN:WRITE} to {@code ADMIN:APPROVE}.</b>
     * ADR-014's capability table assigns "manage … minutes" (drafting) to {@code ADMIN:WRITE}
     * and <b>"approve minutes"</b> to {@code ADMIN:APPROVE}. The code guarded this with
     * {@code WRITE}, so the decision the ADR recorded was never implemented: the **3** roles that
     * draft the minutes were the only ones who could approve them, and the **15** roles ADR-014
     * entitled could not. Worse, no separation existed between authoring a meeting's record and
     * approving it. All three secretariat roles also hold {@code ADMIN:APPROVE} (V317), so this
     * widens the population to the one ADR-014 specified without stranding anyone.
     *
     * <p>The behavioural half of the fix is in the service: whoever recorded the attendance
     * sheet cannot approve the minutes whose quorum rests on it (409).
     *
     * <p>⚠ This is the divergence a test of mine actively pinned —
     * {@code CommitteeAuthorisationSeparationTest} asserted that <em>no</em> write on this
     * controller used {@code APPROVE}, which encoded the bug as the expectation. The test now
     * states the ADR's split instead.
     */
    @PostMapping("/meetings/{id}/approve-minutes")
    @PreAuthorize("hasPermission('ADMIN','APPROVE')")
    public MeetingResponse approveMinutes(@PathVariable Long id, Authentication auth) {
        return MeetingResponse.from(service.approveMinutes(id, usernameOf(auth), userIdOf(auth)));
    }

    @PostMapping("/meetings/{id}/cancel")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public MeetingResponse cancelMeeting(@PathVariable Long id,
                                         @Valid @RequestBody MeetingCancelRequest req) {
        return MeetingResponse.from(service.cancelMeeting(id, req));
    }

    // ── CH-14: attendance + deterministic quorum ─────────────────────────────

    /**
     * Record who attended, as a selection from the committee's roster.
     *
     * <p><b>F2-28 / DF-5.</b> Still {@code ADMIN:WRITE} — recording attendance is secretariat
     * work and that has not changed. What changed is that the body can no longer author the
     * facts: a mark names a roster seat (or an explicit guest), voting eligibility comes from
     * the seat, and the quorum denominator is the committee's active voting roster rather than a
     * count of the rows supplied. The actor is passed through because the sheet's author is
     * recorded and is barred from approving the resulting minutes.
     */
    @PostMapping("/meetings/{id}/attendance")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public QuorumView recordAttendance(@PathVariable Long id,
                                       @Valid @RequestBody AttendanceRequest req,
                                       Authentication auth) {
        return service.recordAttendance(id, req, userIdOf(auth));
    }

    @GetMapping("/meetings/{id}/quorum")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public QuorumView quorum(@PathVariable Long id) {
        return service.quorum(id);
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
