package com.tss.grc.common.security;

import com.tss.grc.security.GrcUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Centralised resolution of the current actor (authenticated user) and tenant
 * (organisation) for service-layer code.
 *
 * <p>Historically each module declared its own private {@code currentUserId()}
 * helper that copy-pasted the same security-context lookup. This duplication was
 * flagged by the audit (QUAL-DUP-003, and the per-module instances QUAL-ESG-002,
 * QUAL-FA-001, QUAL-RC-004, QUAL-TI-003). Consolidating the canonical lookup here
 * means principal-type changes and null-handling policy live in exactly one place;
 * delegating call sites cannot drift apart over time.
 *
 * <p>This class is intentionally final with no instances — it is a pure static
 * utility.
 */
public final class CurrentActor {

    private CurrentActor() {}

    /**
     * Resolves the current user's id from the Spring Security context.
     *
     * @return the {@link GrcUserDetails#getUserId() user id} of the authenticated
     *         principal, or {@code null} when there is no authentication, the
     *         authentication is not authenticated, or the principal is not a
     *         {@link GrcUserDetails}.
     */
    public static Long userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof GrcUserDetails u)) {
            return null;
        }
        return u.getUserId();
    }

    /**
     * Resolves the current user's username (login name) from the Spring Security
     * context — useful for human-readable "completed by" / "invoked by" columns
     * where a raw user id would be opaque.
     *
     * @return the authenticated principal's username, or {@code null} when there
     *         is no authentication or the principal is not a {@link GrcUserDetails}.
     */
    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof GrcUserDetails u)) {
            return null;
        }
        return u.getUsername();
    }

    /**
     * Whether the current authenticated principal holds the platform-super-admin
     * authority ({@code ROLE_PLATFORM_ADMIN}). PLATFORM_ADMIN bypasses the
     * method-level {@code @PreAuthorize} guards in code and, by platform
     * convention, is excluded from segregation-of-duties self-approval blocks
     * and the nightly SoD scan — so service-layer SoD gates consult this to let
     * the super-admin operate a tenant single-handed.
     *
     * @return {@code true} when the principal carries {@code ROLE_PLATFORM_ADMIN};
     *         {@code false} when unauthenticated or the authority is absent.
     */
    public static boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_PLATFORM_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Resolves the current tenant (organisation) id, failing fast when no tenant
     * is bound to the request thread.
     *
     * @return the current organisation id
     * @throws IllegalStateException if no tenant is present in the
     *         {@link com.tss.grc.common.tenant.TenantContext}
     */
    public static Long organisationId() {
        return com.tss.grc.common.tenant.TenantContext.requireOrganisationId();
    }
}
