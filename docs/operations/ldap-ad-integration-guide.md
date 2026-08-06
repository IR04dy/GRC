# LDAP / Active Directory integration guide
> **HISTORIC — point-in-time snapshot (all files 2026-05-24). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


> Closes Open Issue **#009** — "LDAP/AD integration configuration" in
> `docs/roadmap/04-open-issues-register.md`.

The TSS GRC platform ships with `spring-boot-starter-data-ldap` as a
build-time dependency (declared `<optional>true</optional>` in
`tss-grc-backend/pom.xml`) so customers who want to wire their corporate
Active Directory or OpenLDAP can do so without a separate dependency
PR. This document is the per-customer configuration recipe.

> **Low-priority issue.** Most Saudi customers prefer SAML2 / OIDC over
> raw LDAP for new deployments. This guide is for installations that
> still mandate LDAP — typical for on-prem Saudi-bank rollouts where
> the AD has been the canonical identity source for a decade.

## 1. When to use this guide

| Scenario                                            | Use this? |
|-----------------------------------------------------|-----------|
| Saudi bank / insurer with on-prem AD                | **Yes**   |
| Aramco / Aramco trading on-prem AD + Kerberos       | **Yes**   |
| New-build SaaS deployment with Azure AD             | **No** — use OIDC; LDAP is a regression here |
| Government entity using NAFATH / Absher SSO         | **No** — use the SSO/Nafath integration (V14) |
| Demo / pilot with seeded users only                 | **No** — keep the seeded DB-backed users |

## 2. Architecture choice

There are two integration patterns the platform supports:

### Pattern A — LDAP-bind authentication
The platform delegates the username/password check to the LDAP server.
The LDAP server returns the user's group memberships; the platform
maps groups → TSS GRC roles via `LdapRoleMapper`. **No password is
ever stored locally** — the `iam.users.password_hash` column stays
null for LDAP-sourced users.

Recommended for: most LDAP customers.

### Pattern B — LDAP for attribute sync only
The platform stays the password authority (BCrypt in `iam.users`) but
periodically syncs display name, email, department, and group
memberships from LDAP. Useful when the customer wants to disable LDAP
on weekends or maintain offline-login capability.

Recommended for: customers with intermittent LDAP availability or
hybrid-cloud deployments where the LDAP isn't always reachable.

Both patterns share the same `LdapRoleMapper` for group → role
resolution. The rest of this document focuses on Pattern A.

## 3. Per-customer configuration recipe

Add to `application-prod.yml` (override per customer with an
external `application-prod-<customer>.yml`):

```yaml
spring:
  ldap:
    urls: ldaps://ad.corp.tss-bank.sa:636
    base: DC=corp,DC=tss-bank,DC=sa
    username: CN=grc-service,OU=ServiceAccounts,DC=corp,DC=tss-bank,DC=sa
    password: ${LDAP_BIND_PASSWORD}
    # Pool tuning — adjust based on directory size.
    pool:
      maxTotal: 8
      maxIdle: 4
      minIdle: 1
      validationQuery: "(objectClass=*)"

grc:
  iam:
    ldap:
      enabled: true
      case-insensitive: true                 # AD is case-insensitive by default
      default-role: ROLE_VIEWER              # users in no GRC_* group get read-only
      role-mapping:
        # Group DN → TSS GRC role
        "[CN=GRC_PlatformAdmins,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":   ROLE_PLATFORM_ADMIN
        "[CN=GRC_RiskManagers,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":     ROLE_RISK_MANAGER
        "[CN=GRC_Auditors,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":         ROLE_AUDITOR
        "[CN=GRC_DPOs,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":             ROLE_DPO
        "[CN=GRC_CISOs,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":            ROLE_CISO
        "[CN=GRC_ComplianceOfficers,OU=Groups,DC=corp,DC=tss-bank,DC=sa]": ROLE_COMPLIANCE_OFFICER
        "[CN=GRC_BCMManagers,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":      ROLE_BCM_MANAGER
        "[CN=GRC_Privacy,OU=Groups,DC=corp,DC=tss-bank,DC=sa]":          ROLE_PRIVACY_ANALYST
```

The map keys are wrapped in `[...]` brackets in YAML so the
embedded `=` characters don't confuse the parser.

## 4. Attribute mapping

| LDAP attribute (AD)   | TSS GRC field             | Notes |
|-----------------------|---------------------------|-------|
| `sAMAccountName`      | `iam.users.username`      | The login name |
| `userPrincipalName`   | `iam.users.username`      | Alternate (`user@corp.tss-bank.sa`) |
| `cn`                  | `iam.users.full_name_en`  | Display name (English) |
| `givenName` + `sn`    | `iam.users.full_name_en`  | Fallback if `cn` is unstructured |
| `mail`                | `iam.users.email`         | Required — used for notifications |
| `telephoneNumber`     | `iam.users.phone`         | Optional |
| `mobile`              | `iam.users.phone_mobile`  | Optional |
| `department`          | `iam.users.department_id` | Resolved through department lookup |
| `title`               | `iam.users.job_title_en`  | English job title |
| `memberOf`            | (mapped to roles)         | Group DNs → roles via `LdapRoleMapper` |
| `userAccountControl`  | `iam.users.is_active`     | Bit 0x2 cleared = enabled |

