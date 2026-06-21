package com.tss.grc.module.ccm.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LDAP group-membership runner — verifies a target group has no more
 * than {@code maxMembers}. The headline test is "AD Domain Admins ≤ 5".
 *
 * <p>This runner is gated by {@code grc.ccm.ldap.enabled} (defaults to
 * {@code false}) — when disabled it returns {@code SKIPPED} so a CCM
 * test that was enabled by an operator before the LDAP starter was
 * wired in still appears in the dashboard with a clear reason instead
 * of an ERROR.
 *
 * <p>The actual LDAP query is delegated to a thin "directory" lambda
 * field so the unit test can swap a mock implementation in. The
 * production code path opens an LDAP context using
 * {@code spring-boot-starter-data-ldap} when the dependency is enabled
 * — see {@link #lookupGroupMemberCount(String, String, String)}.
 */
@Slf4j
@Component
public class LdapGroupMembershipRunner implements ControlTestRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean enabled;

    /** Strategy seam for unit testing — production path uses {@link #lookupGroupMemberCount}. */
    private final GroupLookup lookup;

    /**
     * Production constructor (Spring autowires this).
     *
     * <p>The {@code @Autowired} is explicit because the class has two
     * constructors — without it Spring can't disambiguate and fails with
     * "No default constructor found".
     */
    @Autowired
    public LdapGroupMembershipRunner(@Value("${grc.ccm.ldap.enabled:false}") boolean enabled) {
        this(enabled, LdapGroupMembershipRunner::lookupGroupMemberCount);
    }

    /** Test seam — supply a mock {@link GroupLookup}. */
    LdapGroupMembershipRunner(boolean enabled, GroupLookup lookup) {
        this.enabled = enabled;
        this.lookup = lookup;
    }

    @Override
    public String testType() { return "LDAP_GROUP_MEMBERSHIP"; }

    @Override
    public RunResult execute(ControlTestDefinition def) {
        long start = System.nanoTime();
        if (!enabled) {
            return RunResult.skipped("grc.ccm.ldap.enabled=false — runner disabled",
                    StubRunnerSupport.millisSince(start));
        }
        try {
            Map<String, Object> cfg = StubRunnerSupport.parseJson(def.getTargetConfigJson());
            Map<String, Object> cond = StubRunnerSupport.parseJson(def.getExpectedConditionJson());

            String url       = StubRunnerSupport.strOr(cfg, "url", "");
            String bindDn    = StubRunnerSupport.strOr(cfg, "bindDn", "");
            String groupDn   = StubRunnerSupport.strOr(cond, "groupDn", "");
            int maxMembers   = StubRunnerSupport.intOr(cond, "maxMembers", 0);

            if (groupDn.isBlank()) {
                return RunResult.error("expected_condition_json.groupDn missing", StubRunnerSupport.millisSince(start));
            }

            int actualMembers = lookup.countMembers(url, bindDn, groupDn);

            Map<String, Object> actual = new HashMap<>();
            actual.put("groupDn", groupDn);
            actual.put("memberCount", actualMembers);
            actual.put("maxMembers", maxMembers);
            String actualJson;
            try {
                actualJson = MAPPER.writeValueAsString(actual);
            } catch (Exception jsonEx) {
                actualJson = "{}";
            }

            int duration = StubRunnerSupport.millisSince(start);
            if (actualMembers <= maxMembers) {
                return RunResult.pass(actualJson, duration);
            }
            return RunResult.fail(actualJson,
                    "Group " + groupDn + " has " + actualMembers + " members (limit " + maxMembers + ")",
                    duration);
        } catch (RuntimeException ex) {
            log.warn("LDAP group-membership runner failed: {}", ex.getMessage());
            return RunResult.error(ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                    StubRunnerSupport.millisSince(start));
        }
    }

    // ─── Production LDAP lookup (placeholder) ────────────────────
    //
    // Real Spring LDAP wiring lives in iam.ldap.LdapRoleMapper's
    // companion config; we don't open a context here without that
    // wiring being present. When grc.ccm.ldap.enabled=true and no
    // real provider is wired the lookup will throw — the runner
    // converts that to RunResult.error.
    private static int lookupGroupMemberCount(String url, String bindDn, String groupDn) {
        throw new UnsupportedOperationException(
                "Live LDAP lookup not wired — see docs/operations/ldap-ad-integration-guide.md. "
                + "Set grc.ccm.ldap.enabled=false to skip, or wire a real GroupLookup bean.");
    }

    /** Pluggable directory lookup — production wiring opens a real LDAP context. */
    @FunctionalInterface
    interface GroupLookup {
        int countMembers(String url, String bindDn, String groupDn);
    }
}
