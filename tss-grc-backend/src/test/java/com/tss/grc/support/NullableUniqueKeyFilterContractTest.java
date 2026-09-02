package com.tss.grc.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F2-433 — a UNIQUE key whose key includes a NULLABLE column must be FILTERED.
 *
 * <h4>The rule</h4>
 *
 * SQL Server treats NULLs as <b>EQUAL</b> for uniqueness. So a plain unique key over a nullable
 * column permits at most <b>ONE</b> row with NULL there per distinct value of the rest of the key —
 * refusing a state the column's own nullability declares legal, with a message about uniqueness for
 * a value the caller never supplied.
 *
 * <h4>Why this gate exists rather than the one that already stated the rule</h4>
 *
 * F2-309 built {@code V396} — nine filtered unique indexes — and
 * {@code ReferenceRenameContractTest#everyImmutableReferenceIsAlsoUniquePerTenant} to hold them.
 * That gate's javadoc states this rule correctly, in these words: <em>"filtered on {@code WHERE
 * <code> IS NOT NULL} if the column is nullable, because SQL Server treats NULLs as equal for
 * uniqueness"</em>. Its POPULATION is the nine registers whose reference F2-306..F2-308 froze
 * IMMUTABLE — so {@code audit_eng.engagements}, which has a nullable code, a plain unfiltered
 * UNIQUE constraint and no immutability guard, was in neither the fix nor the gate.
 *
 * <p>🔑 <b>And one of the nine is the TWIN of it.</b> {@code compliance.audit_engagements} carries
 * the same column name in a different schema, so a reader grepping {@code engagement_code} in the
 * migrations finds V396's filtered index and concludes the register is covered — while V396's own
 * missing-index self-check verifies its coverage over a population of nine that excludes the tenth.
 * That is F2-195's shape at its sharpest: the correct rule, in prose, in the gate built to enforce
 * it, beside the one place it was not applied.
 *
 * <h4>Why this is a CENSUS with verdicts and not a hard zero</h4>
 *
 * A blanket "every nullable unique key must be filtered" fails on correct code, which is the one
 * failure a gate must never have (F2-214). On four of the five members a NULL {@code
 * organisation_id} <b>means every tenant</b> (F2-216: six repositories read {@code organisation_id
 * IS NULL OR organisation_id = :orgId}), so uniqueness over {@code (NULL, code)} is exactly right —
 * a global catalogue code must be unique globally, and filtering those would let two global roles
 * share a name. So the boundary is asserted in BOTH directions: they must stay unfiltered.
 *
 * <h4>The blind spot, stated rather than implied</h4>
 *
 * This gate reads the MIGRATIONS, so {@code mvn -o clean package} stays hermetic. The live
 * catalogue holds <b>6</b> such keys and the corpus <b>5</b>: the difference is {@code
 * UQ_lookup_type_code} on {@code platform.lookup_tables}, which <b>no migration creates at all</b>
 * — it is one of the objects the Python bootstrap makes (F2-255 measured the same gap for the
 * permission catalogue: 93 pairs from Flyway against 140 live). A hard assertion over a population
 * the instrument cannot see is the most reassuring possible artefact (F2-205), so
 * {@link #theBootstrapOnlyKeysAreNamedBecauseNoHermeticGateCanSeeThem()} names it.
 */
@DisplayName("F2-433 — a unique key over a nullable column must be filtered")
class NullableUniqueKeyFilterContractTest {

    /**
     * Every unfiltered unique key over a nullable column, with a verdict each.
     *
     * <p>Asserted EXACTLY, never as a ceiling: a ceiling would let a new one in behind a fixed one
     * (house rule 3), and each of these is a reading rather than a count.
     */
    private static final Map<String, String> ADJUDICATED = new LinkedHashMap<>();

    static {
        ADJUDICATED.put("fraud.fraud_typology.uq_fraud_typ_code", """
            BY DESIGN. A NULL organisation_id on this table MEANS every tenant — the shared
            typology catalogue — and FraudTypologyRepository reads
            `organisation_id IS NULL OR organisation_id = :org` (F2-216). So uniqueness over
            (NULL, code) is the correct rule: a global typology code must be unique globally.
            Filtering it would let two GLOBAL rows share one code, which is strictly worse than
            the state this gate exists to prevent.""");
        ADJUDICATED.put("iam.roles.uq_role_name", """
            BY DESIGN, and V334 says so in its own THROW: 'a system role name exists both globally
            and tenant-scoped. Setting organisation_id to NULL would violate
            UQ_role_name(organisation_id, role_name). Reconcile the duplicate pair by hand before
            re-running — do NOT widen the constraint.' A NULL organisation_id is the system
            catalogue, and two global roles sharing a name would make hasAnyRole ambiguous.""");
        ADJUDICATED.put("opres.opres_pillar.uq_opres_pillar_code", """
            BY DESIGN. Same shape as fraud.fraud_typology: the SAMA operational-resilience pillar
            catalogue is shared, NULL organisation_id means every tenant, and
            OpResPillarRepository reads the IS NULL OR = :org predicate.""");
        ADJUDICATED.put("compliance.policy_framework_mappings.uq_pfm_combo", """
            BY DESIGN. The nullable member is clause_ref, and a NULL clause_ref is the GENERAL
            mapping — 'policy P satisfies framework F' with no clause named. Exactly one of those
            per (policy, framework) is what the register wants; a second identical general mapping
            is the duplicate this key is for. Measured: 15 rows, 0 with a NULL clause_ref, in both
            databases, so it is not even exercised today.""");
    }

    /**
     * Keys the live catalogue has and the migration corpus cannot: created by the Python bootstrap
     * rather than by Flyway. Named because a census that silently omits them reads as complete.
     */
    private static final Map<String, String> BOOTSTRAP_ONLY = Map.of(
        "platform.lookup_tables.uq_lookup_type_code", """
            Present in BOTH live databases and in NO migration — `grep -rn UQ_lookup_type_code`
            over the whole corpus returns nothing. F2-255 measured this class: a fresh Flyway
            install from V1 builds 93 catalogue pairs where both live databases hold ~140, because
            the Python bootstrap made the rest. Its nullable member is organisation_id, so by the
            reading above it is BY DESIGN — but no hermetic gate can confirm that, which is the
            point of naming it here.""");

    private static List<MigrationVocabularies.UniqueKey> offenders() {
        Map<String, Map<String, Boolean>> cols = MigrationVocabularies.columns();
        List<MigrationVocabularies.UniqueKey> out = new ArrayList<>();
        for (MigrationVocabularies.UniqueKey k : MigrationVocabularies.uniqueKeys()) {
            Map<String, Boolean> table = cols.get(k.table());
            if (table == null) continue;                                  // counted separately
            boolean anyNullable = k.keyColumns().stream()
                .anyMatch(c -> Boolean.TRUE.equals(table.get(c)));
            if (anyNullable && !k.filtered()) out.add(k);
        }
        return out;
    }

    private static String id(MigrationVocabularies.UniqueKey k) {
        return k.table() + "." + k.name().toLowerCase();
    }

    @Test
    @DisplayName("every unfiltered unique key over a nullable column has a verdict, and none is a defect")
    void everyUnfilteredNullableUniqueKeyIsAdjudicated() {
        Set<String> found = new TreeSet<>();
        for (MigrationVocabularies.UniqueKey k : offenders()) {
            found.add(id(k));
        }

        assertThat(found).as("""
            A UNIQUE key over a NULLABLE column is UNFILTERED and has no verdict here.

            SQL Server treats NULLs as EQUAL for uniqueness, so this key permits at most ONE row
            with NULL in that column per distinct value of the rest of the key — and refuses the
            next one with a message about a field the caller may never have supplied. That is what
            F2-433 found on audit_eng.engagements, where both databases already held the one
            NULL-coded row, so the register's create was broken on the FIRST attempt.

            Either add `WHERE <column> IS NOT NULL` to the index (V398 is the worked example), or
            add a verdict above saying why a NULL there is a VALUE rather than an absence — the
            four adjudicated members are all cases where a NULL organisation_id means EVERY
            TENANT, and filtering those would be strictly worse than not filtering them.

            found:      %s
            adjudicated: %s""".formatted(found, ADJUDICATED.keySet()))
            .containsExactlyInAnyOrderElementsOf(ADJUDICATED.keySet());
    }

    @Test
    @DisplayName("no verdict names a key the scan no longer finds")
    void everyVerdictStillNamesAKeyTheScanFinds() {
        Set<String> found = new LinkedHashSet<>();
        for (MigrationVocabularies.UniqueKey k : offenders()) {
            found.add(id(k));
        }
        List<String> stale = ADJUDICATED.keySet().stream().filter(k -> !found.contains(k)).toList();

        assertThat(stale).as("""
            A verdict names a key this scan no longer reports.

            This is the direction nobody writes first (F2-199), and it is the one that catches a
            detector which has stopped matching: a reason written for something that is not in the
            population reads exactly like a reason for something that is. Either the key gained a
            filter — in which case delete its verdict — or the parser broke.

            %s""".formatted(stale))
            .isEmpty();
    }

    @Test
    @DisplayName("audit_eng.engagements: the plain constraint is gone and the replacement is filtered")
    void theEngagementCodeKeyIsFiltered() {
        List<MigrationVocabularies.UniqueKey> onTable = MigrationVocabularies.uniqueKeys().stream()
            .filter(k -> "audit_eng.engagements".equals(k.table()))
            .filter(k -> k.keyColumns().contains("engagement_code"))
            .toList();

        assertThat(onTable).as(
            "audit_eng.engagements must carry exactly one unique key covering engagement_code")
            .hasSize(1);

        MigrationVocabularies.UniqueKey k = onTable.get(0);
        assertThat(k.name()).as("""
            uk_eng_code is back. While the plain unfiltered UNIQUE constraint stands, at most ONE
            engagement per organisation may have no code — and both databases already hold that
            row, so POST /api/audit-eng/engagements omitting the code is refused on the FIRST
            attempt with `409 One of the fields you supplied must be unique`, naming a field the
            caller did not supply.""")
            .isEqualTo("UX_audit_eng_engagement_code_org");
        assertThat(k.filtered()).as(
            "UX_audit_eng_engagement_code_org must be filtered on engagement_code IS NOT NULL")
            .isTrue();
        assertThat(k.keyColumns()).as(
            "tenant first, or one tenant's engagement code blocks another tenant's")
            .containsExactly("organisation_id", "engagement_code");
    }

    @Test
    @DisplayName("BOUNDARY: a global-catalogue key must NOT gain a filter")
    void theGlobalCatalogueKeysStayUnfiltered() {
        Set<String> global = Set.of(
            "fraud.fraud_typology.uq_fraud_typ_code",
            "iam.roles.uq_role_name",
            "opres.opres_pillar.uq_opres_pillar_code");

        List<String> wronglyFiltered = MigrationVocabularies.uniqueKeys().stream()
            .filter(k -> global.contains(id(k)))
            .filter(MigrationVocabularies.UniqueKey::filtered)
            .map(NullableUniqueKeyFilterContractTest::id)
            .toList();

        assertThat(wronglyFiltered).as("""
            A global-catalogue unique key has been given a filter — almost certainly by somebody
            "finishing the sweep" F2-433 started.

            On these tables a NULL organisation_id is a VALUE meaning EVERY TENANT, not an
            absence: six repositories read `organisation_id IS NULL OR organisation_id = :org`
            (F2-216). Adding `WHERE organisation_id IS NOT NULL` would let TWO GLOBAL rows share
            one code — two global roles with the same name, making hasAnyRole ambiguous — which is
            strictly worse than the defect the filter is copied from.

            %s""".formatted(wronglyFiltered))
            .isEmpty();

        // ...and they must still BE in the population, or this leg is asserting nothing.
        Set<String> present = new LinkedHashSet<>();
        for (MigrationVocabularies.UniqueKey k : offenders()) {
            present.add(id(k));
        }
        assertThat(present).as(
            "the three global-catalogue keys must still be parsed, or the leg above is vacuous")
            .containsAll(global);
    }

    @Test
    @DisplayName("the bootstrap-only keys are named, because no hermetic gate can see them")
    void theBootstrapOnlyKeysAreNamedBecauseNoHermeticGateCanSeeThem() {
        assertThat(BOOTSTRAP_ONLY).as(
            "if this is ever empty, say so deliberately — an empty blind-spot list reads as "
            + "'the corpus and the live catalogue agree', which was measured FALSE (5 vs 6)")
            .isNotEmpty();

        Set<String> parsed = new LinkedHashSet<>();
        for (MigrationVocabularies.UniqueKey k : MigrationVocabularies.uniqueKeys()) {
            parsed.add(id(k));
        }
        List<String> nowVisible = BOOTSTRAP_ONLY.keySet().stream().filter(parsed::contains).toList();

        assertThat(nowVisible).as("""
            A key declared bootstrap-only is now IN the migration corpus.

            That is good news and it makes this entry a lie: move it into ADJUDICATED with a real
            verdict, because a blind-spot entry for something the gate CAN see is worse than no
            entry — it claims the instrument is narrower than it is.

            %s""".formatted(nowVisible))
            .isEmpty();
    }

    @Test
    @DisplayName("SELF-TEST: the detector fires on a nullable unfiltered key and not on the others")
    void theDetectorFiresOnTheShapeAndNotOnCorrectCode() {
        // ⚠ The WHERE is on its OWN LINE on purpose. A newline-bounded tail cannot see it, and
        // that exact bug reported 17 offenders where 6 exist -- see MigrationVocabularies trap 1.
        String synthetic = """
            CREATE TABLE t.demo (
                id              BIGINT IDENTITY(1,1) PRIMARY KEY,
                organisation_id BIGINT       NOT NULL,
                nullable_code   NVARCHAR(32),
                tight_code      NVARCHAR(32) NOT NULL,
                CONSTRAINT uq_demo_plain_nullable UNIQUE (organisation_id, nullable_code),
                CONSTRAINT uq_demo_plain_notnull  UNIQUE (organisation_id, tight_code)
            );
            GO
            CREATE UNIQUE INDEX ux_demo_filtered_multiline
                ON t.demo (organisation_id, nullable_code)
             WHERE nullable_code IS NOT NULL;
            GO
            CREATE UNIQUE INDEX ux_demo_unfiltered_index ON t.demo (organisation_id, nullable_code);
            GO
            -- an epitaph, not a corpse: this quotes a retired key and must NOT be parsed as one
            -- CONSTRAINT uq_demo_from_a_comment UNIQUE (organisation_id, nullable_code)
            """;

        Map<String, Boolean> demoCols = Map.of(
            "id", false, "organisation_id", false, "nullable_code", true, "tight_code", false);

        Map<String, MigrationVocabularies.UniqueKey> live = new LinkedHashMap<>();
        for (MigrationVocabularies.Ev e : MigrationVocabularies.eventsIn(synthetic)) {
            if (e.add()) live.put(e.key(), e.value());
            else live.remove(e.key());
        }

        assertThat(live.keySet()).as("a key quoted inside a comment must not be parsed (F2-193)")
            .doesNotContain("t.demo.uq_demo_from_a_comment");

        Set<String> flagged = new TreeSet<>();
        for (MigrationVocabularies.UniqueKey k : live.values()) {
            boolean anyNullable = k.keyColumns().stream()
                .anyMatch(c -> Boolean.TRUE.equals(demoCols.get(c)));
            if (anyNullable && !k.filtered()) flagged.add(k.name());
        }

        assertThat(flagged).as("""
            The detector must flag exactly the two unfiltered keys over the nullable column, and
            neither the NOT NULL one nor the one whose WHERE sits on the following line.

            If ux_demo_filtered_multiline appears here, the filter detector has gone back to being
            bounded by a newline -- the bug that reported 17 offenders where 6 exist.
            If uq_demo_plain_notnull appears here, nullability parsing is broken and this gate is
            about to fail on correct code.""")
            .containsExactly("uq_demo_plain_nullable", "ux_demo_unfiltered_index");
    }

    @Test
    @DisplayName("SELF-TEST: a later DROP wins, so a fixed key does not read as still broken")
    void aLaterDropWins() {
        // MigrationVocabularies trap 3, and it is load-bearing here: V25 declares uk_eng_code and
        // V398 drops it. A parser reading only first declarations would report the defect as live
        // on a tree that has already been fixed -- a gate failing on correct code.
        String synthetic = """
            CREATE TABLE t.demo (
                organisation_id BIGINT NOT NULL,
                code            NVARCHAR(32),
                CONSTRAINT uq_demo_old UNIQUE (organisation_id, code)
            );
            GO
            ALTER TABLE t.demo DROP CONSTRAINT uq_demo_old;
            GO
            CREATE UNIQUE INDEX ux_demo_new
                ON t.demo (organisation_id, code)
             WHERE code IS NOT NULL;
            GO
            """;

        Map<String, MigrationVocabularies.UniqueKey> live = new LinkedHashMap<>();
        for (MigrationVocabularies.Ev e : MigrationVocabularies.eventsIn(synthetic)) {
            if (e.add()) live.put(e.key(), e.value());
            else live.remove(e.key());
        }

        assertThat(live.keySet())
            .as("a dropped constraint must not survive; the replacement must")
            .containsExactly("t.demo.ux_demo_new");
        assertThat(live.get("t.demo.ux_demo_new").filtered()).isTrue();
    }

    @Test
    @DisplayName("ANTI-VACUITY: the corpus really was parsed")
    void theCorpusWasActuallyParsed() {
        // Floors sit above the largest WRONG answer, not above zero (F2-215). The wrong answers
        // here are "the walk resolved no files" (0) and "the column parser matched nothing" (0),
        // both of which produce an EMPTY offender list -- indistinguishable from a clean tree.
        assertThat(MigrationVocabularies.columns()).as("CREATE TABLE bodies parsed").hasSizeGreaterThan(400);
        assertThat(MigrationVocabularies.uniqueKeys()).as("unique keys parsed").hasSizeGreaterThan(200);

        long resolvable = MigrationVocabularies.uniqueKeys().stream()
            .filter(k -> MigrationVocabularies.columns().containsKey(k.table()))
            .count();
        assertThat(resolvable).as(
            "most unique keys must resolve to a parsed table, or the census is measuring a "
            + "fraction of the corpus and reporting it as the whole")
            .isGreaterThan((long) (MigrationVocabularies.uniqueKeys().size() * 0.9));
    }
}
