package com.tss.grc.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private final ModulePermissionEvaluator evaluator =
        new ModulePermissionEvaluator(new EntityOwnershipRegistry(List.of(ML_MODEL)));

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

    // ── the 4-argument overload: entity OWNERSHIP, not a module check with an ignored id ──

    /**
     * F2-159 — ⚠ <b>this section previously asserted the defect, and that is the finding.</b>
     *
     * <p>The replaced test was named {@code the_targetId_overload_delegates_to_the_module_level_check}
     * and asserted {@code hasPermission(auth, 42L, "EVIDENCE", "WRITE")} is <b>true</b> for a holder of
     * {@code EVIDENCE_WRITE} — i.e. it pinned, as intended behaviour, that the record id is
     * <em>discarded</em>. So the decoy was not an undocumented oversight: a green test with a reassuring
     * name made it look like a design decision, and anyone who questioned the overload would have found
     * that test agreeing with it.
     *
     * <p>🔑 <b>A test can lock in a defect, and it is hardest to see when it describes what the code does
     * accurately.</b> That test was not wrong about the behaviour — it was wrong about the behaviour being
     * correct. The distinction is invisible to the suite, which is why the guard expression
     * {@code hasPermission(#id, 'RISK', 'READ')} — "may I read THIS risk?" answered as "may I read
     * risks?" — could have shipped past both review and CI.
     */
    private static final String OWNED = "11111111-1111-1111-1111-111111111111";
    private static final String NOT_OWNED = "22222222-2222-2222-2222-222222222222";

    @Test
    void the_targetId_overload_asks_the_ownership_resolver_and_honours_its_answer() {
        Authentication auth = principal(7L, 1L, "AI_READ");
        assertThat(evaluator.hasPermission(auth, OWNED, "ML_MODEL", "OWNER")).isTrue();
        assertThat(evaluator.hasPermission(auth, NOT_OWNED, "ML_MODEL", "OWNER")).isFalse();
    }

    @Test
    void the_targetId_overload_never_falls_back_to_the_module_level_check() {
        // The regression that matters. Holding EVIDENCE_WRITE must NOT satisfy an entity check, however
        // the targetType and permission happen to spell out — that fallback is what made an ownership
        // guard silently module-wide.
        Authentication auth = principal(7L, 1L, "EVIDENCE_WRITE", "RISK_READ");
        assertThat(evaluator.hasPermission(auth, 42L, "EVIDENCE", "WRITE")).isFalse();
        assertThat(evaluator.hasPermission(auth, 42L, "RISK", "READ")).isFalse();
    }

    @Test
    void an_unregistered_target_type_is_refused_rather_than_answered() {
        Authentication auth = principal(7L, 1L, "AI_READ");
        assertThat(evaluator.hasPermission(auth, OWNED, "NO_SUCH_ENTITY", "OWNER")).isFalse();
    }

    @Test
    void a_relation_other_than_OWNER_is_refused() {
        // Only ownership is modelled. Answering something else in its place is the original defect.
        Authentication auth = principal(7L, 1L, "AI_READ");
        assertThat(evaluator.hasPermission(auth, OWNED, "ML_MODEL", "APPROVER")).isFalse();
        assertThat(evaluator.hasPermission(auth, OWNED, "ML_MODEL", "READ")).isFalse();
    }

    @Test
    void ownership_is_per_record_so_a_different_user_is_refused_the_same_record() {
        // The whole reason D-14 refused to seat a MODEL_OWNER role: a role admits every holder to every
        // record, and this must not.
        assertThat(evaluator.hasPermission(principal(7L, 1L), OWNED, "ML_MODEL", "OWNER")).isTrue();
        assertThat(evaluator.hasPermission(principal(8L, 1L), OWNED, "ML_MODEL", "OWNER")).isFalse();
    }

    @Test
    void ownership_is_tenant_scoped() {
        assertThat(evaluator.hasPermission(principal(7L, 2L), OWNED, "ML_MODEL", "OWNER")).isFalse();
    }

    @Test
    void a_resolver_that_throws_denies_instead_of_breaking_the_whole_expression() {
        // The ownership check is one leg of an `or`. A leg that propagates an exception 500s an endpoint
        // the caller may legitimately reach by role, turning a refusal into an outage.
        Authentication auth = principal(7L, 1L, "AI_READ");
        assertThat(evaluator.hasPermission(auth, "boom", "ML_MODEL", "OWNER")).isFalse();
    }

    @Test
    void the_targetId_overload_still_honours_the_platform_admin_bypass() {
        // Unchanged and deliberate: the platform administrator does not have to own a record to
        // administer it, exactly as on the module-level overload.
        assertThat(evaluator.hasPermission(adminPrincipal(), NOT_OWNED, "ML_MODEL", "OWNER")).isTrue();
    }

    @Test
    void a_non_GrcUserDetails_principal_is_refused_rather_than_assumed() {
        // Off-request and machine callers have no user id, so there is nobody to be the owner.
        Authentication auth = authWith("AI_READ");
        assertThat(evaluator.hasPermission(auth, OWNED, "ML_MODEL", "OWNER")).isFalse();
    }

    @Test
    void two_resolvers_claiming_one_target_type_fail_at_startup_rather_than_racing() {
        // Ownership is an authorisation decision, so it may not depend on bean ordering. A map that
        // silently kept the last registered resolver would make the effective rule a build-order
        // accident — A17-D2's reason for failing closed on an unconfigured gate map.
        EntityOwnershipResolver duplicate = new EntityOwnershipResolver() {
            @Override public String targetType() { return "ml_model"; }   // same type, different case
            @Override public boolean isOwnedBy(java.io.Serializable i, Long u, Long o) { return true; }
        };
        assertThatThrownBy(() -> new EntityOwnershipRegistry(List.of(ML_MODEL, duplicate)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ML_MODEL")
            .hasMessageContaining("may not depend on bean ordering");
    }

    // ── fixtures for the ownership overload ─────────────────────────────────────

    /** Owner of {@link #OWNED} is user 7 in organisation 1; anything else is not owned. */
    private static final EntityOwnershipResolver ML_MODEL = new EntityOwnershipResolver() {
        @Override public String targetType() { return "ML_MODEL"; }
        @Override public boolean isOwnedBy(java.io.Serializable id, Long userId, Long orgId) {
            if ("boom".equals(id)) throw new IllegalStateException("resolver blew up");
            return OWNED.equals(String.valueOf(id)) && Long.valueOf(7L).equals(userId)
                && Long.valueOf(1L).equals(orgId);
        }
    };

    private Authentication principal(Long userId, Long orgId, String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toList();
        GrcUserDetails me = new GrcUserDetails(
                userId, orgId, "u" + userId, "n/a", true, true, granted);
        return new UsernamePasswordAuthenticationToken(me, "n/a", granted);
    }

    private Authentication adminPrincipal() {
        return principal(9L, 1L, "ROLE_PLATFORM_ADMIN");
    }
}
