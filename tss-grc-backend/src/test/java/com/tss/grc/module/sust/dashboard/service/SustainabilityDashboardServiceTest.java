package com.tss.grc.module.sust.dashboard.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-365 — the executive ESG dashboard's GHG verification rate (0.20-weighted, the
 * joint-highest score component) must divide two counts drawn from the SAME full-year
 * population.
 *
 * <p>It was computed as {@code verified-over-a-1000-row-page / total-over-all-pages}:
 * {@code total = page.getTotalElements()} is Spring Data's separate all-pages COUNT,
 * but {@code verified} was counted over {@code page.getContent()}, capped at 1000 rows.
 * A tenant with more than 1000 GHG records in one year had verified rows beyond the
 * first page dropped from the numerator while still counted in the denominator, so the
 * rate was understated (a large-tenant defect; the arithmetic inconsistency itself is
 * unconditional). The fix reads {@code verifiedCount} and {@code recordCount} from
 * {@code GhgEmissionRecordService.aggregate}, whose single unpaged loop computes both
 * over the same list — so the mismatch is unrepresentable (F2-213/F2-216).
 *
 * <p>Hermetic: the arithmetic is a pure static helper, and the wiring / mechanism-removal
 * is a source scan (the idiom of {@code FullReplaceUpdateContractTest} etc.). No Spring
 * context, no DB, no 11-collaborator stub of {@code dashboard()} (F2-217 — a full render
 * test is disproportionate for a compiler-typed one-line read).
 */
class SustainabilityDashboardServiceTest {

    // ── The rate arithmetic (pure) ────────────────────────────────────

    @Test
    void pctOf_dividesPartByWhole_toOneDecimalPlace() {
        // The defect example: 900 verified of 1500 records is 60.0%, never 66.7%
        // (which is what verified-over-1000-page / 1500-total yielded).
        assertThat(SustainabilityDashboardService.pctOf(900, 1500)).isEqualByComparingTo("60.0");
        assertThat(SustainabilityDashboardService.pctOf(3, 4)).isEqualByComparingTo("75.0");
        assertThat(SustainabilityDashboardService.pctOf(1500, 1500)).isEqualByComparingTo("100.0");
    }

    @Test
    void pctOf_isZeroWhenThereAreNoRecords() {
        assertThat(SustainabilityDashboardService.pctOf(0, 0)).isEqualByComparingTo("0");
        assertThat(SustainabilityDashboardService.pctOf(5, 0)).isEqualByComparingTo("0");
    }

    // ── The wiring / mechanism-removal (source scan) ───────────────────

    @Test
    void theRateReadsBothCountsFromTheGhgAggregate_andTheCappedPageMechanismIsGone() throws Exception {
        Path src = Path.of("src/main/java/com/tss/grc/module/sust/dashboard/service/"
                + "SustainabilityDashboardService.java");
        assertThat(Files.exists(src)).as("dashboard source must be readable for this scan").isTrue();
        // Strip comments so the scan reads code, not the prose that explains the fix
        // (F2-193: a detector that cannot tell a claim from its epitaph reads the epitaph).
        String code = Files.readString(src)
                .replaceAll("(?<!:)//[^\\n]*|/\\*[\\s\\S]*?\\*/", " ");

        assertThat(code)
                .as("anti-vacuity: the class body must actually have been read")
                .contains("class SustainabilityDashboardService");

        // Wiring (F2-205): the rate is derived from the GHG aggregate's two count keys,
        // not recomputed from a fetch of its own.
        assertThat(code)
                .as("the GHG verification rate must be pctOf(verifiedCount, recordCount) over the `ghg` aggregate")
                .contains("ghg.get(\"verifiedCount\")")
                .contains("ghg.get(\"recordCount\")")
                .contains("pctOf(");

        // Mechanism removal (F2-216): the capped-page helper is gone entirely, so the
        // population mismatch cannot reappear in this class.
        assertThat(code)
                .as("the dashboard must no longer fetch a capped page to compute the verification rate")
                .doesNotContain("listByYear")
                .doesNotContain("PageRequest");
    }
}
