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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

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
 * {@code JwtAuthenticationFilter}. When no tenant is in scope the aspect splits on <i>why</i>,
 * because two very different situations look identical from here:
 * <ul>
 *   <li><b>Off-request</b> — scheduler, {@code @Async}, event listener. <b>Fails closed</b> with
 *       {@link ModuleDisabledException} and {@code organisationId=null}.</li>
 *   <li><b>An anonymous call on a {@code permitAll} endpoint</b> — the whistleblower portal, the
 *       complaints portal, the vendor attestation portal, the PDPL transparency list.
 *       <b>Proceeds</b>: see {@link #isAnonymousHttpRequest()} for the rule and for why it does
 *       not widen the guard.</li>
 * </ul>
 * <p>This javadoc previously said the aspect fails closed on <em>any</em> null tenant, "which
 * matches the platform-wide stance that anonymous calls cannot access licensed features". That
 * stance was never platform-wide — {@code SecurityConfig} declares eleven anonymous endpoints, four
 * of which sit in packages that ADR-018 sells — and it held here only because entitlement had
 * reached one module (F2-04).
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
            if (isAnonymousHttpRequest()) {
                log.debug("[license-guard] anonymous request to {} (module={}) — public endpoint, "
                        + "no tenant to entitle", pjp.getSignature().toShortString(), moduleCode);
                return pjp.proceed();
            }
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

    /**
     * Is this an anonymous call on a deliberately public endpoint?
     *
     * <p><b>The rule: an endpoint reachable without authentication cannot be entitlement-checked,
     * because entitlement is a property of a tenant and an anonymous caller has none.</b> F2-04
     * annotates controllers at class level, and four of them mix {@code permitAll} endpoints in
     * with authenticated ones — the Nazaha/SAMA-mandated whistleblower portal
     * ({@code WhistleblowerCaseController}), the SAMA consumer-complaints portal
     * ({@code ComplaintController}), the vendor BCP attestation portal
     * ({@code VendorBcpAttestationController}) and the PDPL Article 12 transparency list
     * ({@code SubprocessorController}). Without this branch, one class-level annotation would turn
     * every one of those into a 403, which is a compliance incident rather than a bug.
     *
     * <p><b>Why this does not weaken the guard.</b> {@code SecurityConfig} ends with
     * {@code .anyRequest().authenticated()}, so an unauthenticated caller reaches this aspect on
     * <em>only</em> the paths that config explicitly declared {@code permitAll} — every other
     * request is rejected with 401 before the aspect runs. SecurityConfig is the right authority
     * for "is this endpoint public", so the exemption is scoped by it rather than restated here
     * and allowed to drift.
     *
     * <p><b>Why the request-attributes check comes first.</b> A background thread — scheduler,
     * {@code @Async}, event listener — also has no tenant and no authentication, and it must keep
     * failing closed. {@code RequestContextHolder.getRequestAttributes()} is null off-request, so
     * the two cases are distinguishable; without that check, this branch would hand every
     * scheduler a free pass. Same idiom, and the same reason, as the off-request guard in
     * {@code AuditLogService}.
     *
     * <p>⚠ {@code AnonymousAuthenticationToken.isAuthenticated()} returns {@code true}, so the
     * type check is not redundant with the {@code isAuthenticated()} call next to it.
     *
     * <p>⚠ <b>Known asymmetry, and it is not this aspect's to fix.</b> A <em>logged-in</em> user
     * posting to one of those public endpoints does have a tenant, so they are entitlement-checked
     * while an anonymous caller is not. That reads backwards until you see why the anonymous path
     * cannot be checked: {@code WhistleblowerCaseService.PUBLIC_PORTAL_DEFAULT_ORG} and
     * {@code ComplaintService.PUBLIC_PORTAL_DEFAULT_ORG} are both the literal {@code 1L}, so an
     * anonymous submission is not merely unentitled — it is filed against the wrong tenant
     * (F2-83). Once the portal resolves its own organisation the way
     * {@code SubprocessorController.publicList} already does, entitlement can apply uniformly and
     * this asymmetry disappears.
     */
    private static boolean isAnonymousHttpRequest() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return false;   // off-request: scheduler / @Async / listener — stay fail-closed
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null
            || !auth.isAuthenticated()
            || auth instanceof AnonymousAuthenticationToken;
    }
}
