package com.tss.grc.module.regreporting.service;

import com.tss.grc.module.regreporting.entity.RegReportObligation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-395 — {@code buildReference}'s WEEKLY arm built the reference from the ISO week
 * number ({@code weekOfWeekBasedYear()}) paired with the CALENDAR year
 * ({@code getYear()}). The ISO week number is measured against the week-BASED year, and
 * the two disagree at year boundaries — so two distinct weekly periods 52 weeks apart
 * produced the SAME reference on the SAMA/CMA regulatory-reporting register. The four
 * sibling arms (MONTHLY/QUARTERLY/SEMIANNUAL/ANNUAL) all pair {@code getYear()} with a
 * calendar sub-period, so only WEEKLY mixed the two calendars.
 *
 * <p>{@code buildReference} is package-private static — driven directly here, no Spring/DB.
 */
class RegReportingServiceTest {

    private static RegReportObligation obligation(String code, String frequency) {
        RegReportObligation o = new RegReportObligation();
        o.setCode(code);
        o.setFrequency(frequency);
        return o;
    }

    /** The discriminating leg: the year-boundary weekly reference uses the week-BASED year,
     *  so two distinct periods that used to collide are now distinct. */
    @Test void weeklyReference_usesTheWeekBasedYear_soYearBoundaryWeeksDoNotCollide() {
        RegReportObligation o = obligation("SAMA-DLR", "WEEKLY");
        // 2023-01-01 (Sun) is ISO week 52 of week-based-year 2022, but calendar year 2023.
        String jan1 = RegReportingService.buildReference(o, LocalDate.of(2023, 1, 1));
        // 2023-12-31 (Sun) is ISO week 52 of week-based-year 2023.
        String dec31 = RegReportingService.buildReference(o, LocalDate.of(2023, 12, 31));

        assertThat(jan1).isEqualTo("SAMA-DLR-W52-2022");   // pre-fix: "SAMA-DLR-W52-2023"
        assertThat(dec31).isEqualTo("SAMA-DLR-W52-2023");
        assertThat(jan1).isNotEqualTo(dec31);              // pre-fix these two collided
    }

    /** Guard (non-discriminating): a mid-year week is unchanged by the fix, because the
     *  week-based year equals the calendar year away from the boundary. */
    @Test void weeklyReference_isUnchangedForMidYearWeeks() {
        RegReportObligation o = obligation("SAMA-DLR", "WEEKLY");
        String midYear = RegReportingService.buildReference(o, LocalDate.of(2023, 6, 15));
        assertThat(midYear).startsWith("SAMA-DLR-W").endsWith("-2023");
    }

    /** Guard (non-discriminating): the sibling MONTHLY arm is untouched — the fix is surgical. */
    @Test void monthlyReference_isUnaffectedByTheWeeklyFix() {
        RegReportObligation o = obligation("SAMA-M", "MONTHLY");
        assertThat(RegReportingService.buildReference(o, LocalDate.of(2023, 3, 31)))
            .isEqualTo("SAMA-M-2023-03");
    }
}
