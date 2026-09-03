package com.tss.grc.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** F2-449 — a caller's term must match literally inside a {@code LIKE} pattern. */
class LikePatternsTest {

    private static final char E = LikePatterns.ESCAPE_CHAR;

    @Test
    @DisplayName("F2-449 — every LIKE metacharacter is escaped")
    void everyMetacharacterIsEscaped() {
        assertThat(LikePatterns.escape("100%"))
            .as("a literal percent is the one a user really types")
            .isEqualTo("100" + E + "%");
        assertThat(LikePatterns.escape("NCA_ECC"))
            .as("""
                the everyday case on this platform: its vocabularies are UPPER_SNAKE_CASE, so a
                term with an underscore is the normal way to search the data, and unescaped the _
                matches any single character.""")
            .isEqualTo("NCA" + E + "_ECC");
        assertThat(LikePatterns.escape("[REVIEW-TEST]"))
            .as("""
                house rule 5's own trap, asked of user input: a CLOSED bracket is a SQL character
                class, so unescaped this matches any single character from the set {R,E,V,I,W,-,T,S}
                rather than the literal tag.""")
            .isEqualTo(E + "[REVIEW-TEST]");
        assertThat(LikePatterns.escape("a" + E + "b"))
            .as("the escape character must itself be escaped, or it swallows the next character")
            .isEqualTo("a" + E + E + "b");
    }

    @Test
    @DisplayName("F2-449 — a closing bracket is deliberately left alone")
    void aClosingBracketIsLeftAlone() {
        assertThat(LikePatterns.escape("a]b"))
            .as("""
                Outside a character class SQL Server treats ] as an ordinary character, and
                escaping [ means a class can never open — so a lone ] has nothing to close and
                stays literal. Escaping it too would be harmless and would state a rule that is
                not the one doing the work.""")
            .isEqualTo("a]b");
    }

    @Test
    @DisplayName("F2-449 — ordinary text is returned unchanged")
    void ordinaryTextIsUnchanged() {
        assertThat(LikePatterns.escape("Access Review")).isEqualTo("Access Review");
        assertThat(LikePatterns.escape("مخاطر الأمن"))
            .as("Arabic is ordinary text — nothing in it is a LIKE metacharacter (house rule 7)")
            .isEqualTo("مخاطر الأمن");
    }

    @Test
    @DisplayName("F2-449 — null survives as null, because null means 'no filter'")
    void nullSurvivesAsNull() {
        assertThat(LikePatterns.escape(null))
            .as("""
                Every one of these queries reads ":q IS NULL OR ...", so null means no filter.
                Turning it into "" would change which rows a filterless request returns — a fix
                that breaks correct usage (F2-214).""")
            .isNull();
        assertThat(LikePatterns.escape("")).isEmpty();
        assertThat(LikePatterns.contains(null)).isNull();
    }

    @Test
    @DisplayName("F2-449 — contains() keeps the wrapping and the escaping together")
    void containsWrapsTheEscapedTerm() {
        assertThat(LikePatterns.contains("AUDIT_COMMITTEE"))
            .as("the surrounding wildcards stay wildcards; only the term is escaped")
            .isEqualTo("%AUDIT" + E + "_COMMITTEE%");
        assertThat(LikePatterns.contains("x"))
            .as("and an ordinary term is simply wrapped")
            .isEqualTo("%x%");
    }

    @Test
    @DisplayName("F2-449 — the declared clause is the one the queries carry")
    void theDeclaredClauseMatchesWhatTheQueriesCarry() {
        assertThat(LikePatterns.ESCAPE_CLAUSE)
            .as("""
                ⚠ Both halves are required and neither works alone: escaping with no ESCAPE clause
                stores a literal backslash in the pattern and matches nothing, and a clause with no
                escaping changes nothing at all. This constant is what a reader compares the 25
                query occurrences against.""")
            .isEqualTo("ESCAPE '" + E + "'");
    }
}
