package com.tss.grc.module.sust.evidence.service;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-B — proves the evidence expiry sweep cadence is driven by the
 * {@code evidence_sweep_cron} setting (dead config before S-B).
 */
class SustainabilityEvidenceExpirySchedulerTest {

    private static final ZoneId Z = ZoneId.of("Asia/Riyadh");
    private static final ZonedDateTime FROM = ZonedDateTime.of(2026, 7, 2, 0, 0, 0, 0, Z);

    @Test
    void nextFire_dailyAtSix_fromMidnight_isSameDaySixAm() {
        ZonedDateTime next = SustainabilityEvidenceExpiryScheduler.nextFire("0 0 6 * * *", FROM);
        assertThat(next.getHour()).isEqualTo(6);
        assertThat(next.toLocalDate()).isEqualTo(FROM.toLocalDate());
    }

    @Test
    void nextFire_changesWhenCronChanges() {
        ZonedDateTime six      = SustainabilityEvidenceExpiryScheduler.nextFire("0 0 6 * * *", FROM);
        ZonedDateTime midnight = SustainabilityEvidenceExpiryScheduler.nextFire("0 0 0 * * *", FROM);

        // Different cron → different next fire: the setting drives scheduling.
        assertThat(six).isNotEqualTo(midnight);
        assertThat(six.getHour()).isEqualTo(6);
        // From exactly 00:00, the next 00:00 fire is the following day.
        assertThat(midnight.getHour()).isEqualTo(0);
        assertThat(midnight.toLocalDate()).isEqualTo(FROM.toLocalDate().plusDays(1));
    }

    @Test
    void nextFire_invalidCron_fallsBackToDefault() {
        ZonedDateTime next = SustainabilityEvidenceExpiryScheduler.nextFire("not-a-cron", FROM);
        // Default "0 0 6 * * *" → 06:00 same day, so a bad setting never disables the sweep.
        assertThat(next.getHour()).isEqualTo(6);
        assertThat(next.toLocalDate()).isEqualTo(FROM.toLocalDate());
    }
}
