package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.entity.CommitteeMeetingPaper;
import com.tss.grc.module.admin.committee.service.CommitteeMeetingPapersService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/committees/papers")
@RequiredArgsConstructor
public class CommitteeMeetingPapersController {

    private final CommitteeMeetingPapersService service;

    @GetMapping("/by-meeting/{meetingId}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public List<CommitteeMeetingPaper> listForMeeting(@PathVariable Long meetingId) {
        return service.listForMeeting(meetingId);
    }

    @GetMapping("/by-meeting/{meetingId}/stats")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public Map<String, Object> readStats(@PathVariable Long meetingId) {
        return service.readStats(meetingId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ADMIN','READ')")
    public CommitteeMeetingPaper get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/circulate")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeMeetingPaper circulate(@RequestBody CommitteeMeetingPaper in) {
        return service.circulate(in);
    }

    @PostMapping("/{id}/read/{userId}")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeMeetingPaper markRead(@PathVariable Long id, @PathVariable Long userId) {
        return service.markRead(id, userId);
    }

    @PostMapping("/{id}/mark-late")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeMeetingPaper markLate(@PathVariable Long id, @RequestBody LateRequest req) {
        return service.markLate(id, req.reason());
    }

    public record LateRequest(String reason) {}
}
