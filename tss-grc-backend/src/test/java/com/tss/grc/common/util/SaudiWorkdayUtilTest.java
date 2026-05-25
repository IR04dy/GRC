package com.tss.grc.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SaudiWorkdayUtil}.
 *
 * <p>The Saudi business week is Sun-Thu; Fri+Sat are the weekend. These
 * tests pin that semantics so the JDK default (Sat+Sun) cannot creep
 * back in unnoticed.
 */
class SaudiWorkdayUtilTest {

    // 2026-04-26 is a Sunday — the start of the Saudi business week.
    private static final LocalDateTime SUNDAY = LocalDateTime.of(2026, 4, 26, 9, 0);
    // 2026-04-29 is a Wednesday.
    private static final LocalDateTime WEDNESDAY = LocalDateTime.of(2026, 4, 29, 9, 0);
    // 2026-04-30 is a Thursday.
    private static final LocalDateTime THURSDAY = LocalDateTime.of(2026, 4, 30, 9, 0);
    // 2026-05-01 is a Friday (KSA weekend).
    private static final LocalDateTime FRIDAY = LocalDateTime.of(2026, 5, 1, 9, 0);

    // ─── addBusinessDays ───────────────────────────────────────────

    @Test
    void addBusinessDays_sundayPlusOne_isMonday() {
        // Sunday + 1 working day → Monday
        LocalDateTime result = SaudiWorkdayUtil.addBusinessDays(SUNDAY, 1);
        assertThat(result.getDayOfWeek()).hasToString("MONDAY");
        assertThat(result.toLocalDate()).isEqualTo("2026-04-27");
    }

    @Test
    void addBusinessDays_thursdayPlusOne_skipsFriAndSat_landsOnSunday() {
        // Thursday + 1 working day → Sunday (skip Fri + Sat)
        LocalDateTime result = SaudiWorkdayUtil.addBusinessDays(THURSDAY, 1);
        assertThat(result.getDayOfWeek()).hasToString("SUNDAY");
        assertThat(result.toLocalDate()).isEqualTo("2026-05-03");
    }

    @Test
    void addBusinessDays_wednesdayPlusFive_skipsOneWeekend_landsOnNextWednesday() {
        // Wednesday + 5 working days → Wed (Thu, Sun, Mon, Tue, Wed). Skips Fri+Sat once.
        LocalDateTime result = SaudiWorkdayUtil.addBusinessDays(WEDNESDAY, 5);
        assertThat(result.getDayOfWeek()).hasToString("WEDNESDAY");
        assertThat(result.toLocalDate()).isEqualTo("2026-05-06");
    }

    @Test
    void addBusinessDays_zero_returnsSameMoment() {
        LocalDateTime result = SaudiWorkdayUtil.addBusinessDays(SUNDAY, 0);
        assertThat(result).isEqualTo(SUNDAY);
    }

    @Test
    void addBusinessDays_fromFriday_lands20BusinessDaysOnTheCorrectDate() {
        // Friday is a weekend day in KSA — the counter begins on the
        // next calendar day. Stepping +20 business days from Fri
        // 2026-05-01 lands on Thursday 2026-05-28 (4 weekends are
        // skipped between).
        LocalDateTime result = SaudiWorkdayUtil.addBusinessDays(FRIDAY, 20);
        // Sanity: result must NOT be Friday or Saturday.
        assertThat(result.getDayOfWeek().getValue()).isNotIn(5, 6);
        assertThat(result.toLocalDate()).isEqualTo("2026-05-28");
    }

    @Test
    void addBusinessDays_negative_throws() {
        assertThatThrownBy(() -> SaudiWorkdayUtil.addBusinessDays(SUNDAY, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── businessDaysBetween ───────────────────────────────────────

    @Test
    void businessDaysBetween_sameDay_isZero() {
        assertThat(SaudiWorkdayUtil.businessDaysBetween(SUNDAY, SUNDAY)).isZero();
    }

    @Test
    void businessDaysBetween_sundayToThursday_isFour() {
        // Sun -> Thu = 4 business days (Mon, Tue, Wed, Thu).
        long days = SaudiWorkdayUtil.businessDaysBetween(SUNDAY, THURSDAY);
        assertThat(days).isEqualTo(4);
    }

    @Test
    void businessDaysBetween_acrossWeekend_excludesFridayAndSaturday() {
        // Thursday 2026-04-30 → Sunday 2026-05-03 = 1 business day (only Sunday).
        LocalDateTime sundayAfter = LocalDateTime.of(2026, 5, 3, 9, 0);
        long days = SaudiWorkdayUtil.businessDaysBetween(THURSDAY, sundayAfter);
        assertThat(days).isEqualTo(1);
    }

    @Test
    void businessDaysBetween_reversedDates_isNegated() {
        long forward = SaudiWorkdayUtil.businessDaysBetween(SUNDAY, THURSDAY);
        long reverse = SaudiWorkdayUtil.businessDaysBetween(THURSDAY, SUNDAY);
        assertThat(reverse).isEqualTo(-forward);
    }
}
