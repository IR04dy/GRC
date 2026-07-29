package com.tss.grc.security;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.governance.threelines.service.ThreeLinesService;
import com.tss.grc.module.iam.service.CustomUserDetailsService;
import com.tss.grc.module.platform.licensing.service.LicensingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SCR-002 — the bearer path must consult the account's live state, and take the tenant from the
 * loaded user rather than from the token.
 *
 * <p><b>What the report got right and wrong, because the difference decided the fix.</b> It said
 * bearer authentication "ignores disabled/locked state and does not bind the JWT tenant to the live
 * user". The second half is exactly right. The first is right in effect but wrong in mechanism:
 * authorities were <em>always</em> re-loaded from the database on every request, so a revoked role
 * already took effect immediately, and {@code CustomUserDetailsService} already computed the lock
 * state correctly (honouring {@code lockedUntil}). <b>The defect was that nothing consumed it</b> —
 * the filter built a {@code UsernamePasswordAuthenticationToken} by hand, skipping the
 * {@code enabled}/{@code accountNonLocked} checks a {@code DaoAuthenticationProvider} applies at
 * login. A fix premised on "the token's roles are stale" would have solved a problem that did not
 * exist.
 *
 * <p><b>The tenant half had a trap.</b> The obvious reading — "token org must equal the user's org,
 * else 401" — would have broken {@code POST /organisations/switch/{id}}, which re-issues a token with
 * a different {@code org} and deliberately does not change the user record. That endpoint is
 * {@code hasRole('PLATFORM_ADMIN')}, so the mismatch is legitimate for exactly that role and for no
 * one else. Both branches are asserted below; the platform-admin one is the reason this slice is not
 * a one-line change.
 */
class JwtFilterLivenessTest {

    private static final String SECRET =
        "tss-grc-test-jwt-secret-0123456789-abcdefghij-KLMNOPQRSTUVWXYZ";
    private static final Long USER = 7L;
    private static final Long HOME_ORG = 1L;
    private static final Long OTHER_ORG = 2L;

    private JwtTokenProvider provider;
    private CustomUserDetailsService users;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<LicensingService> licensing = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ThreeLinesService> threeLines = mock(ObjectProvider.class);
        provider = new JwtTokenProvider(SECRET, "", 900_000L, 604_800_000L, licensing, threeLines);
        users = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(provider, users);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    /** @param enabled isActive on the row · @param unlocked !currentlyLocked */
    private GrcUserDetails principal(boolean enabled, boolean unlocked, Long org, String... roles) {
        return new GrcUserDetails(USER, org, "u", "x", enabled, unlocked,
            List.of(roles).stream().map(SimpleGrantedAuthority::new).toList());
    }

    private String accessTokenFor(Long org, String... roles) {
        GrcUserDetails p = principal(true, true, org, roles);
        Authentication a = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        return provider.generateAccessToken(a);
    }

    private MockHttpServletResponse callWith(String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/risk/risks");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    // ── the liveness half ────────────────────────────────────────────────────

    @Test
    void aDeactivatedAccountIsRejectedEvenWithAValidUnexpiredToken() throws Exception {
        when(users.loadUserById(anyLong())).thenReturn(principal(false, true, HOME_ORG, "ROLE_VIEWER"));

        MockHttpServletResponse res = callWith(accessTokenFor(HOME_ORG, "ROLE_VIEWER"));

        assertThat(res.getStatus())
            .as("a user deactivated mid-session kept working until their token expired — up to an hour")
            .isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aLockedOutAccountIsRejected() throws Exception {
        when(users.loadUserById(anyLong())).thenReturn(principal(true, false, HOME_ORG, "ROLE_VIEWER"));

        assertThat(callWith(accessTokenFor(HOME_ORG, "ROLE_VIEWER")).getStatus())
            .as("lockout is the control that answers a brute-force; it has to bind the bearer path too")
            .isEqualTo(401);
    }

    @Test
    void aHealthyAccountStillAuthenticatesAndBindsItsOwnTenant() throws Exception {
        when(users.loadUserById(anyLong())).thenReturn(principal(true, true, HOME_ORG, "ROLE_VIEWER"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/risk/risks");
        req.addHeader("Authorization", "Bearer " + accessTokenFor(HOME_ORG, "ROLE_VIEWER"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        // The tenant is cleared in a finally once the chain returns, so it has to be observed
        // from inside the chain — which is also the only place it actually matters.
        Long[] seen = new Long[1];
        filter.doFilter(req, res, (rq, rs) -> seen[0] = TenantContext.getCurrentTenant());

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(seen[0]).isEqualTo(HOME_ORG);
        assertThat(TenantContext.getCurrentTenant())
            .as("and it must not survive the request — pooled threads")
            .isNull();
    }

    // ── the tenant half ──────────────────────────────────────────────────────

    @Test
    void anOrdinaryUserCannotPresentATokenForAnotherTenant() throws Exception {
        // The user lives in org 1; the token claims org 2. The claim is signed, so this is either a
        // leaked admin-issued token or a tampered one — never legitimate for a non-admin.
        when(users.loadUserById(anyLong())).thenReturn(principal(true, true, HOME_ORG, "ROLE_VIEWER"));

        assertThat(callWith(accessTokenFor(OTHER_ORG, "ROLE_VIEWER")).getStatus())
            .as("every repository query filters on this value; it cannot come from the client")
            .isEqualTo(401);
    }

    @Test
    void aPlatformAdminMayOperateAnotherTenant_theOrgSwitcherMustKeepWorking() throws Exception {
        // POST /organisations/switch/{id} is hasRole('PLATFORM_ADMIN') and re-issues a token with a
        // different org WITHOUT changing the user row. A blanket token-org-must-equal-user-org rule
        // would 401 the first request after every switch.
        when(users.loadUserById(anyLong()))
            .thenReturn(principal(true, true, HOME_ORG, "ROLE_PLATFORM_ADMIN"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/risk/risks");
        req.addHeader("Authorization", "Bearer " + accessTokenFor(OTHER_ORG, "ROLE_PLATFORM_ADMIN"));
        Long[] seen = new Long[1];
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, (rq, rs) -> seen[0] = TenantContext.getCurrentTenant());

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(seen[0])
            .as("the switched-into tenant, not the admin's home org — otherwise switching does nothing")
            .isEqualTo(OTHER_ORG);
    }

    @Test
    void aDisabledPlatformAdminGetsNoTenantOverride() throws Exception {
        // Liveness is checked before the tenant, so a deactivated admin cannot ride the exception.
        when(users.loadUserById(anyLong()))
            .thenReturn(principal(false, true, HOME_ORG, "ROLE_PLATFORM_ADMIN"));

        assertThat(callWith(accessTokenFor(OTHER_ORG, "ROLE_PLATFORM_ADMIN")).getStatus())
            .isEqualTo(401);
    }
}
