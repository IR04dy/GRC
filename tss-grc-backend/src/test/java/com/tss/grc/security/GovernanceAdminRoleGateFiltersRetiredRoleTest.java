package com.tss.grc.security;

import com.tss.grc.module.iam.governance.repository.SodViolationRepository;
import com.tss.grc.module.iam.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-384 — "does this user hold role X?" is one question, and the platform answered it two ways
 * that disagreed on a soft-deleted (retired) role.
 *
 * <p>The authz SOURCE OF TRUTH is {@code RoleRepository#findRoleNamesByUserId}: the native query
 * {@code CustomUserDetailsService} uses to build a user's JWT authorities. It joins
 * {@code iam.user_roles} to {@code iam.roles} and filters {@code r.is_deleted = 0}, so a retired
 * role confers no authority. {@code SodViolationRepository#countUserRole} — the identical
 * {@code user_roles JOIN roles} shape, feeding {@code SodService#isGovernanceAdmin}, which gates
 * SoD overrides and recertification closures — omitted that filter, so it treated a retired admin
 * role's surviving grant as still conferring governance-admin authority (F2-282 drift; F2-205: the
 * authoritative reader filters and the SoD gate was the lone outlier — {@code privilegedRoleIds} and
 * {@code countActiveHoldersByRole} filter too).
 *
 * <p>Latent today (GRC_ADMINISTRATOR / PLATFORM_ADMIN are system roles that
 * {@code AccessGovernanceService#deleteRole} refuses to retire), and the fix is provably
 * non-breaking — the filter can only exclude a role the JWT authz path already excludes (F2-182:
 * reachability is a fact about today, not a property of the control).
 *
 * <p>Hermetic: the {@code @Query} annotation is {@code @Retention(RUNTIME)}, so its SQL is read by
 * reflection — a mocked repository can't run a native query and there is no DB (F2-320 / F2-325);
 * assert the clause structurally, the {@code PrivilegedRolePredicateSharingTest} idiom one method
 * over. The source-of-truth leg is the anti-vacuity anchor: if it ever stops filtering, F2-384's
 * whole premise changed and this gate must be re-derived rather than trusted.
 */
class GovernanceAdminRoleGateFiltersRetiredRoleTest {

    /** `r.is_deleted = 0` on the roles alias `r` — the `\br` never matches `ur.` (u-r is no boundary). */
    private static final Pattern ROLES_ALIVE =
        Pattern.compile("\\br\\.is_deleted\\s*=\\s*0\\b", Pattern.CASE_INSENSITIVE);

    private static String nativeSql(Class<?> repo, String method, Class<?>... params) throws Exception {
        Query q = repo.getMethod(method, params).getAnnotation(Query.class);
        assertThat(q).as("%s#%s must carry a @Query", repo.getSimpleName(), method).isNotNull();
        return q.value().replaceAll("\\s+", " ").trim();
    }

    @Test
    void governanceAdminGateFiltersSoftDeletedRoles() throws Exception {
        String sql = nativeSql(SodViolationRepository.class, "countUserRole", Long.class, String.class);
        assertThat(sql)
            .as("countUserRole joins iam.roles, so the retired-role filter has a target")
            .contains("JOIN iam.roles r");
        assertThat(sql)
            .as("""
                SodViolationRepository#countUserRole must filter `r.is_deleted = 0` — it feeds
                SodService#isGovernanceAdmin, an authz gate for SoD overrides and recertification
                closures, and the authz source of truth (RoleRepository#findRoleNamesByUserId) filters
                retired roles, so a soft-deleted admin role must confer no governance-admin authority
                here either (F2-384). Query was: %s""".formatted(sql))
            .containsPattern(ROLES_ALIVE);
    }

    @Test
    void theAuthzSourceOfTruthAlsoFiltersThem() throws Exception {
        String sql = nativeSql(RoleRepository.class, "findRoleNamesByUserId", Long.class);
        assertThat(sql)
            .as("the JWT-authorities role lookup must still filter retired roles, or F2-384's "
                + "premise (the gate must AGREE with this source of truth) no longer holds")
            .contains("JOIN iam.roles r")
            .containsPattern(ROLES_ALIVE);
    }
}
