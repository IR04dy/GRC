package com.tss.grc.module.admin.committee.controller;

import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import com.tss.grc.module.admin.committee.service.CommitteeCalendarService;
import com.tss.grc.module.platform.licensing.guard.RequireModule;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Annual-calendar + agenda-auto-populator REST endpoints.
 *
 * <p>The "Generate calendar" button on the committees page hits
 * {@code POST /generate-calendar/{committeeId}?year=YYYY} — pre-creates
 * 4 / 12 / 26 / 52 meeting rows depending on the committee's cadence.
 *
 * <p>The "Refresh agenda" button on a meeting hits
 * {@code POST /populate-agenda/{meetingId}} — re-runs the agenda
 * auto-populator to pick up newly-added standing items or newly-opened
 * actions from prior meetings.
 */
@RestController
@RequestMapping("/api/v1/committees/calendar")
@RequiredArgsConstructor
@RequireModule("BOARD")
public class CommitteeCalendarController {

    private final CommitteeCalendarService service;

    @PostMapping("/generate-calendar/{committeeId}")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public Map<String, Object> generateAnnualCalendar(
            @PathVariable Long committeeId,
            @RequestParam int year) {
        int created = service.generateAnnualCalendar(committeeId, year);
        return Map.of(
            "committeeId", committeeId,
            "year",        year,
            "created",     created);
    }

    @PostMapping("/populate-agenda/{meetingId}")
    @PreAuthorize("hasPermission('ADMIN','WRITE')")
    public CommitteeMeeting populateAgenda(@PathVariable Long meetingId) {
        return service.populateAgenda(meetingId);
    }
}
