package com.tss.grc.config;

import com.tss.grc.common.tenant.TenantWriteGuardInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Wires {@link TenantWriteGuardInterceptor} into Hibernate as a session-level
 * interceptor — Sprint-6 platform-wide mass-assignment defense activation.
 *
 * <p>The interceptor itself was added in PR #125 as a Spring bean but was
 * intentionally not connected to the SessionFactory so it could be
 * code-reviewed and tested separately. This config completes the wiring
 * via Spring Boot's {@code HibernatePropertiesCustomizer} hook, which is
 * preferred over the raw {@code hibernate.session_factory.interceptor}
 * property because it lets the interceptor keep its {@code @Autowired}
 * dependencies (audit log service).
 *
 * <h3>Feature flag — default OFF</h3>
 *
 * Controlled by the property {@code grc.security.tenant-write-guard.enabled}
 * (default {@code false}). To activate in a deployment, set:
 *
 * <pre>
 * # application-prod.yml or env override
 * grc:
 *   security:
 *     tenant-write-guard:
 *       enabled: true
 * </pre>
 *
 * Default-off because the interceptor is new code; operators should
 * activate first in staging, watch the audit log for any
 * {@code SECURITY_BLOCK} entries on legitimate flows for a sprint, then
 * flip to {@code true} in prod.
 *
 * <p>See {@code docs/security/MASS_ASSIGNMENT_DEFENSE.md} for the full
 * design and pre-activation checklist.
 */
@Configuration
public class TenantWriteGuardConfig {

    private static final Logger log = LoggerFactory.getLogger(TenantWriteGuardConfig.class);

    @Value("${grc.security.tenant-write-guard.enabled:false}")
    private boolean enabled;

    private final TenantWriteGuardInterceptor interceptor;

    public TenantWriteGuardConfig(TenantWriteGuardInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Bean
    public HibernatePropertiesCustomizer tenantWriteGuardCustomizer() {
        return (Map<String, Object> properties) -> {
            if (enabled) {
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
