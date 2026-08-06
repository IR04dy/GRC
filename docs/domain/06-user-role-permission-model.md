# User, Role, and Permission Model
> **HISTORIC — point-in-time snapshot (all files 2026-04-30). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


## 1. Purpose

This document defines the Identity and Access Management (IAM) data model for the TSS GRC platform. It covers user identity, role-based access control (RBAC), permission granularity, scope-aware role assignments, and the authorization evaluation flow.

---

## 2. IAM Schema Overview

```
iam.users ──── iam.user_roles ──── iam.roles ──── iam.role_permissions ──── iam.permissions
   │                │                                                            │
   │                ├── org_id (scope)                                           │
   │                └── bu_id (scope, nullable)                                 │
   │                                                                             │
   └── org_id, bu_id                                            module_code + resource_type + action
```

---

## 3. Users Entity

### 3.1 Table Definition

```sql
CREATE TABLE iam.users (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id         UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    username            VARCHAR(100) NOT NULL,
    email               VARCHAR(320) NOT NULL,
    first_name_en       NVARCHAR(100) NOT NULL,
    last_name_en        NVARCHAR(100) NOT NULL,
    first_name_ar       NVARCHAR(100) NULL,
    last_name_ar        NVARCHAR(100) NULL,
    display_name_en     NVARCHAR(200) NULL,
    display_name_ar     NVARCHAR(200) NULL,
    password_hash       VARCHAR(500) NULL,          -- NULL for SSO-only users
    org_id              BIGINT NOT NULL REFERENCES platform.organizations(id),
    bu_id               BIGINT NULL REFERENCES platform.business_units(id),
    job_title_en        NVARCHAR(200) NULL,
    job_title_ar        NVARCHAR(200) NULL,
    phone               VARCHAR(30) NULL,
    mobile              VARCHAR(30) NULL,
    avatar_attachment_id BIGINT NULL,
    preferred_locale    VARCHAR(10) NOT NULL DEFAULT 'ar',
    preferred_timezone  VARCHAR(50) NOT NULL DEFAULT 'Asia/Riyadh',
    is_active           BIT NOT NULL DEFAULT 1,
    is_locked           BIT NOT NULL DEFAULT 0,
    is_system           BIT NOT NULL DEFAULT 0,     -- System accounts (cannot delete)
    mfa_enabled         BIT NOT NULL DEFAULT 0,
    mfa_secret          VARCHAR(500) NULL,
    mfa_backup_codes    NVARCHAR(MAX) NULL,         -- JSON array of hashed backup codes
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until        DATETIME2 NULL,
    last_login_at       DATETIME2 NULL,
    last_login_ip       VARCHAR(45) NULL,
    password_changed_at DATETIME2 NULL,
    must_change_password BIT NOT NULL DEFAULT 0,
    email_verified      BIT NOT NULL DEFAULT 0,
    email_verified_at   DATETIME2 NULL,
    is_deleted          BIT NOT NULL DEFAULT 0,
    created_by          BIGINT NOT NULL,
    created_at          DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT NULL,
    updated_at          DATETIME2 NULL,
    deleted_by          BIGINT NULL,
    deleted_at          DATETIME2 NULL,
    CONSTRAINT UQ_user_external_id UNIQUE (external_id),
    CONSTRAINT UQ_user_username UNIQUE (username),
    CONSTRAINT UQ_user_email UNIQUE (email)
);

CREATE INDEX IX_users_org ON iam.users(org_id) WHERE is_deleted = 0;
CREATE INDEX IX_users_bu ON iam.users(bu_id) WHERE is_deleted = 0;
CREATE INDEX IX_users_active ON iam.users(is_active, is_locked) WHERE is_deleted = 0;
```

### 3.2 Column Descriptions

| Column | Type | Description |
|---|---|---|
| id | BIGINT IDENTITY | Internal auto-incrementing ID |
| external_id | UNIQUEIDENTIFIER | Public-facing UUID (used in APIs, never exposed internal id) |
| username | VARCHAR(100) | Unique login name |
| email | VARCHAR(320) | Unique email address |
| first_name_en / last_name_en | NVARCHAR(100) | Name in English (required) |
| first_name_ar / last_name_ar | NVARCHAR(100) | Name in Arabic (optional) |
| password_hash | VARCHAR(500) | bcrypt/argon2 hash; NULL for SSO-only users |
| org_id | BIGINT | Primary organization |
| bu_id | BIGINT | Primary business unit (nullable) |
| preferred_locale | VARCHAR(10) | 'ar' or 'en' for UI language |
| mfa_enabled | BIT | Whether MFA is active |
| failed_login_attempts | INT | Counter; resets on successful login |
| locked_until | DATETIME2 | Auto-lock expiry (NULL = not locked) |
| must_change_password | BIT | Force password change on next login |

