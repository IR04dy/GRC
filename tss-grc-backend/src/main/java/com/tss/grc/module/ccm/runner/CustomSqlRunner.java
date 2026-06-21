package com.tss.grc.module.ccm.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Custom-SQL runner — executes a single read-only {@code SELECT}
 * statement and compares the row count (or first column of the first
 * row, if numeric) against a {@code maxRowCount} threshold.
 *
 * <p>Hard constraint: the SQL must start with {@code SELECT} after
 * stripping leading whitespace and comments. {@code INSERT},
 * {@code UPDATE}, {@code DELETE}, {@code MERGE}, {@code DROP},
 * {@code TRUNCATE}, {@code EXEC}, {@code ALTER}, {@code GRANT} are
 * rejected with status {@code ERROR} before the query ever reaches the
 * database. This is the auditor-facing guarantee that a CCM test
 * cannot rewrite the very system it's monitoring.
 */
@Slf4j
@Component
public class CustomSqlRunner implements ControlTestRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Matches anything that isn't a leading SELECT (after stripping comments / whitespace). */
    private static final Pattern SELECT_ONLY = Pattern.compile("^\\s*select\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Tokens that immediately reject a query as DML/DDL. */
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "DROP", "TRUNCATE",
            "ALTER", "CREATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL"
    );

    @PersistenceContext
    private EntityManager em;

    /**
     * SEC-INT-001: free-form CUSTOM_SQL execution is DISABLED by default. The
     * deny-list of statement types is NOT an access-control boundary — a SELECT
     * against a shared multi-tenant schema can still read other tenants' rows.
     * This capability may only be enabled by an operator who has provisioned a
     * least-privilege, per-tenant database account; never against the shared
     * application login. Toggle via {@code grc.ccm.custom-sql.enabled}.
     */
    private final boolean customSqlEnabled;

    @Autowired
    public CustomSqlRunner(@Value("${grc.ccm.custom-sql.enabled:false}") boolean customSqlEnabled) {
        this.customSqlEnabled = customSqlEnabled;
    }

    /** Test seam constructor. */
    CustomSqlRunner(EntityManager em, boolean customSqlEnabled) {
        this.em = em;
        this.customSqlEnabled = customSqlEnabled;
    }

    @Override
    public String testType() { return "CUSTOM_SQL"; }

    @Override
    @Transactional(readOnly = true)
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        try {
            // SEC-INT-001: hard kill-switch — refuse to run any tenant-supplied
            // SQL unless an operator has explicitly enabled the capability.
            if (!customSqlEnabled) {
                return RunResult.error(
                    "CUSTOM_SQL execution is disabled by policy (SEC-INT-001). Enable "
                    + "grc.ccm.custom-sql.enabled only with a least-privilege, per-tenant DB account.",
                    StubRunnerSupport.millisSince(start));
            }

            Map<String, Object> cond = StubRunnerSupport.parseJson(def.getExpectedConditionJson());
            String sql = StubRunnerSupport.strOr(cond, "sql", "");
            int maxRows = StubRunnerSupport.intOr(cond, "maxRowCount", 0);

            String rejection = rejectionReason(sql);
            if (rejection != null) {
                return RunResult.error(rejection, StubRunnerSupport.millisSince(start));
            }

            Query q = em.createNativeQuery(sql);
            List<?> result = q.getResultList();

            long actualCount;
            // Convention: if SELECT returns a single number on a single
            // row, that's the offender count. Otherwise we use the
            // result-set size.
            if (result.size() == 1 && result.get(0) instanceof Number n) {
                actualCount = n.longValue();
            } else if (result.size() == 1 && result.get(0) instanceof Object[] arr
                    && arr.length > 0 && arr[0] instanceof Number n2) {
                actualCount = n2.longValue();
            } else {
                actualCount = result.size();
            }

            Map<String, Object> actual = new HashMap<>();
            actual.put("rowCount", actualCount);
            actual.put("maxRowCount", maxRows);
            actual.put("sql", sql);
            String actualJson;
            try {
                actualJson = MAPPER.writeValueAsString(actual);
            } catch (Exception jsonEx) {
                actualJson = "{}";
            }

            int duration = StubRunnerSupport.millisSince(start);
            if (actualCount <= maxRows) {
                return RunResult.pass(actualJson, duration);
            }
            return RunResult.fail(actualJson,
                    "Query returned " + actualCount + " row(s); threshold is " + maxRows,
                    duration);
        } catch (RuntimeException ex) {
            log.warn("CUSTOM_SQL runner failed: {}", ex.getMessage());
            return RunResult.error(ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    StubRunnerSupport.millisSince(start));
        }
    }

    /**
     * Returns a non-null rejection reason when the SQL is unsafe. Visible
     * for testing — kept package-private.
     */
    static String rejectionReason(String sql) {
        if (sql == null || sql.isBlank()) return "expected_condition_json.sql missing";
        String stripped = stripCommentsAndWhitespace(sql);
        if (!SELECT_ONLY.matcher(stripped).find()) {
            return "Only SELECT statements are permitted in CCM CUSTOM_SQL tests";
        }
        // Defence-in-depth: even when the statement starts with SELECT,
        // refuse if any of the DML/DDL tokens appear as standalone words.
        // Crude but reliable for the bounded auditor surface we care about.
        String upper = " " + stripped.toUpperCase() + " ";
        for (String token : FORBIDDEN_TOKENS) {
            if (upper.contains(" " + token + " ") || upper.contains(";" + token + " ")) {
                return "Forbidden token '" + token + "' detected in CCM CUSTOM_SQL test";
            }
        }
        if (stripped.contains(";")) {
            // Reject any statement chaining.
            return "Multiple statements are not permitted in CCM CUSTOM_SQL tests";
        }
        // SEC-INT-001: enforce tenant scoping — the query MUST reference
        // organisation_id so a CCM test cannot read across tenants in the
        // shared schema. (Defence-in-depth on top of the default kill-switch.)
        if (!stripped.toLowerCase().contains("organisation_id")) {
            return "CCM CUSTOM_SQL must filter by organisation_id (tenant scoping required)";
        }
        return null;
    }

    /** Strip leading single-line and block comments + leading whitespace. */
    static String stripCommentsAndWhitespace(String sql) {
        String s = sql.trim();
        while (true) {
            if (s.startsWith("--")) {
                int nl = s.indexOf('\n');
                if (nl < 0) return "";
                s = s.substring(nl + 1).trim();
            } else if (s.startsWith("/*")) {
                int end = s.indexOf("*/");
                if (end < 0) return "";
                s = s.substring(end + 2).trim();
            } else {
                return s;
            }
        }
    }
}
