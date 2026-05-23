package com.tss.grc.common.tenant;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Hibernate interceptor that blocks cross-tenant writes — Sprint-6
 * platform-wide backstop for the mass-assignment defense pattern
 * (see {@code docs/security/MASS_ASSIGNMENT_DEFENSE.md}).
 *
 * <h3>What it catches</h3>
 *
 * The per-service {@code req.setId(null)} pattern catches the common
 * shape of the attack (id-bearing body → JPA {@code merge()} →
 * cross-tenant overwrite). But it's easy to forget on the next write
 * endpoint a developer adds. This interceptor watches every UPDATE that
 * passes through Hibernate and rejects any attempt to change an entity's
 * {@code organisationId} field unless the calling code has explicitly
 * opted in via {@link TenantContext#allowCrossTenantWrite()}.
 *
 * <h3>Why {@code onFlushDirty} and not {@code @PreUpdate}</h3>
 *
 * JPA's {@code @PreUpdate} lifecycle hook only sees the post-mutation
 * state — by then the attacker's cross-tenant {@code organisationId}
 * has already been copied onto the managed entity. Hibernate's
 * {@code onFlushDirty} hook is the only place that sees BOTH old and
 * new property values, so it can detect the mutation specifically.
 *
 * <h3>Wiring status — NOT YET ACTIVE</h3>
 *
 * This bean exists as a stub so it can be code-reviewed and unit-tested
 * before being wired into the EntityManager / SessionFactory. To
 * activate, set in {@code application.yml}:
 *
 * <pre>
 * spring:
 *   jpa:
 *     properties:
 *       hibernate.session_factory.interceptor: com.tss.grc.common.tenant.TenantWriteGuardInterceptor
 * </pre>
 *
 * Or, for Spring-managed lifecycle, register via a Spring
 * {@code HibernatePropertiesCustomizer} that resolves this bean. The
 * second approach is preferred so {@code @Autowired} dependencies
 * (audit log) work — the YAML approach instantiates a plain class
 * outside the Spring container.
 *
 * <h3>Pre-activation checklist</h3>
 *
 * <ol>
 *   <li>Verify every scheduled sweep that walks tenants
 *       (SocOpsScheduledSweep, CommitteeScheduledSweep, NdiScheduler)
 *       wraps its tenant loop body in {@code try (var scope =
 *       TenantContext.allowCrossTenantWrite()) { ... }}.</li>
 *   <li>Verify {@code OrganisationController.switchTo} opts in for the
 *       org-switcher path (the only legitimate runtime cross-tenant
 *       write).</li>
 *   <li>Add unit tests:
 *       <ul>
 *         <li>Attack simulation — tenant-A user supplies cross-tenant id,
 *             interceptor throws SecurityException, response is 403.</li>
 *         <li>Legitimate path — PLATFORM_ADMIN org switcher flips orgs
 *             under the scope, no exception.</li>
 *       </ul>
 *   </li>
 *   <li>Flip the wiring switch above; deploy to staging; verify nothing
 *       else trips the interceptor in normal operation.</li>
 * </ol>
 */
@Component
public class TenantWriteGuardInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantWriteGuardInterceptor.class);
    private static final String ORG_PROP = "organisationId";

    /**
     * Audit log of blocked attempts — gives ops visibility for tracking
     * attack attempts even when the interceptor's exception is caught and
     * masked by an upstream try/catch. Optional dependency so the bean
     * can be constructed in unit tests that don't bring up the audit log.
     */
    @Autowired(required = false)
    private AuditLogService audit;

    @Override
    public boolean onFlushDirty(
        Object entity, Object id,
        Object[] currentState, Object[] previousState,
        String[] propertyNames, Type[] types
    ) {
        int orgIdx = indexOf(propertyNames, ORG_PROP);
        if (orgIdx < 0) {
            // Entity doesn't have an organisationId — out of scope for
            // this guard (platform-shared catalogs etc).
            return false;
        }
        Object oldOrg = previousState == null ? null : previousState[orgIdx];
        Object newOrg = currentState[orgIdx];
        if (Objects.equals(oldOrg, newOrg)) {
            // Tenant unchanged — the normal case.
            return false;
        }
        if (TenantContext.isCrossTenantWriteAllowed()) {
            // Legitimate cross-tenant write (PLATFORM_ADMIN org switcher,
            // scheduled sweep). Allow but log so we can audit later.
            log.info("[TenantWriteGuard] allowed cross-tenant write on {} id={} ({} -> {})",
                entity.getClass().getSimpleName(), id, oldOrg, newOrg);
            return false;
        }

        // Cross-tenant write detected without an explicit opt-in.
        // This is either a mass-assignment attack or a code path that
        // forgot to wrap in TenantContext.allowCrossTenantWrite().
        String entityName = entity.getClass().getSimpleName();
        String msg = String.format(
            "Cross-tenant write blocked on %s id=%s (org %s → %s). " +
            "Use TenantContext.allowCrossTenantWrite() for legitimate cross-tenant " +
            "operations; otherwise check the calling service for a missing " +
            "req.setId(null) mass-assignment defense.",
            entityName, id, oldOrg, newOrg);
        log.warn("[TenantWriteGuard] {}", msg);
        try {
            if (audit != null) {
                audit.log("SECURITY_BLOCK", entityName, id instanceof Long ? (Long) id : null, msg);
            }
        } catch (RuntimeException ignored) {
            // Don't let the audit log failure mask the security exception.
        }
        throw new SecurityException(msg);
    }

    private static int indexOf(String[] propertyNames, String target) {
        for (int i = 0; i < propertyNames.length; i++) {
            if (target.equals(propertyNames[i])) return i;
        }
        return -1;
    }
}
