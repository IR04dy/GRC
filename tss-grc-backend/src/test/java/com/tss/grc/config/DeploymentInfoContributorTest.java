package com.tss.grc.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W0-7 — {@code /actuator/info} must be able to say which artifact is running and what has actually
 * been applied to this database.
 *
 * <p>The scenario these tests encode is not hypothetical. Master carried a fix for a defect failing
 * 33–35×/day in production while production served a jar from six days earlier, and no query against
 * the running system would have revealed it. So the assertions are about the two numbers whose
 * disagreement *is* that situation: the head packaged in the artifact, and the head applied in the
 * database.
 */
class DeploymentInfoContributorTest {

    @SuppressWarnings("unchecked")
    private JdbcTemplate historyReturning(String... versions) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(versions));
        return jdbc;
    }

    private JdbcTemplate historyMissing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
            .thenThrow(new org.springframework.jdbc.BadSqlGrammarException(
                "select", "SELECT version FROM flyway_schema_history",
                new java.sql.SQLException("Invalid object name 'flyway_schema_history'.")));
        return jdbc;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> migrationsFrom(JdbcTemplate jdbc, boolean flywayEnabled) {
        return migrationsFrom(jdbc, flywayEnabled, "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> migrationsFrom(JdbcTemplate jdbc, boolean flywayEnabled,
                                               String flywaySchema) {
        Info.Builder builder = new Info.Builder();
        new DeploymentInfoContributor(jdbc, "flyway_schema_history", flywaySchema, flywayEnabled)
            .contribute(builder);
        return (Map<String, Object>) builder.build().getDetails().get("migrations");
    }

    @Test
    void reportsWhatThisArtifactPackages() {
        Map<String, Object> m = migrationsFrom(historyMissing(), false);

        // Read from the real classpath, so this also proves the resource scan finds the migration
        // folder at all — a silently-empty scan would make every other field meaningless.
        assertThat((Integer) m.get("packagedCount"))
            .as("the migration folder should be found on the test classpath")
            .isGreaterThan(300);
        assertThat((String) m.get("packagedHead")).isNotNull();
    }

    @Test
    void anAbsentHistoryTableIsSaidPlainly_notReportedAsEverythingPending() {
        // The dev profile bootstraps its schema with Python and runs Flyway off, so the history table
        // genuinely does not exist. Listing all 313 packaged migrations as "pending" would be true of
        // a fresh database and false here, and an operator reading it on the wrong box would draw
        // precisely the wrong conclusion.
        Map<String, Object> m = migrationsFrom(historyMissing(), false);

        assertThat(m.get("appliedHead")).isNull();
        assertThat(m.get("historyAvailable")).isEqualTo(false);
        assertThat(m).doesNotContainKey("pending");
        assertThat((String) m.get("note")).contains("Flyway is disabled");
    }

    @Test
    void theDeploymentGapIsReportedAsTheListOfWhatIsMissing() {
        // The situation W0-7 exists for: the artifact is ahead of the database.
        Map<String, Object> m = migrationsFrom(historyReturning("1", "2", "300"), true);

        assertThat(m.get("appliedHead")).isEqualTo("300");
        assertThat(m.get("historyAvailable")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<String> pending = (List<String>) m.get("pending");
        assertThat(pending)
            .as("every packaged version above the applied head, so \"it applies on the next "
                + "restart\" becomes a checkable statement")
            .isNotEmpty()
            .allSatisfy(v -> assertThat(DeploymentInfoContributor.compareVersions(v, "300"))
                .isGreaterThan(0));
        assertThat((String) m.get("note")).contains("next start");
    }

    @Test
    void noPendingNoteWhenTheDatabaseIsUpToDate() {
        // An applied head far above anything packaged: nothing pending, and no note claiming there is.
        Map<String, Object> m = migrationsFrom(historyReturning("99999"), true);

        assertThat(m.get("pendingCount")).isEqualTo(0);
        assertThat(m.get("note")).isNull();
    }

    @Test
    void noFileInTheMigrationFolderIsOneFlywayCannotVersion() {
        // ── This assertion was inverted on 2026-07-30, exactly as its previous form instructed. ──
        //
        // It used to assert that `unversionedFiles` containsExactly V46a__cyber_pdpl_bridge_seed_fix.sql
        // and V47a__evidence_chain_broken_demo.sql — pinning the *presence* of F2-49 so the defect class
        // stayed visible while the disposition was an open owner decision. Its own comment said: "If the
        // two files are renamed or deleted this fails, and the right response is to update the
        // expectation — not to drop the check." That is what happened, so that is what was done.
        //
        // ADR-017 D-6 closed the decision: both files were DELETED, not renumbered. Measured first, in
        // both databases — dev TSS_GRC and the deployed TSS_GRC_Deployment each already held the rows
        // both files would have inserted, so nothing was lost. And renumbering would have been worse
        // than leaving them: both are demo seed data, so a valid version would have made Flyway inject
        // fabricated PDPL breach records and a deliberately corrupted chain of custody into every
        // future production database. The broken filename was accidentally load-bearing.
        //
        // ⚠ The positive path — "the detector fires when a bad file exists" — is NOT exercised here and
        // cannot be: this contributor scans the real classpath, so proving it fires needs a bad file in
        // the tree. That proof lives in MigrationFilenameRatchetTest, which is mutation-proven by
        // adding V99z__mutation_probe.sql and confirming the failure names it. This test now guards the
        // other direction: that the folder stays clean, reported through the endpoint an operator reads.
        Map<String, Object> m = migrationsFrom(historyMissing(), true);

        assertThat(m.get("unversionedFiles"))
            .as("the contributor omits this key entirely when every file is versioned; a non-null value "
                + "means a file is shipping in the jar that Flyway will silently never apply (F2-49)")
            .isNull();
        assertThat(m.get("unversionedNote")).isNull();
        // Anti-vacuity: prove the scan actually resolved the migration folder rather than finding
        // nothing and reporting a clean folder because it looked in the wrong place.
        assertThat((Integer) m.get("packagedCount"))
            .as("packagedCount must be real, or 'no unversioned files' means 'no files found'")
            .isGreaterThan(250);
    }

    @Test
    void versionsOrderNumerically_becauseStringOrderPutsV10BeforeV9() {
        // This is the bug that would silently misreport the head the moment the count crossed a power
        // of ten — "V9" > "V10" as strings, and this project is at V323.
        assertThat(DeploymentInfoContributor.compareVersions("9", "10")).isNegative();
        assertThat(DeploymentInfoContributor.compareVersions("323", "99")).isPositive();
        assertThat(DeploymentInfoContributor.compareVersions("12", "12")).isZero();
        // Dotted versions are legal Flyway and sort part-wise, not lexically.
        assertThat(DeploymentInfoContributor.compareVersions("1.2", "1.10")).isNegative();
        assertThat(DeploymentInfoContributor.compareVersions("2", "1.9")).isPositive();
    }

    // ── F2-73 ────────────────────────────────────────────────────────────────────────────────
    // Production reported `historyAvailable: false` with the note "nothing has been applied through
    // Flyway" while platform.flyway_schema_history held 45 rows through V336. Two defects in one
    // sentence: the table name was unqualified, and a read failure was rendered as a fact about the
    // world. Both are pinned below, because `pending` — and therefore deployment_identity.py's
    // "packaged but NOT applied" check — is computed only when the history reads successfully.

    @Test
    void theHistoryTableIsQualifiedWithFlywaysOwnDefaultSchema() {
        JdbcTemplate jdbc = historyReturning("300");
        migrationsFrom(jdbc, true, "platform");

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).queryForList(sql.capture(), eq(String.class));

        // Flyway creates its history table INSIDE spring.flyway.default-schema. Query it unqualified
        // and SQL Server resolves against the login's default schema — dbo in production, which is
        // empty — so the read raises `Invalid object name` and this class reported "nothing applied".
        assertThat(sql.getValue())
            .as("the query must name the schema Flyway actually puts the table in")
            .contains("platform.flyway_schema_history");
    }

    @Test
    void withNoConfiguredSchemaTheTableStaysUnqualified() {
        // Dev sets no default-schema, and adding a bogus prefix there would break the one
        // environment that was working. The fix must be inert when the property is absent.
        JdbcTemplate jdbc = historyReturning("300");
        migrationsFrom(jdbc, true, "   ");

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbc).queryForList(sql.capture(), eq(String.class));
        assertThat(sql.getValue()).contains("FROM flyway_schema_history");
        assertThat(sql.getValue()).doesNotContain(".flyway_schema_history");
    }

    @Test
    void anUnreadableHistoryIsReportedAsBlindness_notAsAnEmptyHistory() {
        // The table EXISTS (OBJECT_ID answers) but cannot be read. That is the production case, and
        // it is the opposite of "nothing has been applied".
        JdbcTemplate jdbc = historyMissing();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(1234);

        Map<String, Object> m = migrationsFrom(jdbc, true, "platform");

        assertThat(m.get("historyAvailable")).isEqualTo(false);
        assertThat(m.get("historyReadable")).as("the field that distinguishes the two").isEqualTo(false);
        assertThat((String) m.get("note"))
            .as("an operator must not read a failed query as a clean database")
            .contains("EXISTS but could not be read")
            .contains("Invalid object name")
            .doesNotContain("nothing has been applied");
    }

    @Test
    void anAbsentHistoryStillReadsAsAbsent_soDevIsUnchanged() {
        // OBJECT_ID answers null: the table really is not there. This is the dev truth and it must
        // survive the fix — otherwise every dev box would start claiming its instrument is blind.
        JdbcTemplate jdbc = historyMissing();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(null);

        Map<String, Object> m = migrationsFrom(jdbc, true, "platform");

        assertThat(m.get("historyAvailable")).isEqualTo(false);
        assertThat(m).doesNotContainKey("historyReadable");
        assertThat((String) m.get("note")).contains("nothing has been applied through Flyway");
    }

    @Test
    void aBrokenHistoryQueryDegradesInsteadOfFailingTheEndpoint() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class)))
            .thenThrow(new RuntimeException("connection reset"));

        // A probe that 500s tells an operator nothing about the deployment and everything about this
        // class, which is the opposite of the point.
        Map<String, Object> m = migrationsFrom(jdbc, true);
        assertThat(m.get("historyAvailable")).isEqualTo(false);
        assertThat((Integer) m.get("packagedCount")).isGreaterThan(0);
    }
}
