package com.tss.grc.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
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

    // ── F2-241: the range reading, and the (table, column) address ─────────────────────────────

    @Test
    @DisplayName("a numeric range is read as an interval, and is NOT offered as a vocabulary")
    void aRangeIsReadAsABoundNotAsTokens() {
        List<MigrationVocabularies.Range> all = MigrationVocabularies.ranges();
        assertThat(all).as("anti-vacuity: 44 single-column numeric CHECKs measured in dev at F2-241")
            .hasSizeGreaterThanOrEqualTo(20);

        // The two forms this database uses, one of each. ck_ndi_comp_weight is `>= 0 AND <= 10`;
        // CK_vln_sla_policy_warn_pct is the EXCLUSIVE lower bound `> 0 AND <= 1`, which is the case
        // a reader that assumed inclusivity would get wrong in the only direction that matters.
        var weight = named(all, "ck_ndi_comp_weight");
        assertThat(weight.parsed()).isTrue();
        assertThat(weight.admits(new BigDecimal("0"))).isTrue();
        assertThat(weight.admits(new BigDecimal("10"))).isTrue();
        assertThat(weight.admits(new BigDecimal("10.01"))).isFalse();
        assertThat(weight.admits(new BigDecimal("-0.01"))).isFalse();

        var warnPct = named(all, "CK_vln_sla_policy_warn_pct");
        assertThat(warnPct.admits(new BigDecimal("0")))
            .as("`> 0` excludes zero; an inclusive reading would admit a warning threshold of 0%%")
            .isFalse();
        assertThat(warnPct.admits(new BigDecimal("0.750"))).isTrue();
        assertThat(warnPct.admits(new BigDecimal("1.000"))).isTrue();

        // …and a range must never appear as a vocabulary, which is F2-230's asserted limit.
        assertThat(MigrationVocabularies.byConstraint()).doesNotContainKey("ck_ndi_comp_weight");
    }

    @Test
    @DisplayName("an unparsed bound admits everything, and says so rather than pretending")
    void anUnparsedBoundIsVisible() {
        // Nothing may quietly behave as a satisfied bound: admits() returning true for an unparsed
        // range is deliberate (a gate must not fail on a rule it could not read), so the flag is
        // what a caller checks. If this count grows, a new predicate shape has arrived.
        long unparsed = MigrationVocabularies.ranges().stream().filter(r -> !r.parsed()).count();
        assertThat(unparsed)
            .as("single-column numeric CHECKs whose interval this reader could not reduce; each one "
              + "is a bound that admits everything, so the number is worth knowing")
            .isEqualTo(0);
    }

    @Test
    @DisplayName("a bare table name that exists in two schemas is refused, not guessed")
    void anAmbiguousTableNameFailsLoudly() {
        // scm.vendor_assessments and tprm.vendor_assessments BOTH carry a status vocabulary, and the
        // tokens differ — ESCALATED is legal in one and not the other. F2-240's filing was written
        // from the wrong twin of this pair; resolving it by bare name is how that happens.
        assertThatThrownBy(() -> MigrationVocabularies.vocabularyOn("", "vendor_assessments", "status"))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("ambiguous")
            .hasMessageContaining("scm")
            .hasMessageContaining("tprm");

        // Qualified, each resolves to its own register's vocabulary, and they are NOT the same set.
        Set<String> scm = MigrationVocabularies.vocabularyOn("scm", "vendor_assessments", "status");
        Set<String> tprm = MigrationVocabularies.vocabularyOn("tprm", "vendor_assessments", "status");
        assertThat(scm).contains("ESCALATED");
        assertThat(tprm).doesNotContain("ESCALATED");
        assertThat(scm).isNotEqualTo(tprm);
    }

    @Test
    @DisplayName("a column with no CHECK returns empty, and that is not an error")
    void anUnconstrainedColumnIsEmptyRatherThanLoud() {
        // The distinction matters: valuesOf() throws on a misspelled CONSTRAINT because a name one
        // letter wrong fails exactly like a missing one (F2-177). But asking what an arbitrary
        // column admits is a question most columns legitimately answer "nothing in particular" to,
        // and 169 of the 183 initialiser comparisons F2-241 makes rely on that being cheap.
        assertThat(MigrationVocabularies.vocabularyOn("tprm", "vendor_assessments", "assessor"))
            .isEmpty();
        assertThat(MigrationVocabularies.vocabularyOn("scm", "no_such_table", "status")).isEmpty();
    }

    @Test
    @DisplayName("every column of a multi-column ALTER ... ADD is parsed, not just the first")
    void aMultiColumnAddDeclaresEveryColumn() {
        // F2-463. `ALTER TABLE t ADD a T, b T, c T;` is one statement adding three columns, and the
        // house style puts each on its own line. A pattern that captures the first column and reads
        // its tail as `[^;\n]*` drops the rest -- 43 columns across 8 clauses -- and a column
        // missing from this map is one every reader of it is blind to. It fails in the QUIET
        // direction: NullableUniqueKeyFilterContractTest asks Boolean.TRUE.equals(table.get(c)),
        // and a missing column answers false, so a unique key over a nullable column it could not
        // see read as safe.
        var auditLog = MigrationVocabularies.columnSpecs().get("platform.audit_log");
        assertThat(auditLog).as("V340 adds four columns in one ADD clause")
            .containsKeys("row_hash", "prev_hash", "hash_alg", "sealed_at");
        assertThat(auditLog.get("prev_hash").width())
            .as("the second column of the clause, which the single-column form dropped")
            .isEqualTo(64);

        // The same clause mixes text and non-text; a type with no parenthesised size has no width.
        assertThat(auditLog.get("sealed_at").width())
            .as("DATETIME2 carries no width").isNull();

        // Nullability travels per item, not per clause.
        assertThat(auditLog.get("hash_alg")).isNotNull();
        assertThat(auditLog.get("hash_alg").nullable()).isTrue();

        // V37 adds nine columns to cyber.cyber_frameworks in one clause, the last of them text.
        var frameworks = MigrationVocabularies.columnSpecs().get("cyber.cyber_frameworks");
        assertThat(frameworks.get("reference_url").width())
            .as("the ninth column of a nine-column ADD").isEqualTo(500);
        assertThat(frameworks.get("framework_code").width()).isEqualTo(20);
    }

    @Test
    @DisplayName("columns() is a second reader over columnSpecs(), never a second walk")
    void theNullabilityViewIsDerivedFromTheOneWalk() {
        // Two walks would be free to disagree about which files are packaged and which declaration
        // wins (F2-190), and the disagreement would be invisible: each map would look complete.
        assertThat(MigrationVocabularies.columns().keySet())
            .isEqualTo(MigrationVocabularies.columnSpecs().keySet());
        MigrationVocabularies.columnSpecs().forEach((table, specs) ->
            assertThat(MigrationVocabularies.columns().get(table).keySet())
                .as("%s", table).isEqualTo(specs.keySet()));
    }

    private static MigrationVocabularies.Range named(
            List<MigrationVocabularies.Range> all, String constraint) {
        return all.stream().filter(r -> r.constraint().equalsIgnoreCase(constraint)).findFirst()
            .orElseThrow(() -> new AssertionError(
                "no single-column numeric range named '" + constraint + "'. Parsed "
                + all.size() + ": " + all.stream().map(MigrationVocabularies.Range::constraint)
                    .sorted().toList()));
    }
}
