# Tenant and Organization Structure

## 1. Purpose

This document defines the multi-level organizational hierarchy model for the TSS GRC platform. The hierarchy supports future multi-tenancy at the top level, with organizations, business units, and departments forming the internal structure that scopes all business data.

---

## 2. Hierarchy Overview

```
Tenant (Organization with is_tenant = true)
 └── Organization (SUBSIDIARY, BRANCH)
      └── Business Unit (DIVISION, DEPARTMENT, TEAM, SECTION)
           └── Business Unit (nested, self-referencing)
```

- **Tenant** is not a separate table. It is represented by an `organization` record where `is_tenant = true`. This simplifies the model while preserving the ability to introduce full multi-tenancy later.
- **Organizations** form a tree under the tenant via `parent_org_id`.
- **Business Units** form a separate tree within each organization via `parent_bu_id`.
- **Departments** are modeled as business units with `bu_type = 'DEPARTMENT'`, avoiding a separate table.

---

## 3. Organization Entity

### 3.1 Table Definition

```sql
CREATE TABLE platform.organizations (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    code            VARCHAR(50) NOT NULL,
    name_en         NVARCHAR(300) NOT NULL,
    name_ar         NVARCHAR(300) NOT NULL,
    short_name_en   NVARCHAR(100) NULL,
    short_name_ar   NVARCHAR(100) NULL,
    org_type        VARCHAR(20) NOT NULL DEFAULT 'SUBSIDIARY',
    parent_org_id   BIGINT NULL REFERENCES platform.organizations(id),
    is_tenant       BIT NOT NULL DEFAULT 0,
    country_id      INT NULL REFERENCES platform.countries(id),
    region_id       INT NULL REFERENCES platform.regions(id),
    city            NVARCHAR(200) NULL,
    address_en      NVARCHAR(500) NULL,
    address_ar      NVARCHAR(500) NULL,
    postal_code     VARCHAR(20) NULL,
    phone           VARCHAR(30) NULL,
    email           VARCHAR(200) NULL,
    website         VARCHAR(300) NULL,
    industry_id     INT NULL REFERENCES platform.industries(id),
    tax_id          VARCHAR(50) NULL,
    cr_number       VARCHAR(50) NULL,           -- Commercial Registration (Saudi)
    logo_attachment_id BIGINT NULL,
    default_locale  VARCHAR(10) NOT NULL DEFAULT 'ar',
    default_timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Riyadh',
    default_currency_code CHAR(3) NOT NULL DEFAULT 'SAR',
    fiscal_year_start_month INT NOT NULL DEFAULT 1,
    is_active       BIT NOT NULL DEFAULT 1,
    is_deleted      BIT NOT NULL DEFAULT 0,
    created_by      BIGINT NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT NULL,
    updated_at      DATETIME2 NULL,
    deleted_by      BIGINT NULL,
    deleted_at      DATETIME2 NULL,
    CONSTRAINT UQ_org_code UNIQUE (code),
    CONSTRAINT CHK_org_type CHECK (org_type IN ('TENANT', 'SUBSIDIARY', 'BRANCH', 'DIVISION', 'JOINT_VENTURE')),
    CONSTRAINT CHK_tenant_no_parent CHECK (
        (is_tenant = 1 AND parent_org_id IS NULL) OR (is_tenant = 0)
    )
);

CREATE INDEX IX_organizations_parent ON platform.organizations(parent_org_id) WHERE is_deleted = 0;
CREATE INDEX IX_organizations_tenant ON platform.organizations(is_tenant) WHERE is_deleted = 0;
```

### 3.2 Column Descriptions