For Arabic names, AD does not have a standard attribute. Typical
Saudi-bank ADs use one of these conventions:

- `displayName` populated with `<English Name> | <Arabic Name>` — split
  by `|` on sync.
- Custom attribute `tsbArabicName` — declare in the AD schema, sync
  directly into `iam.users.full_name_ar`.

If the AD has no Arabic attribute, leave `full_name_ar` empty — the
platform's RTL UI falls back to the English name with a `dir="auto"`
hint that handles bidi correctly.

## 5. User search filters

```ldif
# Look up a user by login
(&(objectClass=user)(sAMAccountName={0})(!(userAccountControl:1.2.840.113556.1.4.803:=2)))

# The !(userAccountControl:...:=2) clause excludes disabled accounts,
# which is critical — without it a disabled user can still bind as long
# as their password is valid.
```

```ldif
# List users in a group (for "what GRC role does User X have?" admin view)
(&(objectClass=user)(memberOf=CN=GRC_PlatformAdmins,OU=Groups,DC=corp,DC=tss-bank,DC=sa))

# Recursive group membership — covers nested groups (AD-only)
(&(objectClass=user)(memberOf:1.2.840.113556.1.4.1941:=CN=GRC_All,OU=Groups,DC=corp,DC=tss-bank,DC=sa))
```

## 6. Transport security

| Network segment                    | Acceptable transport |
|------------------------------------|----------------------|
| Same VLAN (corporate, trusted)     | `ldap://` port 389 with StartTLS upgrade |
| Cross-segment (DMZ → AD)           | `ldaps://` port 636 — **required** |
| Cross-WAN (multi-region)           | `ldaps://` 636 + IPSec tunnel |

The Spring LDAP starter auto-detects `ldaps://` and uses the JVM's
default truststore. Add the AD CA certificate to `cacerts` (or a
custom truststore via `-Djavax.net.ssl.trustStore=...`) on the GRC
backend host.

## 7. Saudi-specific deployment notes

- **Microsoft Active Directory** dominates Saudi banks. Most are on
  Windows Server 2019 or 2022 with functional levels at 2016+.
- **Azure AD** is increasingly common for newer deployments and
  hybrid setups. For Azure AD, prefer the OIDC integration over LDAP —
  this guide is for the LDAP fallback path.
- **Aramco** uses on-prem AD federated with Kerberos for SSO. The
  platform's LDAP-bind path doesn't use Kerberos; for Kerberos SSO,
  see the SPNEGO / NAFATH integration documented separately.
- **Saudi data-residency.** The LDAP traffic stays inside the corporate
  network and never leaves KSA. No PDPL Article 29 (cross-border
  transfer) concerns arise from LDAP itself; the residency concern is
  whether the GRC backend's hosting region is inside KSA.

## 8. Troubleshooting

| Symptom                                     | Likely cause                          | Fix |
|---------------------------------------------|---------------------------------------|-----|
| `LDAP: error code 49 - 80090308` on every login | Bind credentials wrong               | Check `LDAP_BIND_PASSWORD`, confirm service account isn't locked out |
| `LDAP: error code 49 - 80090346` on some logins | Account is in "Protected Users" group | Move the user out, or switch to Kerberos auth |
| User has no role after successful bind      | `memberOf` DN case mismatch           | Set `grc.iam.ldap.case-insensitive: true` (default) |
| Group memberships incomplete                | Nested groups not flattened           | Use the AD-specific recursive filter `:1.2.840.113556.1.4.1941:=` (§5) |
| Large directories time out on user search   | No paged search                       | Set `spring.ldap.referral: follow` and tune pool max-total upward |
| "Object class not found"                    | Custom AD schema not extended         | Confirm with the AD admin that schema extensions are installed |

## 9. API surface in this platform

The `LdapRoleMapper` component (`module/iam/ldap/LdapRoleMapper.java`)
is the integration point:

```java
@Autowired
private LdapRoleMapper roleMapper;

List<String> memberOf = ldapPrincipal.getAttribute("memberOf");
Set<String>  grcRoles = roleMapper.mapRoles(memberOf);
// → grcRoles populates the Spring Security GrantedAuthority list
//   and the iam.users.role_assignment table on sync
```

When `grc.iam.ldap.enabled=false` (the default), `mapRoles()` returns
an empty set — the component is safe to autowire regardless of
deployment posture. The full LDAP-bind wiring (custom
`AuthenticationProvider`, ActiveDirectoryLdapAuthenticationProvider,
or LdapTemplate) is per-customer and lives outside this guide.

## 10. Related artefacts

- `tss-grc-backend/pom.xml` — `spring-boot-starter-data-ldap` (optional)
- `tss-grc-backend/src/main/java/com/tss/grc/module/iam/ldap/LdapRoleMapper.java`
  — group → role resolver
- `tss-grc-backend/src/test/java/com/tss/grc/module/iam/ldap/LdapRoleMapperTest.java`
  — unit-test coverage (case sensitivity, nested fall-backs, default
  role)
- `docs/roadmap/04-open-issues-register.md` — Open Issue #009 entry
- `tss-grc-backend/src/main/resources/db/migration/sqlserver/V14__sso_nafath.sql`
  — the Nafath / NCA SSO alternative integration
