package com.tss.grc.module.change_mgmt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S8b / CE-12 — the KPI pack's honesty rules, pinned.
 *
 * <p>Every test here exists because the alternative behaviour is a number that reads better than the
 * truth. That is not hypothetical: {@code successRatePct} shipped rendering <b>100%</b> in dev off two
 * rows out of seventeen (F2-107), and my own first drafts of two of these metrics reported "0 overdue"
 * and "0 terms expiring" over populations of zero — clean bills of health for registers nobody had
 * written to. The live probe caught both, and these tests keep them caught.
 */
class ChangeKpiHonestyTest {

    private static final Path PLAN =
        Path.of("..", "CHANGE_ENABLEMENT_MODULE_SEPARATION_AND_CAB_UPLIFT_PLAN.md");

    // ── the two axes ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("coverage and provenance are independent — a complete figure can still be asserted")
    void coverageAndProvenanceAreOrthogonal() {
        // The CAB quorum-met rate, exactly as measured: 2 of 2 held meetings recorded a basis, and
        // both bases were ASSERTED_LEGACY. Complete coverage. Zero measurement.
        ChangeKpi q = ChangeKpi.pctOfSubset("cabQuorumMetRate", 2, 2, 2)
            .withProvenance(ChangeKpiService.provenanceOf(2, 2));

        assertThat(q.value()).isEqualTo(100.0);
        assertThat(q.basis())
            .as("coverage IS complete — pretending otherwise would be a different lie")
            .isEqualTo(ChangeKpi.COMPUTED);
        assertThat(q.provenance())
            .as("but nothing computed it. Collapsing the two axes into one loses this, and 'quorum "
              + "met: 100%%' then reads as a passed control")
            .isEqualTo(ChangeKpi.ASSERTED);
        assertThat(q.isAsserted()).isTrue();
        assertThat(q.isPartial()).isFalse();
    }

    @Test
    @DisplayName("provenance is MIXED when some bases were computed and some asserted")
    void mixedProvenanceIsItsOwnState() {
        assertThat(ChangeKpiService.provenanceOf(10, 4)).isEqualTo(ChangeKpi.MIXED);
        assertThat(ChangeKpiService.provenanceOf(10, 10)).isEqualTo(ChangeKpi.ASSERTED);
        assertThat(ChangeKpiService.provenanceOf(10, 0))
            .as("nothing asserted means it really was measured")
            .isEqualTo(ChangeKpi.MEASURED);
        assertThat(ChangeKpiService.provenanceOf(0, 0))
            .as("no rows at all: MEASURED is the vacuous-but-safe answer; NO_DATA carries the emptiness")
            .isEqualTo(ChangeKpi.MEASURED);
    }

    // ── the zero-denominator trap, three ways ───────────────────────────────────────────────────

    @Test
    @DisplayName("a rate over an empty population is NO_DATA, never 0%")
    void anEmptyPopulationIsNotZeroPercent() {
        ChangeKpi k = ChangeKpi.pct("failedChangeRate", 0, 0);
        assertThat(k.basis()).isEqualTo(ChangeKpi.NO_DATA);
        assertThat(k.value())
            .as("0%% failed would read as a flawless record. 'No change has closed yet' is the fact.")
            .isNull();
    }

    @Test
    @DisplayName("a median over an empty sample is NO_DATA, never 0 days")
    void anEmptySampleIsNotZeroDays() {
        assertThat(ChangeKpi.days("leadTime", 0, 0, 17L).basis()).isEqualTo(ChangeKpi.NO_DATA);
        assertThat(ChangeKpi.perMeeting("itemsPerMeeting", 0, 0).basis()).isEqualTo(ChangeKpi.NO_DATA);
    }

