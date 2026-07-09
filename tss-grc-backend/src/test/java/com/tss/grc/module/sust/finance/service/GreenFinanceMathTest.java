package com.tss.grc.module.sust.finance.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Green-Finance G1 — the §4.2 use-of-proceeds calculation rules, pure and provable. */
class GreenFinanceMathTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test
    void netProceeds_subtractsCosts_andFloorsAtZero() {
        assertThat(GreenFinanceMath.netProceeds(d("500"), d("5"))).isEqualByComparingTo("495");
        assertThat(GreenFinanceMath.netProceeds(d("500"), null)).isEqualByComparingTo("500");
        assertThat(GreenFinanceMath.netProceeds(d("3"), d("9"))).isEqualByComparingTo("0");
    }

    @Test
    void allocationRatio_andUnallocated() {
        assertThat(GreenFinanceMath.allocationRatioPct(d("490"), d("495"))).isEqualByComparingTo("98.9899");
        assertThat(GreenFinanceMath.unallocated(d("495"), d("490"))).isEqualByComparingTo("5");
        assertThat(GreenFinanceMath.allocationRatioPct(d("10"), BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void overAllocation_detection() {
        assertThat(GreenFinanceMath.wouldOverAllocate(d("490"), d("100"), d("495"))).isTrue();
        assertThat(GreenFinanceMath.wouldOverAllocate(d("490"), d("5"), d("495"))).isFalse();  // exactly full
        assertThat(GreenFinanceMath.wouldOverAllocate(d("490"), d("5.0001"), d("495"))).isTrue();
    }

    @Test
    void refinancingShare() {
        assertThat(GreenFinanceMath.refinancingSharePct(d("190"), d("490"))).isEqualByComparingTo("38.7755");
        assertThat(GreenFinanceMath.refinancingSharePct(d("0"), d("0"))).isEqualByComparingTo("0");
    }

    @Test
    void proRataImpactAttribution_capsShareAtOne() {
        // 300 allocated into a 300-cost project → share 1.0 → full actual impact
        BigDecimal share = GreenFinanceMath.projectShare(d("300"), d("300"));
        assertThat(share).isEqualByComparingTo("1");
        assertThat(GreenFinanceMath.attributedImpact(share, d("120000"))).isEqualByComparingTo("120000");
        // 100 into a 400-cost project → 25 % of impact
        BigDecimal quarter = GreenFinanceMath.projectShare(d("100"), d("400"));
        assertThat(GreenFinanceMath.attributedImpact(quarter, d("80000"))).isEqualByComparingTo("20000");
        // over-funded share caps at 1 (no >100 % impact claims)
        assertThat(GreenFinanceMath.projectShare(d("500"), d("300"))).isEqualByComparingTo("1");
        // null actual impact contributes zero (estimates don't count silently)
        assertThat(GreenFinanceMath.attributedImpact(quarter, null)).isEqualByComparingTo("0");
    }

    @Test
    void intensity_perSarMillion() {
        assertThat(GreenFinanceMath.intensityPerSarMn(d("140000"), d("490"))).isEqualByComparingTo("285.714286");
        assertThat(GreenFinanceMath.intensityPerSarMn(d("140000"), BigDecimal.ZERO)).isEqualByComparingTo("0");
    }
}
