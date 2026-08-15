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

    /**
     * Flag that explicitly opts the current thread into a cross-tenant write
     * operation. Used by the Sprint-6 {@code TenantWriteGuardInterceptor}
     * (see docs/security/MASS_ASSIGNMENT_DEFENSE.md) to distinguish:
     *
     *   - Legitimate cross-tenant writes (PLATFORM_ADMIN org switcher,
     *     scheduled cross-tenant sweeps) — set this flag in a
     *     try-with-resources block via {@link #allowCrossTenantWrite()}.
     *   - Mass-assignment attack attempts — the flag is not set, so the
     *     interceptor's {@code onFlushDirty} hook throws SecurityException
     *     when it sees {@code organisationId} change between previous-state
     *     and current-state.
     *
     * The flag is intentionally a separate thread-local from CURRENT_TENANT
     * so that authentication / JWT-filter code paths can't accidentally
     * grant the privilege.
     */
    private static final ThreadLocal<Boolean> CROSS_TENANT_WRITE_ALLOWED = new InheritableThreadLocal<>();

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
        CROSS_TENANT_WRITE_ALLOWED.remove();
    }

    // ─── Cross-tenant write guard ───────────────────────────────────────
    //
    // These helpers back the platform-wide mass-assignment backstop in
    // docs/security/MASS_ASSIGNMENT_DEFENSE.md.
    //
    // ⚠ F2-216 — this comment said "The Hibernate interceptor is not yet wired in — these
    // are the building blocks so the interceptor PR is a small, focused change when someone
    // can run it locally to verify." That stopped being true when F2-22b added the insert
    // leg and turned the flag on: TenantWriteGuardInterceptor is wired by
    // TenantWriteGuardConfig and `grc.security.tenant-write-guard.enabled` is TRUE in both
    // application-dev.yml and application-prod.yml. Both legs run, on every request.
    //
    // 🔑 The correction matters more than the fact. A reader who opens the class that DEFINES
    // the mechanism is told the platform-wide cross-tenant backstop does not run — so they
    // either build a redundant per-service guard, or conclude that whatever guard they are
    // looking at is the only thing standing between a request body and another tenant's data.
    // A comment describing a control as absent is worse than no comment, because it answers
    // the question the reader came to ask (F2-184's shape, on a security control's own
    // wiring status). TenantGuardNullExemptionContractTest fails the build if either this
    // comment or TenantWriteGuardConfig's javadoc drifts back out of step with the profiles.

    /**
     * Opens a scope in which the current thread is permitted to write
     * across tenants. Use ONLY for legitimate cross-tenant operations:
     *
     *   - PLATFORM_ADMIN org switcher (changes a user's active org)
     *   - Scheduled sweeps that walk all tenants (SocOpsScheduledSweep,
     *     CommitteeScheduledSweep, NdiScheduler)
     *
     * Returns an {@link AutoCloseable} so the caller can use try-with-resources
     * to guarantee the flag is cleared even if the body throws:
     *
     * <pre>
     * try (var scope = TenantContext.allowCrossTenantWrite()) {
     *     // ... perform the legitimate cross-tenant write ...
     * }
     * </pre>
     */
    public static CrossTenantScope allowCrossTenantWrite() {
        CROSS_TENANT_WRITE_ALLOWED.set(Boolean.TRUE);
        return CrossTenantScope.INSTANCE;
    }

    /**
     * True when the current thread is inside an
     * {@link #allowCrossTenantWrite()} scope. Consulted by the Sprint-6
     * Hibernate interceptor to decide whether to throw or pass.
     */
    public static boolean isCrossTenantWriteAllowed() {
        return Boolean.TRUE.equals(CROSS_TENANT_WRITE_ALLOWED.get());
    }

    /**
     * AutoCloseable returned by {@link #allowCrossTenantWrite()}. Closing
     * the scope removes the thread-local flag so the next request on the
     * same pooled thread starts with the default (deny) policy.
     */
    public static final class CrossTenantScope implements AutoCloseable {
        private static final CrossTenantScope INSTANCE = new CrossTenantScope();
        private CrossTenantScope() {}
        @Override public void close() {
            CROSS_TENANT_WRITE_ALLOWED.remove();
        }
    }
}
