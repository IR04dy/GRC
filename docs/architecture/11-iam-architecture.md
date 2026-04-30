# 11 - Identity and Access Management Architecture

## Purpose

This document defines the Identity and Access Management (IAM) architecture for the TSS GRC platform. It covers authentication, authorization, user provisioning, session management, and integration with enterprise directories. IAM is a cross-cutting concern enforced at every layer of the platform.

---

## 1. Authentication

### 1.1 Primary Authentication: Form-Based Login

**Decision**: Spring Security form-based authentication with server-side session management.

**Rationale**: The TSS GRC platform is a server-rendered Thymeleaf application. Form-based login with server-side sessions is the simplest, most secure approach for this architecture. It avoids the complexity of token-based flows for browser clients while providing full Spring Security integration out of the box.

**Implementation**:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", false)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );
        return http.build();
    }
}
```

### 1.2 Password Policy

All password policies are configurable via `admin.system_configurations` to accommodate varying organizational security requirements.

| Policy | Default | Configurable Range | Rationale |
|---|---|---|---|
| Minimum length | 12 | 8-128 | NIST SP 800-63B recommendation |
| Uppercase required | Yes | Yes/No | Regulatory common expectation |
| Lowercase required | Yes | Yes/No | Regulatory common expectation |
| Digit required | Yes | Yes/No | Regulatory common expectation |
| Special character required | Yes | Yes/No | Regulatory common expectation |
| Password expiry | 90 days | 30-365 days or disabled | NCA ECC requirement for periodic rotation |
| Password history | 12 | 1-24 | Prevents reuse of recent passwords |
| Minimum age | 1 day | 0-7 days | Prevents rapid cycling to circumvent history |

**Password Storage**: BCrypt with configurable work factor.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12); // 2^12 iterations, configurable via property
}
```

**Decision**: BCrypt over Argon2.

**Rationale**: BCrypt is battle-tested, natively supported by Spring Security, and sufficient for the threat model. Argon2 offers marginal improvement for password hashing but adds a native library dependency that complicates deployment on customer-managed Windows Server environments. The BCrypt work factor is configurable to increase over time as hardware improves.

### 1.3 Account Lockout

| Setting | Default | Configurable |
|---|---|---|
| Failed attempt threshold | 5 | 3-20 |
| Lockout duration | 30 minutes | 5 min - 24 hours or permanent |
| Lockout counter reset | After successful login | Configurable |

**Implementation**: Failed attempts tracked in `iam.users.failed_login_attempts` and `iam.users.locked_until`. An `AuthenticationFailureHandler` increments the counter; an `AuthenticationSuccessHandler` resets it.

### 1.4 Multi-Factor Authentication (MFA) Readiness

**Phase 1**: MFA is not implemented but the authentication pipeline is designed to support it.

**Future approach**: TOTP-based MFA (Google Authenticator, Microsoft Authenticator) as a second factor after password verification. The `iam.users` table includes `mfa_enabled` and `mfa_secret` columns reserved for this purpose.

---

## 2. Enterprise Directory Integration

### 2.1 LDAP/Active Directory

**Decision**: Spring LDAP for AD/LDAP integration with delegated authentication and optional attribute synchronization.

**Rationale**: Saudi government entities and large enterprises universally use Active Directory. LDAP integration is a hard requirement for on-premises deployments. Spring LDAP provides mature, well-tested AD integration within the Spring ecosystem.

**Authentication flow**:

```
User submits credentials
  → Spring Security AuthenticationManager
    → Try LDAP authentication first (if LDAP enabled for org)
      → Bind to AD with user credentials
      → On success: sync user attributes, create/update local user record
      → On failure: fall through to local authentication
    → Try local database authentication
      → On success: proceed
      → On failure: increment failed attempts, return error
```

**Configuration** (per organization):

```yaml
tss:
  ldap:
    enabled: false  # Per-org toggle
    url: ldap://dc.example.com:389
    base-dn: dc=example,dc=com
    user-search-base: ou=Users
    user-search-filter: (sAMAccountName={0})
    group-search-base: ou=Groups
    bind-dn: cn=svc-tss,ou=ServiceAccounts,dc=example,dc=com
    bind-password: ${LDAP_BIND_PASSWORD}  # From environment variable
```

