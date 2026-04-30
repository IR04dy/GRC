package com.tss.grc.security;

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
@Component
public class ModulePermissionEvaluator implements PermissionEvaluator {

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

    @Override
    public boolean hasPermission(Authentication authentication,
                                 Serializable targetId,
                                 String targetType,
                                 Object permission) {
        // Entity-instance permission checks not used — delegate to module-level check
        return hasPermission(authentication, targetType, permission);
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