| Column | Type | Description |
|---|---|---|
| id | BIGINT IDENTITY | Auto-incrementing primary key |
| code | VARCHAR(50) | Unique organization code (e.g., "TSS-HQ") |
| name_en | NVARCHAR(300) | Full legal name in English |
| name_ar | NVARCHAR(300) | Full legal name in Arabic |
| short_name_en | NVARCHAR(100) | Abbreviated name for display |
| short_name_ar | NVARCHAR(100) | Abbreviated name in Arabic |
| org_type | VARCHAR(20) | TENANT, SUBSIDIARY, BRANCH, DIVISION, JOINT_VENTURE |
| parent_org_id | BIGINT | Self-reference for hierarchy; NULL for tenant root |
| is_tenant | BIT | True for the top-level tenant organization |
| country_id | INT | Reference to platform.countries |
| region_id | INT | Reference to platform.regions (Saudi province) |
| city | NVARCHAR(200) | City name |
| industry_id | INT | Reference to platform.industries |
| cr_number | VARCHAR(50) | Saudi Commercial Registration number |
| default_locale | VARCHAR(10) | Default language: 'ar' or 'en' |
| default_timezone | VARCHAR(50) | IANA timezone (e.g., 'Asia/Riyadh') |
| default_currency_code | CHAR(3) | ISO 4217 currency code |
| fiscal_year_start_month | INT | Month (1-12) when fiscal year begins |

---

## 4. Business Unit Entity

### 4.1 Table Definition

```sql
CREATE TABLE platform.business_units (
    id              BIGINT IDENTITY(1,1) PRIMARY KEY,
    org_id          BIGINT NOT NULL REFERENCES platform.organizations(id),
    code            VARCHAR(50) NOT NULL,
    name_en         NVARCHAR(300) NOT NULL,
    name_ar         NVARCHAR(300) NOT NULL,
    short_name_en   NVARCHAR(100) NULL,
    short_name_ar   NVARCHAR(100) NULL,
    bu_type         VARCHAR(30) NOT NULL DEFAULT 'BUSINESS_UNIT',
    parent_bu_id    BIGINT NULL REFERENCES platform.business_units(id),
    head_user_id    BIGINT NULL,                -- FK to iam.users (deferred, set after user creation)
    level           INT NOT NULL DEFAULT 1,     -- Depth in hierarchy (1 = top-level BU)
    description_en  NVARCHAR(500) NULL,
    description_ar  NVARCHAR(500) NULL,
    cost_center     VARCHAR(50) NULL,
    location        NVARCHAR(200) NULL,
    phone           VARCHAR(30) NULL,
    email           VARCHAR(200) NULL,
    is_active       BIT NOT NULL DEFAULT 1,
    is_deleted      BIT NOT NULL DEFAULT 0,
    created_by      BIGINT NOT NULL,
    created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT NULL,
    updated_at      DATETIME2 NULL,
    deleted_by      BIGINT NULL,
    deleted_at      DATETIME2 NULL,
    CONSTRAINT UQ_bu_code_org UNIQUE (org_id, code),
    CONSTRAINT CHK_bu_type CHECK (bu_type IN (
        'BUSINESS_UNIT', 'DIVISION', 'DEPARTMENT', 'TEAM', 'SECTION', 'BRANCH'
    )),
    CONSTRAINT CHK_bu_no_self_parent CHECK (id != parent_bu_id)
);

-- Deferred FK for head_user_id (cross-schema reference set up after iam.users table exists)
-- ALTER TABLE platform.business_units ADD CONSTRAINT FK_bu_head_user FOREIGN KEY (head_user_id) REFERENCES iam.users(id);

CREATE INDEX IX_bu_org ON platform.business_units(org_id) WHERE is_deleted = 0;
CREATE INDEX IX_bu_parent ON platform.business_units(parent_bu_id) WHERE is_deleted = 0;
CREATE INDEX IX_bu_type ON platform.business_units(bu_type) WHERE is_deleted = 0;
```

### 4.2 Column Descriptions

| Column | Type | Description |
|---|---|---|
| id | BIGINT IDENTITY | Auto-incrementing primary key |
| org_id | BIGINT | Owning organization |
| code | VARCHAR(50) | Unique code within organization (e.g., "IT-SEC") |
| name_en | NVARCHAR(300) | Full name in English |
| name_ar | NVARCHAR(300) | Full name in Arabic |
| bu_type | VARCHAR(30) | Type: BUSINESS_UNIT, DIVISION, DEPARTMENT, TEAM, SECTION, BRANCH |
| parent_bu_id | BIGINT | Self-reference for hierarchy; NULL for top-level BUs |
| head_user_id | BIGINT | BU head/manager (references iam.users) |
| level | INT | Depth in tree (auto-calculated on insert/move) |
| cost_center | VARCHAR(50) | Financial cost center code |

### 4.3 Modeling Departments