**Attribute mapping**:

| LDAP Attribute | TSS Field | Notes |
|---|---|---|
| sAMAccountName | username | Primary identifier |
| mail | email | Required |
| givenName | first_name | Display name |
| sn | last_name | Display name |
| displayName | display_name_en | Fallback for name |
| department | department | Mapped to TSS department if match found |
| memberOf | roles | Optional: auto-assign roles based on AD group membership |

**Decision**: LDAP users maintain a local shadow record in `iam.users` with `auth_source = 'LDAP'`.

**Rationale**: This allows role assignment, audit logging, and permission management to work identically for LDAP and local users. If LDAP is unavailable, the user simply cannot authenticate, but their profile, role assignments, and activity history remain intact.

### 2.2 SSO Readiness (Phase 2+)

**Decision**: SAML 2.0 and OIDC support designed but not implemented in Phase 1.

**Preparation**:
- `auth_source` field on `iam.users` supports values: `LOCAL`, `LDAP`, `SAML`, `OIDC`
- Spring Security's SAML2 and OAuth2 Login modules will be added when needed
- No architectural changes required; it is an additive configuration

---

## 3. Authorization Model

### 3.1 RBAC Architecture

**Decision**: Role-Based Access Control with three-level structure: Users, Roles, Permissions.

**Rationale**: RBAC is the standard authorization model for enterprise GRC platforms. It maps directly to organizational hierarchies and regulatory requirements (segregation of duties, least privilege). Fine-grained permissions allow precise control without the complexity of ABAC.

```
User
  └── assigned to → Role(s)
                      └── grants → Permission(s)
                                    └── defines → resource:action
                                                    └── optionally scoped to → org/BU
```

### 3.2 Permission Model

Permissions follow a `module:resource:action` convention:

| Permission | Description |
|---|---|
| `erm:risk:read` | View risks |
| `erm:risk:create` | Create new risks |
| `erm:risk:update` | Edit existing risks |
| `erm:risk:delete` | Delete risks |
| `erm:risk:approve` | Approve/accept risks |
| `erm:risk:export` | Export risk data |
| `bcm:bia:read` | View business impact analyses |
| `compliance:assessment:submit` | Submit compliance assessments |
| `admin:user:create` | Create users |
| `admin:role:assign` | Assign roles to users |
| `admin:config:update` | Modify system configuration |

**Action types**: `read`, `create`, `update`, `delete`, `approve`, `submit`, `export`, `import`, `assign`, `configure`.

### 3.3 Role Design

Roles are scoped to modules and can be combined for users who span multiple areas.

**System roles** (predefined, not deletable):

| Role | Module Scope | Key Permissions | Typical Assignment |
|---|---|---|---|
| `SUPER_ADMIN` | All | All permissions | Platform administrator |
| `ORG_ADMIN` | Admin | User/role/config management | Organization administrator |
| `ERM_MANAGER` | ERM | Full ERM CRUD + approve | Risk Manager |
| `ERM_ANALYST` | ERM | ERM read + create + update | Risk Analyst |
| `ERM_VIEWER` | ERM | ERM read only | Business Unit Manager |
| `COMPLIANCE_MANAGER` | Compliance | Full compliance CRUD + approve | Compliance Officer |
| `COMPLIANCE_ASSESSOR` | Compliance | Assessment read + create + submit | Compliance Analyst |
| `BCM_MANAGER` | BCM | Full BCM CRUD + approve | BCM Manager |
| `AUDIT_MANAGER` | Audit | Full audit CRUD + approve | Internal Audit Head |
| `AUDITOR` | Audit | Audit read + create + update | Internal Auditor |
| `CISO` | All security modules | Read across all + dashboards | CISO |
| `CRO` | ERM + Compliance | Read across all + dashboards | Chief Risk Officer |

**Custom roles**: Organizations can create custom roles with any combination of permissions.

### 3.4 Organization/BU Scoping

**Decision**: Permissions can be scoped to a specific Business Unit, enabling data-level access control.

**Implementation**:

