package com.tss.grc.module.reporting.service;

import com.tss.grc.module.reporting.entity.ReportSchedule;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-293 — the cadence vocabulary had three copies and the DATABASE held a token none of them knew.
 *
 * <p>{@code reporting.report_schedules} has <b>zero CHECK constraints</b>, so the vocabulary lived
 * only as {@code ReportSchedulerService#advanceNextRun}'s {@code case} labels and, separately, as
 * {@code CADENCE_OPTIONS} in {@code ReportSchedulesPage.tsx}. Those two agreed on
 * DAILY/WEEKLY/MONTHLY/QUARTERLY — and {@code seed_real_data.py} had written
 * <b>{@code SEMIANNUAL}</b> onto the "Semi-annual ISO 22301 / NCEMA BCM readiness" schedule, which
 * fell to the {@code default} arm and was therefore rescheduled <b>every day</b>: 13 consecutive
 * {@code FAILED} runs in dev and prod between 2026-08-11 and 2026-08-23.
 *
 * <p>🔑 <b>Every reader agreed and the stored data was the outlier.</b> F2-205's question — "how
 * many things read this, and which of them is the server?" — assumes the disagreement is between
 * readers, and here consistency among the readers is what made the third value invisible. F2-180's
 * mirror: there the screen offered categories the column's CHECK refused; here the column holds a
 * token every reader refuses, one loudly ({@code default -> throw} on {@code report_type}) and one
 * silently ({@code default -> plusDays(1)} on {@code cadence}).
 *
 * <p>⚠ Deliberately NOT a {@code ClosedVocabulary}: that primitive requires the name of the
 * {@code CHECK} it mirrors, and there is none on this table. The claim here is not about the schema
 * — it is <em>the scheduler can compute a next run for every one of these</em>, which is checkable
 * in Java, and this class checks it behaviourally rather than by matching {@code case} labels.
 *
 * <p>⚠ No CHECK migration rides with the fix, and the reason is recorded rather than assumed:
 * {@code report_type} is the larger open vocabulary on the same table — <b>7 of 11 live rows</b>
 * hold a type {@code renderBody} answers with {@code default -> throw}, so those seven reports have
 * never been delivered — and constraining {@code cadence} alone would leave the table's real
 * vocabulary problem open while looking addressed. Both want one migration once the report-type
 * question has an owner decision (F2-183: a constraint and its writers land together).
 */
class ReportCadenceContractTest {

    private static final Path PAGE = Path.of("..", "tss-grc-ui-v2", "src", "pages", "reporting",
                                             "ReportSchedulesPage.tsx");

    /**
     * Drive the real {@code advanceNextRun} and read the date it produced. Behavioural on purpose:
     * asserting the {@code case} labels exist would pass over an arm that computes the wrong thing,
     * and F2-292's harness lesson is that a leg whose name claims more than its assertion can see is
     * the family this register keeps catching.
     */
    private static LocalDateTime nextRunFor(String cadence, String anchor, String timeOfDay) {
        ReportSchedule s = new ReportSchedule();
        s.setId(1L);
        s.setName("[contract] " + cadence);
        s.setCadence(cadence);
        s.setCadenceAnchor(anchor);
        s.setTimeOfDay(timeOfDay);
        ReportSchedulerService svc = new ReportSchedulerService(
            null, null, null, null, null, null, null);
        ReflectionTestUtils.invokeMethod(svc, "advanceNextRun", s);
        return s.getNextRunAt();
    }

    /**
     * ⚠ <b>Distance from today cannot discriminate the fallback arm, and the first draft of this
     * gate assumed it could.</b> It asserted that no non-daily cadence lands within two days, and it
     * failed on <em>correct</em> code: {@code nextWeekly} reads the anchor as a day-of-week, so a
     * WEEKLY schedule legitimately lands tomorrow whenever tomorrow is the anchor day. A gate that
     * fails on working code trains the next reader to edit the assertion rather than the code
     * (F2-214), and a red leg is a finding only when the code is actually wrong (F2-206).
     *
     * <p>So each cadence is checked against a <b>property the {@code default} arm cannot produce</b>
     * — the anchor's own day-of-week or day-of-month, and the month a period must start in — with
     * every anchor chosen so that tomorrow's date cannot satisfy it by coincidence.
     */
    @Test
    void everyDeclaredCadenceIsComputedRatherThanFallingToTheDailyArm() {
        assertThat(ReportSchedulerService.SUPPORTED_CADENCES)
            .as("anti-vacuity: an emptied list would make every assertion below pass over nothing")
            .hasSizeGreaterThanOrEqualTo(5)
            .contains("SEMIANNUAL");

        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
        // anchors deliberately different from tomorrow's, so the fallback cannot satisfy them
        String weekdayAnchor = tomorrow.plusDays(3).getDayOfWeek().name();
        int domAnchor = tomorrow.getDayOfMonth() == 12 ? 13 : 12;

        List<String> unchecked = new ArrayList<>(ReportSchedulerService.SUPPORTED_CADENCES);

        LocalDateTime daily = nextRunFor("DAILY", null, "22:00");
        assertThat(daily.getHour()).isEqualTo(22);
        assertThat(daily).isAfter(LocalDateTime.now()).isBefore(LocalDateTime.now().plusDays(2));
        unchecked.remove("DAILY");

        LocalDateTime weekly = nextRunFor("WEEKLY", weekdayAnchor, "22:00");
        assertThat(weekly.getDayOfWeek().name())
            .as("WEEKLY must land on its anchor day-of-week — a property `plusDays(1)` cannot have")
            .isEqualTo(weekdayAnchor);
        unchecked.remove("WEEKLY");

        LocalDateTime monthly = nextRunFor("MONTHLY", String.valueOf(domAnchor), "22:00");
        assertThat(monthly.getDayOfMonth()).isEqualTo(domAnchor);
        unchecked.remove("MONTHLY");

        LocalDateTime quarterly = nextRunFor("QUARTERLY", String.valueOf(domAnchor), "22:00");
        assertThat(quarterly.getDayOfMonth()).isEqualTo(domAnchor);
        assertThat(quarterly.getMonthValue())
            .as("quarters start in Jan/Apr/Jul/Oct")
            .isIn(1, 4, 7, 10);
        unchecked.remove("QUARTERLY");

        LocalDateTime semi = nextRunFor("SEMIANNUAL", String.valueOf(domAnchor), "22:00");
        assertThat(semi.getDayOfMonth()).isEqualTo(domAnchor);
        assertThat(semi.getMonthValue()).as("half-years start in Jan/Jul").isIn(1, 7);
        unchecked.remove("SEMIANNUAL");

        assertThat(unchecked)
            .as("a cadence was declared and this gate has no property for it, so nothing here "
                + "would notice it silently taking the daily fallback arm — which is exactly what "
                + "SEMIANNUAL did for 13 days in dev and prod")
            .isEmpty();
    }

    @Test
    void semiannualIsSixMonthsOutAndAnchoredOnTheHalfYear() {
        LocalDateTime next = nextRunFor("SEMIANNUAL", "15", "09:30");
        assertThat(next.getDayOfMonth()).as("the anchor is a day-of-month").isEqualTo(15);
        assertThat(next.getMonthValue())
            .as("half-years start in January and July, mirroring nextQuarterly's Jan/Apr/Jul/Oct")
            .isIn(1, 7);
        assertThat(next).isAfter(LocalDateTime.now());
        assertThat(next)
            .as("and no more than a half-year out, or the anchor arithmetic has slipped a period")
            .isBefore(LocalDateTime.now().plusMonths(7));
    }

    /**
     * ⚠⚠ <b>The leg above is only discriminating for half the year, and the mutation harness is what
     * showed it.</b> A mutant computing the QUARTERLY start instead of the half-year start survived:
     * quarters begin Jan/Apr/Jul/Oct and halves Jan/Jul, so the two <b>agree in six months of
     * twelve</b> — and the run happened to be in August, where both answer July. So
     * {@code isIn(1, 7)} passes over a quarterly implementation for Jan–Mar and Jul–Sep.
     *
     * <p>🔑 <b>A test whose strength depends on the month it runs in is a test that will be strong
     * on the day you write it and weak on the day it matters.</b> The fix is not a better assertion
     * at "now" — no single instant can separate the two functions — but to drive the arithmetic
     * across a whole year. {@code nextSemiannual} takes {@code now} as a parameter, so it can be
     * asked directly, which is also why this is a unit assertion rather than a probe.
     */
    @Test
    void theSemiannualArithmeticHoldsInEveryMonthOfTheYear() {
        ReportSchedulerService svc = new ReportSchedulerService(
            null, null, null, null, null, null, null);
        List<String> wrong = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDateTime now = LocalDateTime.of(2026, month, 20, 12, 0);
            LocalDateTime next = ReflectionTestUtils.invokeMethod(
                svc, "nextSemiannual", now, LocalTime.of(9, 30), "15");
            assertThat(next).as("month " + month).isNotNull();
            int expectedMonth = month <= 6 ? 1 : 7;
            // the 15th of the current half has already passed when now is the 20th, so the answer
            // is always the NEXT half-year start
            LocalDateTime expected = LocalDateTime.of(
                month <= 6 ? 2026 : 2027, expectedMonth == 1 ? 7 : 1, 15, 9, 30);
            if (!next.equals(expected)) {
                wrong.add("from 2026-%02d-20 expected %s but got %s".formatted(month, expected, next));
            }
        }
        assertThat(wrong)
            .as("a quarterly implementation agrees with a half-yearly one in six months of twelve, "
                + "so only walking the year can tell them apart")
            .isEmpty();
    }

    /**
     * The token this defect was made of. Kept as its own leg because the fix must stay loud: an
     * unrecognised cadence still falls back to a day (throwing would break the run loop, which
     * calls {@code advanceNextRun} from its catch path too), and what changed is that it now warns.
     */
    @Test
    void anUnknownCadenceStillFallsBackToADayRatherThanThrowingInTheRunLoop() {
        assertThat(nextRunFor("ANNUAL", "1", "06:00"))
            .as("the run loop must survive a row written before the write-boundary guard")
            .isNotNull();
        assertThat(ReportSchedulerService.SUPPORTED_CADENCES)
            .as("...and ANNUAL is not silently blessed by being survivable")
            .doesNotContain("ANNUAL");
    }

    @Test
    void theFrontendOffersExactlyTheCadencesTheSchedulerCanCompute() throws IOException {
        assertThat(Files.exists(PAGE))
            .as("ReportSchedulesPage.tsx must be readable from the backend module — this gate is "
                + "cross-codebase in the same way ConnectorBackingContractTest is (F2-193)")
            .isTrue();
        Set<String> offered = cadenceOptions(Files.readString(PAGE));
        assertThat(offered)
            .as("CADENCE_OPTIONS is the third copy of this vocabulary. It is the one an operator "
                + "sees, so a cadence the register holds and this select cannot express is a "
                + "control that silently rewrites its own row on save.")
            .containsExactlyInAnyOrderElementsOf(ReportSchedulerService.SUPPORTED_CADENCES);
    }

    /**
     * Self-test for the parser above: a detector that cannot match reads exactly like a clean tree,
     * and it under-reports (F2-208). Both directions pinned.
     */
    @Test
    void theCadenceOptionParserFiresOnTheShapeItClaimsToRead() {
        String good = """
            const CADENCE_OPTIONS = [
              { value: 'DAILY',      label: 'Daily' },
              { value: 'SEMIANNUAL', label: 'Semi-annual' },
            ];
            const OTHER_OPTIONS = [{ value: 'NOT_A_CADENCE', label: 'x' }];
            """;
        assertThat(cadenceOptions(good))
            .as("it must read the CADENCE_OPTIONS block and nothing else in the file")
            .containsExactlyInAnyOrder("DAILY", "SEMIANNUAL");

        assertThat(cadenceOptions("const CADENCE_OPTIONS = [];"))
            .as("an emptied list must read as empty rather than as unparseable, or a rotted parser "
                + "and a deleted vocabulary are indistinguishable")
            .isEmpty();
    }

    /** Extract the {@code value:} tokens of the {@code CADENCE_OPTIONS} array literal. */
    private static Set<String> cadenceOptions(String tsx) {
        Matcher block = Pattern.compile(
            "CADENCE_OPTIONS\\s*(?::[^=]*)?=\\s*\\[(.*?)]", Pattern.DOTALL).matcher(tsx);
        assertThat(block.find()).as("CADENCE_OPTIONS declaration not found").isTrue();
        Set<String> out = new LinkedHashSet<>();
        Matcher v = Pattern.compile("value\\s*:\\s*'([A-Z_]+)'").matcher(block.group(1));
        while (v.find()) {
            out.add(v.group(1));
        }
        return out;
    }
}
