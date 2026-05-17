package com.tss.grc.module.platform.licensing.guard;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.licensing.service.LicensingService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * F9 LicenseGuard — runtime enforcement of {@link RequireModule}.
 *
 * <p>The aspect runs <i>around</i> every method (or class-level) annotated with
 * {@code @RequireModule}, resolves the annotation, asks {@link LicensingService}
 * whether the current tenant has that module enabled, and either proceeds or throws
 * {@link ModuleDisabledException}.
 *
 * <h3>Pointcut strategy</h3>
 * <p>Two pointcuts are combined with {@code ||}:
 * <ul>
 *   <li>{@code @annotation(RequireModule)} — covers method-level annotations.</li>
 *   <li>{@code @within(RequireModule)} — covers class-level annotations.</li>
 * </ul>
 * Method-level wins by lookup order in {@link #resolveAnnotation(ProceedingJoinPoint)}:
 * if a method has its own {@code @RequireModule}, the class-level one is ignored. This
 * matches Spring's own {@code @Transactional} resolution semantics so the behaviour is
 * unsurprising for anyone who has used Spring before.
 *
 * <h3>Tenant resolution</h3>
 * <p>Uses {@code TenantContext.getCurrentTenant()} which is populated by
 * {@code JwtAuthenticationFilter}. If no tenant is in scope (e.g. background thread
 * without filter context) the aspect fails closed — it throws
 * {@link ModuleDisabledException} with {@code organisationId=null}. That matches the
 * platform-wide stance that anonymous calls cannot access licensed features.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class LicenseGuardAspect {

    private static final Logger log = LoggerFactory.getLogger(LicenseGuardAspect.class);

    private final LicensingService licensing;

    @Around("@annotation(com.tss.grc.module.platform.licensing.guard.RequireModule) "
          + "|| @within(com.tss.grc.module.platform.licensing.guard.RequireModule)")
    public Object enforce(ProceedingJoinPoint pjp) throws Throwable {
        RequireModule annotation = resolveAnnotation(pjp);
        if (annotation == null) {
            // Defensive: the pointcut matched but no annotation was found. Don't block.
            return pjp.proceed();
        }
        String moduleCode = annotation.value();
        Long organisationId = TenantContext.getCurrentTenant();

        if (organisationId == null) {
            log.warn("[license-guard] no tenant in scope when invoking {} (module={})",
                    pjp.getSignature().toShortString(), moduleCode);
            throw new ModuleDisabledException(moduleCode, null);
        }
        if (!licensing.isModuleEnabled(organisationId, moduleCode)) {
            log.info("[license-guard] org={} blocked from {} — module {} not enabled",
                    organisationId, pjp.getSignature().toShortString(), moduleCode);
            throw new ModuleDisabledException(moduleCode, organisationId);
        }
        return pjp.proceed();
    }

    /**
     * Method-level annotation takes precedence over class-level — mirrors Spring's own
     * {@code @Transactional} resolution. Uses {@link AnnotationUtils#findAnnotation} so
     * inherited annotations on superclasses or interfaces are also picked up.
     */
    private RequireModule resolveAnnotation(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        RequireModule onMethod = AnnotationUtils.findAnnotation(method, RequireModule.class);
        if (onMethod != null) return onMethod;
        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), RequireModule.class);
    }
}