    @Test
    @DisplayName("a subset denominator downgrades the basis, and the population survives")
    void aSubsetIsLabelledPartialAndKeepsItsPopulation() {
        // F2-107 in miniature: 2 successes out of 2 recorded outcomes, in a population of 17.
        ChangeKpi wrong = ChangeKpi.pctOfSubset("successRate", 2, 2, 17);
        assertThat(wrong.value()).isEqualTo(100.0);
        assertThat(wrong.basis())
            .as("the number is 100 and there is no honest way to hide that — so the LABEL has to "
              + "carry the warning, and the population has to travel with it")
            .isEqualTo(ChangeKpi.COMPUTED_PARTIAL);
        assertThat(wrong.denominator()).isEqualTo(2L);
        assertThat(wrong.population()).isEqualTo(17L);
        assertThat(wrong.isPartial()).isTrue();

        // And full coverage is NOT permanently labelled partial — a tenant that records properly
        // must be able to reach COMPUTED.
        assertThat(ChangeKpi.pctOfSubset("successRate", 3, 4, 4).basis()).isEqualTo(ChangeKpi.COMPUTED);
    }

    @Test
    @DisplayName("NOT_MEASURABLE always names what is missing")
    void notMeasurableAlwaysNamesItsBlocker() {
        ChangeKpi k = ChangeKpi.notMeasurable("scheduleAdherenceRate", ChangeKpi.PCT,
            ChangeKpi.Blocked.NO_IMPLEMENTATION_TIMESTAMPS);
        assertThat(k.value()).isNull();
        assertThat(k.blockedBy()).isNotBlank();
        assertThat(ChangeKpi.Blocked.VALUES)
            .as("a blocker outside the published set is one the frontend cannot translate, so it "
              + "would render as a blank cell — indistinguishable from a metric nobody thought about")
            .contains(k.blockedBy());
    }

    // ── vocabularies stay closed ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every basis, provenance, unit and blocker a factory can emit is published")
    void everyEmittableValueIsPublished() {
        List<ChangeKpi> samples = List.of(
            ChangeKpi.pct("a", 1, 2),
            ChangeKpi.pctOfSubset("b", 1, 2, 9),
            ChangeKpi.pctOfSubset("c", 1, 2, 2),
            ChangeKpi.count("d", 3, 9),
            ChangeKpi.days("e", 4.5, 2, 9),
            ChangeKpi.perMeeting("f", 4, 2),
            ChangeKpi.noData("g", ChangeKpi.PCT, 0L),
            ChangeKpi.notMeasurable("h", ChangeKpi.DAYS, ChangeKpi.Blocked.NO_PIR_TIMESTAMPS));

        for (ChangeKpi k : samples) {
            assertThat(ChangeKpi.BASES).as("basis of %s", k.key()).contains(k.basis());
            assertThat(ChangeKpi.UNITS).as("unit of %s", k.key()).contains(k.unit());
            if (k.provenance() != null)
                assertThat(ChangeKpi.PROVENANCES).as("provenance of %s", k.key()).contains(k.provenance());
            if (k.blockedBy() != null)
                assertThat(ChangeKpi.Blocked.VALUES).contains(k.blockedBy());
        }
        assertThat(new HashSet<>(ChangeKpi.Blocked.VALUES))
            .as("duplicate blocker codes would silently collapse two different gaps into one")
            .hasSameSizeAs(ChangeKpi.Blocked.VALUES);
    }

    @Test
    @DisplayName("a value is present exactly when the basis says one should be")
    void valuePresenceMatchesBasis() {
        for (ChangeKpi k : List.of(ChangeKpi.pct("a", 1, 2), ChangeKpi.count("b", 3, 9),
                                   ChangeKpi.days("c", 1.0, 1, 1), ChangeKpi.perMeeting("d", 1, 1)))
            assertThat(k.value()).as("%s is %s and must carry a number", k.key(), k.basis()).isNotNull();

        for (ChangeKpi k : List.of(ChangeKpi.noData("e", ChangeKpi.PCT, 0L),
                                   ChangeKpi.notMeasurable("f", ChangeKpi.PCT,
                                       ChangeKpi.Blocked.CE11_REGISTER_ABSENT)))
            assertThat(k.value())
                .as("%s is %s — a number here is the fabrication this class exists to prevent",
                    k.key(), k.basis())
                .isNull();
    }

