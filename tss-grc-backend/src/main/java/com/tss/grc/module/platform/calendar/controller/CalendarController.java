package com.tss.grc.module.platform.calendar.controller;

import com.tss.grc.module.platform.calendar.dto.CalendarEventDto;
import com.tss.grc.module.platform.calendar.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService service;

    /**
     * The Outlook-equivalent feed. {@code from} and {@code to} bound
     * a half-open date window [from, to); a typical month view sends
     * {@code from = 1st of visible month}, {@code to = 1st of next month}.
     *
     * <p>{@code types}, if present, is a comma-separated filter on
     * {@code event_type} ("CAPA_DUE,FINDING_DEADLINE,PUBLIC_HOLIDAY" …).
     */
    @GetMapping("/events")
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public List<CalendarEventDto> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String types) {
        List<String> typeFilter = types == null || types.isBlank()
            ? null
            : Arrays.asList(types.split(","));
        return service.listInWindow(from, to, typeFilter);
    }

    /**
     * iCalendar (.ics) feed for the same window. Buyers point Outlook
     * (File → Account Settings → Internet Calendars) or Google
     * (Settings → Add calendar → From URL) at this URL to subscribe.
     *
     * <p>The response is plain {@code text/calendar; charset=utf-8}
     * with a {@code Content-Disposition: attachment} hint so a
     * browser download saves a sensibly-named .ics file too.
     */
    @GetMapping(value = "/events.ics", produces = "text/calendar; charset=UTF-8")
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public ResponseEntity<byte[]> exportIcs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) String types) {
        List<String> typeFilter = types == null || types.isBlank()
            ? null
            : Arrays.asList(types.split(","));
        String body = service.exportIcs(from, to, typeFilter);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"));
        headers.setContentDispositionFormData(
            "attachment", "tss-grc-calendar-" + from + "-to-" + to + ".ics");
        return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
    }

    /** "On the calendar" panel — every persisted event linked to
     *  this entity. */
    @GetMapping("/events/for-entity")
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public List<CalendarEventDto> listForEntity(@RequestParam String entityType,
                                                @RequestParam Long entityId) {
        return service.listForEntity(entityType, entityId);
    }

    @GetMapping("/events/{externalId}")
    @PreAuthorize("hasPermission('PLATFORM','READ')")
    public CalendarEventDto get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @PostMapping("/events")
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    public ResponseEntity<CalendarEventDto> create(@Valid @RequestBody CalendarEventDto.CreateRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PutMapping("/events/{externalId}")
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    public CalendarEventDto update(@PathVariable UUID externalId,
                                   @Valid @RequestBody CalendarEventDto.CreateRequest req) {
        return service.update(externalId, req);
    }

    @DeleteMapping("/events/{externalId}")
    @PreAuthorize("hasPermission('PLATFORM','WRITE')")
    public ResponseEntity<Void> delete(@PathVariable UUID externalId) {
        service.delete(externalId);
        return ResponseEntity.noContent().build();
    }
}
