package com.tss.grc.module.ccm.runner;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomSqlRunnerTest {

    @Mock private EntityManager em;
    @Mock private Query query;

    private CustomSqlRunner runner;

    @BeforeEach
    void setUp() {
        // Enabled for the execution-path tests; a disabled-runner test is below.
        runner = new CustomSqlRunner(em, true);
    }

    private static ControlTestDefinition def(String sql, int maxRows) {
        ControlTestDefinition d = new ControlTestDefinition();
        d.setTestCode("CCM-PDPL-RETENTION-001");
        d.setTestType("CUSTOM_SQL");
        d.setTargetSystemType("INTERNAL_DB");
        d.setExpectedConditionJson("{\"sql\":\"" + sql.replace("\"", "\\\"") + "\",\"maxRowCount\":" + maxRows + "}");
        return d;
    }

    @Test
    void selectAtThresholdPasses() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(0L));

        ControlTestRunner.RunResult r = runner.execute(
                def("SELECT COUNT(*) FROM customer WHERE organisation_id = 1 AND created_at < '2020-01-01'", 0));

        assertThat(r.status()).isEqualTo("PASS");
        assertThat(r.actualValueJson()).contains("\"rowCount\":0");
    }

    @Test
    void selectAboveThresholdFails() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(15L));

        ControlTestRunner.RunResult r = runner.execute(
                def("SELECT COUNT(*) FROM customer WHERE organisation_id = 1 AND created_at < '2020-01-01'", 0));

        assertThat(r.status()).isEqualTo("FAIL");
        assertThat(r.failureReason()).contains("15");
    }

    @Test
    void insertIsRejected() {
        ControlTestRunner.RunResult r = runner.execute(
                def("INSERT INTO customer (id) VALUES (1)", 0));
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.failureReason()).contains("Only SELECT");
    }

    @Test
    void updateIsRejected() {
        ControlTestRunner.RunResult r = runner.execute(
                def("UPDATE customer SET name='x'", 0));
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.failureReason()).contains("Only SELECT");
    }

    @Test
    void deleteIsRejected() {
        ControlTestRunner.RunResult r = runner.execute(
                def("DELETE FROM customer", 0));
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.failureReason()).contains("Only SELECT");
    }

    @Test
    void chainedSelectIsRejected() {
        ControlTestRunner.RunResult r = runner.execute(
                def("SELECT 1; DROP TABLE customer", 0));
        assertThat(r.status()).isEqualTo("ERROR");
    }

    @Test
    void errorOnBadSql() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenThrow(new RuntimeException("Invalid syntax"));

        ControlTestRunner.RunResult r = runner.execute(
                def("SELECT garbage FROM x WHERE organisation_id = 1", 0));
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.failureReason()).contains("Invalid syntax");
    }

    @Test
    void rejectionReasonHelperUnitTest() {
        // Tenant-scoped SELECTs pass.
        assertThat(CustomSqlRunner.rejectionReason("SELECT 1 WHERE organisation_id = 1")).isNull();
        assertThat(CustomSqlRunner.rejectionReason("/* test */ SELECT count(*) FROM t WHERE organisation_id = 1")).isNull();
        assertThat(CustomSqlRunner.rejectionReason("-- c\nSELECT 1 FROM t WHERE organisation_id = 1")).isNull();
        assertThat(CustomSqlRunner.rejectionReason("INSERT INTO x VALUES (1)")).contains("Only SELECT");
        assertThat(CustomSqlRunner.rejectionReason("")).contains("missing");
        assertThat(CustomSqlRunner.rejectionReason(null)).contains("missing");
    }

    // ─── SEC-INT-001 regressions ──────────────────────────────────

    @Test
    void disabledRunner_rejectsEvenValidSelect() {
        CustomSqlRunner disabled = new CustomSqlRunner(em, false);
        ControlTestRunner.RunResult r = disabled.execute(
                def("SELECT COUNT(*) FROM customer WHERE organisation_id = 1", 0));
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.failureReason()).contains("disabled by policy");
    }

    @Test
    void selectWithoutOrganisationScope_isRejected() {
        // The classic cross-tenant exfiltration attempt — must be blocked.
        ControlTestRunner.RunResult r = runner.execute(
                def("SELECT username, password_hash FROM iam.users", 0));
        assertThat(r.status()).isEqualTo("ERROR");
        assertThat(r.failureReason()).contains("organisation_id");
    }
}
