package com.tss.grc.module.iam.ldap;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LdapRoleMapper} (Open Issue #009).
 */
class LdapRoleMapperTest {

    @Test
    void disabled_by_default_returns_empty_set() {
        LdapRoleMapper m = new LdapRoleMapper();
        // enabled = false out of the box
        Set<String> roles = m.mapRoles(List.of("CN=Admins,DC=corp,DC=sa"));
        assertThat(roles).isEmpty();
        assertThat(m.isActive()).isFalse();
    }

    @Test
    void enabled_with_mapping_resolves_known_groups() {
        LdapRoleMapper m = configured();
        Set<String> roles = m.mapRoles(List.of(
            "CN=GRC_PlatformAdmins,OU=Groups,DC=corp,DC=tss-bank,DC=sa",
            "CN=Some_Other_Group,OU=Groups,DC=corp,DC=tss-bank,DC=sa"));
        assertThat(roles).containsExactlyInAnyOrder("ROLE_PLATFORM_ADMIN");
    }

    @Test
    void empty_memberOf_falls_back_to_default_role_when_present() {
        LdapRoleMapper m = configured();
        m.setDefaultRole("ROLE_VIEWER");
        Set<String> roles = m.mapRoles(List.of());
        assertThat(roles).containsExactly("ROLE_VIEWER");
    }

    @Test
    void no_match_no_default_returns_empty_set() {
        LdapRoleMapper m = configured();
        // default-role intentionally null
        Set<String> roles = m.mapRoles(List.of("CN=Unmapped,DC=corp,DC=sa"));
        assertThat(roles).isEmpty();
    }

    @Test
    void case_insensitive_matches_AD_style_DN() {
        LdapRoleMapper m = configured();
        // AD usually returns DNs in mixed case; the mapping config might use the lower-case form
        Set<String> roles = m.mapRoles(List.of(
            "cn=grc_riskmanagers,ou=groups,dc=corp,dc=tss-bank,dc=sa"));
        assertThat(roles).containsExactly("ROLE_RISK_MANAGER");
    }

    @Test
    void case_sensitive_mode_does_not_match_mixed_case() {
        LdapRoleMapper m = configured();
        m.setCaseInsensitive(false);
        Set<String> roles = m.mapRoles(List.of(
            "cn=grc_riskmanagers,ou=groups,dc=corp,dc=tss-bank,dc=sa"));
        // Mixed-case mapping key is no longer matched.
        assertThat(roles).isEmpty();
    }

    @Test
    void multi_group_returns_multiple_roles() {
        LdapRoleMapper m = configured();
        Set<String> roles = m.mapRoles(List.of(
            "CN=GRC_PlatformAdmins,OU=Groups,DC=corp,DC=tss-bank,DC=sa",
            "CN=GRC_Auditors,OU=Groups,DC=corp,DC=tss-bank,DC=sa"));
        assertThat(roles).containsExactlyInAnyOrder("ROLE_PLATFORM_ADMIN", "ROLE_AUDITOR");
    }

    @Test
    void null_memberOf_is_safe() {
        LdapRoleMapper m = configured();
        assertThat(m.mapRoles(null)).isEmpty();
    }

    private static LdapRoleMapper configured() {
        LdapRoleMapper m = new LdapRoleMapper();
        m.setEnabled(true);
        Map<String, String> map = new LinkedHashMap<>();
        map.put("CN=GRC_PlatformAdmins,OU=Groups,DC=corp,DC=tss-bank,DC=sa", "ROLE_PLATFORM_ADMIN");
        map.put("CN=GRC_RiskManagers,OU=Groups,DC=corp,DC=tss-bank,DC=sa",  "ROLE_RISK_MANAGER");
        map.put("CN=GRC_Auditors,OU=Groups,DC=corp,DC=tss-bank,DC=sa",      "ROLE_AUDITOR");
        m.setRoleMapping(map);
        return m;
    }
}
