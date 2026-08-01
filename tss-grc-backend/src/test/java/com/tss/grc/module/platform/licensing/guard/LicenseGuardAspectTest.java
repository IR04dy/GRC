package com.tss.grc.module.platform.licensing.guard;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.licensing.service.LicensingService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the F9 LicenseGuard behaviour:
 *
 * <ul>
 *   <li>method annotation triggers the licence check</li>
 *   <li>class annotation triggers the licence check when method has none</li>
 *   <li>method annotation wins over class annotation</li>
 *   <li>enabled module → proceeds</li>
 *   <li>disabled module → throws ModuleDisabledException</li>
 * </ul>
 *
 * <p>and, added by F2-04, the three ways a <i>missing</i> tenant can arise — which used to be one
 * case and one test:
 * <ul>
 *   <li>off-request (scheduler / {@code @Async}) → <b>throws</b>, fail-closed</li>
 *   <li>anonymous on a {@code permitAll} endpoint → <b>proceeds</b>, because entitlement is a
 *       property of a tenant and an anonymous caller has none</li>
 *   <li>authenticated on a request but tenantless (JWT with no {@code org} claim) → <b>throws</b>,
 *       because the exemption is anonymity, not merely being on a request thread</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LicenseGuardAspectTest {

    @Mock private LicensingService licensing;
    @Mock private ProceedingJoinPoint pjp;
    @Mock private MethodSignature signature;

    @InjectMocks private LicenseGuardAspect aspect;

    @RequireModule("RISK") static class ClassGuarded {
        public void plainMethod() {}
        @RequireModule("BCM") public void methodGuarded() {}
    }

    static class PlainHolder {
        @RequireModule("CYBER") public void only() {}
    }

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(42L); }

    @AfterEach void tearDown() {
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** Put the thread on a servlet request, the way a real HTTP call would be. */
    private static void onHttpRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test void enabledModule_proceeds() throws Throwable {
        stubSignature(PlainHolder.class.getMethod("only"));
        when(licensing.isModuleEnabled(42L, "CYBER")).thenReturn(true);
        when(pjp.proceed()).thenReturn("ok");

        Object out = aspect.enforce(pjp);

        assertThat(out).isEqualTo("ok");
        verify(pjp).proceed();
    }

    @Test void disabledModule_throws() throws Throwable {
        stubSignature(PlainHolder.class.getMethod("only"));
        when(licensing.isModuleEnabled(42L, "CYBER")).thenReturn(false);

        assertThatThrownBy(() -> aspect.enforce(pjp))
            .isInstanceOf(ModuleDisabledException.class)
            .hasMessageContaining("CYBER");
    }

    /**
     * No tenant and <b>no servlet request</b> — a scheduler, {@code @Async} or event listener.
     * Must stay fail-closed. This test predates F2-04 and used to be the whole story for a null
     * tenant; it is now one of three, and its name says which.
     */
    @Test void missingTenant_offRequest_throws() throws Throwable {
        TenantContext.clear();
        assertThat(RequestContextHolder.getRequestAttributes())
            .as("this test's whole point is that the thread is NOT on a request")
            .isNull();
        stubSignature(PlainHolder.class.getMethod("only"));

        assertThatThrownBy(() -> aspect.enforce(pjp))
            .isInstanceOf(ModuleDisabledException.class)
            .hasMessageContaining("CYBER");
    }

    /**
     * F2-04. An anonymous caller on a {@code permitAll} endpoint proceeds. Four controllers mix
     * public endpoints in with authenticated ones — the whistleblower portal (Nazaha / SAMA CG
     * Art.28 / CMA CG Art.87), the consumer-complaints portal (SAMA Consumer Protection), the
     * vendor BCP attestation portal and the PDPL Art.12 transparency list — so without this a
     * single class-level {@code @RequireModule} would 403 a regulator-mandated channel.
     */
    @Test void anonymousCallerOnAPublicEndpoint_proceeds() throws Throwable {
        TenantContext.clear();
        onHttpRequest();
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        stubSignature(PlainHolder.class.getMethod("only"));
        when(pjp.proceed()).thenReturn("submitted");

        assertThat(aspect.enforce(pjp)).isEqualTo("submitted");
        verify(licensing, never()).isModuleEnabled(org.mockito.ArgumentMatchers.any(),
                                                   org.mockito.ArgumentMatchers.anyString());
    }

    /** Same as above but with no {@code Authentication} at all, which is the other anonymous shape. */
    @Test void nullAuthenticationOnARequestThread_proceeds() throws Throwable {
        TenantContext.clear();
        onHttpRequest();
        SecurityContextHolder.clearContext();
        stubSignature(PlainHolder.class.getMethod("only"));
        when(pjp.proceed()).thenReturn("submitted");

        assertThat(aspect.enforce(pjp)).isEqualTo("submitted");
    }

    /**
     * The case that keeps the exemption honest: an <b>authenticated</b> caller on a request thread
     * whose tenant did not resolve — a JWT with no {@code org} claim, the SCR-002 shape. Anonymity
     * is the exemption, not "being on a request", so this must still fail closed.
     */
    @Test void authenticatedButTenantlessRequest_stillThrows() throws Throwable {
        TenantContext.clear();
        onHttpRequest();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "n/a", List.of()));
        stubSignature(PlainHolder.class.getMethod("only"));

        assertThatThrownBy(() -> aspect.enforce(pjp))
            .isInstanceOf(ModuleDisabledException.class)
            .hasMessageContaining("CYBER");
    }

    /**
     * An anonymous caller is exempt from <i>entitlement</i>, not from the check running at all:
     * once a tenant IS in scope the licence decides, even on a request thread with an anonymous
     * token. Guards against a future refactor that reorders the two conditions.
     */
    @Test void anonymousTokenDoesNotBypassWhenATenantIsInScope() throws Throwable {
        onHttpRequest();   // tenant 42 is still set by setUp()
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        stubSignature(PlainHolder.class.getMethod("only"));
        when(licensing.isModuleEnabled(42L, "CYBER")).thenReturn(false);

        assertThatThrownBy(() -> aspect.enforce(pjp))
            .isInstanceOf(ModuleDisabledException.class);
        verify(licensing).isModuleEnabled(42L, "CYBER");
    }

    @Test void classLevelAnnotation_isHonoured() throws Throwable {
        stubSignature(ClassGuarded.class.getMethod("plainMethod"));
        when(licensing.isModuleEnabled(42L, "RISK")).thenReturn(true);
        when(pjp.proceed()).thenReturn(null);

        aspect.enforce(pjp);

        verify(licensing).isModuleEnabled(42L, "RISK");
    }

    @Test void methodLevelWinsOverClass() throws Throwable {
        stubSignature(ClassGuarded.class.getMethod("methodGuarded"));
        when(licensing.isModuleEnabled(42L, "BCM")).thenReturn(true);
        when(pjp.proceed()).thenReturn(null);

        aspect.enforce(pjp);

        verify(licensing).isModuleEnabled(42L, "BCM");
    }

    private void stubSignature(Method m) {
        when(pjp.getSignature()).thenReturn((Signature) signature);
        when(signature.getMethod()).thenReturn(m);
    }
}
