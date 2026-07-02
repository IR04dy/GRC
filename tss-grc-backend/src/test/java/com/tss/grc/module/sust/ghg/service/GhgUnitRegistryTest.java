package com.tss.grc.module.sust.ghg.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** GhgUnitRegistry — S-J unit conversions (g / kg / t / kt / Mt aware). */
class GhgUnitRegistryTest {

    private static final BigDecimal ACT = new BigDecimal("1000");   // 1000 activity units
    private static final BigDecimal F   = new BigDecimal("2");      // factor 2 <unit>/activity-unit

    @Test
    void kg_dividesByThousand() {
        // 1000 × 2 = 2000 kg → 2.00 t
        assertThat(GhgUnitRegistry.toTco2e(ACT, F, "kgCO2e/L")).isEqualByComparingTo("2.00");
    }

    @Test
    void tonnes_isCanonical() {
        // 1000 × 2 = 2000 t
        assertThat(GhgUnitRegistry.toTco2e(ACT, F, "tCO2e/MWh")).isEqualByComparingTo("2000.00");
    }

    @Test
    void grams_dividesByMillion() {
        // 1000 × 2 = 2000 g → 0.002 t → 0.00 at scale 2
        assertThat(GhgUnitRegistry.toTco2e(ACT, F, "gCO2e/unit")).isEqualByComparingTo("0.00");
    }

    @Test
    void megatonnes_scaleUp() {
        // 1000 × 2 = 2000 Mt → 2,000,000,000 t
        assertThat(GhgUnitRegistry.toTco2e(ACT, F, "MtCO2e/x")).isEqualByComparingTo("2000000000.00");
    }

    @Test
    void unknownOrNull_treatedAsTonnes() {
        assertThat(GhgUnitRegistry.toTco2e(ACT, F, "widgets")).isEqualByComparingTo("2000.00");
        assertThat(GhgUnitRegistry.toTco2e(ACT, F, null)).isEqualByComparingTo("2000.00");
        assertThat(GhgUnitRegistry.isRecognised("kgCO2e/L")).isTrue();
        assertThat(GhgUnitRegistry.isRecognised("widgets")).isFalse();
    }
}