Departments are **not** a separate entity. They are `business_units` with `bu_type = 'DEPARTMENT'`. This avoids redundant tables while enabling:

- Uniform hierarchy traversal using a single recursive CTE.
- Consistent `bu_id` foreign keys across all business tables.
- Flexible nesting (a department can contain teams/sections).

Example structure:

```
Organization: TSS Holdings (is_tenant = true)
├── BU: Technology Division (bu_type = DIVISION, level = 1)
│   ├── BU: Information Security (bu_type = DEPARTMENT, level = 2)
│   │   ├── BU: SOC Team (bu_type = TEAM, level = 3)
│   │   └── BU: GRC Team (bu_type = TEAM, level = 3)
│   ├── BU: IT Infrastructure (bu_type = DEPARTMENT, level = 2)
│   └── BU: Software Development (bu_type = DEPARTMENT, level = 2)
├── BU: Operations Division (bu_type = DIVISION, level = 1)
│   ├── BU: Human Resources (bu_type = DEPARTMENT, level = 2)
│   └── BU: Finance (bu_type = DEPARTMENT, level = 2)
└── BU: Compliance Division (bu_type = DIVISION, level = 1)
    ├── BU: Internal Audit (bu_type = DEPARTMENT, level = 2)
    └── BU: Risk Management (bu_type = DEPARTMENT, level = 2)
```

---

## 5. Hierarchy Traversal Queries

### 5.1 Organization Tree (Recursive CTE)

```sql
-- Get full organization tree from tenant root
WITH OrgTree AS (
    -- Anchor: tenant root
    SELECT id, code, name_en, name_ar, org_type, parent_org_id,
           0 AS depth,
           CAST(code AS VARCHAR(MAX)) AS path
    FROM platform.organizations
    WHERE is_tenant = 1 AND is_deleted = 0

    UNION ALL

    -- Recursive: children
    SELECT o.id, o.code, o.name_en, o.name_ar, o.org_type, o.parent_org_id,
           t.depth + 1,
           CAST(t.path + '/' + o.code AS VARCHAR(MAX))
    FROM platform.organizations o
    INNER JOIN OrgTree t ON o.parent_org_id = t.id
    WHERE o.is_deleted = 0
)
SELECT * FROM OrgTree ORDER BY path;
```

### 5.2 Business Unit Tree Within Organization

```sql
-- Get BU hierarchy for a specific organization
WITH BuTree AS (
    -- Anchor: top-level BUs (no parent)
    SELECT id, org_id, code, name_en, name_ar, bu_type, parent_bu_id, head_user_id, level,
           0 AS depth,
           CAST(code AS VARCHAR(MAX)) AS path
    FROM platform.business_units
    WHERE org_id = @org_id AND parent_bu_id IS NULL AND is_deleted = 0

    UNION ALL

    -- Recursive: children
    SELECT bu.id, bu.org_id, bu.code, bu.name_en, bu.name_ar, bu.bu_type,
           bu.parent_bu_id, bu.head_user_id, bu.level,
           t.depth + 1,
           CAST(t.path + '/' + bu.code AS VARCHAR(MAX))
    FROM platform.business_units bu
    INNER JOIN BuTree t ON bu.parent_bu_id = t.id
    WHERE bu.is_deleted = 0
)
SELECT * FROM BuTree ORDER BY path;
```

### 5.3 All Descendants of a Business Unit

```sql
-- Get all BUs under a specific parent (for data scoping)
WITH Descendants AS (
    SELECT id FROM platform.business_units WHERE id = @parent_bu_id AND is_deleted = 0
    UNION ALL
    SELECT bu.id
    FROM platform.business_units bu
    INNER JOIN Descendants d ON bu.parent_bu_id = d.id
    WHERE bu.is_deleted = 0
)
SELECT id FROM Descendants;
```

### 5.4 Ancestors of a Business Unit (Path to Root)

```sql
-- Get path from a BU to the root for breadcrumb display
WITH Ancestors AS (
    SELECT id, code, name_en, name_ar, bu_type, parent_bu_id, level
    FROM platform.business_units WHERE id = @bu_id AND is_deleted = 0
    UNION ALL
    SELECT bu.id, bu.code, bu.name_en, bu.name_ar, bu.bu_type, bu.parent_bu_id, bu.level
    FROM platform.business_units bu
    INNER JOIN Ancestors a ON bu.id = a.parent_bu_id
    WHERE bu.is_deleted = 0
)
SELECT * FROM Ancestors ORDER BY level;
```

