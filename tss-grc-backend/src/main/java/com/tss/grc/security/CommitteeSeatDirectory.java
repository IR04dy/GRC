package com.tss.grc.security;

/**
 * F2-177 — the third authorisation question this platform asks, after "what may you do?" and
 * "do you own this record?": <em>are you seated on this body?</em>
 *
 * <h3>Why this is not an {@link EntityOwnershipResolver}</h3>
 * <p>F2-52 spent six slices draining role guards and finished with three names it could not remove by
 * any of the three routes it had — {@code AUDIT_COMMITTEE_CHAIR}, {@code AUDIT_COMMITTEE_MEMBER},
 * {@code AUDIT_DIRECTOR}. They could not be <b>seated</b> as roles (a tenant's audit committee is a
 * roster, and a role would admit every holder to every committee's business), could not be
 * <b>dropped</b> without discarding the only record of who the endpoint was built for, and could not
 * be <b>converted</b> to a module permission without handing the same access to everyone with the
 * module's read.
 *
 * <p>The obvious fourth route was F2-159's entity ownership, and it does not fit: the record being
 * guarded is <em>not</em> the committee. It is a risk-control-finding pivot, or a three-lines
 * overview — an artefact the committee <em>reviews</em>. There is no owner column to consult, and
 * {@code hasPermission(#id, 'X', 'OWNER')} would be asking about the wrong object. {@code F2-52}'s own
 * register entry says so in as many words: <i>"a {@code CommitteeSeatResolver} is not an
 * {@link EntityOwnershipResolver} because the record being guarded is not the committee"</i>. This
 * interface is that sentence made into a type.
 *
 * <h3>The expression</h3>
 * <pre>
 * &#64;PreAuthorize("hasAnyRole('AUDITOR','INTERNAL_AUDITOR') or hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')")
 * </pre>
 * <p>{@code targetId} is the committee <b>type</b> ({@code admin.committees.committee_type}), not an id:
 * a guard is written once and every tenant has its own audit committee with its own primary key. The
 * type is the only stable way to name "the audit committee" across tenants, and it is a closed
 * vocabulary — 17 values measured in dev.
 *
 * <h3>What it is deliberately NOT</h3>
 * <p>There is one relation, {@code SEAT}, and it means "holds a current seat on a committee of this
 * type in the caller's own organisation". There is no {@code CHAIR} relation, even though
 * {@link com.tss.grc.module.admin.committee.service.CommitteeSeatOffice} could answer it and it would
 * be four lines — because no guard needs it today. F2-159 is the reason that restraint is written down:
 * the four-argument {@code hasPermission} overload sat in this codebase for months implemented as a
 * <em>decoy</em> that discarded its id, and a relation nobody consults is exactly the kind of thing
 * that rots into one. Add {@code CHAIR} in the commit that first guards on it.
 */
public interface CommitteeSeatDirectory {

    /** As written in the guards: {@code hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')}. */
    String TARGET_TYPE = "COMMITTEE";

    /** The one relation implemented. See the class javadoc for why there is not a second. */
    String SEAT_RELATION = "SEAT";

    /**
     * @param committeeType {@code admin.committees.committee_type}, e.g. {@code AUDIT_COMMITTEE}.
     *                      Compared case-insensitively; a value matching no committee is {@code false}.
     * @param userId        the authenticated caller, from {@link GrcUserDetails}. Never from a request body.
     * @param organisationId the caller's own tenant, from the token — so the query cannot be steered
     *                       across tenants by the guard's literal.
     * @return {@code true} only when a current, non-ended seat on an ACTIVE committee of that type in
     *         that organisation names this user. Must never throw: this runs as one leg of an
     *         {@code or}, and a leg that throws takes the whole expression down with it — which
     *         surfaces as a 500 on an endpoint the caller may legitimately reach by role.
     */
    boolean holdsActiveSeat(String committeeType, Long userId, Long organisationId);
}