    // ── coverage against the specification ──────────────────────────────────────────────────────

    @Test
    @DisplayName("§12's own metric list is covered — nothing was quietly dropped")
    void everyMetricTheSpecificationAsksForHasAKey() throws IOException {
        // ⚠ Read the plan, not a copy of it. The failure mode this guards is the tempting one: build
        // the eight metrics that compute cleanly, ship "the KPI pack", and let the seven with no data
        // vanish. A page with no gaps visible looks finished.
        assertThat(PLAN).as("the CE uplift plan should sit at the repo root").exists();
        String section = Files.readString(PLAN);
        int at = section.indexOf("## §12 Analytics & KPIs");
        assertThat(at).as("§12 heading not found — the scan is broken, not the code").isGreaterThan(-1);
        section = section.substring(at, section.indexOf("## §13", at));

        // The plan writes them as prose, so match on the distinctive phrase for each.
        record Spec(String phrase, String key) { }
        List<Spec> specs = List.of(
            new Spec("emergency ratio",       ChangeKpiService.EMERGENCY_RATIO),
            new Spec("success rate",          ChangeKpiService.SUCCESS_RATE),
            new Spec("failed-change rate",    ChangeKpiService.FAILED_CHANGE_RATE),
            new Spec("rollback rate",         ChangeKpiService.ROLLBACK_RATE),
            new Spec("unauthorised-change count", ChangeKpiService.UNAUTHORISED_COUNT),
            new Spec("schedule adherence",    ChangeKpiService.SCHEDULE_ADHERENCE),
            new Spec("PIR completion",        ChangeKpiService.PIR_COMPLETION),
            new Spec("quorum-met rate",       ChangeKpiService.CAB_QUORUM_MET_RATE),
            new Spec("deferral rate",         ChangeKpiService.CAB_DEFERRAL_RATE),
            new Spec("time-in-queue",         ChangeKpiService.CAB_TIME_IN_QUEUE),
            new Spec("attendance rate",       ChangeKpiService.CAB_ATTENDANCE_RATE),
            new Spec("notice compliance",     ChangeKpiService.CAB_NOTICE_COMPLIANCE),
            new Spec("expiring terms",        ChangeKpiService.CAB_TERMS_EXPIRING));

        for (Spec s : specs) {
            assertThat(section)
                .as("§12 no longer mentions '%s' — if the spec changed, this list must change with it",
                    s.phrase())
                .containsIgnoringCase(s.phrase());
            assertThat(ChangeKpiService.ALL_KEYS)
                .as("§12 asks for '%s' and the service publishes no key for it", s.phrase())
                .contains(s.key());
        }
    }

    @Test
    @DisplayName("ALL_KEYS is the whole published set, with no duplicates and no strays")
    void allKeysIsCompleteAndUnique() {
        Set<String> unique = new HashSet<>(ChangeKpiService.ALL_KEYS);
        assertThat(unique)
            .as("a duplicated key would overwrite a metric in the response map, losing it silently")
            .hasSameSizeAs(ChangeKpiService.ALL_KEYS);
        assertThat(ChangeKpiService.ALL_KEYS).hasSize(19);
        for (String k : ChangeKpiService.ALL_KEYS)
            assertThat(k).as("keys are i18n suffixes, so they must be lowerCamelCase")
                .matches("[a-z][A-Za-z0-9]+");
    }

    @Test
    @DisplayName("the outcome denominator is terminal statuses — the F2-107 fix, pinned by name")
    void theSuccessRateDenominatorIsChangesThatReachedAnEnd() {
        assertThat(ChangeKpiService.TERMINAL_STATUSES)
            .as("dividing by 'rows that happen to have an outcome' is what rendered 100%% off n=2; "
              + "the denominator must be changes that reached an end and therefore OWE an outcome")
            .containsExactlyInAnyOrder("IMPLEMENTED", "ROLLED_BACK", "CLOSED");
    }
}