---

## 6. Data Scoping Strategy

### 6.1 Organization-Level Scoping

All business tables carry `org_id` as a mandatory NOT NULL foreign key. Every query includes `org_id` in its WHERE clause to enforce tenant isolation.

```sql
-- Example: all risks for an organization
SELECT * FROM risk.risks WHERE org_id = @org_id AND is_deleted = 0;
```

### 6.2 Business Unit-Level Scoping

Tables that support departmental scoping carry `bu_id` as an optional foreign key. When a user's permissions are scoped to a specific BU, queries include both `org_id` and a BU hierarchy filter.

```sql
-- Example: risks visible to a user scoped to a specific BU and its children
WITH UserBUs AS (
    SELECT id FROM platform.business_units WHERE id = @user_bu_id AND is_deleted = 0
    UNION ALL
    SELECT bu.id FROM platform.business_units bu
    INNER JOIN UserBUs u ON bu.parent_bu_id = u.id WHERE bu.is_deleted = 0
)
SELECT r.* FROM risk.risks r
WHERE r.org_id = @org_id
  AND r.bu_id IN (SELECT id FROM UserBUs)
  AND r.is_deleted = 0;
```

### 6.3 Scoping Matrix

| Entity | org_id | bu_id | Scoping Level |
|---|---|---|---|
| risk.risks | Required | Optional | Organization or BU |
| risk.risk_categories | Required | N/A | Organization |
| risk.risk_appetite_statements | Required | N/A | Organization |
| cyber.incidents | Required | Optional | Organization or BU |
| cyber.assets | Required | Optional | Organization or BU |
| bcm.critical_processes | Required | Optional | Organization or BU |
| bcm.bcm_plans | Required | N/A | Organization |
| privacy.data_inventory | Required | Optional | Organization or BU |
| privacy.processing_activities | Required | N/A | Organization |
| compliance.assessments | Required | N/A | Organization |
| reporting.dashboard_configurations | Required | N/A | Organization + User |
| audit.audit_logs | Required | N/A | Organization |
| iam.users | Required | Optional | Organization or BU |
| iam.user_roles | Required | Optional | Organization or BU (scope of role) |

---

## 7. Multi-Tenancy Considerations

### 7.1 Current Design (Single-Tenant with Multi-Org)

In the initial release, a single tenant (the top-level organization with `is_tenant = true`) contains all data. Organizations under the tenant represent subsidiaries, branches, or divisions.

### 7.2 Future Multi-Tenancy Path

When multi-tenancy is required:

1. **Data isolation**: Each tenant's data is already scoped by `org_id`. The tenant root org's `id` serves as the effective `tenant_id`.
2. **Row-level security**: SQL Server RLS policies can be layered on `org_id` to enforce isolation at the database level.
3. **Connection-level context**: `SESSION_CONTEXT` stores the current tenant's `org_id`, used by RLS predicates.

```sql
-- Future RLS policy example
CREATE SECURITY POLICY platform.TenantFilter
ADD FILTER PREDICATE platform.fn_tenant_filter(org_id) ON risk.risks,
ADD FILTER PREDICATE platform.fn_tenant_filter(org_id) ON cyber.incidents,
-- ... repeated for all business tables
WITH (STATE = ON);

-- Filter function checks session context
CREATE FUNCTION platform.fn_tenant_filter(@org_id BIGINT)
RETURNS TABLE
WITH SCHEMABINDING
AS
RETURN SELECT 1 AS result
WHERE @org_id IN (
    SELECT id FROM platform.organizations
    WHERE id = CAST(SESSION_CONTEXT(N'org_id') AS BIGINT)
       OR parent_org_id = CAST(SESSION_CONTEXT(N'org_id') AS BIGINT)
);
```

### 7.3 Schema Isolation (Alternative)

For high-security tenants, a schema-per-tenant approach is possible where each tenant gets its own set of schemas (`tenant1_risk`, `tenant1_cyber`, etc.), with the application dynamically selecting the correct schema. This is an advanced option reserved for regulated industries.
