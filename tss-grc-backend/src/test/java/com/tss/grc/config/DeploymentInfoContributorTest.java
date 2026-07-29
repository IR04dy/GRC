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
        Info.Builder builder = new Info.Builder();
        new DeploymentInfoContributor(jdbc, "flyway_schema_history", flywayEnabled)
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
    void filesThatLookLikeMigrationsButCannotBeVersionedAreNamed() {
        // Found by building the contributor, not by reading: V46a__ and V47a__ sit in the migration
        // folder, and a Flyway version admits only digits separated by '.' or '_'. Flyway skips an
        // unparseable name rather than failing — proven by the dates: those two landed 2026-05-11 and
        // migrations through V312 have applied since, so no boot ever choked on them. Which means
        // their contents have never been applied anywhere Flyway runs, and never will be.
        //
        // Pinned so the *class* of defect stays visible. If the two files are renamed or deleted this
        // fails, and the right response is to update the expectation — not to drop the check, which is
        // the only thing standing between "a file that looks like a migration" and "a migration".
        Map<String, Object> m = migrationsFrom(historyMissing(), true);

        @SuppressWarnings("unchecked")
        List<String> unversioned = (List<String>) m.get("unversionedFiles");
        assertThat(unversioned)
            .as("the two known-dead migration files must be reported, not silently skipped")
            .containsExactly("V46a__cyber_pdpl_bridge_seed_fix.sql",
                             "V47a__evidence_chain_broken_demo.sql");
        assertThat((String) m.get("unversionedNote")).contains("never been applied");
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
