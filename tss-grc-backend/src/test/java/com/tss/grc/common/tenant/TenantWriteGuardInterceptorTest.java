package com.tss.grc.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for the Sprint-6 mass-assignment backstop.
 *
 * <p>Tests target the package-private {@code decide()} method on the
 * interceptor — a pure function over the Hibernate change-set + the
 * thread-local cross-tenant-write flag. Going through {@code decide()}
 * instead of {@code onFlushDirty()} shields the tests from Hibernate
 * interface churn (deprecated overloads, etc) across minor versions.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Attack simulation — organisationId changes, no opt-in → throws SecurityException</li>
 *   <li>Legitimate cross-tenant write — same change, inside allowCrossTenantWrite() → passes</li>
 *   <li>Tenant unchanged — normal update flow → passes</li>
 *   <li>Entity without organisationId column — out of scope → passes</li>
 *   <li>Null previousState handled gracefully</li>
 *   <li>AutoCloseable cleanup — flag clears even if body throws</li>
 * </ul>
 *
 * <p>No Spring context, no Hibernate. The interceptor is constructed
 * directly with audit=null (its @Autowired dep is optional).
 */
class TenantWriteGuardInterceptorTest {

    private final TenantWriteGuardInterceptor guard = new TenantWriteGuardInterceptor();

    @AfterEach
    void cleanup() {
        // ThreadLocals leak across tests in the same JVM; clear after each.
        TenantContext.clear();
    }

    // ── Attack simulation ─────────────────────────────────────────────────

    @Test
    void blocksWhenOrganisationIdChangesWithoutOptIn() {
        Object[] previousState = new Object[] { "old-title", 100L };
        Object[] currentState  = new Object[] { "new-title", 200L };
        String[] propertyNames = new String[] { "title", "organisationId" };

        // Sanity: flag really is off at the start of the test
        assertThat(TenantContext.isCrossTenantWriteAllowed()).isFalse();

        assertThatThrownBy(() ->
            guard.decide(
                new FakeEntity(), 12345L,
                currentState, previousState,
                propertyNames)
        )
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("Cross-tenant write blocked")
        .hasMessageContaining("FakeEntity")
        .hasMessageContaining("id=12345")
        .hasMessageContaining("100")
        .hasMessageContaining("200")
        .hasMessageContaining("allowCrossTenantWrite")
        .hasMessageContaining("setId(null)");
    }

    @Test
    void blocksWhenOrganisationIdGoesFromNullToValue() {
        // Defensive — fresh entities go through onSave not onFlushDirty,
        // but if a code path lands here with a null previous org the
        // change is still suspicious.
        Object[] previousState = new Object[] { "x", null };
        Object[] currentState  = new Object[] { "x", 99L };
        String[] propertyNames = new String[] { "field", "organisationId" };

        assertThat(TenantContext.isCrossTenantWriteAllowed()).isFalse();

        assertThatThrownBy(() ->
            guard.decide(
                new FakeEntity(), 1L,
                currentState, previousState,
                propertyNames)
        ).isInstanceOf(SecurityException.class);
    }

    // ── Legitimate path ───────────────────────────────────────────────────

    @Test
    void allowsCrossTenantWriteInsideExplicitOptInScope() {
        Object[] previousState = new Object[] { 100L };
        Object[] currentState  = new Object[] { 200L };
        String[] propertyNames = new String[] { "organisationId" };

        try (var scope = TenantContext.allowCrossTenantWrite()) {
            boolean modified = guard.decide(
                new FakeEntity(), 1L,
                currentState, previousState,
                propertyNames);
            assertThat(modified).isFalse();
        }
    }

    @Test
    void optInScopeClearsAfterTryWithResources() {
        try (var scope = TenantContext.allowCrossTenantWrite()) {
            assertThat(TenantContext.isCrossTenantWriteAllowed()).isTrue();
        }
        assertThat(TenantContext.isCrossTenantWriteAllowed()).isFalse();
    }

    @Test
    void optInScopeClearsEvenIfBodyThrows() {
        // Critical safety property — the AutoCloseable must clear the flag
        // even if the body of the scope throws. Otherwise an exception in
        // legitimate code could leave the thread permanently privileged for
        // any future request that lands on it from the same pool.
        try {
            try (var scope = TenantContext.allowCrossTenantWrite()) {
                assertThat(TenantContext.isCrossTenantWriteAllowed()).isTrue();
                throw new RuntimeException("simulated body failure");
            }
        } catch (RuntimeException ignored) {
            // Expected.
        }
        assertThat(TenantContext.isCrossTenantWriteAllowed()).isFalse();
    }

    // ── Pass-through cases ────────────────────────────────────────────────

    @Test
    void allowsNormalUpdateWhereOrganisationIdUnchanged() {
        Object[] previousState = new Object[] { "old-title", 100L };
        Object[] currentState  = new Object[] { "new-title", 100L };  // same org
        String[] propertyNames = new String[] { "title", "organisationId" };

        boolean modified = guard.decide(
            new FakeEntity(), 1L,
            currentState, previousState,
            propertyNames);

        assertThat(modified).isFalse();
    }

    @Test
    void ignoresEntitiesWithoutOrganisationIdColumn() {
        // Platform-shared catalog entities (CyberFramework, BcpPlanTemplate)
        // don't carry an organisationId. The guard must pass them through.
        Object[] previousState = new Object[] { "old-name" };
        Object[] currentState  = new Object[] { "new-name" };
        String[] propertyNames = new String[] { "name" };

        boolean modified = guard.decide(
            new FakeEntity(), 1L,
            currentState, previousState,
            propertyNames);

        assertThat(modified).isFalse();
    }

    @Test
    void handlesNullPreviousStateGracefully() {
        // Hibernate occasionally passes a null previousState. The guard
        // should treat that as "no prior org" and block unless opt-in.
        Object[] currentState = new Object[] { 100L };
        String[] propertyNames = new String[] { "organisationId" };

        assertThatThrownBy(() ->
            guard.decide(
                new FakeEntity(), 1L,
                currentState, null,
                propertyNames)
        ).isInstanceOf(SecurityException.class);

        try (var scope = TenantContext.allowCrossTenantWrite()) {
            assertThatCode(() ->
                guard.decide(
                    new FakeEntity(), 1L,
                    currentState, null,
                    propertyNames)
            ).doesNotThrowAnyException();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    /** Placeholder so {@code entity.getClass().getSimpleName()} returns
     *  a readable name in error messages. */
    private static class FakeEntity {}
}
