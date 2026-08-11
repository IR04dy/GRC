package com.tss.grc.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared migration parser's own tests.
 *
 * <p><b>Why this class exists, and it is a lesson rather than housekeeping.</b> When F2-179 extracted
 * this parser out of three test classes, its mutation harness found that <b>two of its safety rules
 * mask each other</b>, so neither was pinned by any consumer:
 *
 * <ul>
 *   <li>Remove the literal-stripping and {@code ratification_state <> N'PENDING'} yields the phantom
 *       column {@code PEND} (because {@code PENDING} contains {@code IN})…</li>
 *   <li>…but that makes the predicate's column set size <b>two</b>, so the multi-column rule then
 *       drops the whole constraint instead of admitting the phantom — and every downstream
 *       assertion stayed green.</li>
 * </ul>
 *
 * <p>🔑 <b>Two guards that each hide the other's failure are, between them, one guard that nothing
 * tests.</b> A downstream consumer notices a parser defect only when the defect happens to change an
 * answer it asserts; the parser needs assertions of its own. Both mutations are caught here.
 *
 * <p>⚙ The same reasoning is why {@code PromotionVocabularyTest} keeps an anti-vacuity floor on the
 * <em>number</em> of comparisons it makes: a parse that quietly returns less is the failure mode of
 * every artefact built on a regex over someone else's language.
 */
class MigrationVocabulariesTest {

    @Test
    @DisplayName("a value inside a string literal never becomes a column name")
    void noPhantomColumnIsInventedFromAStringLiteral() {
        Map<String, MigrationVocabularies.Vocabulary> all = MigrationVocabularies.byConstraint();

        assertThat(all).as("anti-vacuity: the parse must have found the platform's vocabularies")
            .hasSizeGreaterThanOrEqualTo(40);

        // 'PEND' is the exact phantom the first draft produced, from ck_change_ratification_clock.
        // 'REJECT' is the same shape from CK_cbt_rejection's `status <> N'REJECTED'` — six more
        // constraints in this database have that predicate, so it is the likelier next instance.
        assertThat(all.values().stream().map(MigrationVocabularies.Vocabulary::column).toList())
            .as("a column name that is really a fragment of a quoted value means the parser is "
              + "reading SQL and the strings inside it as one language")
            .doesNotContain("pend", "reject", "sign", "waive", "open");
    }

    @Test
    @DisplayName("a cross-column rule is not read as a vocabulary")
    void multiColumnPredicatesAreExcluded() {
        Map<String, MigrationVocabularies.Vocabulary> all = MigrationVocabularies.byConstraint();

        // These constrain a RELATIONSHIP between two columns ("if status is REJECTED then
        // rejection_reason must be present"). They contain quoted upper-case tokens, so a parser
        // looking only for literals would offer them as vocabularies — and a caller asking what
        // `status` may hold would get {REJECTED} alone, which is precisely wrong.
        for (String crossColumn : new String[] {
                "ck_change_ratification_clock", "ck_cbt_rejection", "ck_rca_rejection_reason"}) {
            assertThat(all).as(
                "%s relates two columns; sys.check_constraints reports parent_column_id = 0 for it, "
                + "and this parser must agree with the database about that", crossColumn)
                .doesNotContainKey(crossColumn);
        }

        // …while the single-column vocabulary on the SAME table is still found, so the exclusion is
        // narrow rather than a wholesale skip of the file.
        assertThat(MigrationVocabularies.valuesOf("CK_wb_severity"))
            .containsExactlyInAnyOrder("CRITICAL", "HIGH", "MEDIUM", "LOW");
    }

    @Test
    @DisplayName("the last definition in version order wins")
    void aRedefinedConstraintIsReadAsItWillEndUp() {
        // CK_incidents_category is the F2-180 case: read as the chain leaves it, not as V4 wrote it.
        Set<String> categories = MigrationVocabularies.valuesOf("CK_incidents_category");
        assertThat(categories).isNotEmpty();
        assertThat(categories).allSatisfy(t ->
            assertThat(t).as("every token is an upper-case identifier").matches("[A-Z][A-Z0-9_]*"));
    }

    @Test
    @DisplayName("an unknown constraint name is an error, never an empty set")
    void anUnknownNameFailsLoudly() {
        assertThatThrownBy(() -> MigrationVocabularies.valuesOf("CK_does_not_exist"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("CK_does_not_exist")
            .hasMessageContaining("misspelled");
    }

    @Test
    @DisplayName("lookup is case-insensitive on the constraint name, as SQL Server is")
    void theNameLookupMatchesTheDatabasesOwnCollation() {
        assertThat(MigrationVocabularies.valuesOf("ck_wb_severity"))
            .isEqualTo(MigrationVocabularies.valuesOf("CK_WB_SEVERITY"))
            .isEqualTo(MigrationVocabularies.valuesOf("CK_wb_severity"));
    }
}
