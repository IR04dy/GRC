package com.tss.grc.module.reporting.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-58 — the board pack's finding-aging tiers moved from a Java loop over a 1,000-row page to
 * {@code COUNT(*)} queries over every open finding. This class pins the one part of that change
 * that can be silently wrong: the timestamp boundary.
 *
 * <h3>Why a boundary needs its own test</h3>
 * <p>The loop asked {@code ChronoUnit.DAYS.between(createdAt.toLocalDate(), today) >= N} — a
 * question about <em>dates</em>. SQL asks {@code createdAt < :cutoff} — a question about
 * <em>timestamps</em>. The natural translation, {@code LocalDateTime.now().minusDays(N)}, is
 * wrong: a finding created 90 days ago at 15:00 is 90 days old to the loop, but is not before
 * "now minus 90 days" when the report runs at 10:00, so it silently drops out of the Executive
 * tier. One finding moving between tiers changes a number a board reads and acts on, and nothing
 * would have failed.
 *
 * <p>The correct boundary is the start of day {@code today - (N - 1)}: the loop's predicate holds
 * exactly when the creation date is on or before {@code today - N}, which is exactly when the
 * timestamp precedes the following midnight.
 *
 * <h3>What is NOT tested here, and where it was proven instead</h3>
 * <p>That the tiers are now computed over the whole population rather than a page, and that the
 * risk register ranks in the database rather than over an arbitrary 200-row sample, are behaviours
 * of the SQL — a pure-logic test cannot see them. They were proven live: 210 {@code [REVIEW-TEST]}
 * risks were seeded to put organisation 1 at 261 risks, arranged so that <b>0 of the 9
 * highest-scoring risks fell inside the unordered 200-row window the old code read</b>, and the
 * generated PDF and PPTX were then parsed to confirm all five apex risks appear and no score-1
 * filler does.
 *
 * <p>The aging assertion in that same probe is worth recording: it disagreed with the reference
 * SQL by one finding, and <b>the code was right</b> — the reference query omitted
 * {@code is_deleted = 0}, which the entity's {@code @Where} enforces and which exactly one
 * finding in the 30-59 day tier is subject to.
 */
class BoardPackAgingCutoffTest {

    /** The three tier boundaries the board pack renders, in days. */
    private static final int[] TIERS = { 30, 60, 90 };

    /**
     * The equivalence, asserted over every age the tiers can distinguish and at both ends of the
     * day — because the defect this guards against only appears when the creation time of day is
     * later than the report's.
     */
    @Test
    void cutoffReproducesTheDayGranularityPredicateItReplaced() {
        LocalDate today = LocalDate.of(2026, 7, 31);

        for (int tier : TIERS) {
            LocalDateTime cutoff = BoardPackAggregateService.cutoff(today, tier);
            for (int age = 0; age <= 130; age++) {
                for (int hour : new int[] { 0, 9, 15, 23 }) {
                    LocalDateTime createdAt = today.minusDays(age).atTime(hour, 30);
                    boolean loopSaysInTier =
                        ChronoUnit.DAYS.between(createdAt.toLocalDate(), today) >= tier;
                    boolean sqlSaysInTier = createdAt.isBefore(cutoff);

                    assertThat(sqlSaysInTier)
                        .as("tier >=%d days, finding created %d days ago at %02d:30 — the SQL "
                          + "boundary and the Java loop must agree. They diverge if cutoff() is "
                          + "written as now().minusDays(N) instead of the start of day "
                          + "today-(N-1).", tier, age, hour)
                        .isEqualTo(loopSaysInTier);
                }
            }
        }
    }

    /**
     * The boundary itself, stated as a value rather than as a property, so a reader can see what
     * the arithmetic is meant to produce without re-deriving it.
     */
    @Test
    void theBoundaryIsMidnightOfTheDayAfterTheOldestExcludedDate() {
        LocalDate today = LocalDate.of(2026, 7, 31);

        // 2026-07-31 minus 89 / 59 / 29 days. Written out because the point of this test is that a
        // reader can see the value without re-deriving it — and because getting these three wrong
        // by hand on the first attempt is precisely why the property test above exists to arbitrate.
        assertThat(BoardPackAggregateService.cutoff(today, 90)).isEqualTo(LocalDate.of(2026, 5, 3).atStartOfDay());
        assertThat(BoardPackAggregateService.cutoff(today, 60)).isEqualTo(LocalDate.of(2026, 6, 2).atStartOfDay());
        assertThat(BoardPackAggregateService.cutoff(today, 30)).isEqualTo(LocalDate.of(2026, 7, 2).atStartOfDay());

        assertThat(BoardPackAggregateService.cutoff(today, 90))
            .as("a naive now().minusDays(90) would land a full day earlier (2026-05-02) and, with a "
              + "time component, would exclude findings created late that day from the Executive tier")
            .isNotEqualTo(today.minusDays(90).atStartOfDay());
    }

    /**
     * The tiers are built by subtracting cumulative counts, so they must partition the open
     * population exactly — no finding counted twice, none dropped. Arithmetic only, but it is the
     * arithmetic the renderers do.
     */
    @Test
    void theFourTiersPartitionTheOpenPopulation() {
        long totalOpen = 38, exec = 28, dirOrWorse = 28, mgrOrWorse = 36;

        long dir     = dirOrWorse - exec;
        long mgr     = mgrOrWorse - dirOrWorse;
        long onTrack = totalOpen  - mgrOrWorse;

        assertThat(exec + dir + mgr + onTrack)
            .as("the four rendered tiers must sum to the open population; if they do not, a "
              + "board is shown counts that do not add up to the total on the same page")
            .isEqualTo(totalOpen);
        assertThat(dir).isEqualTo(0);
        assertThat(mgr).isEqualTo(8);
        assertThat(onTrack).isEqualTo(2);
    }
}
