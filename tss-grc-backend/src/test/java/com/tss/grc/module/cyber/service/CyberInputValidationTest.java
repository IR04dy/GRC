package com.tss.grc.module.cyber.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CY-H — the shared cyber input-validation helper. These are the branches the
 * cyber write surface relies on to turn malformed bodies into 400s instead of
 * 500s / silent persists.
 */
class CyberInputValidationTest {

    private static final Set<String> ALLOWED = Set.of("CURRENT", "EXPIRED", "STALE");

    @Test
    void requireText_blankOrNull_rejected() {
        assertThatThrownBy(() -> CyberInputValidation.requireText(null, "titleEn"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("titleEn");
        assertThatThrownBy(() -> CyberInputValidation.requireText("   ", "titleEn"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("titleEn");
    }

    @Test
    void requireText_trimsAndReturns() {
        assertThat(CyberInputValidation.requireText("  Policy PDF  ", "titleEn")).isEqualTo("Policy PDF");
    }

    @Test
    void requireId_nullOrNonPositive_rejected() {
        assertThatThrownBy(() -> CyberInputValidation.requireId(null, "controlId"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("controlId");
        assertThatThrownBy(() -> CyberInputValidation.requireId(0L, "controlId"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CyberInputValidation.requireId(7L, "controlId")).isEqualTo(7L);
    }

    @Test
    void checkEnum_blankOptional_returnsDefault() {
        assertThat(CyberInputValidation.checkEnum(null, "status", ALLOWED, false, "CURRENT")).isEqualTo("CURRENT");
        assertThat(CyberInputValidation.checkEnum("  ", "status", ALLOWED, false, "CURRENT")).isEqualTo("CURRENT");
    }

    @Test
    void checkEnum_blankRequired_rejected() {
        assertThatThrownBy(() -> CyberInputValidation.checkEnum(null, "discoveredVia", ALLOWED, true, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("discoveredVia");
    }

    @Test
    void checkEnum_normalisesCase() {
        assertThat(CyberInputValidation.checkEnum(" current ", "status", ALLOWED, false, "CURRENT")).isEqualTo("CURRENT");
    }

    @Test
    void checkEnum_unknownValue_rejected() {
        assertThatThrownBy(() -> CyberInputValidation.checkEnum("NONSENSE", "status", ALLOWED, false, "CURRENT"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NONSENSE");
    }
}
