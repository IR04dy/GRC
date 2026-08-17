package com.tss.grc.module.admin.committee.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-230 — the CMA Article 54 question, and the transposition that answered it wrongly.
 */
class SkillLevelTest {

    @Test
    @DisplayName("the ladder is least to most competent")
    void theLadderIsDeclared() {
        assertThat(SkillLevel.ladder()).containsExactly("BASIC", "INTERMEDIATE", "EXPERT");
    }

    @Test
    @DisplayName("ORDER BY skillLevel DESC ranked INTERMEDIATE above EXPERT")
    void theLexicalOrderIsTheDefect() {
        List<String> lexicalDesc = SkillLevel.ladder().stream()
            .sorted(Comparator.reverseOrder()).toList();
        assertThat(lexicalDesc).containsExactly("INTERMEDIATE", "EXPERT", "BASIC");
        assertThat(lexicalDesc.get(0))
            .as("the query answering 'does this committee have a financial expert?' put the "
              + "INTERMEDIATE member first")
            .isEqualTo("INTERMEDIATE");

        List<String> ranked = SkillLevel.ladder().stream()
            .sorted(Comparator.comparingInt(SkillLevel::rank).reversed()).toList();
        assertThat(ranked).containsExactly("EXPERT", "INTERMEDIATE", "BASIC");
    }

    @Test
    @DisplayName("the measured live shape: one EXPERT among six INTERMEDIATE sorted LAST of seven")
    void theMeasuredCommitteeOrderIsFixed() {
        // Probed 2026-08-17 in dev AND prod, org 1, the dimension with more than one level present:
        // CFO, CISO, COO, DPO, General Counsel and Head of Internal Audit all INTERMEDIATE, CRO EXPERT.
        // Lexically DESC the sole EXPERT — the answer to the Article 54 question — came back seventh.
        List<String> stored = new ArrayList<>();
        for (int i = 0; i < 6; i++) stored.add("INTERMEDIATE");
        stored.add("EXPERT");

        List<String> lexical = stored.stream().sorted(Comparator.reverseOrder()).toList();
        assertThat(lexical.get(lexical.size() - 1))
            .as("pre-fix: the only EXPERT was last of seven")
            .isEqualTo("EXPERT");

        List<String> ranked = stored.stream()
            .sorted(Comparator.comparingInt(SkillLevel::rank).reversed()).toList();
        assertThat(ranked.get(0))
            .as("post-fix: the EXPERT leads, which is the whole point of the query")
            .isEqualTo("EXPERT");
    }

    @Test
    @DisplayName("atLeast answers the Article 54 threshold, casing-normalised")
    void atLeastAnswersTheArticle54Question() {
        assertThat(SkillLevel.atLeast("EXPERT", "EXPERT")).isTrue();
        assertThat(SkillLevel.atLeast("INTERMEDIATE", "EXPERT")).isFalse();
        assertThat(SkillLevel.atLeast("EXPERT", "INTERMEDIATE")).isTrue();
        assertThat(SkillLevel.atLeast("BASIC", "INTERMEDIATE")).isFalse();
        assertThat(SkillLevel.rank(" expert ")).isEqualTo(2);
        assertThat(SkillLevel.rank(null)).isEqualTo(-1);
    }
}
