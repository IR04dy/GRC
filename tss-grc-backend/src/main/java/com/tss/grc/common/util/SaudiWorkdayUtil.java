package com.tss.grc.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Business-day arithmetic for the Saudi calendar.
 *
 * <p>The Saudi business week runs Sunday → Thursday. The weekend is
 * <strong>Friday and Saturday</strong> — not Saturday and Sunday — so
 * the JDK's {@code DayOfWeek.SATURDAY/SUNDAY} convention cannot be used
 * directly when computing SLA deadlines for SAMA Banking Consumer
 * Protection Principles (acknowledge in 5 working days, resolve in 20
 * working days), audit working-day budgets, vendor SLA reporting, etc.
 *
 * <p>This utility intentionally does NOT model public holidays (Eid
 * al-Fitr, Eid al-Adha, National Day, Founding Day). Those are
 * tenant-configurable in a future iteration; for now treating only the
 * Fri/Sat weekend gives ~95% accuracy and is the right Pareto cut.
 *
 * <p>All methods are pure and side-effect-free; the class is final and
 * has a private constructor.
 */
public final class SaudiWorkdayUtil {

    private SaudiWorkdayUtil() {}

    /**
     * Add {@code businessDays} working days to {@code start}, skipping
     * Friday + Saturday. Time-of-day is preserved.
     *
     * <p>Examples (assuming start time-of-day is preserved):
     * <ul>
     *   <li>Sunday + 1 → Monday</li>
     *   <li>Thursday + 1 → Sunday (skip Fri+Sat)</li>
     *   <li>Wednesday + 5 → next Wednesday (skip Fri+Sat once)</li>
     * </ul>
     */
    public static LocalDateTime addBusinessDays(LocalDateTime start, int businessDays) {
        if (businessDays < 0) {
            throw new IllegalArgumentException("businessDays must be >= 0");
        }
        LocalDateTime current = start;
        int added = 0;
        while (added < businessDays) {
            current = current.plusDays(1);
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.FRIDAY && dow != DayOfWeek.SATURDAY) {
                added++;
            }
        }
        return current;
    }

    /**
     * Count business days between two instants — exclusive of the start
     * date, inclusive of the end date. Friday + Saturday are not
     * counted.
     *
     * <p>If {@code start} is after {@code end}, the result is negated.
     */
    public static long businessDaysBetween(LocalDateTime start, LocalDateTime end) {
        LocalDate s = start.toLocalDate();
        LocalDate e = end.toLocalDate();
        if (s.isAfter(e)) {
            return -businessDaysBetween(end, start);
        }
        long days = 0;
        LocalDate cur = s;
        while (cur.isBefore(e)) {
            cur = cur.plusDays(1);
            DayOfWeek dow = cur.getDayOfWeek();
            if (dow != DayOfWeek.FRIDAY && dow != DayOfWeek.SATURDAY) {
                days++;
            }
        }
        return days;
    }
}