---

## 4. Roles Entity

### 4.1 Table Definition

```sql
CREATE TABLE iam.roles (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    code            VARCHAR(100) NOT NULL,
    name_en         NVARCHAR(200) NOT NULL,
    name_ar         NVARCHAR(200) NOT NULL,
    description_en  NVARCHAR(500) NULL,
    description_ar  NVARCHAR(500) NULL,
    module_code     VARCHAR(50) NULL,           -- NULL = cross-module role
    org_id          BIGINT NULL REFERENCES platform.organizations(id), -- NULL = system-wide
    is_system       BIT NOT NULL DEFAULT 0,     -- System roles: non-deletable, non-editable
    is_active       BIT NOT NULL DEFAULT 1,
    is_deleted      BIT NOT NULL DEFAULT 0,
    created_by      BIGINT NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT NULL,
    updated_at      DATETIME2 NULL,
    CONSTRAINT UQ_role_code UNIQUE (code)
);
```

### 4.2 Pre-Defined System Roles

| code | name_en | name_ar | module_code | Description |
|---|---|---|---|---|
| PLATFORM_ADMIN | Platform Administrator | مدير المنصة | NULL | Full system access, manage tenants and configurations |
| ORG_ADMIN | Organization Administrator | مدير المنظمة | NULL | Full access within an organization |
| RISK_MANAGER | Risk Manager | مدير المخاطر | risk | Manage risk register, assessments, treatments |
| CISO | Chief Information Security Officer | رئيس أمن المعلومات | cyber | Manage cybersecurity program |
| BCM_MANAGER | BCM Manager | مدير استمرارية الأعمال | bcm | Manage BCM program, plans, exercises |
| DPO | Data Protection Officer | مسؤول حماية البيانات | privacy | Manage privacy program, PDPL compliance |
| COMPLIANCE_OFFICER | Compliance Officer | مسؤول الامتثال | compliance | Manage compliance assessments and frameworks |
| DATA_GOVERNANCE_OFFICER | Data Governance Officer | مسؤول حوكمة البيانات | privacy | Oversee data classification and inventory |
| AUDITOR | Auditor | مدقق | audit | Read-only access to audit logs and reports |
| BU_MANAGER | Business Unit Manager | مدير وحدة الأعمال | NULL | Manage BU-scoped data across modules |
| EXECUTIVE | Executive | تنفيذي | NULL | Dashboard and report access, high-level views |
| WORKFLOW_APPROVER | Workflow Approver | معتمد سير العمل | workflow | Approve/reject workflow tasks |
| STANDARD_USER | Standard User | مستخدم عادي | NULL | Basic read access, submit requests |

---

## 5. Permissions Entity

### 5.1 Table Definition

```sql
CREATE TABLE iam.permissions (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    code            VARCHAR(200) NOT NULL,      -- Format: module:resource:action
    name_en         NVARCHAR(300) NOT NULL,
    name_ar         NVARCHAR(300) NOT NULL,
    description_en  NVARCHAR(500) NULL,
    description_ar  NVARCHAR(500) NULL,
    module_code     VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    action          VARCHAR(30) NOT NULL,
    is_system       BIT NOT NULL DEFAULT 1,
    is_active       BIT NOT NULL DEFAULT 1,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_permission_code UNIQUE (code),
    CONSTRAINT CHK_permission_action CHECK (action IN (
        'CREATE', 'READ', 'UPDATE', 'DELETE', 'APPROVE', 'REJECT',
        'EXPORT', 'IMPORT', 'ASSIGN', 'SUBMIT', 'REVIEW', 'ADMIN'
    ))
);

CREATE INDEX IX_permissions_module ON iam.permissions(module_code, resource_type);
```

### 5.2 Permission Naming Convention

Format: `{module_code}:{resource_type}:{action}`

Examples:

| code | name_en | module_code | resource_type | action |
|---|---|---|---|---|
| `risk:risk_register:create` | Create Risk | risk | risk_register | CREATE |
| `risk:risk_register:read` | View Risks | risk | risk_register | READ |
| `risk:risk_register:update` | Update Risk | risk | risk_register | UPDATE |
| `risk:risk_register:delete` | Delete Risk | risk | risk_register | DELETE |
| `risk:risk_register:approve` | Approve Risk | risk | risk_register | APPROVE |
| `risk:risk_register:export` | Export Risks | risk | risk_register | EXPORT |
| `risk:risk_assessment:create` | Create Risk Assessment | risk | risk_assessment | CREATE |
| `risk:risk_assessment:read` | View Risk Assessments | risk | risk_assessment | READ |
| `risk:risk_treatment:create` | Create Treatment Plan | risk | risk_treatment | CREATE |
| `risk:risk_treatment:approve` | Approve Treatment Plan | risk | risk_treatment | APPROVE |
| `cyber:incident:create` | Report Incident | cyber | incident | CREATE |
| `cyber:incident:read` | View Incidents | cyber | incident | READ |
| `cyber:incident:update` | Update Incident | cyber | incident | UPDATE |
| `cyber:incident:approve` | Approve Incident Closure | cyber | incident | APPROVE |
| `cyber:asset:create` | Create Asset | cyber | asset | CREATE |
| `cyber:asset:read` | View Assets | cyber | asset | READ |
| `bcm:plan:create` | Create BCM Plan | bcm | plan | CREATE |
| `bcm:plan:approve` | Approve BCM Plan | bcm | plan | APPROVE |
| `bcm:exercise:create` | Create Exercise | bcm | exercise | CREATE |
| `bcm:bia:create` | Create BIA | bcm | bia | CREATE |
| `privacy:data_inventory:create` | Create Data Inventory Item | privacy | data_inventory | CREATE |
| `privacy:data_inventory:read` | View Data Inventory | privacy | data_inventory | READ |
| `privacy:assessment:create` | Create Privacy Assessment | privacy | assessment | CREATE |
| `privacy:breach:create` | Report Privacy Breach | privacy | breach | CREATE |
| `privacy:breach:approve` | Approve Breach Notification | privacy | breach | APPROVE |
| `privacy:consent:read` | View Consent Records | privacy | consent | READ |
| `compliance:assessment:create` | Create Compliance Assessment | compliance | assessment | CREATE |
| `compliance:assessment:read` | View Compliance Assessments | compliance | assessment | READ |
| `compliance:finding:create` | Create Finding | compliance | finding | CREATE |
| `compliance:evidence:submit` | Submit Evidence | compliance | evidence | SUBMIT |
| `compliance:evidence:review` | Review Evidence | compliance | evidence | REVIEW |
| `reporting:dashboard:read` | View Dashboards | reporting | dashboard | READ |
| `reporting:dashboard:admin` | Manage Dashboards | reporting | dashboard | ADMIN |
| `reporting:report:export` | Export Reports | reporting | report | EXPORT |
| `audit:audit_log:read` | View Audit Logs | audit | audit_log | READ |
| `audit:audit_log:export` | Export Audit Logs | audit | audit_log | EXPORT |
| `iam:user:create` | Create User | iam | user | CREATE |
| `iam:user:read` | View Users | iam | user | READ |
| `iam:user:update` | Update User | iam | user | UPDATE |
| `iam:role:assign` | Assign Roles | iam | role | ASSIGN |
| `platform:control:create` | Create Control | platform | control | CREATE |
| `platform:control:read` | View Controls | platform | control | READ |
| `platform:control:update` | Update Control | platform | control | UPDATE |
| `platform:organization:admin` | Manage Organizations | platform | organization | ADMIN |
| `workflow:task:assign` | Assign Tasks | workflow | task | ASSIGN |
| `workflow:task:approve` | Approve/Reject Tasks | workflow | task | APPROVE |

### 5.3 Complete Permission Count by Module

