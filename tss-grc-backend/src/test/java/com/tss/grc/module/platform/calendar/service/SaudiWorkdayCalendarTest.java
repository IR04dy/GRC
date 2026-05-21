package com.tss.grc.module.platform.calendar.service;

import com.tss.grc.module.platform.calendar.entity.CalendarEvent;
import com.tss.grc.module.platform.calendar.repository.CalendarEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Locks the Saudi-workday math against the cases that bite a buyer:
 *   - Sun-Thu working week (5 days, not 7)
 *   - Fri + Sat are weekend (not Sat + Sun)
 *   - Public holidays from V134 are excluded from work
 *   - addBusinessDays() walks the calendar, not just the days array
 *   - nextWorkday() coerces a weekend / holiday start onto Sunday
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SaudiWorkdayCalendarTest {

    private static final Long ORG_ID = 1L;

    @Mock  private CalendarEventRepository repo;
    @InjectMocks private SaudiWorkdayCalendar workdays;

    private void noHolidays() {
        when(repo.findHolidaysInWindow(anyLong(), any(), any())).thenReturn(List.of());
    }

    private void holidaysOn(LocalDate... dates) {
        List<CalendarEvent> rows = java.util.Arrays.stream(dates).map(d -> {
            CalendarEvent e = new CalendarEvent();
            e.setStartsAt(d.atStartOfDay());
            e.setIsHoliday(true);
            return e;
        }).toList();
        when(repo.findHolidaysInWindow(anyLong(), any(), any())).thenReturn(rows);
    }

    // ─── isWorkday ─────────────────────────────────────────────────────

    @Test void isWorkday_sundayIsWorkday() {
        noHolidays();
        // 2026-05-24 is a Sunday.
        assertThat(workdays.isWorkday(ORG_ID, LocalDate.of(2026, 5, 24))).isTrue();
    }

    @Test void isWorkday_thursdayIsWorkday() {
        noHolidays();
        // 2026-05-28 is a Thursday.
        assertThat(workdays.isWorkday(ORG_ID, LocalDate.of(2026, 5, 28))).isTrue();
    }

    @Test void isWorkday_fridayIsWeekend() {
        noHolidays();
        // 2026-05-29 is a Friday. In Saudi this is weekend, NOT a work day.
        // Locking this — a regression here would break the F8 reminder
        // scheduler (Friday fires would land out of office hours).
        assertThat(workdays.isWorkday(ORG_ID, LocalDate.of(2026, 5, 29))).isFalse();
    }

    @Test void isWorkday_saturdayIsWeekend() {
        noHolidays();
        // 2026-05-30 is a Saturday — also weekend in Saudi.
        assertThat(workdays.isWorkday(ORG_ID, LocalDate.of(2026, 5, 30))).isFalse();
    }

    @Test void isWorkday_publicHolidayIsNotWorkday() {
        // National Day — fixed 23 September 2026, a Wednesday.
        // Wednesday is Sun-Thu so the day-of-week test passes; the
        // holiday lookup is what knocks it out.
        holidaysOn(LocalDate.of(2026, 9, 23));
        assertThat(workdays.isWorkday(ORG_ID, LocalDate.of(2026, 9, 23))).isFalse();
    }

    // ─── addBusinessDays ────────────────────────────────────────────────

    @Test void addBusinessDays_skipsTheWeekend() {
        noHolidays();
        // Thu 2026-05-28 + 1 BD = Sun 2026-05-31 (Fri+Sat skipped).
        assertThat(workdays.addBusinessDays(ORG_ID, LocalDate.of(2026, 5, 28), 1))
            .isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test void addBusinessDays_skipsHolidayInTheMiddle() {
        // Founding Day (Sun 2026-02-22) is a Sunday — a normal
        // workday by day-of-week, but a public holiday so the
        // workday count skips it. Start Thu 2026-02-19, +2 BD:
        //   Thu+1 = Sun 22 = HOLIDAY (skip)
        //   Thu+1 (real) = Mon 23
        //   +1 more = Tue 24
        holidaysOn(LocalDate.of(2026, 2, 22));
        assertThat(workdays.addBusinessDays(ORG_ID, LocalDate.of(2026, 2, 19), 2))
            .isEqualTo(LocalDate.of(2026, 2, 24));
    }

    @Test void addBusinessDays_negativeStepsBackwards() {
        noHolidays();
        // Sun 2026-05-31 - 1 BD = Thu 2026-05-28 (Fri+Sat skipped backwards).
        assertThat(workdays.addBusinessDays(ORG_ID, LocalDate.of(2026, 5, 31), -1))
            .isEqualTo(LocalDate.of(2026, 5, 28));
    }

    // ─── nextWorkday ───────────────────────────────────────────────────

    @Test void nextWorkday_coercesFridayOntoSunday() {
        noHolidays();
        // The F8 reminder scheduler's bread-and-butter case: a
        // deadline fires on Friday — push the actual notification
        // to Sunday morning so the buyer sees it at the start of
        // their workweek, not on the weekend.
        LocalDate friday = LocalDate.of(2026, 5, 29);
        assertThat(workdays.nextWorkday(ORG_ID, friday))
            .isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test void nextWorkday_workdayInputReturnedUnchanged() {
        noHolidays();
        LocalDate tuesday = LocalDate.of(2026, 5, 26);
        assertThat(workdays.nextWorkday(ORG_ID, tuesday)).isEqualTo(tuesday);
    }

    @Test void nextWorkday_skipsConsecutiveHolidays() {
        // Eid Al-Fitr 2026 (provisional V134 dates 20-23 March). If a
        // deadline fires on day-1 of Eid, push to the next workday
        // after the whole Eid window. 20 March 2026 is a Friday;
        // 21-22 are Sat+Sun (Sun is Day 2 of Eid — holiday); 23 is
        // Mon (Day 3 of Eid — holiday). 24 Tuesday is the first
        // post-Eid workday.
        holidaysOn(
            LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 21),
            LocalDate.of(2026, 3, 22), LocalDate.of(2026, 3, 23));
        assertThat(workdays.nextWorkday(ORG_ID, LocalDate.of(2026, 3, 20)))
            .isEqualTo(LocalDate.of(2026, 3, 24));
    }

    @Test void nextWorkday_localDateTimeOverloadKeepsOriginalTimeOnWorkday() {
        noHolidays();
        LocalDateTime mondayMorning = LocalDate.of(2026, 5, 25).atTime(9, 30);
        assertThat(workdays.nextWorkday(ORG_ID, mondayMorning)).isEqualTo(mondayMorning);
    }
}
