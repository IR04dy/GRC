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

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 *   <li>missing tenant → throws ModuleDisabledException</li>
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
    @AfterEach  void tearDown() { TenantContext.clear(); }

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

    @Test void missingTenant_throws() throws Throwable {
        TenantContext.clear();
        stubSignature(PlainHolder.class.getMethod("only"));

        assertThatThrownBy(() -> aspect.enforce(pjp))
            .isInstanceOf(ModuleDisabledException.class)
            .hasMessageContaining("CYBER");
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
