package com.tss.grc.module.platform.calendar.controller;

import com.tss.grc.module.platform.calendar.dto.CalendarEventDto;
import com.tss.grc.module.platform.calendar.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("isAuthenticated()")
    public List<CalendarEventDto> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String types) {
        List<String> typeFilter = types == null || types.isBlank()
            ? null
            : Arrays.asList(types.split(","));
        return service.listInWindow(from, to, typeFilter);
    }

    /** "On the calendar" panel — every persisted event linked to
     *  this entity. */
    @GetMapping("/events/for-entity")
    @PreAuthorize("isAuthenticated()")
    public List<CalendarEventDto> listForEntity(@RequestParam String entityType,
                                                @RequestParam Long entityId) {
        return service.listForEntity(entityType, entityId);
    }

    @GetMapping("/events/{externalId}")
    @PreAuthorize("isAuthenticated()")
    public CalendarEventDto get(@PathVariable UUID externalId) {
        return service.get(externalId);
    }

    @PostMapping("/events")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CalendarEventDto> create(@Valid @RequestBody CalendarEventDto.CreateRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @PutMapping("/events/{externalId}")
    @PreAuthorize("isAuthenticated()")
    public CalendarEventDto update(@PathVariable UUID externalId,
                                   @Valid @RequestBody CalendarEventDto.CreateRequest req) {
        return service.update(externalId, req);
    }

    @DeleteMapping("/events/{externalId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable UUID externalId) {
        service.delete(externalId);
        return ResponseEntity.noContent().build();
    }
}
