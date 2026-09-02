package com.tss.grc.module.platform.calendar.service;

import com.tss.grc.module.platform.calendar.dto.CalendarEventDto;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Minimal RFC-5545 iCalendar serializer for the Compliance Calendar.
 *
 * <p>Why minimal: buyers ask for "subscribe from Outlook / Google",
 * which only needs VEVENT + VALARM. We don't ship VTIMEZONE blocks —
 * all timed events are emitted in UTC ({@code Z} suffix), which every
 * mainstream client interprets correctly. All-day events use the
 * {@code VALUE=DATE} form so they render on the local calendar day.
 *
 * <p>Reminder ladder ({@code "7,1,0"} = 7 days / 1 day / day-of) is
 * unpacked into one VALARM per stop. The trigger uses a negative
 * duration relative to DTSTART, which is the only mode all clients
 * support consistently.
 *
 * <p>Recurrence: when the persisted event has a recurrence rule, we
 * emit a single RRULE so the client expands it locally. UNTIL is
 * inclusive in iCal so we add one day to the stored
 * {@code recurrenceUntil} (which is also inclusive in our model).
 */
public final class IcsBuilder {

    private static final DateTimeFormatter DT_UTC =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DT_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    private IcsBuilder() {}

    public static String build(List<CalendarEventDto> events, String calendarName) {
        StringBuilder sb = new StringBuilder(events.size() * 256 + 256);
        sb.append("BEGIN:VCALENDAR\r\n")
          .append("VERSION:2.0\r\n")
          .append("PRODID:-//TSS//GRC Platform//EN\r\n")
          .append("CALSCALE:GREGORIAN\r\n")
          .append("METHOD:PUBLISH\r\n")
          .append("X-WR-CALNAME:").append(escape(calendarName)).append("\r\n")
          .append("X-WR-TIMEZONE:Asia/Riyadh\r\n");

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        String dtstamp = DT_UTC.format(nowUtc);

        for (CalendarEventDto e : events) {
            appendEvent(sb, e, dtstamp);
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private static void appendEvent(StringBuilder sb, CalendarEventDto e, String dtstamp) {
        if (e.getStartsAt() == null) return;

        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:").append(uid(e)).append("@tss-grc\r\n");
        sb.append("DTSTAMP:").append(dtstamp).append("\r\n");

        boolean allDay = Boolean.TRUE.equals(e.getAllDay());
        if (allDay) {
            sb.append("DTSTART;VALUE=DATE:")
              .append(DT_DATE.format(e.getStartsAt().toLocalDate())).append("\r\n");
            LocalDateTime end = e.getEndsAt() != null ? e.getEndsAt()
                : e.getStartsAt().plusDays(1);
            sb.append("DTEND;VALUE=DATE:")
              .append(DT_DATE.format(end.toLocalDate())).append("\r\n");
        } else {
            sb.append("DTSTART:").append(toUtc(e.getStartsAt())).append("\r\n");
            LocalDateTime end = e.getEndsAt() != null ? e.getEndsAt()
                : e.getStartsAt().plusHours(1);
            sb.append("DTEND:").append(toUtc(end)).append("\r\n");
        }

        sb.append("SUMMARY:").append(escape(summary(e))).append("\r\n");

        String desc = description(e);
        if (!desc.isEmpty()) {
            sb.append("DESCRIPTION:").append(escape(desc)).append("\r\n");
        }
        if (e.getLocation() != null && !e.getLocation().isBlank()) {
            sb.append("LOCATION:").append(escape(e.getLocation())).append("\r\n");
        }

        sb.append("CATEGORIES:").append(escape(safe(e.getEventType()))).append("\r\n");

        if (e.getSeverity() != null && !e.getSeverity().isBlank()) {
            sb.append("PRIORITY:").append(priorityFor(e.getSeverity())).append("\r\n");
            sb.append("X-TSS-SEVERITY:").append(escape(e.getSeverity())).append("\r\n");
        }

        if (e.getEntityType() != null && e.getEntityId() != null) {
            sb.append("X-TSS-ENTITY:")
              .append(escape(e.getEntityType()))
              .append('/')
              .append(e.getEntityId())
              .append("\r\n");
        }

        if (e.getRecurrence() != null && !"NONE".equals(e.getRecurrence())) {
            String rrule = rruleFor(e);
            if (rrule != null) sb.append(rrule).append("\r\n");
        }

        // VALARM ladder — one per "days before" stop.
        if (e.getReminderDays() != null && !e.getReminderDays().isBlank()) {
            for (String tok : e.getReminderDays().split(",")) {
                String t = tok.trim();
                if (t.isEmpty()) continue;
                try {
                    int days = Integer.parseInt(t);
                    sb.append("BEGIN:VALARM\r\n")
                      .append("ACTION:DISPLAY\r\n")
                      .append("DESCRIPTION:").append(escape(summary(e))).append("\r\n")
                      .append("TRIGGER:-P").append(days).append("D\r\n")
                      .append("END:VALARM\r\n");
                } catch (NumberFormatException ignored) {
                    // tolerate junk in the ladder string
                }
            }
        }

        sb.append("END:VEVENT\r\n");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static String uid(CalendarEventDto e) {
        if (e.getExternalId() != null) return e.getExternalId().toString();
        // Fallback for any row that somehow lacks an externalId.
        return UUID.nameUUIDFromBytes(
            (safe(e.getEntityType()) + ":" + e.getEntityId() + ":"
             + safe(e.getEventType()) + ":" + e.getStartsAt()).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    private static String summary(CalendarEventDto e) {
        String en = safe(e.getTitleEn());
        String ar = safe(e.getTitleAr());
        if (en.isEmpty() && ar.isEmpty()) return safe(e.getEventType());
        if (en.isEmpty()) return ar;
        if (ar.isEmpty()) return en;
        return en + " — " + ar;
    }

    private static String description(CalendarEventDto e) {
        StringBuilder d = new StringBuilder();
        if (e.getDescriptionEn() != null && !e.getDescriptionEn().isBlank()) {
            d.append(e.getDescriptionEn());
        }
        if (e.getDescriptionAr() != null && !e.getDescriptionAr().isBlank()) {
            if (d.length() > 0) d.append("\n\n");
            d.append(e.getDescriptionAr());
        }
        if (e.getOwnerLabel() != null && !e.getOwnerLabel().isBlank()) {
            if (d.length() > 0) d.append("\n\n");
            d.append("Owner: ").append(e.getOwnerLabel());
        }
        if (e.getStatus() != null && !e.getStatus().isBlank()) {
            if (d.length() > 0) d.append("\n");
            d.append("Status: ").append(e.getStatus());
        }
        return d.toString();
    }

    private static String rruleFor(CalendarEventDto e) {
        String freq = switch (e.getRecurrence()) {
            case "DAILY"      -> "DAILY";
            case "WEEKLY"     -> "WEEKLY";
            case "MONTHLY"    -> "MONTHLY";
            case "QUARTERLY"  -> "MONTHLY;INTERVAL=3";
            case "ANNUAL"     -> "YEARLY";
            default           -> null;
        };
        if (freq == null) return null;
        StringBuilder rrule = new StringBuilder("RRULE:FREQ=").append(freq);
        if (e.getRecurrenceUntil() != null) {
            // UNTIL is inclusive in iCal; ours is too, so emit end-of-day UTC.
            rrule.append(";UNTIL=").append(
                DT_UTC.format(e.getRecurrenceUntil().atTime(23, 59, 59))
            );
        }
        return rrule.toString();
    }

    private static String toUtc(LocalDateTime dt) {
        // Treat incoming as Riyadh local time, convert to UTC for the wire.
        return DT_UTC.format(dt.atZone(RIYADH).withZoneSameInstant(ZoneOffset.UTC));
    }

    /** Maps our severity label to RFC-5545 PRIORITY (1=highest, 9=lowest). */
    private static int priorityFor(String sev) {
        return switch (sev == null ? "" : sev.toUpperCase(Locale.ROOT)) {
            case "CRITICAL", "SEV-1", "SEV1" -> 1;
            case "HIGH",     "SEV-2", "SEV2" -> 3;
            case "MEDIUM",   "SEV-3", "SEV3" -> 5;
            case "LOW",      "SEV-4", "SEV4" -> 7;
            default                          -> 5;
        };
    }

    /** RFC-5545 §3.3.11 text escape — commas / semicolons / backslashes / newlines. */
    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case ';':  out.append("\\;");  break;
                case ',':  out.append("\\,");  break;
                case '\n': out.append("\\n");  break;
                case '\r': /* drop */          break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
