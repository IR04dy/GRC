package com.tss.grc.common.tenant;

import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
 * endpoint a developer adds. This interceptor watches every UPDATE and
 * every INSERT that passes through Hibernate and rejects any attempt to
 * move an entity's {@code organisationId} away from the request's tenant
 * unless the calling code has explicitly opted in via
 * {@link TenantContext#allowCrossTenantWrite()}.
 *
 * <p><b>F2-22b — the INSERT leg was missing until 2026-07-26.</b> The class shipped with
 * {@code onFlushDirty} as its only override, so for its whole life the "platform-wide backstop"
 * examined updates and ignored creates. Since F2-22a made {@code organisationId} the largest
 * remaining mass-assignment surface — bindable on 148 of the 214 entity-bound write endpoints,
 * because it is declared per-entity and {@code BaseEntity} never carried it — the uncovered half
 * was the half that mattered: a create endpoint that forgets to stamp the tenant writes straight
 * into whatever org the request body names. See {@link #onSave}.
 *
 * <h3>Why {@code onFlushDirty} and not {@code @PreUpdate}</h3>
 *
 * JPA's {@code @PreUpdate} lifecycle hook only sees the post-mutation
 * state — by then the attacker's cross-tenant {@code organisationId}
 * has already been copied onto the managed entity. Hibernate's
 * {@code onFlushDirty} hook is the only place that sees BOTH old and
 * new property values, so it can detect the mutation specifically.
 *
 * <h3>Wiring status — ACTIVE IN PRODUCTION, OFF IN DEV</h3>
 *
 * Wired into Hibernate by {@link com.tss.grc.config.TenantWriteGuardConfig} through Spring Boot's
 * {@code HibernatePropertiesCustomizer} (so the {@code @Autowired} audit-log dependency resolves),
 * behind {@code grc.security.tenant-write-guard.enabled}. That property is <b>true</b> in
 * {@code application-prod.yml} and unset — therefore false — in dev, which is why the insert-leg
 * gap could not surface from local use: in dev the whole interceptor is inert, and in prod the
 * half that ran was the half without the hole.
 *
 * <p><b>Consequence for tests.</b> The decision logic is deliberately extracted into the
 * package-private {@link #decide} and {@link #decideInsert} so both legs can be asserted without
 * Hibernate and without the flag — see {@code TenantWriteGuardInterceptorTest}. Treat those as the
 * proof of behaviour; a dev-machine HTTP probe exercises the per-service stamp, never this bean.
 *
 * <h3>Legitimate cross-tenant writes — every one must opt in</h3>
 *
 * With the insert leg live, any code that creates a row for another tenant has to say so. Known
 * cases, all wrapped:
 *
 * <ol>
 *   <li>{@code LicensingService.upsert} — the module-licence grant, and the only service on the
 *       platform that <em>requires</em> {@code organisationId} from its argument (it throws
 *       {@code "organisationId is required"} without one). Wrapped in the same commit as this leg,
 *       and it is the mutation proof that the leg works: remove the scope, grant org 2 a module it
 *       does not hold, get 403. Note that its HTTP endpoint is already single-tenant — the
 *       controller overwrites the body's tenant — so the scope exists for the service-level caller
 *       W2-0 will use, not to keep today's API working.</li>
 *   <li>{@code OrganisationController.switchTo} — the org switcher.</li>
 *   <li>Scheduled sweeps that walk tenants (SocOps, Committee, Ndi) — these run off-request where
 *       {@code TenantContext} is unset, which {@link #decideInsert} already treats as
 *       unjudgeable, but wrapping them is still the honest expression of intent.</li>
 * </ol>
 */
@Component
public class TenantWriteGuardInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantWriteGuardInterceptor.class);
    private static final String ORG_PROP = "organisationId";

    /**
     * Audit log of blocked attempts — gives ops visibility for tracking
     * attack attempts even when the interceptor's exception is caught and
     * masked by an upstream try/catch. Injected as a lazy provider, not a
     * direct reference: this interceptor is wired INTO the EntityManagerFactory,
     * and AuditLogService needs a JPA repository (which needs the EMF) — a
     * direct field injection forms a startup cycle the moment the prod-only
     * tenant-write-guard flag turns the bean on (found on the first real prod
     * boot). The provider defers resolution to block-time, when the context is
     * fully started. Optional so the bean can still be constructed bare in
     * unit tests that don't bring up the audit log.
     */
    @Autowired(required = false)
    private ObjectProvider<AuditLogService> auditProvider;

    @Override
    public boolean onFlushDirty(
        Object entity, Object id,
        Object[] currentState, Object[] previousState,
        String[] propertyNames, Type[] types
    ) {
        // Delegate to the package-private decide() method so the decision
        // logic is unit-testable without instantiating Hibernate. The
        // interface signature here can shift across Hibernate minor
        // versions (deprecated overloads, etc) — keeping the body thin
        // shields the tests from that churn.
        return decide(entity, id, currentState, previousState, propertyNames);
    }

    /**
     * F2-22b — the INSERT leg, which did not exist.
     *
     * <p>Until this method landed, {@code onFlushDirty} was the interceptor's only override, so
     * the guard fired exclusively on <em>changes to an existing row's</em> {@code organisationId}.
     * A <b>create</b> was never examined: {@code POST} a body carrying another tenant's
     * {@code organisationId}, and if the service forgot to stamp the tenant, the row was inserted
     * into that tenant with the backstop watching and saying nothing. `application-prod.yml`
     * describes this bean as the "platform-wide Hibernate-level cross-tenant write backstop … so
     * a single missed per-service guard cannot silently write across tenants" and turns it ON —
     * which was true for updates and false for inserts, and inserts are the likelier place to
     * miss the stamp, because a new create endpoint has no existing row to inherit a tenant from.
     *
     * <p><b>The rule differs from the update rule.</b> An insert has no previous state, so
     * "unchanged" is meaningless; the invariant is that a new row must belong to the tenant on
     * whose request it is being written. Two deliberate exemptions:
     * <ul>
     *   <li>{@code organisationId == null} — <b>see the section below. This is the exemption
     *       whose stated reason was false.</b></li>
     *   <li>no tenant on the thread — schedulers, sweeps and bootstrap run off-request, where
     *       {@code TenantContext} is unset (landmine: {@code TenantContext} isn't populated off
     *       -request). With nothing to compare against there is no judgement to make, and
     *       throwing here would break every sweep the moment the flag is switched on.</li>
     * </ul>
     *
     * <h3>⚠⚠ F2-216 — a NULL tenant is not "missing", it is "every tenant"</h3>
     *
     * <p>This exemption used to read: <em>"not a cross-tenant write. It is a NOT NULL violation
     * about to happen, and the database says so far more clearly than a SecurityException
     * would."</em> Measured 2026-08-15 from {@code sys.columns} in both databases: of the
     * <b>427 tables carrying an {@code organisation_id}</b> (421 in prod), <b>11 declare it
     * NULLABLE</b> — so for those eleven the database says nothing at all, and the sentence
     * describing what would happen instead was the only analysis anybody had done.
     *
     * <p>Worse than merely permissive. On <b>eight</b> of the eleven, NULL is not an absence,
     * it is a <b>value with a meaning</b>: {@code aml.sanctions_list},
     * {@code aml.sanctions_list_entry}, {@code fraud.fraud_typology}, {@code iam.roles},
     * {@code iam.sod_rules}, {@code opres.opres_pillar}, {@code platform.help_topic} and
     * {@code platform.three_lines_role_mapping} are all read by a repository asking
     * {@code organisationId IS NULL OR organisationId = :orgId}, and
     * {@code SanctionsListRepository}'s own javadoc states it — <em>"Lists with
     * organisation_id IS NULL are platform-global"</em>. <b>169 of their 177 rows in dev, 158
     * of 166 in prod, are stored that way.</b>
     *
     * <p>🔑 <b>So the value this guard declines to judge is precisely the value that publishes
     * a row to every tenant on the platform.</b> A caller who could reach one of these tables
     * with a null tenant would not be committing the "NOT NULL violation" the comment predicted;
     * they would be adding an entry to every other tenant's sanctions screening, or a role to
     * every other tenant's IAM catalogue, and the platform-wide cross-tenant backstop would
     * return {@code false} without logging a line.
     *
     * <p><b>Note the internal inconsistency this exposes, which is the tell.</b> The UPDATE leg
     * treats the same value as protected: {@code decide} compares old against new, so a
     * platform-global row being pulled into one tenant ({@code null → 5}) throws, and
     * {@code TenantWriteGuardInterceptorTest.blocksWhenOrganisationIdGoesFromNullToValue} has
     * asserted that since the beginning. One value, guarded on update, unjudged on insert.
     *
     * <h3>REFUTED, and the refutation is why this is documented rather than changed</h3>
     *
     * <p><b>No caller can reach it today.</b> Measured, not assumed: none of the eight
     * globally-visible entities is bound by any write endpoint (the authority is
     * {@code MassAssignmentRatchetTest}'s reflection scan — 202 entity-bound writes, none of
     * them these), and all four services that write those tables stamp the tenant from
     * {@code TenantContext} on the create path ({@code SanctionsListService#create},
     * {@code AccessGovernanceService#createRole}, {@code HelpTopicService}, {@code ThreeLinesService}).
     * So this is <b>latent</b>, and it stays latent only because of a property nobody had
     * written down, let alone enforced.
     *
     * <p>⚠ <b>Which is the F2-182 rule: reachability is a fact about today, not a property of a
     * control.</b> Throwing on a null tenant here is <em>not</em> the fix — it is F2-183's
     * defect, a constraint added while its writers are open: eight tables legitimately hold
     * 169 such rows, the tenant-onboarding bootstrap creates them off-request, and the
     * exemption is also the shape a service produces when it stamps the tenant <em>after</em>
     * building the entity. The honest fix is to state the true reason and enforce the property
     * that makes it safe, which is what
     * {@code TenantGuardNullExemptionContractTest.noEntityBoundWriteBindsAPlatformGlobalEntity}
     * does — a hard zero over a population <b>derived from the repositories</b>, so a ninth
     * globally-visible entity is covered the day its repository is written.
     *
     * <p>⚠⚠ <b>And the branch WAS tested — which is worse, and is the part worth carrying
     * forward.</b> My first draft of this note claimed it had no test; the test list disproved
     * that. {@code TenantWriteGuardInterceptorTest} covers it, and covered it from the start.
     * But the test was <b>named for the false premise</b> —
     * {@code allowsAnInsertWithNoTenantOnTheRowAndLetsTheColumnComplain} — and its javadoc
     * repeated the claim word for word: <em>"It is a NOT NULL violation about to happen, and
     * the database reports that far more usefully than a SecurityException would."</em> Its
     * assertion is correct (the guard must pass); everything a reader uses to decide whether
     * the branch is <em>right</em> was wrong.
     *
     * <p>🔑 <b>A green test whose NAME asserts the behaviour under review is the strongest
     * possible reason not to look further.</b> An untested branch at least reads as untested.
     * This is F2-159's decoy and F2-175's rule in one artefact: a test written by reading the
     * implementation can only agree with it, and the agreement is hardest to spot when the
     * description is accurate about the code and wrong about the world. Renamed to
     * {@code allowsAnInsertWithANullTenantOnTheRow} — what it verifies — with the reason
     * corrected.
     *
     * @return {@code false} always — this interceptor inspects, it never mutates the state array.
     */
    @Override
    public boolean onSave(Object entity, Object id, Object[] state,
                          String[] propertyNames, Type[] types) {
        return decideInsert(entity, id, state, propertyNames);
    }

    /**
     * Package-private decision logic — pure function over the Hibernate
     * change-set plus the thread-local cross-tenant-write flag. Returns
     * false if the change should be allowed; throws SecurityException if
     * the change is a blocked cross-tenant write.
     *
     * <p>Visible for testing so the test can call it without depending on
     * the exact Hibernate {@code Interceptor} method overload Hibernate
     * decides to dispatch to.
     */
    boolean decide(
        Object entity, Object id,
        Object[] currentState, Object[] previousState,
        String[] propertyNames
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
            "Cross-tenant write blocked on %s id=%s (org %s -> %s). " +
            "Use TenantContext.allowCrossTenantWrite() for legitimate cross-tenant " +
            "operations; otherwise check the calling service for a missing " +
            "req.setId(null) mass-assignment defense.",
            entityName, id, oldOrg, newOrg);
        log.warn("[TenantWriteGuard] {}", msg);
        try {
            AuditLogService audit = auditProvider == null ? null : auditProvider.getIfAvailable();
            if (audit != null) {
                audit.log("SECURITY_BLOCK", entityName, id instanceof Long ? (Long) id : null, msg);
            }
        } catch (RuntimeException ignored) {
            // Don't let the audit log failure mask the security exception.
        }
        throw new SecurityException(msg);
    }

    /**
     * Insert-side counterpart to {@link #decide}: the new row's tenant must be the request's
     * tenant. Package-private for the same reason — the decision is unit-testable without
     * Hibernate, which is how the update leg is covered and how this one is.
     */
    boolean decideInsert(Object entity, Object id, Object[] state, String[] propertyNames) {
        int orgIdx = indexOf(propertyNames, ORG_PROP);
        if (orgIdx < 0) {
            return false;                       // platform-shared catalogue, no tenant column
        }
        Object rowOrg = state[orgIdx];
        if (rowOrg == null) {
            // F2-216 — NOT "the NOT NULL column will speak": 11 of 427 org-bearing tables allow
            // NULL, and on 8 of them NULL means PLATFORM-GLOBAL (readable by every tenant). We
            // pass because throwing would break the 169 legitimate global rows and the
            // stamp-after-build shape, and because no write endpoint binds a globally-visible
            // entity — an invariant TenantGuardNullExemptionContractTest holds at zero.
            return false;
        }
        Long current = TenantContext.getCurrentTenant();
        if (current == null || Objects.equals(current, rowOrg)) {
            return false;                       // off-request, or the normal case
        }
        if (TenantContext.isCrossTenantWriteAllowed()) {
            log.info("[TenantWriteGuard] allowed cross-tenant INSERT of {} into org {} "
                     + "(request tenant {})", entity.getClass().getSimpleName(), rowOrg, current);
            return false;
        }

        String entityName = entity.getClass().getSimpleName();
        String msg = String.format(
            "Cross-tenant insert blocked: %s would be created in org %s on a request "
            + "authenticated for org %s. Either the calling service is missing its "
            + "setOrganisationId(TenantContext.getCurrentTenant()) stamp — so the tenant came "
            + "from the request body — or the operation is a legitimate cross-tenant write and "
            + "belongs inside TenantContext.allowCrossTenantWrite().",
            entityName, rowOrg, current);
        log.warn("[TenantWriteGuard] {}", msg);
        try {
            AuditLogService audit = auditProvider == null ? null : auditProvider.getIfAvailable();
            if (audit != null) {
                audit.log("SECURITY_BLOCK", entityName, id instanceof Long ? (Long) id : null, msg);
            }
        } catch (RuntimeException ignored) {
            // Don't let an audit-log failure mask the security exception.
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
