package com.tss.grc.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W1-2 / independent-assessment finding F-2: {@link ModulePermissionEvaluator} is the
 * single mechanism behind every {@code @PreAuthorize("hasPermission('MODULE','ACTION')")}
 * guard in the platform — 859 guarded write endpoints depend on it — and it had **no
 * test at all**. A silent change here (or a mis-cased authority) would open or close
 * authorization platform-wide with a green suite.
 *
 * <p>These tests pin the contract the guards rely on:
 * <ul>
 *   <li>authority naming is exactly {@code MODULE_ACTION}, matched case-insensitively
 *       on the <em>inputs</em> but exactly on the granted authority string;</li>
 *   <li>{@code ROLE_PLATFORM_ADMIN} bypasses every check;</li>
 *   <li><b>fail-closed</b> on anything unknown — the property that makes migration
 *       V313 a hard prerequisite for the SIGNATURE/AI guards: a guard naming a module
 *       nobody has been granted denies everyone rather than defaulting open.</li>
 * </ul>
 */
class ModulePermissionEvaluatorTest {

    private final ModulePermissionEvaluator evaluator = new ModulePermissionEvaluator();

    private Authentication authWith(String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken("tester", "n/a", granted);
    }

    // ── the happy path the 859 guards depend on ──────────────────────────────

    @Test
    void grants_when_the_matching_module_action_authority_is_held() {
        Authentication auth = authWith("RISK_READ", "RISK_WRITE");
        assertThat(evaluator.hasPermission(auth, "RISK", "READ")).isTrue();
        assertThat(evaluator.hasPermission(auth, "RISK", "WRITE")).isTrue();
    }

    @Test
    void denies_when_the_authority_is_absent() {
        Authentication auth = authWith("RISK_READ");
        assertThat(evaluator.hasPermission(auth, "RISK", "WRITE")).isFalse();
        assertThat(evaluator.hasPermission(auth, "RISK", "DELETE")).isFalse();
    }

    @Test
    void denies_the_same_action_on_a_different_module() {
        // Holding CYBER_READ must not satisfy a RISK READ check.
        Authentication auth = authWith("CYBER_READ");
        assertThat(evaluator.hasPermission(auth, "RISK", "READ")).isFalse();
    }

    // ── platform-admin bypass (documented in the evaluator's javadoc) ────────

    @Test
    void platform_admin_role_bypasses_every_module_and_action() {
        Authentication auth = authWith("ROLE_PLATFORM_ADMIN");
        assertThat(evaluator.hasPermission(auth, "RISK", "DELETE")).isTrue();
        assertThat(evaluator.hasPermission(auth, "SIGNATURE", "APPROVE")).isTrue();
        assertThat(evaluator.hasPermission(auth, "ANYTHING_AT_ALL", "WHATEVER")).isTrue();
    }

    @Test
    void a_plain_platform_admin_authority_without_the_role_prefix_does_not_bypass() {
        // PLATFORM_ADMIN_READ is an ordinary module grant, not the bypass role.
        Authentication auth = authWith("PLATFORM_ADMIN_READ");
        assertThat(evaluator.hasPermission(auth, "RISK", "DELETE")).isFalse();
    }

    // ── case handling: inputs are upper-cased, authorities are matched exactly ──

    @Test
    void module_and_action_arguments_are_case_insensitive() {
        Authentication auth = authWith("COMPLIANCE_WRITE");
        assertThat(evaluator.hasPermission(auth, "compliance", "write")).isTrue();
        assertThat(evaluator.hasPermission(auth, "Compliance", "Write")).isTrue();
    }

    @Test
    void a_lowercase_granted_authority_does_not_match() {
        // Authorities are compared exactly, so grants must be stored upper-case.
        // This pins the convention rather than silently tolerating both.
        Authentication auth = authWith("compliance_write");
        assertThat(evaluator.hasPermission(auth, "COMPLIANCE", "WRITE")).isFalse();
    }

    // ── fail-closed properties ──────────────────────────────────────────────

    @Test
    void denies_an_uncatalogued_module_even_for_a_richly_granted_user() {
        // Why this matters: the SIGNATURE and AI guards planned for stage S1/S2 will
        // deny EVERYONE until migration V313 catalogues those modules and grants the
        // resulting MODULE_ACTION authorities. Fail-closed, never fail-open.
        Authentication auth = authWith("RISK_READ", "RISK_WRITE", "COMPLIANCE_WRITE", "BCM_APPROVE");
        assertThat(evaluator.hasPermission(auth, "SIGNATURE", "APPROVE")).isFalse();
        assertThat(evaluator.hasPermission(auth, "AI", "WRITE")).isFalse();
    }

    @Test
    void denies_a_null_authentication() {
        assertThat(evaluator.hasPermission(null, "RISK", "READ")).isFalse();
    }

    @Test
    void denies_a_null_module() {
        Authentication auth = authWith("RISK_READ");
        assertThat(evaluator.hasPermission(auth, null, "READ")).isFalse();
    }

    @Test
    void denies_a_user_with_no_authorities_at_all() {
        Authentication auth = authWith();
        assertThat(evaluator.hasPermission(auth, "RISK", "READ")).isFalse();
    }

    @Test
    void denies_a_prefix_or_suffix_near_miss() {
        // Exact-match only: neither a truncated nor an extended authority may pass.
        Authentication auth = authWith("RISK_REA", "RISK_READ_EXTRA", "XRISK_READ");
        assertThat(evaluator.hasPermission(auth, "RISK", "READ")).isFalse();
    }

    // ── the 4-argument overload used by entity-scoped expressions ────────────

    @Test
    void the_targetId_overload_delegates_to_the_module_level_check() {
        Authentication auth = authWith("EVIDENCE_WRITE");
        assertThat(evaluator.hasPermission(auth, 42L, "EVIDENCE", "WRITE")).isTrue();
        assertThat(evaluator.hasPermission(auth, 42L, "EVIDENCE", "DELETE")).isFalse();
    }

    @Test
    void the_targetId_overload_also_honours_the_platform_admin_bypass() {
        Authentication auth = authWith("ROLE_PLATFORM_ADMIN");
        assertThat(evaluator.hasPermission(auth, 7L, "PRIVACY", "DELETE")).isTrue();
    }
}
