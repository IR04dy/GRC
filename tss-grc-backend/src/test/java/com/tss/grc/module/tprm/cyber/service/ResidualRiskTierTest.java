package com.tss.grc.module.tprm.cyber.service;

import com.tss.grc.common.validation.OrderedVocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F2-230 — the residual-risk ladder, and the arithmetic that made a lexical sort a false ranking.
 */
class ResidualRiskTierTest {

    @Test
    @DisplayName("the ladder is five rungs, least exposed first")
    void theLadderIsDeclared() {
        assertThat(ResidualRiskTier.ladder())
            .containsExactly("LOW", "MODERATE", "ELEVATED", "HIGH", "CRITICAL");
    }

    @Test
    @DisplayName("the ladder is NOT alphabetical, which is exactly why the DESC sort was wrong")
    void theLadderIsNotAlphabetical() {
        List<String> alphabetical = ResidualRiskTier.ladder().stream().sorted().toList();
        assertThat(alphabetical)
            .containsExactly("CRITICAL", "ELEVATED", "HIGH", "LOW", "MODERATE");
        assertThat(alphabetical).isNotEqualTo(ResidualRiskTier.ladder());
        assertThat(ResidualRiskTier.LADDER.isAlphabetical()).isFalse();
    }

    @Test
    @DisplayName("the pre-fix lexical DESC order put CRITICAL last and LOW second")
    void theLexicalOrderIsTheDefect() {
        // The literal arithmetic of the defect, so the finding is reproducible without a database.
        // Alphabetically descending, the five rungs read MODERATE > LOW > HIGH > ELEVATED > CRITICAL.
        List<String> lexicalDesc = ResidualRiskTier.ladder().stream()
            .sorted(java.util.Comparator.reverseOrder()).toList();
        assertThat(lexicalDesc)
            .containsExactly("MODERATE", "LOW", "HIGH", "ELEVATED", "CRITICAL");
        assertThat(lexicalDesc.get(lexicalDesc.size() - 1))
            .as("the most exposed rung sorted LAST — on a paged register, off the first page")
            .isEqualTo("CRITICAL");
        assertThat(lexicalDesc.get(1))
            .as("and the LEAST exposed rung sorted second")
            .isEqualTo("LOW");

        // What the ranked ordering gives instead.
        List<String> ranked = ResidualRiskTier.ladder().stream()
            .sorted(java.util.Comparator.comparingInt(ResidualRiskTier::rank).reversed()).toList();
        assertThat(ranked).containsExactly("CRITICAL", "HIGH", "ELEVATED", "MODERATE", "LOW");
    }

    @Test
    @DisplayName("the measured live order: the two ELEVATED rows sort above every MODERATE one")
    void theMeasuredRegisterOrderIsFixed() {
        // The exact shape measured 2026-08-17 in dev AND prod: 16 rows, LOW 7 / MODERATE 7 /
        // ELEVATED 2. Lexically DESC the seven MODERATE rows came first and both ELEVATED rows last.
        List<String> stored = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) stored.add("LOW");
        for (int i = 0; i < 7; i++) stored.add("MODERATE");
        stored.add("ELEVATED");
        stored.add("ELEVATED");
        assertThat(stored).hasSize(16);

        List<String> lexical = stored.stream().sorted(java.util.Comparator.reverseOrder()).toList();
        assertThat(lexical.get(0)).as("pre-fix: a MODERATE row led the register").isEqualTo("MODERATE");

        List<String> ranked = stored.stream()
            .sorted(java.util.Comparator.comparingInt(ResidualRiskTier::rank).reversed()).toList();
        assertThat(ranked.subList(0, 2))
            .as("post-fix: the highest tier present leads")
            .containsExactly("ELEVATED", "ELEVATED");
    }

    @Test
    @DisplayName("atLeast is a threshold, normalises casing, and refuses a non-rung threshold")
    void atLeastIsAThreshold() {
        assertThat(ResidualRiskTier.atLeast("CRITICAL", "ELEVATED")).isTrue();
        assertThat(ResidualRiskTier.atLeast("ELEVATED", "ELEVATED")).isTrue();
        assertThat(ResidualRiskTier.atLeast("MODERATE", "ELEVATED")).isFalse();
        assertThat(ResidualRiskTier.atLeast("LOW", "ELEVATED")).isFalse();
        // The exact pre-fix hole: ELEVATED satisfied neither `=== HIGH || === CRITICAL` nor
        // `=== LOW || === MODERATE`, so it was in no bucket at all.
        assertThat(ResidualRiskTier.atLeast("ELEVATED", "ELEVATED")
                   || ResidualRiskTier.atLeast("ELEVATED", "LOW"))
            .as("under a threshold, no rung can fall between two buckets")
            .isTrue();

        // The collation is case-insensitive, so a lower-case token is storable (F2-179).
        assertThat(ResidualRiskTier.rank(" elevated ")).isEqualTo(2);
        assertThat(ResidualRiskTier.rank(null)).isEqualTo(OrderedVocabulary.UNRECOGNISED);
        assertThat(ResidualRiskTier.rank("SEVERE")).isEqualTo(OrderedVocabulary.UNRECOGNISED);

        assertThatThrownBy(() -> ResidualRiskTier.atLeast("HIGH", "SEVERE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is not a rung of residualRiskTier");
    }

    @Test
    @DisplayName("an unrecognised stored tier never satisfies a threshold")
    void anUnrecognisedTierIsNotElevated() {
        // Answering 0 for an unknown token would file it at the bottom of the ladder as though that
        // had been measured; UNRECOGNISED is below every rung, so it satisfies nothing.
        assertThat(ResidualRiskTier.atLeast("SEVERE", "LOW")).isFalse();
        assertThat(ResidualRiskTier.atLeast(null, "LOW")).isFalse();
        assertThat(ResidualRiskTier.atLeast("", "LOW")).isFalse();
    }
}
