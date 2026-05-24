package com.tss.grc.module.iam.ldap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reusable LDAP/AD {@code memberOf}-to-role mapper (Open Issue #009).
 *
 * <p>Spring Boot's {@code spring-boot-starter-data-ldap} resolves a user's
 * group memberships via the {@code memberOf} (AD) or {@code member} (OpenLDAP)
 * attribute. This component takes that raw list of group DNs and maps them
 * to TSS GRC role strings using a per-tenant configuration map.
 *
 * <h3>Configuration shape</h3>
 * <pre>
 * grc:
 *   iam:
 *     ldap:
 *       enabled: false                     # off by default
 *       role-mapping:
 *         "[CN=GRC_PlatformAdmins,OU=Groups,DC=corp,DC=tss-bank,DC=sa]": ROLE_PLATFORM_ADMIN
 *         "[CN=GRC_RiskManagers,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":   ROLE_RISK_MANAGER
 *         "[CN=GRC_Auditors,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":       ROLE_AUDITOR
 *         "[CN=GRC_DPOs,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":           ROLE_DPO
 *         "[CN=GRC_CISOs,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":          ROLE_CISO
 *       default-role: ROLE_VIEWER          # applied when no group match
 *       case-insensitive: true             # AD is case-insensitive
 * </pre>
 *
 * <p>When {@code grc.iam.ldap.enabled} is {@code false} (the default), this
 * component still loads but is a no-op — callers that ask for roles get an
 * empty set. This lets the platform ship with the LDAP starter as a build-time
 * dependency without enabling it at runtime.
 *
 * <h3>Why a separate component (and not Spring Security's
 * {@code GrantedAuthoritiesMapper})?</h3>
 * The Spring Security mapper assumes a particular auth-flow shape and can't
 * easily be unit-tested in isolation. This class is a pure
 * {@code List<String> → Set<String>} function plus configuration loading —
 * trivially testable, swap-friendly, and reusable from both the LDAP
 * authentication provider and from a future SAML/OIDC group-claim mapper.
 *
 * <p>See {@code docs/operations/ldap-ad-integration-guide.md} for the
 * end-to-end LDAP setup recipe.
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "grc.iam.ldap")
public class LdapRoleMapper {

    /** Master switch — {@code false} by default. */
    private boolean enabled = false;

    /** Raw group DN → role string map. Case-sensitivity controlled by {@link #caseInsensitive}. */
    private Map<String, String> roleMapping = new LinkedHashMap<>();

    /** Role applied to users with no matching group. {@code null} = no default. */
    private String defaultRole;

    /** Active Directory is case-insensitive on DNs — keep this true unless using a case-sensitive directory. */
    private boolean caseInsensitive = true;

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Map a list of group DNs (from the {@code memberOf} attribute) to a set
     * of resolved role strings. Returns an empty set when:
     * <ul>
     *   <li>{@link #enabled} is {@code false} — i.e. LDAP isn't configured for this deployment</li>
     *   <li>{@code memberOf} is null or empty</li>
     *   <li>None of the supplied DNs appears in {@link #roleMapping} and no {@link #defaultRole} is set</li>
     * </ul>
     */
    public Set<String> mapRoles(List<String> memberOf) {
        if (!enabled) {
            log.debug("LdapRoleMapper disabled (grc.iam.ldap.enabled=false) — returning empty");
            return Set.of();
        }
        if (memberOf == null || memberOf.isEmpty()) {
            return defaultRole == null ? Set.of() : Set.of(defaultRole);
        }
        // Build the lookup map once per call; for tenants with N=8 mappings
        // this is < 1µs and avoids stateful field caching on a singleton.
        Map<String, String> lookup = caseInsensitive
            ? roleMapping.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue,
                    (a, b) -> a, LinkedHashMap::new))
            : roleMapping;

        Set<String> resolved = new LinkedHashSet<>();
        for (String dn : memberOf) {
            if (dn == null) continue;
            String key = caseInsensitive ? dn.toLowerCase(Locale.ROOT) : dn;
            String role = lookup.get(key);
            if (role != null) resolved.add(role);
        }
        if (resolved.isEmpty() && defaultRole != null) {
            resolved.add(defaultRole);
        }
        return resolved;
    }

    /** True when the component is configured to do real mapping work. */
    public boolean isActive() {
        return enabled;
    }

    // ── Configuration getters / setters (Spring Binder) ──────────────

    public boolean isEnabled()                       { return enabled; }
    public void setEnabled(boolean enabled)          { this.enabled = enabled; }
    public Map<String, String> getRoleMapping()      { return roleMapping; }
    public void setRoleMapping(Map<String, String> m) { this.roleMapping = m == null ? new LinkedHashMap<>() : m; }
    public String getDefaultRole()                   { return defaultRole; }
    public void setDefaultRole(String defaultRole)   { this.defaultRole = defaultRole; }
    public boolean isCaseInsensitive()               { return caseInsensitive; }
    public void setCaseInsensitive(boolean ci)       { this.caseInsensitive = ci; }
}
