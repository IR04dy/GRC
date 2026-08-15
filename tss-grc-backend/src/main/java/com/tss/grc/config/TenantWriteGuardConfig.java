package com.tss.grc.config;

import com.tss.grc.common.tenant.TenantWriteGuardInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Wires {@link TenantWriteGuardInterceptor} into Hibernate as a session-level
 * interceptor — Sprint-6 platform-wide mass-assignment defense activation.
 *
 * <p>This config wires it via Spring Boot's {@code HibernatePropertiesCustomizer} hook,
 * which is preferred over the raw {@code hibernate.session_factory.interceptor} property
 * because it lets the interceptor keep its {@code @Autowired} dependencies (audit log
 * service).
 *
 * <h3>Flag — the CODE default is off; every shipped profile turns it ON</h3>
 *
 * Controlled by {@code grc.security.tenant-write-guard.enabled}. The
 * {@code @Value} default below is {@code false}, but that default is reached by no
 * deployment this repository ships: {@code application-dev.yml} sets it {@code true}
 * literally and {@code application-prod.yml} sets {@code ${TENANT_WRITE_GUARD_ENABLED:true}}
 * (a placeholder, per F2-149 — a kill-switch that cannot be thrown is not a kill-switch).
 * Both legs of the interceptor run on every request in dev and in prod.
 *
 * <p>⚠ <b>F2-216 — this javadoc described the opposite, and said so in three places.</b> It
 * claimed the interceptor was <em>"intentionally not connected to the SessionFactory"</em>,
 * headed its flag section <em>"default OFF"</em>, and told operators to
 * <em>"activate first in staging, watch the audit log … for a sprint, then flip to true in
 * prod."</em> All three stopped being true when F2-22b added the {@code onSave} leg and
 * enabled the flag. 🔑 <b>A stale claim about whether a security control RUNS is not a
 * documentation nit: it is the input to somebody's next threat model.</b> Read together with
 * {@code TenantContext}'s matching comment, the pair told a reader that the platform-wide
 * cross-tenant backstop was unbuilt and unwired, while it was in fact adjudicating every
 * insert and every update in both environments.
 *
 * <p>{@code TenantGuardNullExemptionContractTest} now asserts the wiring status these
 * comments assert against the profiles that actually set the property, in both directions,
 * so neither can drift again without failing the build.
 *
 * <p>See {@code docs/security/MASS_ASSIGNMENT_DEFENSE.md} for the full
 * design and pre-activation checklist.
 */
@Configuration
public class TenantWriteGuardConfig {

    private static final Logger log = LoggerFactory.getLogger(TenantWriteGuardConfig.class);

    @Value("${grc.security.tenant-write-guard.enabled:false}")
    private boolean enabled;

    /**
     * Lazy resolution of the interceptor via {@link ObjectProvider} so we don't
     * create a circular dependency at startup:
     *
     * <pre>
     *   tenantWriteGuardConfig
     *     → tenantWriteGuardInterceptor (Spring bean with @Autowired AuditLogService)
     *     → auditLogService → auditLogRepository (JPA repo)
     *     → entityManagerFactory → HibernateJpaConfiguration
     *     → tenantWriteGuardConfig  (CYCLE)
     * </pre>
     *
     * The customizer only needs the interceptor when {@code enabled=true}, and
     * by then Spring has already finished wiring the EntityManagerFactory's
     * dependent beans. Using {@link ObjectProvider#getIfAvailable()} defers the
     * lookup until the customizer is actually invoked.
     */
    private final ObjectProvider<TenantWriteGuardInterceptor> interceptorProvider;

    public TenantWriteGuardConfig(ObjectProvider<TenantWriteGuardInterceptor> interceptorProvider) {
        this.interceptorProvider = interceptorProvider;
    }

    @Bean
    public HibernatePropertiesCustomizer tenantWriteGuardCustomizer() {
        return (Map<String, Object> properties) -> {
            if (enabled) {
                TenantWriteGuardInterceptor interceptor = interceptorProvider.getIfAvailable();
                if (interceptor == null) {
                    log.warn("[TenantWriteGuard] enabled=true but interceptor bean not available — skipping wiring.");
                    return;
                }
                properties.put("hibernate.session_factory.interceptor", interceptor);
                log.info("[TenantWriteGuard] interceptor WIRED — cross-tenant writes "
                       + "without TenantContext.allowCrossTenantWrite() will throw SecurityException.");
            } else {
                log.info("[TenantWriteGuard] interceptor NOT wired "
                       + "(grc.security.tenant-write-guard.enabled=false). Per-service "
                       + "setId(null) defense is the only mass-assignment guard active.");
            }
        };
    }
}