```java
// User-role assignment includes optional BU scope
@Entity
@Table(name = "user_roles", schema = "iam")
public class UserRole {
    @Id
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Role role;

    @ManyToOne
    private BusinessUnit businessUnit; // null = all BUs (org-wide)

    private LocalDateTime assignedAt;
    private Long assignedBy;
}
```

**Scoping rules**:
- `businessUnit = null`: Role applies across the entire organization
- `businessUnit = specific BU`: Role applies only to data within that BU
- Users can have multiple role assignments with different BU scopes
- Cross-BU roles (CISO, CRO) are assigned with `businessUnit = null`

### 3.5 Module Authorization

**Decision**: Users only see navigation items and can only access modules their roles permit AND that are licensed for the organization.

**Implementation**:

```java
// Thymeleaf navigation rendering
// th:if="${@moduleAccess.canAccess('erm')}"

@Component("moduleAccess")
public class ModuleAccessChecker {
    public boolean canAccess(String moduleCode) {
        TenantContext tenant = TenantContextHolder.get();
        UserContext user = SecurityContextHelper.getCurrentUser();

        // Check module is licensed for this organization
        if (!tenant.isModuleLicensed(moduleCode)) return false;

        // Check user has at least one permission in this module
        return user.hasAnyPermissionInModule(moduleCode);
    }
}
```

---

## 4. Permission Checking

### 4.1 Method-Level Security

**Decision**: `@PreAuthorize` annotations on service methods for fine-grained permission checks.

**Rationale**: Method-level security ensures permissions are enforced regardless of the entry point (web controller, API endpoint, scheduled task). It is declarative, testable, and integrated with Spring Security's expression evaluation.

```java
@Service
public class RiskService {

    @PreAuthorize("hasPermission('erm:risk:read')")
    public Page<RiskDto> findAll(Pageable pageable) { ... }

    @PreAuthorize("hasPermission('erm:risk:create')")
    public RiskDto create(CreateRiskCommand command) { ... }

    @PreAuthorize("hasPermission('erm:risk:approve')")
    public void approveRisk(Long riskId, ApprovalCommand command) { ... }
}
```

### 4.2 Custom Permission Evaluator

```java
@Component
public class TssPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        String permissionCode = (String) permission;
        TssUserDetails user = (TssUserDetails) auth.getPrincipal();

        // Check if user has the permission (direct or via role)
        // Also enforces BU scoping if target has a businessUnitId
        return user.getEffectivePermissions().contains(permissionCode);
    }
}
```

