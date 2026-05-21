package com.tss.grc.module.platform.calendar.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.calendar.entity.CalendarEvent;
import com.tss.grc.module.platform.calendar.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F8 reminder scheduler — fires {@code REMINDER_DUE} domain events for
 * calendar events whose reminder ladder (V133 {@code reminder_days})
 * crosses today.
 *
 * <p>How it works:
 * <ol>
 *   <li>Every hour we scan upcoming calendar events (next 90 days) per
 *       tenant via {@link CalendarEventRepository#findUpcomingWithReminders}.</li>
 *   <li>For each event, we parse the ladder (e.g. {@code "14,7,1,0"})
 *       and compute the nominal fire date as {@code startsAt - daysBefore}.</li>
 *   <li>If the nominal fire date lands on a Saudi weekend or public
 *       holiday, we shift it <em>backwards</em> to the preceding workday
 *       — better to remind people slightly early than have a Friday
 *       deadline pass without a Thursday nudge.</li>
 *   <li>If today equals the (possibly shifted) fire date AND we haven't
 *       fired this stop before (audited via a "REMINDER_FIRED:Nd" row),
 *       we publish a {@link DomainEvent} with trigger
 *       {@code REMINDER_DUE} and write an audit row to mark the fire.</li>
 * </ol>
 *
 * <p>Idempotency is by audit-log lookup, not a marker column — that
 * keeps V133's schema clean and lets ops re-run the scheduler safely
 * after a partial outage (it will not double-fire the same stop). The
 * AutomationRuleEngine on the other end of the {@code DomainEvent}
 * already handles tenant scoping and rule deduplication.
 *
 * <p>The job is intentionally cheap: hourly tick, bounded 90-day
 * lookahead, in-memory dedup of audit rows per event. If reminder
 * volume grows, swap the audit-log dedup for a dedicated
 * {@code calendar_reminder_fires(event_id, days_before)} table.
 */
@Component
@RequiredArgsConstructor
public class CalendarReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(CalendarReminderScheduler.class);

    /** Audit action_type marker. Format {@code "REMINDER_FIRED:7d"} so
     *  the per-stop dedup is exact. */
    static final String AUDIT_PREFIX = "REMINDER_FIRED:";

    /** F8 domain trigger emitted when a reminder is due. */
    static final String TRIGGER = "REMINDER_DUE";

    private final CalendarEventRepository repo;
    private final AuditLogRepository auditRepo;
    private final AuditLogService audit;
    private final SaudiWorkdayCalendar workdays;
    private final ApplicationEventPublisher publisher;

    /**
     * Hourly tick. {@code initialDelayString} gives the app a minute
     * to finish warming up before the first scan. Configurable via
     * {@code grc.calendar.reminder-period-ms} and
     * {@code grc.calendar.reminder-initial-delay-ms}.
     */
    @Scheduled(fixedDelayString = "${grc.calendar.reminder-period-ms:3600000}",
               initialDelayString = "${grc.calendar.reminder-initial-delay-ms:60000}")
    public void tick() {
        LocalDate today = LocalDate.now();
        for (Long orgId : repo.findDistinctOrganisationIds()) {
            try {
                TenantContext.setCurrentTenant(orgId);
                int fired = scanTenant(orgId, today);
                if (fired > 0) {
                    log.info("[calendar reminder] org={} fired {} reminder(s) for {}",
                        orgId, fired, today);
                }
            } catch (Exception ex) {
                log.warn("[calendar reminder] org={} scan failed: {}", orgId, ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * Process one tenant. Returns the number of REMINDER_DUE events
     * fired. Visible for tests.
     */
    int scanTenant(Long orgId, LocalDate today) {
        LocalDateTime windowStart = today.atStartOfDay();
        // 90-day lookahead — covers monthly + quarterly cadences without
        // scanning the whole future.
        LocalDateTime windowEnd = today.plusDays(90).atStartOfDay();
        List<CalendarEvent> upcoming = repo.findUpcomingWithReminders(orgId, windowStart, windowEnd);
        int fired = 0;

        for (CalendarEvent ev : upcoming) {
            Set<Integer> firedStops = previouslyFiredStops(orgId, ev);
            int[] stops = parseLadder(ev.getReminderDays());
            for (int days : stops) {
                if (firedStops.contains(days)) continue;
                LocalDate nominal = ev.getStartsAt().toLocalDate().minusDays(days);
                LocalDate shifted = shiftBackToWorkday(orgId, nominal);
                if (!shifted.equals(today)) continue;
                if (fireOne(orgId, ev, days, nominal, shifted)) fired++;
            }
        }
        return fired;
    }

    /**
     * Emit a single {@code REMINDER_DUE} event + audit marker. Returns
     * true if the fire was recorded, false on a recoverable error.
     */
    private boolean fireOne(Long orgId, CalendarEvent ev, int days,
                            LocalDate nominal, LocalDate shifted) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("calendarEventId", ev.getId());
        payload.put("eventType", nullSafe(ev.getEventType()));
        payload.put("titleEn", nullSafe(ev.getTitleEn()));
        payload.put("titleAr", nullSafe(ev.getTitleAr()));
        payload.put("startsAt", ev.getStartsAt() == null ? "" : ev.getStartsAt().toString());
        payload.put("daysBefore", days);
        payload.put("nominalFireDate", nominal.toString());
        payload.put("actualFireDate", shifted.toString());
        payload.put("workdayShifted", !nominal.equals(shifted));
        payload.put("severity", nullSafe(ev.getSeverity()));
        payload.put("entityType", nullSafe(ev.getEntityType()));
        payload.put("entityId", ev.getEntityId() == null ? "" : ev.getEntityId().toString());
        payload.put("ownerId", ev.getOwnerId() == null ? "" : ev.getOwnerId().toString());

        DomainEvent de = new DomainEvent(orgId, TRIGGER, "CalendarEvent", ev.getId(),
            "reminder:" + ev.getId() + ":" + days, payload);

        try {
            publisher.publishEvent(de);
            audit.log(AUDIT_PREFIX + days + "d", "CalendarEvent", ev.getId(),
                "Reminder fired " + days + "d before '" + nullSafe(ev.getTitleEn())
                + "' (event starts " + (ev.getStartsAt() == null ? "?" : ev.getStartsAt().toLocalDate())
                + (nominal.equals(shifted) ? "" : ", shifted from " + nominal + " due to workday rule")
                + ")");
            return true;
        } catch (RuntimeException ex) {
            log.warn("[calendar reminder] fire failed for event={} days={}: {}",
                ev.getId(), days, ex.getMessage());
            return false;
        }
    }

    /**
     * Loads the set of "REMINDER_FIRED:Nd" audit rows already written
     * for this event, returns the N values. Bounded by the entity-id
     * index on audit_log so the lookup is cheap.
     */
    private Set<Integer> previouslyFiredStops(Long orgId, CalendarEvent ev) {
        Set<Integer> out = new HashSet<>();
        List<AuditLogEntry> rows = auditRepo
            .findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                orgId, "CalendarEvent", ev.getId());
        for (AuditLogEntry r : rows) {
            String t = r.getActionType();
            if (t == null || !t.startsWith(AUDIT_PREFIX)) continue;
            String tail = t.substring(AUDIT_PREFIX.length());
            if (tail.endsWith("d")) tail = tail.substring(0, tail.length() - 1);
            try { out.add(Integer.parseInt(tail)); }
            catch (NumberFormatException ignored) { /* tolerate */ }
        }
        return out;
    }

    /**
     * If {@code nominal} is a Saudi weekend or public holiday, walk
     * back one day at a time until we hit a workday. Returns
     * {@code nominal} unchanged if it's already a workday. We push
     * <em>backwards</em> rather than forwards because a Friday/Saturday
     * deadline shouldn't have its prep window swallowed by the weekend.
     */
    private LocalDate shiftBackToWorkday(Long orgId, LocalDate nominal) {
        LocalDate d = nominal;
        // Cap iterations defensively — a stuck calendar shouldn't loop.
        for (int i = 0; i < 14 && !workdays.isWorkday(orgId, d); i++) {
            d = d.minusDays(1);
        }
        return d;
    }

    /**
     * Parse a "7,1,0" reminder string to {@code [7, 1, 0]}. Bad tokens
     * are skipped, not thrown. Visible for tests.
     */
    static int[] parseLadder(String ladder) {
        if (ladder == null || ladder.isBlank()) return new int[0];
        String[] toks = ladder.split(",");
        int[] out = new int[toks.length];
        int n = 0;
        for (String t : toks) {
            try {
                int v = Integer.parseInt(t.trim());
                if (v >= 0) out[n++] = v;
            } catch (NumberFormatException ignored) {}
        }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    private static String nullSafe(String v) { return v == null ? "" : v; }
}
