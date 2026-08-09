package com.tss.grc.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Custom {@link PermissionEvaluator} enabling module-scoped permission checks.
 *
 * <h3>Usage in controllers</h3>
 * <pre>
 * &#64;PreAuthorize("hasPermission('RISK', 'READ')")
 * &#64;PreAuthorize("hasPermission('RISK', 'WRITE')")
 * &#64;PreAuthorize("hasPermission('CYBER', 'DELETE')")
 * </pre>
 *
 * <h3>Authority naming convention</h3>
 * <p>Granted authorities follow the pattern {@code MODULE_ACTION}, e.g.:
 * <ul>
 *   <li>{@code RISK_READ}, {@code RISK_WRITE}, {@code RISK_DELETE}</li>
 *   <li>{@code CYBER_READ}, {@code CYBER_WRITE}</li>
 *   <li>{@code ADMIN_READ}, {@code ADMIN_WRITE}</li>
 * </ul>
 * <p>Platform admins carry {@code ROLE_PLATFORM_ADMIN} which grants all permissions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModulePermissionEvaluator implements PermissionEvaluator {

    /** The entity relation: <em>is the caller the owner of this record?</em> */
    static final String OWNER_RELATION = "OWNER";

    private final EntityOwnershipRegistry ownership;
    private final CommitteeSeatDirectory seats;

    @Override
    public boolean hasPermission(Authentication authentication,
                                 Object targetDomainObject,
                                 Object permission) {
        if (authentication == null || targetDomainObject == null) return false;

        String module = targetDomainObject.toString().toUpperCase();
        String action = permission.toString().toUpperCase();

        return hasPlatformAdminRole(authentication)
            || hasAuthority(authentication, module + "_" + action);
    }

    /**
     * F2-159 / ADR-017 D-14 — the entity-instance check: <em>is the caller the owner of THIS record?</em>
     *
     * <pre>
     * &#64;PreAuthorize("hasAnyRole('RISK_MANAGER','CISO') or hasPermission(#externalId,'ML_MODEL','OWNER')")
     * </pre>
     *
     * <p><b>⚠ What this method used to be, because the replacement only makes sense against it.</b> It was
     * <pre>
     *   // Entity-instance permission checks not used — delegate to module-level check
     *   return hasPermission(authentication, targetType, permission);
     * </pre>
     * which <b>silently discarded {@code targetId}</b>. A developer writing the idiomatic Spring ownership
     * guard {@code hasPermission(#id, 'RISK', 'READ')} — which reads as "may I read THIS risk?" — got
     * "may I read risks?", a cross-record hole that compiles and survives review. Measured 2026-08-07: no
     * guard in the platform used the four-argument form, so nothing was exposed; the trap was unsprung,
     * not absent. It was also the exact mechanism four register items were blocked on, so the gap they
     * were waiting for was worse than empty — it was a decoy that would have answered the wrong question
     * the moment someone used it.
     *
     * <p><b>Fails closed by construction.</b> An unknown {@code permission} relation or an unregistered
     * {@code targetType} returns {@code false} and logs; it never falls back to the module-level check.
     * That is the whole difference: a mistake here costs a 403, not silent breadth.
     *
     * <p>{@code PLATFORM_ADMIN} still bypasses, matching the other overload — the platform administrator
     * is not expected to own every record in order to administer it.
     *
     * <h3>F2-177 — the second question this overload answers: <em>are you seated on this body?</em></h3>
     * <pre>
     * &#64;PreAuthorize("hasAnyRole('AUDITOR') or hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')")
     * </pre>
     * <p>Dispatch is on {@code targetType}, and the two branches are genuinely different questions rather
     * than two flavours of one: ownership resolves a <b>record id</b> to a person, while a seat resolves a
     * <b>committee type</b> to a roster. See {@link CommitteeSeatDirectory} for why a seat could not be
     * modelled as an {@link EntityOwnershipResolver}.
     *
     * <p>⚠ <b>The vocabulary stays closed in both branches.</b> An unknown {@code targetType} falls through
     * to the ownership registry, which refuses an unregistered type and logs; an unknown relation on a
     * known type refuses here. Nothing falls back to the module-level check — that fallback <em>was</em>
     * F2-159's defect, and it is the reason this method is worth being pedantic about: a mistake must cost
     * a 403, never silent breadth.
     */
    @Override
    public boolean hasPermission(Authentication authentication,
                                 Serializable targetId,
                                 String targetType,
                                 Object permission) {
        if (authentication == null || targetId == null || targetType == null || permission == null) {
            return false;
        }
        if (hasPlatformAdminRole(authentication)) return true;
        if (!(authentication.getPrincipal() instanceof GrcUserDetails me)) {
            return false;
        }

        String relation = permission.toString().toUpperCase();
        String type = targetType.toUpperCase();

        if (CommitteeSeatDirectory.TARGET_TYPE.equals(type)) {
            if (!CommitteeSeatDirectory.SEAT_RELATION.equals(relation)) {
                // No CHAIR relation exists yet, on purpose — CommitteeSeatDirectory says why. A guard
                // asking for one must not silently degrade to "any seat".
                log.warn("[F2-177] unsupported committee relation '{}' — refusing. The only relation "
                    + "implemented is '{}'.", relation, CommitteeSeatDirectory.SEAT_RELATION);
                return false;
            }
            return seats.holdsActiveSeat(targetId.toString(), me.getUserId(), me.getOrganisationId());
        }

        if (!OWNER_RELATION.equals(relation)) {
            // Anything else is a guard bug, and answering the module-level question in its place is
            // precisely the defect this overload used to have.
            log.warn("[F2-159] unsupported entity relation '{}' for targetType '{}' — refusing. "
                + "The relations implemented are '{}' and '{}' (on targetType '{}').",
                relation, targetType, OWNER_RELATION, CommitteeSeatDirectory.SEAT_RELATION,
                CommitteeSeatDirectory.TARGET_TYPE);
            return false;
        }
        return ownership.isOwnedBy(targetType, targetId, me.getUserId(), me.getOrganisationId());
    }

    private boolean hasPlatformAdminRole(Authentication auth) {
        return hasAuthority(auth, "ROLE_PLATFORM_ADMIN");
    }

    private boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
