package com.tss.grc.common.tenant;

/**
 * Thread-local holder for the current tenant (organisation) ID.
 *
 * <p>Set by {@link com.tss.grc.security.JwtAuthenticationFilter} on every
 * authenticated request from the JWT {@code org} claim. Cleared in the
 * filter's finally block to prevent thread-local leakage in thread pools.
 *
 * <h3>Usage</h3>
 * <pre>
 * // In a repository or service:
 * Long orgId = TenantContext.getCurrentTenant();
 * </pre>
 *
 * <p>All tenant-scoped JPA queries use this value via a Hibernate filter or
 * explicit {@code WHERE organisation_id = :orgId} clauses.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new InheritableThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenant(Long organisationId) {
        CURRENT_TENANT.set(organisationId);
    }

    public static Long getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Returns the current tenant or throws — use in service-layer code where
     * an unauthenticated/missing tenant is a programmer error, not a recoverable
     * condition. Lifecycle modules (risk_lc, audit_lc, privacy_lc, bcm_lc, cyber_lc)
     * call this to fail-fast rather than silently leaking across tenants.
     */
    public static Long requireOrganisationId() {
        Long tenant = CURRENT_TENANT.get();
        if (tenant == null) {
            throw new IllegalStateException(
                "No tenant in TenantContext — JwtAuthenticationFilter did not run, " +
                "or the request is unauthenticated. Refusing to query without tenant scope."
            );
        }
        return tenant;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