| Module | Resource Types | Actions per Resource | Estimated Permissions |
|---|---|---|---|
| risk | 6 (register, assessment, treatment, appetite, indicator, category) | 4-6 | ~30 |
| cyber | 5 (incident, asset, vulnerability, threat, control) | 4-6 | ~25 |
| bcm | 5 (plan, exercise, bia, process, incident) | 4-6 | ~25 |
| privacy | 6 (inventory, activity, assessment, consent, breach, ropa) | 4-6 | ~30 |
| compliance | 5 (assessment, finding, gap, remediation, evidence) | 4-6 | ~25 |
| reporting | 3 (dashboard, report, filter) | 3-4 | ~10 |
| audit | 2 (audit_log, data_access) | 2-3 | ~5 |
| iam | 3 (user, role, permission) | 4-6 | ~15 |
| platform | 4 (organization, business_unit, control, lookup) | 4-6 | ~20 |
| workflow | 3 (definition, instance, task) | 4-6 | ~15 |
| **Total** | | | **~200** |

---

## 6. User-Role Assignment

### 6.1 Table Definition

```sql
CREATE TABLE iam.user_roles (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES iam.users(id),
    role_id         BIGINT NOT NULL REFERENCES iam.roles(id),
    org_id          BIGINT NOT NULL REFERENCES platform.organizations(id),
    bu_id           BIGINT NULL REFERENCES platform.business_units(id), -- NULL = org-wide
    valid_from      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    valid_to        DATETIME2 NULL,             -- NULL = no expiry
    assigned_by     BIGINT NOT NULL REFERENCES iam.users(id),
    assigned_at     DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    revoked_by      BIGINT NULL REFERENCES iam.users(id),
    revoked_at      DATETIME2 NULL,
    is_active       BIT NOT NULL DEFAULT 1,
    notes           NVARCHAR(500) NULL,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_user_role_scope UNIQUE (user_id, role_id, org_id, COALESCE(bu_id, 0)),
    CONSTRAINT CHK_valid_date_range CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX IX_user_roles_user ON iam.user_roles(user_id) WHERE is_active = 1;
CREATE INDEX IX_user_roles_role ON iam.user_roles(role_id) WHERE is_active = 1;
CREATE INDEX IX_user_roles_org ON iam.user_roles(org_id) WHERE is_active = 1;
```

### 6.2 Scope Rules

| Scenario | org_id | bu_id | Meaning |
|---|---|---|---|
| Organization-wide role | Required | NULL | User has the role across entire organization |
| BU-scoped role | Required | Required | User has the role only within that BU and its children |
| Time-limited role | Required | Optional | Role valid only between valid_from and valid_to |

---

## 7. Role-Permission Assignment

### 7.1 Table Definition

```sql
CREATE TABLE iam.role_permissions (
    role_id         BIGINT NOT NULL REFERENCES iam.roles(id),
    permission_id   BIGINT NOT NULL REFERENCES iam.permissions(id),
    created_by      BIGINT NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_role_permissions PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX IX_role_permissions_permission ON iam.role_permissions(permission_id);
```

---

## 8. Authorization Evaluation Flow

### 8.1 Decision Process

```
1. Extract user identity from JWT/session
2. Load active user_roles for the user
   - Filter: is_active = 1
   - Filter: valid_from <= NOW AND (valid_to IS NULL OR valid_to > NOW)
3. For each user_role, load role_permissions via role_id
4. Collect all permission codes into a set
5. Check requested action:
   - Input: module_code, resource_type, action, target_org_id, target_bu_id
   - Match: permission.code == "{module_code}:{resource_type}:{action}"
   - Scope check: user_role.org_id == target_org_id
   - BU scope: if user_role.bu_id is set, target_bu_id must be within BU hierarchy
6. Grant or deny
```

### 8.2 Authorization Query

```sql
-- Check if user has a specific permission for a given org/bu scope
DECLARE @user_id BIGINT = 123;
DECLARE @permission_code VARCHAR(200) = 'risk:risk_register:create';
DECLARE @target_org_id BIGINT = 1;
DECLARE @target_bu_id BIGINT = 5;

-- Get all BU ancestors for hierarchical check
WITH BuAncestors AS (
    SELECT id, parent_bu_id FROM platform.business_units WHERE id = @target_bu_id AND is_deleted = 0
    UNION ALL
    SELECT bu.id, bu.parent_bu_id FROM platform.business_units bu
    INNER JOIN BuAncestors a ON bu.id = a.parent_bu_id WHERE bu.is_deleted = 0
)
SELECT CASE WHEN EXISTS (
    SELECT 1
    FROM iam.user_roles ur
    INNER JOIN iam.role_permissions rp ON ur.role_id = rp.role_id
    INNER JOIN iam.permissions p ON rp.permission_id = p.id
    WHERE ur.user_id = @user_id
      AND ur.is_active = 1
      AND ur.valid_from <= SYSUTCDATETIME()
      AND (ur.valid_to IS NULL OR ur.valid_to > SYSUTCDATETIME())
      AND ur.org_id = @target_org_id
      AND (ur.bu_id IS NULL OR ur.bu_id IN (SELECT id FROM BuAncestors))
      AND p.code = @permission_code
      AND p.is_active = 1
) THEN 1 ELSE 0 END AS has_permission;
```

