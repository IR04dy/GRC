package com.tss.grc.security;

import java.io.Serializable;

/**
 * F2-159 / ADR-017 D-14 — answers "does this caller own <em>this record</em>?" for one entity type.
 *
 * <h3>Why this interface exists</h3>
 * <p>Every one of the platform's 2,210 handlers authorises by <em>permission</em> (what you may do) or by
 * <em>role name</em> (who you are). Neither can express <em>whose record it is</em>, and four register
 * items were blocked on that gap: F2-52's {@code MODEL_OWNER} (14 guard sites) and {@code FACILITATOR}
 * (18), F2-145(c), and F2-145(d). D-14 decided the shape — bind to the column the platform already
 * populates, because seating a role called {@code MODEL_OWNER} would be <b>looser</b> than the data: the
 * owner of model A could act on model B.
 *
 * <h3>⚠ The gap was not merely empty — it was a decoy, and that is the finding</h3>
 * <p>{@code PermissionEvaluator} declares a four-argument overload for exactly this purpose, and
 * {@link ModulePermissionEvaluator} implemented it as
 * {@code return hasPermission(authentication, targetType, permission)} under the comment
 * <i>"Entity-instance permission checks not used"</i>. So a developer writing the idiomatic Spring
 * ownership guard got a guard that <b>silently discarded the record id</b>:
 * <pre>
 *   &#64;PreAuthorize("hasPermission(#id, 'RISK', 'READ')")   // reads: may I read THIS risk?
 *                                                            // meant: may I read risks?
 * </pre>
 * It compiles, it reads correctly in review, and it is a cross-record hole. Measured 2026-08-07: no guard
 * in the platform used that form, so nothing was exposed — the trap was unsprung, not absent. The
 * evaluator now <b>refuses</b> an unregistered target type instead of falling through to the module
 * check, so the failure mode of a mistake here is a 403, not silent breadth.
 *
 * <h3>Contract</h3>
 * <p>Implementations must scope their lookup by {@code organisationId} — an ownership check that is not
 * tenant-scoped is a cross-tenant read waiting for a guessed id, and this is the one place in the
 * codebase where a raw client-supplied identifier is looked up <em>before</em> any other authorisation
 * has run. Return {@code false} for an unknown id rather than throwing: an ownership check is one leg of
 * an {@code or}, and a leg that throws takes the whole expression down.
 */
public interface EntityOwnershipResolver {

    /**
     * The {@code targetType} this resolver answers for, as written in the guard — {@code ML_MODEL} in
     * {@code hasPermission(#externalId, 'ML_MODEL', 'OWNER')}. Must be unique across resolvers;
     * {@link EntityOwnershipRegistry} refuses duplicates at startup rather than picking one.
     */
    String targetType();

    /**
     * @param id             the identifier exactly as the guard passed it — client-supplied, so treat it
     *                       as untrusted input and never interpolate it into SQL
     * @param userId         the authenticated caller
     * @param organisationId the caller's tenant; the lookup must be scoped by it
     * @return whether {@code userId} is recorded as the owner of that record
     */
    boolean isOwnedBy(Serializable id, Long userId, Long organisationId);
}
