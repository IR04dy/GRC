package com.tss.grc.module.platform.calendar.service;

import com.tss.grc.module.platform.calendar.dto.CalendarEventDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the .ics serializer. We're not validating against
 * a full RFC-5545 parser — the assertions hit the parts buyers
 * actually care about: round-trippable UID, correct DTSTART format
 * for both all-day and timed events, VALARM ladder expansion,
 * RRULE emission, and TEXT-escape of commas / semicolons.
 */
class IcsBuilderTest {

    private static CalendarEventDto allDay(String type, LocalDate on, String titleEn, String titleAr) {
        return CalendarEventDto.builder()
            .externalId(UUID.nameUUIDFromBytes((type + on + titleEn).getBytes()))
            .source("AGGREGATED")
            .titleEn(titleEn)
            .titleAr(titleAr)
            .eventType(type)
            .startsAt(on.atStartOfDay())
            .allDay(true)
            .build();
    }

    @Test
    void buildEmitsValidCalendarSkeleton() {
        String body = IcsBuilder.build(List.of(), "Empty cal");
        assertThat(body)
            .startsWith("BEGIN:VCALENDAR\r\n")
            .contains("VERSION:2.0")
            .contains("PRODID:-//TSS//GRC Platform//EN")
            .contains("X-WR-CALNAME:Empty cal")
            .contains("X-WR-TIMEZONE:Asia/Riyadh")
            .endsWith("END:VCALENDAR\r\n");
    }

    @Test
    void allDayEventEmitsValueDateForm() {
        CalendarEventDto e = allDay("CAPA_DUE", LocalDate.of(2026, 6, 15),
            "Patch SAMA-D2 controls", "ترقيع ضوابط ساما D2");
        String body = IcsBuilder.build(List.of(e), "Test");

        assertThat(body).contains("BEGIN:VEVENT")
                        .contains("DTSTART;VALUE=DATE:20260615")
                        .contains("DTEND;VALUE=DATE:20260616") // open end
                        .contains("CATEGORIES:CAPA_DUE")
                        .contains("SUMMARY:Patch SAMA-D2 controls — ترقيع ضوابط ساما D2")
                        .contains("UID:")
                        .contains("@tss-grc")
                        .contains("END:VEVENT");
    }

    @Test
    void timedEventEmitsUtcDtstart() {
        CalendarEventDto e = CalendarEventDto.builder()
            .externalId(UUID.randomUUID())
            .source("USER")
            .titleEn("Risk committee")
            .eventType("MEETING")
            .startsAt(LocalDateTime.of(2026, 6, 15, 14, 0))  // 14:00 Riyadh = 11:00 UTC
            .endsAt(LocalDateTime.of(2026, 6, 15, 15, 0))
            .allDay(false)
            .location("HQ — Boardroom 3")
            .build();
        String body = IcsBuilder.build(List.of(e), "T");

        assertThat(body).contains("DTSTART:20260615T110000Z")
                        .contains("DTEND:20260615T120000Z")
                        .contains("LOCATION:HQ — Boardroom 3");
        // No VALUE=DATE form for timed events.
        assertThat(body).doesNotContain("DTSTART;VALUE=DATE");
    }

    @Test
    void reminderLadderExpandsToOneValarmPerStop() {
        CalendarEventDto e = allDay("REPORT_DEADLINE", LocalDate.of(2026, 7, 1),
            "Q2 SAMA filing", "إيداع ساما الربع 2");
        e.setReminderDays("14,7,1");
        String body = IcsBuilder.build(List.of(e), "T");

        int alarmCount = body.split("BEGIN:VALARM", -1).length - 1;
        assertThat(alarmCount).isEqualTo(3);
        assertThat(body).contains("TRIGGER:-P14D")
                        .contains("TRIGGER:-P7D")
                        .contains("TRIGGER:-P1D");
    }

    @Test
    void invalidReminderTokensAreSkippedNotCrashed() {
        CalendarEventDto e = allDay("REMINDER", LocalDate.of(2026, 8, 1), "x", "x");
        e.setReminderDays("7,abc,  ,3");
        String body = IcsBuilder.build(List.of(e), "T");
        int alarmCount = body.split("BEGIN:VALARM", -1).length - 1;
        assertThat(alarmCount).isEqualTo(2);
        assertThat(body).contains("TRIGGER:-P7D")
                        .contains("TRIGGER:-P3D");
    }

    @Test
    void recurrenceEmitsRrule() {
        CalendarEventDto e = allDay("AUDIT_DAY", LocalDate.of(2026, 1, 5), "Quarterly audit", "تدقيق ربع سنوي");
        e.setRecurrence("QUARTERLY");
        e.setRecurrenceUntil(LocalDate.of(2026, 12, 31));
        String body = IcsBuilder.build(List.of(e), "T");
        assertThat(body).contains("RRULE:FREQ=MONTHLY;INTERVAL=3")
                        .contains("UNTIL=20261231T235959Z");
    }

    @Test
    void escapeHandlesCommasSemicolonsAndNewlines() {
        CalendarEventDto e = allDay("MEETING", LocalDate.of(2026, 5, 1),
            "Q1 review, owner: CFO; status: open\nlate-running",
            "مراجعة, ربع 1");
        String body = IcsBuilder.build(List.of(e), "T");
        // commas and semicolons in SUMMARY must be backslash-escaped.
        assertThat(body).contains("SUMMARY:Q1 review\\, owner: CFO\\; status: open\\nlate-running — مراجعة\\, ربع 1");
    }

    @Test
    void prioritySeverityMapping() {
        CalendarEventDto e = allDay("CAPA_DUE", LocalDate.of(2026, 5, 1), "x", "x");
        e.setSeverity("CRITICAL");
        String body = IcsBuilder.build(List.of(e), "T");
        assertThat(body).contains("PRIORITY:1")
                        .contains("X-TSS-SEVERITY:CRITICAL");
    }
}
