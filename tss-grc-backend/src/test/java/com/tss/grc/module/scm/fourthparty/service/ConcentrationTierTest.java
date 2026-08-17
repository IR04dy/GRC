package com.tss.grc.module.scm.fourthparty.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F2-229 — the ladder itself. */
class ConcentrationTierTest {

    @Test
    @DisplayName("the ladder and its vocabulary describe the same four tokens")
    void theLadderAgreesWithTheVocabulary() {
        assertThat(ConcentrationTier.ladder())
            .containsExactly("LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(ConcentrationTier.VOCABULARY.tokens())
            .containsExactlyInAnyOrderElementsOf(ConcentrationTier.ladder());
        assertThat(ConcentrationTier.VOCABULARY.constraint()).isEqualTo("CK_fpe_concentration_risk");
    }

    @Test
    @DisplayName("the ladder is NOT in alphabetical order, which is the whole reason it exists")
    void theLadderIsNotAlphabetical() {
        List<String> alphabetical = ConcentrationTier.ladder().stream().sorted().toList();
        assertThat(alphabetical)
            .as("if these ever coincided, a lexical ORDER BY would be a valid ranking and this "
                + "class would be unnecessary — they do not: alphabetically these read "
                + "CRITICAL, HIGH, LOW, MEDIUM")
            .isNotEqualTo(ConcentrationTier.ladder())
            .containsExactly("CRITICAL", "HIGH", "LOW", "MEDIUM");
    }

    @Test
    @DisplayName("rank is case- and whitespace-insensitive, because the column is")
    void rankNormalises() {
        assertThat(ConcentrationTier.rank("LOW")).isZero();
        assertThat(ConcentrationTier.rank(" high ")).isEqualTo(2);
        assertThat(ConcentrationTier.rank("critical")).isEqualTo(3);
        assertThat(ConcentrationTier.rank(null)).isEqualTo(ConcentrationTier.UNRECOGNISED);
        assertThat(ConcentrationTier.rank("")).isEqualTo(ConcentrationTier.UNRECOGNISED);
        assertThat(ConcentrationTier.rank("SEVERE")).isEqualTo(ConcentrationTier.UNRECOGNISED);
    }

    @Test
    @DisplayName("outranks is strict, so an equal tier leaves the row un-dirtied")
    void outranksIsStrict() {
        assertThat(ConcentrationTier.outranks("HIGH", "MEDIUM")).isTrue();
        assertThat(ConcentrationTier.outranks("HIGH", "HIGH")).isFalse();
        assertThat(ConcentrationTier.outranks("MEDIUM", "HIGH")).isFalse();
        assertThat(ConcentrationTier.outranks("HIGH", "CRITICAL"))
            .as("the derivation's ceiling is HIGH, so it can never displace a CRITICAL assessment")
            .isFalse();
        assertThat(ConcentrationTier.outranks("LOW", "SOMETHING_ELSE"))
            .as("an uninterpretable stored value is not an assessment worth protecting")
            .isTrue();
    }

    @Test
    @DisplayName("atLeast is a threshold and refuses a threshold that is not a tier")
    void atLeastIsAThreshold() {
        assertThat(ConcentrationTier.atLeast("CRITICAL", "HIGH")).isTrue();
        assertThat(ConcentrationTier.atLeast("HIGH", "HIGH")).isTrue();
        assertThat(ConcentrationTier.atLeast("MEDIUM", "HIGH")).isFalse();
        assertThat(ConcentrationTier.atLeast(null, "LOW")).isFalse();
        assertThatThrownBy(() -> ConcentrationTier.atLeast("HIGH", "SEVERE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a concentration tier");
    }

    @Test
    @DisplayName("the multiplicity floor tops out at HIGH — it can never produce CRITICAL")
    void theFloorCannotReachCritical() {
        assertThat(ConcentrationTier.multiplicityFloor(0)).isEqualTo("LOW");
        assertThat(ConcentrationTier.multiplicityFloor(1)).isEqualTo("LOW");
        assertThat(ConcentrationTier.multiplicityFloor(2)).isEqualTo("MEDIUM");
        assertThat(ConcentrationTier.multiplicityFloor(3)).isEqualTo("HIGH");
        assertThat(ConcentrationTier.multiplicityFloor(9_999)).isEqualTo("HIGH");
        // This is the property that makes elevation-only mandatory rather than tidy: the derivation
        // cannot express the top of its own column's vocabulary, so anything it overwrote there was
        // gone for good.
        for (long n = 0; n < 500; n++) {
            assertThat(ConcentrationTier.multiplicityFloor(n)).isNotEqualTo("CRITICAL");
        }
    }
}
