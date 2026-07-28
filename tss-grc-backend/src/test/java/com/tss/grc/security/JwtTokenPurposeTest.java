package com.tss.grc.security;

import com.tss.grc.module.governance.threelines.service.ThreeLinesService;
import com.tss.grc.module.platform.licensing.service.LicensingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * SCR-001 — a refresh token must not be usable as an API bearer credential.
 *
 * <p><b>The defect, reproduced live before this fix.</b> Access and refresh tokens are minted by the
 * same builder and differ only in expiry; neither carried a purpose claim; {@code validateToken}
 * accepts any correctly signed unexpired JWT; and the bearer filter authenticated whatever it
 * approved. So:
 * <pre>
 * access token as bearer                     -> 200   (control)
 * refresh token as bearer                    -> 200   (should be 401)
 * logout (204), then the REVOKED refresh     -> 200   (revocation bypassed)
 * the revoked token at /api/auth/refresh     -> 401   (revocation works — only there)
 * </pre>
 * A seven-day refresh token was a de facto seven-day access token that logout could not end.
 *
 * <p><b>What this test class pins.</b> The interim fix uses the discriminator that already existed —
 * refresh tokens carry a server-issued {@code jti}, access tokens do not — so it closes the attack
 * with no token-format change and no forced re-login. That only holds while <b>every</b> mint path
 * respects it, which is the fragile part and therefore what is asserted here, in both directions:
 * every refresh path marks its token, and no access path does. A future mint that forgets breaks a
 * test rather than silently reopening the hole.
 *
 * <p>The durable fix — an explicit immutable {@code typ} claim with separate validation entry points
 * — is slice 5 of {@code SECURE_CODE_REVIEW_REMEDIATION_PLAN.md} and forces re-login, so it ships
 * with a release. When it lands, these assertions should be rewritten against {@code typ} and the
 * jti heuristic retired.
 */
class JwtTokenPurposeTest {

    private static final String SECRET =
        "tss-grc-test-jwt-secret-0123456789-abcdefghij-KLMNOPQRSTUVWXYZ";

    private JwtTokenProvider provider;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        // JwtTokenProviderTest passes null for the two ObjectProviders, which is fine for
        // validateToken but NPEs in the mint paths — enabled_modules and lod_band resolve their
        // services through them. Empty providers give the "service not bootstrapped" behaviour the
        // provider already handles, so the token shape here matches a real one.
        @SuppressWarnings("unchecked")
        ObjectProvider<LicensingService> licensing = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ThreeLinesService> threeLines = mock(ObjectProvider.class);
        provider = new JwtTokenProvider(SECRET, "", 900_000L, 604_800_000L, licensing, threeLines);

        GrcUserDetails principal = new GrcUserDetails(
            7L, 1L, "admin", "x", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        auth = new UsernamePasswordAuthenticationToken(
            principal, null, principal.getAuthorities());
    }

    // ── Every refresh path marks its token ────────────────────────────────────────────

    @Test
    void aLoginRefreshTokenIsIdentifiableAsARefreshToken() {
        String refresh = provider.generateRefreshTokenWithJti(auth, "jti-from-the-store");

        assertThat(provider.isRefreshToken(refresh))
            .as("the login refresh path must mark the token, or the bearer filter cannot reject it")
            .isTrue();
        assertThat(provider.validateToken(refresh))
            .as("it is still a structurally valid token — that was never the problem")
            .isTrue();
    }

    @Test
    void aRotatedRefreshTokenIsIdentifiableAsARefreshToken() {
        String rotated = provider.generateRefreshTokenFromClaimsWithJti(7L, 1L, "fresh-jti");

        assertThat(provider.isRefreshToken(rotated)).isTrue();
    }

    /**
     * The store-absent rotation branch in {@code AuthController} used to mint a refresh token with a
     * null jti. That is what this asserts is impossible to produce accidentally now: the only
     * remaining refresh mint takes a jti argument, and passing a blank one must not read as an
     * access token.
     */
    @Test
    void aRefreshTokenMintedWithABlankJtiIsNotMistakenForAnAccessToken() {
        String noJti = provider.generateRefreshTokenFromClaimsWithJti(7L, 1L, "");

        assertThat(provider.isRefreshToken(noJti))
            .as("a blank jti cannot be marked — which is why the caller must supply a real one, and "
                + "why AuthController's fallback now generates a UUID instead of passing null")
            .isFalse();
    }

    // ── No access path marks its token ────────────────────────────────────────────────

    @Test
    void aLoginAccessTokenIsNotARefreshToken() {
        String access = provider.generateAccessToken(auth);

        assertThat(provider.isRefreshToken(access))
            .as("if an access token ever carried a jti, the filter would reject every real request")
            .isFalse();
        assertThat(provider.validateToken(access)).isTrue();
    }

    @Test
    void aRefreshFlowAccessTokenIsNotARefreshToken() {
        String access = provider.generateAccessTokenFromClaims(7L, 1L);

        assertThat(provider.isRefreshToken(access)).isFalse();
    }

    /**
     * The two kinds must remain distinguishable while staying otherwise identical — the interim fix
     * deliberately changes nothing about the claim set, so existing sessions keep working. If a
     * future change starts omitting {@code roles} or {@code org} from one kind, that is a separate
     * behaviour change and should not ride along silently.
     */
    @Test
    void theTwoKindsDifferOnlyByTheDiscriminator() {
        String access = provider.generateAccessToken(auth);
        String refresh = provider.generateRefreshTokenWithJti(auth, "some-jti");

        assertThat(provider.getUserIdFromToken(access))
            .isEqualTo(provider.getUserIdFromToken(refresh));
        assertThat(provider.getOrganisationIdFromToken(access))
            .isEqualTo(provider.getOrganisationIdFromToken(refresh));
        assertThat(provider.isRefreshToken(access)).isFalse();
        assertThat(provider.isRefreshToken(refresh)).isTrue();
    }

    /** A malformed or foreign-signed token is not a refresh token — it is simply not a token. */
    @Test
    void garbageIsNotClassifiedAsARefreshToken() {
        assertThat(provider.isRefreshToken("not.a.jwt")).isFalse();
        assertThat(provider.isRefreshToken("")).isFalse();
    }
}