### 8.3 Role Hierarchy (Effective Permissions)

| Role | Inherits From | Additional Permissions |
|---|---|---|
| PLATFORM_ADMIN | -- | All permissions (implicit superuser) |
| ORG_ADMIN | -- | All permissions within org scope |
| RISK_MANAGER | STANDARD_USER | risk:* (all risk module permissions) |
| CISO | STANDARD_USER | cyber:*, risk:risk_register:read |
| BCM_MANAGER | STANDARD_USER | bcm:*, risk:risk_register:read |
| DPO | STANDARD_USER | privacy:*, compliance:assessment:read |
| COMPLIANCE_OFFICER | STANDARD_USER | compliance:*, platform:control:read |
| AUDITOR | STANDARD_USER | audit:* (read/export only), read access to all modules |
| BU_MANAGER | STANDARD_USER | Read/update for BU-scoped data across modules |
| EXECUTIVE | STANDARD_USER | reporting:dashboard:read, reporting:report:export |
| WORKFLOW_APPROVER | STANDARD_USER | workflow:task:approve |
| STANDARD_USER | -- | Basic read permissions for assigned module |

Note: Role inheritance is implemented via role_permissions mapping, not database inheritance. Each role explicitly lists all its permissions in `iam.role_permissions`.

---

## 9. Session Management

```sql
CREATE TABLE iam.user_sessions (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    session_token   VARCHAR(500) NOT NULL,
    user_id         BIGINT NOT NULL REFERENCES iam.users(id),
    org_id          BIGINT NOT NULL REFERENCES platform.organizations(id),
    ip_address      VARCHAR(45) NOT NULL,
    user_agent      NVARCHAR(500) NULL,
    started_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    expires_at      DATETIME2 NOT NULL,
    last_activity_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    is_active       BIT NOT NULL DEFAULT 1,
    terminated_at   DATETIME2 NULL,
    terminated_reason VARCHAR(50) NULL,         -- LOGOUT, TIMEOUT, ADMIN_REVOKE, PASSWORD_CHANGE
    CONSTRAINT UQ_session_token UNIQUE (session_token)
);

CREATE INDEX IX_sessions_user ON iam.user_sessions(user_id) WHERE is_active = 1;
CREATE INDEX IX_sessions_expires ON iam.user_sessions(expires_at) WHERE is_active = 1;
```

---

## 10. Login Attempts

```sql
CREATE TABLE iam.login_attempts (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    ip_address      VARCHAR(45) NOT NULL,
    user_agent      NVARCHAR(500) NULL,
    attempted_at    DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    success         BIT NOT NULL,
    failure_reason  VARCHAR(100) NULL,          -- INVALID_PASSWORD, ACCOUNT_LOCKED, MFA_FAILED, ACCOUNT_DISABLED
    user_id         BIGINT NULL REFERENCES iam.users(id)
);

CREATE INDEX IX_login_attempts_user ON iam.login_attempts(username, attempted_at);
CREATE INDEX IX_login_attempts_ip ON iam.login_attempts(ip_address, attempted_at);
```

---

## 11. Account Lockout Policy

| Parameter | Default Value | Description |
|---|---|---|
| max_failed_attempts | 5 | Lock after N consecutive failures |
| lockout_duration_minutes | 30 | Auto-unlock after N minutes |
| password_min_length | 12 | Minimum password length |
| password_require_uppercase | true | Require uppercase letter |
| password_require_lowercase | true | Require lowercase letter |
| password_require_digit | true | Require numeric digit |
| password_require_special | true | Require special character |
| password_expiry_days | 90 | Force change after N days |
| password_history_count | 5 | Prevent reuse of last N passwords |
| session_timeout_minutes | 30 | Idle session timeout |
| session_max_duration_hours | 12 | Maximum session duration |
| mfa_required_for_admins | true | Enforce MFA for admin roles |

These parameters are stored in `platform.system_configurations` and can be overridden per organization.