### 4.3 URL-Level Security

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/login", "/css/**", "/js/**", "/images/**").permitAll()
        .requestMatchers("/actuator/health").permitAll()
        .requestMatchers("/admin/**").hasPermission("admin:access")
        .requestMatchers("/api/v1/**").authenticated()
        .anyRequest().authenticated()
    );
```

### 4.4 Permission Caching

**Decision**: User permissions are loaded at authentication time and cached in the session. Cache is invalidated on role/permission change.

**Rationale**: Permission checks happen on every request. Hitting the database each time is unacceptable. Session-based caching ensures fast lookups. When an admin changes a role's permissions, affected users' sessions are flagged for permission reload on next request.

---

## 5. Session Management

### 5.1 Session Configuration

| Setting | Value | Rationale |
|---|---|---|
| Session store | Server-side (HttpSession) | Simple, secure, no client-side token exposure |
| Idle timeout | 30 minutes (configurable) | Balance between security and usability for GRC analysts |
| Absolute timeout | 12 hours (configurable) | Prevent indefinite sessions even with activity |
| Concurrent sessions | 1 per user (configurable) | Prevent credential sharing; configurable per org |
| Session fixation | Migrate session on login | Spring Security default; prevents fixation attacks |

### 5.2 Secure Cookie Configuration

```java
@Bean
public CookieSerializer cookieSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName("TSSSESSIONID");
    serializer.setUseHttpOnlyCookie(true);    // Prevent XSS access to session cookie
    serializer.setUseSecureCookie(true);       // HTTPS only
    serializer.setSameSite("Strict");          // Prevent CSRF via cross-site requests
    serializer.setCookiePath("/");
    return serializer;
}
```

### 5.3 Concurrent Session Control

```java
http
    .sessionManagement(session -> session
        .maximumSessions(1)                    // One session per user
        .maxSessionsPreventsLogin(false)        // New login expires old session
        .expiredUrl("/login?expired=true")
        .sessionFixation().migrateSession()
    );
```

**Decision**: New login expires old session rather than blocking new login.

**Rationale**: Blocking new logins causes support burden when sessions are not properly closed (browser crash, network loss). Expiring the old session is the better user experience and standard enterprise behavior.

---

## 6. API Security

### 6.1 Web Application (Session-Based)

All Thymeleaf-rendered pages use standard session-based authentication as described above.

### 6.2 REST API (Token Readiness)

**Phase 1**: REST API endpoints (`/api/v1/**`) accept session-based authentication (same session cookie used by the web UI).

**Phase 2**: JWT-based authentication for external API consumers.

**Preparation**:
- API endpoints are separated under `/api/v1/` prefix
- `SecurityFilterChain` is designed to support a second filter chain for API paths
- JWT token generation and validation infrastructure will use Spring Security OAuth2 Resource Server

```java
// Phase 2 addition - separate filter chain for API
@Bean
@Order(1)
public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/api/**")
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
}
```

---

## 7. Database Schema

### 7.1 Core IAM Tables

All IAM tables reside in the `iam` schema.

```sql
-- Users table
CREATE TABLE iam.users (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES admin.organizations(id),
    username        NVARCHAR(100) NOT NULL,
    email           NVARCHAR(255) NOT NULL,
    password_hash   NVARCHAR(255),         -- Null for LDAP-only users
    first_name      NVARCHAR(100),
    last_name       NVARCHAR(100),
    display_name_ar NVARCHAR(200),
    display_name_en NVARCHAR(200),
    auth_source     NVARCHAR(20) NOT NULL DEFAULT 'LOCAL',  -- LOCAL, LDAP, SAML, OIDC
    status          NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, LOCKED
    mfa_enabled     BIT NOT NULL DEFAULT 0,
    mfa_secret      NVARCHAR(255),
    password_changed_at  DATETIME2,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until    DATETIME2,
    last_login_at   DATETIME2,
    preferred_language NVARCHAR(5) DEFAULT 'ar',  -- ar, en
    business_unit_id BIGINT REFERENCES admin.business_units(id),
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    created_by      BIGINT,
    updated_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_by      BIGINT,
    rowversion      ROWVERSION,
    CONSTRAINT uq_users_username_org UNIQUE (org_id, username),
    CONSTRAINT uq_users_email_org UNIQUE (org_id, email)
);

-- Roles table
CREATE TABLE iam.roles (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES admin.organizations(id),
    code            NVARCHAR(50) NOT NULL,
    name_ar         NVARCHAR(200) NOT NULL,
    name_en         NVARCHAR(200) NOT NULL,
    description_ar  NVARCHAR(1000),
    description_en  NVARCHAR(1000),
    module_code     NVARCHAR(50),           -- Scoped to module, null = cross-module
    is_system       BIT NOT NULL DEFAULT 0, -- System roles cannot be deleted
    is_active       BIT NOT NULL DEFAULT 1,
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    created_by      BIGINT,
    updated_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    updated_by      BIGINT,
    rowversion      ROWVERSION,
    CONSTRAINT uq_roles_code_org UNIQUE (org_id, code)
);

-- Permissions table
CREATE TABLE iam.permissions (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    code            NVARCHAR(100) NOT NULL UNIQUE,  -- e.g., erm:risk:read
    module_code     NVARCHAR(50) NOT NULL,
    resource        NVARCHAR(50) NOT NULL,
    action          NVARCHAR(50) NOT NULL,
    name_ar         NVARCHAR(200) NOT NULL,
    name_en         NVARCHAR(200) NOT NULL,
    description_en  NVARCHAR(500),
    description_ar  NVARCHAR(500)
);

-- User-Role assignments
CREATE TABLE iam.user_roles (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES iam.users(id),
    role_id         BIGINT NOT NULL REFERENCES iam.roles(id),
    business_unit_id BIGINT REFERENCES admin.business_units(id),  -- Null = org-wide
    assigned_at     DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    assigned_by     BIGINT NOT NULL REFERENCES iam.users(id),
    expires_at      DATETIME2,              -- Optional role expiry
    CONSTRAINT uq_user_role_bu UNIQUE (user_id, role_id, business_unit_id)
);

-- Role-Permission assignments
CREATE TABLE iam.role_permissions (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    role_id         BIGINT NOT NULL REFERENCES iam.roles(id),
    permission_id   BIGINT NOT NULL REFERENCES iam.permissions(id),
    CONSTRAINT uq_role_permission UNIQUE (role_id, permission_id)
);

-- Password history
CREATE TABLE iam.password_history (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES iam.users(id),
    password_hash   NVARCHAR(255) NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT GETUTCDATE()
);

-- Indexes
CREATE INDEX ix_users_org_status ON iam.users(org_id, status);
CREATE INDEX ix_user_roles_user ON iam.user_roles(user_id);
CREATE INDEX ix_user_roles_role ON iam.user_roles(role_id);
CREATE INDEX ix_role_permissions_role ON iam.role_permissions(role_id);
CREATE INDEX ix_password_history_user ON iam.password_history(user_id);
```

---

## 8. User Provisioning

### 8.1 Admin UI

The platform provides an administrative interface for user management:

- **User list**: paginated, filterable by status/role/BU, searchable by name/email
- **User creation**: form with validation, role assignment, BU assignment
- **User editing**: profile updates, role changes, status changes (activate/deactivate/lock)
- **Approval workflow**: user creation and role assignment require approval by a second admin (configurable)
- **Bulk import readiness**: CSV/Excel import endpoint for provisioning multiple users

### 8.2 User Lifecycle

```
Created (ACTIVE)
  → Deactivated (INACTIVE) — admin action, preserves history
  → Locked (LOCKED) — automatic on failed attempts, or admin action
  → Reactivated (ACTIVE) — admin action from INACTIVE or LOCKED

Locked (LOCKED)
  → Auto-unlock after lockout duration
  → Manual unlock by admin
```

### 8.3 LDAP Synchronization

When LDAP is enabled:
- Users are created in TSS on first LDAP login (just-in-time provisioning)
- User attributes (name, email, department) are synchronized on each login
- Roles are managed in TSS (not automatically mapped from AD groups in Phase 1)
- Disabled AD accounts cannot authenticate; local status is updated on next sync attempt

---

## 9. Audit and Compliance

All IAM operations are logged to `audit.audit_logs`:

| Event | Logged Details |
|---|---|
| Login success | User ID, IP address, timestamp, auth source |
| Login failure | Username attempted, IP address, failure reason |
| Logout | User ID, session duration |
| User created | Created user details, created by whom |
| User updated | Changed fields (old/new values), updated by whom |
| Role assigned | User, role, BU scope, assigned by whom |
| Role removed | User, role, removed by whom |
| Permission changed | Role, permissions added/removed, changed by whom |
| Password changed | User ID, changed by self or admin |
| Account locked | User ID, reason (failed attempts or admin) |
| Account unlocked | User ID, unlocked by whom |

---

## 10. Key Design Decisions Summary

| Decision | Choice | Alternatives Considered | Rationale |
|---|---|---|---|
| Auth mechanism | Form-based + session | JWT SPA, OAuth2 | Server-rendered app; sessions are simpler and more secure for this architecture |
| Password hashing | BCrypt (strength 12) | Argon2, SCrypt | Mature, no native dependencies, configurable strength |
| Authorization model | RBAC | ABAC, ACL | Maps to organizational structure, sufficient for GRC domain |
| Directory integration | Spring LDAP | Apache DS, custom | Native Spring ecosystem integration |
| Permission granularity | module:resource:action | Coarse (module-level only) | Fine-grained control needed for regulatory compliance (segregation of duties) |
| Session store | Server HttpSession | Redis, database | Simplest for Phase 1; Redis-backed Spring Session ready for horizontal scaling |
| BU scoping | Nullable BU on user_roles | Separate ABAC engine | Simple, sufficient for 3-level org hierarchy |
| LDAP user records | Shadow local record | LDAP-only (no local) | Enables consistent role management and audit trail |
