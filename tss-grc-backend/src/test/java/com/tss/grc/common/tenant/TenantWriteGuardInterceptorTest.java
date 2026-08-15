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

    // ── F2-22b: the INSERT leg ────────────────────────────────────────────
    //
    // Until 2026-07-26 this class had exactly one @Override, onFlushDirty, so the guard fired only
    // on a change to an existing row's organisationId. A create was never examined. That matters
    // more than the update case it did cover: organisationId is bindable on 148 of the platform's
    // 214 entity-bound write endpoints (it is declared per-entity, so F2-22a's BaseEntity fix does
    // not reach it), and a create endpoint that forgets its
    // setOrganisationId(TenantContext.getCurrentTenant()) stamp inserts straight into whichever
    // org the request body names. Meanwhile application-prod.yml turns this bean ON and describes
    // it as the backstop "so a single missed per-service guard cannot silently write across
    // tenants" — which was true for updates and false for inserts.
    //
    // decideInsert() cannot reuse the update rule: an insert has no previous state, so "unchanged"
    // is meaningless. The invariant is that a new row belongs to the request's tenant.

    private static final String[] ORG_ONLY = new String[] { "organisationId" };

    @Test
    void blocksAnInsertIntoAnotherTenantWithoutOptIn() {
        TenantContext.setCurrentTenant(1L);

        assertThatThrownBy(() ->
            guard.decideInsert(new FakeEntity(), null, new Object[] { 2L }, ORG_ONLY))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("Cross-tenant insert blocked")
            .hasMessageContaining("org 2")
            .hasMessageContaining("org 1")
            // the message has to tell whoever reads the log which of the two causes it is
            .hasMessageContaining("missing its setOrganisationId")
            .hasMessageContaining("allowCrossTenantWrite");
    }

    @Test
    void allowsAnInsertIntoTheRequestsOwnTenant() {
        TenantContext.setCurrentTenant(1L);

        assertThatCode(() ->
            guard.decideInsert(new FakeEntity(), null, new Object[] { 1L }, ORG_ONLY))
            .doesNotThrowAnyException();
    }

    /**
     * The licence-grant path. {@code LicensingService.upsert} is the only endpoint on the platform
     * that requires {@code organisationId} in the request body, so it runs inside the scope; if
     * this did not pass, enabling the insert leg would break module licensing and W2-0's backfill.
     */
    @Test
    void allowsACrossTenantInsertInsideTheOptInScope() {
        TenantContext.setCurrentTenant(1L);

        try (var scope = TenantContext.allowCrossTenantWrite()) {
            assertThatCode(() ->
                guard.decideInsert(new FakeEntity(), null, new Object[] { 2L }, ORG_ONLY))
                .doesNotThrowAnyException();
        }

        // ...and the scope closes, so the next insert is judged again.
        assertThatThrownBy(() ->
            guard.decideInsert(new FakeEntity(), null, new Object[] { 2L }, ORG_ONLY))
            .isInstanceOf(SecurityException.class);
    }

    /**
     * Off-request there is no tenant to compare against — schedulers and sweeps run with
     * {@code TenantContext} unset (a documented landmine). Throwing there would break every sweep
     * the moment the flag is switched on, and would do it in production only.
     */
    @Test
    void allowsAnInsertWhenNoTenantIsOnTheThread() {
        assertThat(TenantContext.getCurrentTenant()).isNull();

        assertThatCode(() ->
            guard.decideInsert(new FakeEntity(), null, new Object[] { 7L }, ORG_ONLY))
            .doesNotThrowAnyException();
    }

    /**
     * A null tenant on the row is not a cross-tenant write, so the guard passes — that much was
     * always right, and it is what this test verifies.
     *
     * <p>⚠⚠ <b>F2-216 — this test used to be called
     * {@code allowsAnInsertWithNoTenantOnTheRowAndLetsTheColumnComplain}</b>, and its reason read:
     * <em>"It is a NOT NULL violation about to happen, and the database reports that far more
     * usefully than a SecurityException would."</em> Measured from {@code sys.columns}: <b>11 of
     * 427 org-bearing tables declare {@code organisation_id} NULLABLE</b>, and on eight of those
     * NULL is a value meaning <b>platform-global — visible to every tenant</b> (169 rows in dev,
     * 158 in prod). For those tables no column complains and nothing is a violation; a null
     * tenant publishes the row to the whole platform.
     *
     * <p>🔑 <b>The assertion was correct and everything around it was wrong, which is the worst
     * combination.</b> A reader auditing this exemption found a green test whose NAME told them
     * the database would catch it, and stopped — so the name was doing the opposite of the work
     * a test name is for. F2-159's decoy and F2-175's rule in one artefact: a test written by
     * reading the implementation can only agree with it, and that agreement is hardest to see
     * when the description is accurate about the code and false about the world.
     *
     * <p>The behaviour is unchanged and deliberately so: throwing here would break the eight
     * legitimately-global catalogues and the ordinary stamp-after-build shape (F2-183 — never
     * add a constraint while its writers are open). What makes the exemption safe is that no
     * write endpoint binds a globally-visible entity, which is now a build failure rather than
     * an accident — see {@link TenantGuardNullExemptionContractTest}.
     */
    @Test
    void allowsAnInsertWithANullTenantOnTheRow() {
        TenantContext.setCurrentTenant(1L);

        assertThatCode(() ->
            guard.decideInsert(new FakeEntity(), null, new Object[] { null }, ORG_ONLY))
            .as("the guard must pass — but NOT because the column will refuse it; see the javadoc")
            .doesNotThrowAnyException();
    }

    @Test
    void ignoresAnInsertOfAnEntityWithNoTenantColumn() {
        TenantContext.setCurrentTenant(1L);

        assertThatCode(() ->
            guard.decideInsert(new FakeEntity(), null,
                new Object[] { "ISO 27001", "A.5.1" },
                new String[] { "frameworkName", "clause" }))
            .as("platform-shared catalogues have no tenant column and are out of scope")
            .doesNotThrowAnyException();
    }

    /**
     * The interceptor inspects; it must never rewrite the state array Hibernate is about to
     * persist. {@code onSave} returning true tells Hibernate the state was modified, which would
     * make this bean a silent mutator of every insert on the platform.
     */
    @Test
    void theInsertLegNeverReportsHavingModifiedTheState() {
        TenantContext.setCurrentTenant(1L);
        Object[] state = new Object[] { 1L };

        assertThat(guard.decideInsert(new FakeEntity(), null, state, ORG_ONLY)).isFalse();
        assertThat(state[0]).as("and the array is untouched").isEqualTo(1L);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    /** Placeholder so {@code entity.getClass().getSimpleName()} returns
     *  a readable name in error messages. */
    private static class FakeEntity {}
}
