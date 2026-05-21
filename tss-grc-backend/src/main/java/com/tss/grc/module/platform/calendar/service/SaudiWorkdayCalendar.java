package com.tss.grc.module.platform.calendar.service;

import com.tss.grc.module.platform.calendar.repository.CalendarEventRepository;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Saudi-workday math. The Kingdom's working week runs <b>Sunday through
 * Thursday</b>; Friday and Saturday are the weekend. This helper combines
 * that fixed pattern with the public-holiday rows seeded in V134 to
 * answer two operational questions used across the platform:
 *
 *   - {@link #isWorkday(Long, LocalDate)} — was/is/will today be a
 *     working day for this tenant? Used by the F8 reminder scheduler
 *     to suppress weekend / holiday fires and push them to the next
 *     business day.
 *
 *   - {@link #addBusinessDays(Long, LocalDate, int)} — deadline math
 *     that auto-skips Friday/Saturday/holidays. Used by the buyer-
 *     facing "due in N business days" labels on CAPA / Finding /
 *     Workflow Submission detail pages, and by the report scheduler
 *     when planning quarterly filings.
 *
 * <p>Holiday lookups are per-tenant — different demo orgs can layer
 * extra local holidays on the V134 baseline without conflicting.
 * The holiday set is loaded fresh per call; if a hot path needs it
 * faster, drop a Caffeine cache here keyed on
 * (organisationId, year) with a 1-day TTL.
 */
@Component
public class SaudiWorkdayCalendar {

    private final CalendarEventRepository repo;

    public SaudiWorkdayCalendar(CalendarEventRepository repo) {
        this.repo = repo;
    }

    /**
     * Saudi weekend = {Friday, Saturday}. Encoded once so a future
     * tenant-level override (some private-sector companies still use
     * Thu-Fri or Fri-only) is a per-call argument away.
     */
    private static boolean isSaudiWeekend(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY;
    }

    /**
     * True if {@code date} is a business day for the given tenant —
     * i.e. it is Sun..Thu AND not a public holiday in {@code platform
     * .calendar_events}.
     */
    public boolean isWorkday(Long organisationId, LocalDate date) {
        if (isSaudiWeekend(date)) return false;
        return !isHoliday(organisationId, date);
    }

    /**
     * Is {@code date} marked as a Saudi public holiday for this
     * tenant? Loads holidays in a small ±60-day window so a long
     * computation (e.g. 90-day attestation cadence math) doesn't
     * scan the whole table.
     */
    public boolean isHoliday(Long organisationId, LocalDate date) {
        Set<LocalDate> holidays = loadHolidays(organisationId, date.minusDays(60), date.plusDays(60));
        return holidays.contains(date);
    }

    /**
     * Add {@code days} <em>business</em> days to {@code start} —
     * skipping Saudi weekends + public holidays. Negative
     * {@code days} subtracts. Always returns a workday.
     */
    public LocalDate addBusinessDays(Long organisationId, LocalDate start, int days) {
        LocalDate d = start;
        int step = Integer.signum(days);
        int remaining = Math.abs(days);
        while (remaining > 0) {
            d = d.plusDays(step);
            if (isWorkday(organisationId, d)) remaining--;
        }
        return d;
    }

    /**
     * The next business day on or after {@code from}. If {@code from}
     * itself is a workday, returns it unchanged. Used to coerce
     * weekend reminder fires onto Sunday.
     */
    public LocalDate nextWorkday(Long organisationId, LocalDate from) {
        LocalDate d = from;
        while (!isWorkday(organisationId, d)) d = d.plusDays(1);
        return d;
    }

    /** Convenience overload returning {@link LocalDateTime} (00:00 floor). */
    public LocalDateTime nextWorkday(Long organisationId, LocalDateTime from) {
        LocalDate d = nextWorkday(organisationId, from.toLocalDate());
        // Preserve original time-of-day if the date didn't shift.
        return d.equals(from.toLocalDate()) ? from : d.atStartOfDay();
    }

    private Set<LocalDate> loadHolidays(Long organisationId, LocalDate from, LocalDate to) {
        Set<LocalDate> out = new HashSet<>();
        repo.findHolidaysInWindow(organisationId, from.atStartOfDay(), to.atStartOfDay())
            .forEach(e -> out.add(e.getStartsAt().toLocalDate()));
        return out;
    }
}
